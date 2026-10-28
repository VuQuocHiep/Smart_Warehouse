package com.example.iot.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.iot.dto.request.StockRequest;
import com.example.iot.entity.MonitoredShelf;
import com.example.iot.entity.ProductEntity;
import com.example.iot.entity.ProductEnvironmentAlert;
import com.example.iot.entity.ShelfStockEntity;
import com.example.iot.entity.StockTransactionEntity.StockType;
import com.example.iot.entity.WarehouseEntity;
import com.example.iot.repository.MonitoredShelfRepository;
import com.example.iot.repository.ProductEnvironmentAlertRepository;
import com.example.iot.repository.ProductRepository;
import com.example.iot.repository.ShelfStockRepository;
import com.example.iot.repository.StockTransactionRepository;
import com.example.iot.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

/**
 * Đề xuất vị trí xếp hàng: chấm điểm từng kệ theo môi trường, sức chứa, tần suất xuất,
 * gom cùng loại và cảnh báo; mỗi kệ kèm danh sách lý do để người dùng hiểu vì sao.
 */
@Service
@RequiredArgsConstructor
public class PlacementService {
    private static final int DEFAULT_CAPACITY = 100;

    private final MonitoredShelfRepository shelves;
    private final WarehouseRepository warehouses;
    private final ProductRepository products;
    private final ShelfStockRepository shelfStocks;
    private final ProductEnvironmentAlertRepository alerts;
    private final StockTransactionRepository transactions;

    public record Option(String shelfId, String shelfName, String warehouseName, int score, int capacity,
            int used, int free, boolean eligible, boolean recommended, List<String> reasons) {
    }

    public record Result(String sku, String productName, int quantity, List<Option> options) {
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public List<Result> suggest(StockRequest request) {
        List<MonitoredShelf> allShelves = new ArrayList<>(shelves.findAll());
        allShelves.removeIf(s -> !java.util.Objects.equals(request.getWarehouseId(), s.getWarehouseId()));
        allShelves.sort(Comparator.comparing((MonitoredShelf s) -> String.valueOf(s.getWarehouseId()))
                .thenComparing(s -> String.valueOf(s.getName())));

        Set<String> warehouseIds = new HashSet<>();
        for (MonitoredShelf s : allShelves) {
            if (s.getWarehouseId() != null) {
                warehouseIds.add(s.getWarehouseId());
            }
        }
        Map<String, WarehouseEntity> warehouseById = new HashMap<>();
        for (WarehouseEntity w : warehouses.findAllById(warehouseIds)) {
            warehouseById.put(w.getWarehouseId(), w);
        }

        // Thứ tự kệ trong từng kho (0 = đầu dãy, coi là dễ lấy hàng nhất)
        Map<String, Integer> orderInWarehouse = new HashMap<>();
        Map<String, Integer> counter = new HashMap<>();
        for (MonitoredShelf s : allShelves) {
            int idx = counter.merge(String.valueOf(s.getWarehouseId()), 1, Integer::sum) - 1;
            orderInWarehouse.put(s.getId(), idx);
        }

        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Result> results = new ArrayList<>();

        for (StockRequest.Item item : request.getItems()) {
            ProductEntity product = products.findBySkuAndDeletedFalse(item.getSku().trim())
                    .orElseThrow(() -> new RuntimeException("SKU không tồn tại: " + item.getSku()));
            int qty = item.getQuantity();
            long out30 = transactions.sumOutSince(product.getProductId(), StockType.OUT, since);

            List<Option> options = new ArrayList<>();
            for (MonitoredShelf shelf : allShelves) {
                options.add(evaluate(shelf, product, qty, out30,
                        warehouseById.get(shelf.getWarehouseId()),
                        orderInWarehouse.getOrDefault(shelf.getId(), 0)));
            }

            options.sort((a, b) -> {
                if (a.eligible() != b.eligible()) {
                    return a.eligible() ? -1 : 1;
                }
                if (a.score() != b.score()) {
                    return Integer.compare(b.score(), a.score());
                }
                return Integer.compare(b.free(), a.free());
            });

            if (!options.isEmpty() && options.get(0).eligible()) {
                Option best = options.get(0);
                options.set(0, new Option(best.shelfId(), best.shelfName(), best.warehouseName(), best.score(),
                        best.capacity(), best.used(), best.free(), true, true, best.reasons()));
            }

            results.add(new Result(product.getSku(), product.getName(), qty, options));
        }
        return results;
    }

    private Option evaluate(MonitoredShelf shelf, ProductEntity product, int qty, long out30,
            WarehouseEntity warehouse, int orderIdx) {
        int capacity = Math.max(1, shelf.getCapacity() == null ? DEFAULT_CAPACITY : shelf.getCapacity());
        int used = (int) shelfStocks.sumQuantity(shelf.getId());
        int free = capacity - used;

        List<String> reasons = new ArrayList<>();
        boolean eligible = true;
        int score = 50;

        // 1. Môi trường: Kho 2-4 dùng số đo giả lập, Kho 1 dùng số đo thật từ ESP
        Double temperature = shelf.getTemperature();
        Double humidity = shelf.getHumidity();
        boolean simulated = false;
        if (warehouse != null && warehouse.getMonitoringSlot() != null
                && warehouse.getMonitoringSlot() >= 2 && warehouse.getMonitoringSlot() <= 4) {
            var sim = WarehouseMonitoringService.simulate(warehouse.getMonitoringSlot(), LocalDateTime.now());
            temperature = sim.temperature();
            humidity = sim.humidity();
            simulated = true;
        }
        String source = simulated ? " (số đo giả lập)" : "";
        List<String> violations = ShelfEnvironmentService.violations(product, temperature, humidity);
        boolean noReading = temperature == null && humidity == null;
        boolean hasThresholds = product.getMinTemperature() != null || product.getMaxTemperature() != null
                || product.getMinHumidity() != null || product.getMaxHumidity() != null;

        if (!violations.isEmpty()) {
            eligible = false;
            reasons.add("Môi trường không phù hợp: " + String.join("; ", violations) + source);
        } else if (noReading) {
            reasons.add("Kệ chưa có số đo môi trường");
        } else if (!hasThresholds) {
            score += 10;
            reasons.add("Sản phẩm không yêu cầu bảo quản riêng, môi trường kệ "
                    + describe(temperature, humidity) + source);
        } else {
            score += 25;
            reasons.add("Môi trường phù hợp ngưỡng bảo quản (" + describe(temperature, humidity) + ")" + source);
        }

        // 2. Sức chứa
        if (free < qty) {
            eligible = false;
            reasons.add("Không đủ sức chứa (còn trống " + Math.max(free, 0) + ", cần " + qty + ")");
        } else {
            score += Math.round(15f * (free - qty) / capacity);
            reasons.add("Còn trống " + free + "/" + capacity + " chỗ");
        }

        // 3. Gom cùng loại
        int onShelf = shelfStocks.findByShelfIdAndProductId(shelf.getId(), product.getProductId())
                .map(ShelfStockEntity::getQuantity).orElse(0);
        if (onShelf > 0) {
            score += 5;
            reasons.add("Kệ đã có sẵn " + onShelf + " hàng này, gom cùng chỗ");
        }
        if (shelf.getProductIds() != null && shelf.getProductIds().contains(product.getProductId())) {
            score += 10;
            reasons.add("Kệ được gán cho sản phẩm này ở mục giám sát kệ");
        }

        // 4. Tần suất xuất trong 30 ngày
        if (out30 >= 20 && orderIdx < 2) {
            score += 5;
            reasons.add("Hàng xuất nhiều (" + out30 + " trong 30 ngày), ưu tiên kệ đầu dãy dễ lấy");
        } else if (out30 < 5 && orderIdx >= 2) {
            score += 3;
            reasons.add("Hàng xuất ít (" + out30 + " trong 30 ngày), để kệ đầu dãy cho hàng xuất nhiều");
        }

        // 5. Kệ đang có cảnh báo môi trường
        int alertCount = 0;
        for (ProductEnvironmentAlert ignored : alerts.findByShelfIdAndResolvedAtIsNull(shelf.getId())) {
            alertCount++;
        }
        if (alertCount > 0) {
            score -= 20;
            reasons.add("Kệ đang có " + alertCount + " cảnh báo môi trường");
        }

        score = eligible ? Math.max(0, Math.min(100, score)) : 0;
        return new Option(shelf.getId(), shelf.getName(), warehouse == null ? "" : warehouse.getName(), score,
                capacity, used, Math.max(free, 0), eligible, false, reasons);
    }

    private static String describe(Double temperature, Double humidity) {
        return fmt(temperature, "°C") + ", " + fmt(humidity, "%");
    }

    private static String fmt(Double value, String unit) {
        return value == null ? "?" : (Math.round(value * 10) / 10.0) + unit;
    }
}
