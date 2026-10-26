package com.example.iot.service;

import com.example.iot.entity.ProductEntity;
import com.example.iot.entity.WarehouseEntity;
import com.example.iot.repository.MonitoredShelfRepository;
import com.example.iot.repository.ProductRepository;
import com.example.iot.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DemandForecastService {
    private final WarehouseRepository warehouses;
    private final ProductRepository products;
    private final MonitoredShelfRepository shelves;
    private final RestClient client;
    private final boolean enabled;
    private final String token;

    public record Pair(String warehouseId, String warehouseName, String productId, String sku, String productName) {}

    public DemandForecastService(WarehouseRepository warehouses, ProductRepository products,
            MonitoredShelfRepository shelves,
            @Value("${demand.service.url:http://127.0.0.1:8091}") String url,
            @Value("${demand.service.token:}") String token,
            @Value("${demand.service.enabled:false}") boolean enabled) {
        this.warehouses = warehouses;
        this.products = products;
        this.shelves = shelves;
        this.token = token;
        this.enabled = enabled;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public List<Pair> catalog() {
        var warehouseMap = warehouses.findByDeletedFalse().stream().collect(
                Collectors.toMap(WarehouseEntity::getWarehouseId, Function.identity()));
        var productMap = products.findByDeletedFalse().stream().collect(
                Collectors.toMap(ProductEntity::getProductId, Function.identity()));
        Map<String, Pair> pairs = new TreeMap<>();
        shelves.findAll().forEach(shelf -> {
            var warehouse = warehouseMap.get(shelf.getWarehouseId());
            if (warehouse == null) return;
            shelf.getProductIds().forEach(id -> {
                var product = productMap.get(id);
                if (product != null) pairs.put(warehouse.getWarehouseId() + ":" + id,
                        new Pair(warehouse.getWarehouseId(), warehouse.getName(), id, product.getSku(), product.getName()));
            });
        });
        return new ArrayList<>(pairs.values());
    }

    public String startDemo() {
        var pairs = catalog();
        if (pairs.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cần gán sản phẩm vào kệ của kho trước khi chạy dự báo.");
        if (pairs.size() > 64) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Mỗi lần dự báo hỗ trợ tối đa 64 cặp kho/sản phẩm.");
        return exchange("/jobs/demo", Map.of("pairs", pairs, "seed", 42));
    }

    public String latest() { return exchange("/forecasts/latest", null); }
    public String job(UUID id) { return exchange("/jobs/" + id, null); }

    private String exchange(String path, Object body) {
        if (!enabled || token.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Dịch vụ dự báo chưa được bật hoặc chưa cấu hình token.");
        try {
            var request = body == null ? client.get().uri(path) : client.post().uri(path).body(body);
            return request.header("X-Service-Token", token).retrieve().body(String.class);
        } catch (RestClientResponseException ex) {
            var status = ex.getStatusCode().value() == 409 ? HttpStatus.CONFLICT :
                    ex.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, status == HttpStatus.CONFLICT
                    ? "Một tác vụ huấn luyện đang chạy." : "Dịch vụ dự báo trả lỗi; kiểm tra cấu hình hoặc mã tác vụ.");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không kết nối được dịch vụ dự báo. Kiểm tra Python service ở cổng 8091.");
        }
    }
}
