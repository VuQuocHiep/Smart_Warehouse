package com.example.iot.service;

import com.example.iot.entity.*;
import com.example.iot.repository.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShelfEnvironmentService {
    private final MonitoredShelfRepository shelves;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final ProductEnvironmentAlertRepository alerts;
    private final WarehouseEnvironmentReadingRepository readings;
    private final HybridRiskService ai;

    public record ShelfRequest(@NotBlank @Size(max=100) String name, @NotBlank String warehouseId,
            @Size(max=100) String deviceCode, @NotNull Set<@NotBlank String> productIds) {}
    public record Reading(@NotBlank String deviceCode,
            @NotNull @DecimalMin("-100") @DecimalMax("200") Double temperature,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double humidity,
            @DecimalMin("0") Double gas, @DecimalMin("0") Double gasTvocPpb) {
        public Reading(String deviceCode, Double temperature, Double humidity) { this(deviceCode, temperature, humidity, null, null); }
        public Reading(String deviceCode, Double temperature, Double humidity, Double gas) { this(deviceCode, temperature, humidity, gas, null); }
    }
    public record ShelfView(MonitoredShelf shelf, List<ProductEntity> products) {}

    @Transactional(readOnly=true)
    public List<ShelfView> list() {
        return shelves.findAll().stream().map(s -> new ShelfView(s, products.findAllById(s.getProductIds())
                .stream().filter(p -> !p.isDeleted()).toList())).toList();
    }

    @Transactional
    public MonitoredShelf save(String id, ShelfRequest request) {
        WarehouseEntity warehouse = warehouses.findByWarehouseIdAndDeletedFalse(request.warehouseId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kho không tồn tại"));
        if (warehouse.getMonitoringSlot() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hãy chọn Kho 1, 2, 3 hoặc 4");
        for (String productId : request.productIds()) {
            if (!products.existsByProductIdAndDeletedFalse(productId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm không tồn tại");
            }
        }
        MonitoredShelf shelf = id == null ? new MonitoredShelf() : shelves.findForUpdateById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kệ"));
        String code = request.deviceCode() == null || request.deviceCode().isBlank()
                ? shelf.getDeviceCode() == null ? "SHELF_" + UUID.randomUUID() : shelf.getDeviceCode()
                : request.deviceCode().trim();
        shelves.findByDeviceCode(code).ifPresent(other -> {
            if (!Objects.equals(other.getId(), shelf.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "ESP đã được gắn với kệ khác");
            }
        });
        if (!Objects.equals(shelf.getDeviceCode(), code) || !Objects.equals(shelf.getWarehouseId(), request.warehouseId())) {
            shelf.setTemperature(null);
            shelf.setHumidity(null);
            shelf.setRecordedAt(null);
        }
        shelf.setName(request.name().trim());
        shelf.setWarehouseId(request.warehouseId());
        shelf.setDeviceCode(code);
        shelf.setProductIds(new HashSet<>(request.productIds()));
        if (warehouse.getMonitoringSlot() == 1) {
            readings.findTopByOrderByRecordedAtDesc().ifPresent(latest -> {
                shelf.setTemperature(latest.getTemperature());
                shelf.setHumidity(latest.getHumidity());
                shelf.setRecordedAt(latest.getRecordedAt());
            });
        }
        if (warehouse.getMonitoringSlot() != 1) {
            shelf.setTemperature(null);
            shelf.setHumidity(null);
            shelf.setRecordedAt(null);
        }
        shelves.save(shelf);
        if (warehouse.getMonitoringSlot() == 1) {
            evaluate(shelf, shelf.getTemperature(), shelf.getHumidity(), false, warehouse.getName());
        } else {
            var simulated = WarehouseMonitoringService.simulate(warehouse.getMonitoringSlot(), LocalDateTime.now());
            evaluate(shelf, simulated.temperature(), simulated.humidity(), true, warehouse.getName());
        }
        return shelf;
    }

    @Transactional
    public WarehouseEnvironmentReading receive(Reading reading) {
        WarehouseEntity warehouse = warehouses.findByMonitoringDeviceCodeAndDeletedFalse(reading.deviceCode())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ESP chưa được gắn với kho"));
        if (!Integer.valueOf(1).equals(warehouse.getMonitoringSlot())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ Kho 1 nhận dữ liệu ESP; Kho 2–4 dùng giả lập");
        }
        LocalDateTime now = LocalDateTime.now();
        WarehouseEnvironmentReading stored = new WarehouseEnvironmentReading();
        stored.setDeviceCode(reading.deviceCode()); stored.setTemperature(reading.temperature());
        stored.setHumidity(reading.humidity()); stored.setGas(reading.gas()); stored.setRecordedAt(now);
        stored.setGasTvocPpb(reading.gasTvocPpb());
        var prediction = ai.predict(reading.deviceCode(), reading.temperature(), reading.humidity(), reading.gas(), reading.gasTvocPpb());
        stored.setRiskLevel(prediction.riskLevel());
        stored.setConfidence(prediction.confidence());
        stored.setAiStatus(prediction.aiStatus());
        stored.setActionCode(prediction.actionCode());
        stored.setModelVersion(prediction.modelVersion());
        stored.setPredictionStatus(prediction.predictionStatus());
        stored.setAiRiskLevel(prediction.aiRiskLevel());
        stored.setGasRiskLevel(prediction.gasRiskLevel());
        stored.setDecisionSource(prediction.decisionSource());
        readings.save(stored);
        for (MonitoredShelf affected : shelves.findByWarehouseId(warehouse.getWarehouseId())) {
            affected.setTemperature(reading.temperature());
            affected.setHumidity(reading.humidity());
            affected.setRecordedAt(now);
            evaluate(affected, affected.getTemperature(), affected.getHumidity(), false, warehouse.getName());
            shelves.save(affected);
        }
        return stored;
    }

    @Transactional
    public void refreshSimulatedWarehouses() {
        LocalDateTime now = LocalDateTime.now();
        for (WarehouseEntity warehouse : warehouses.findByMonitoringSlotIsNotNullAndDeletedFalseOrderByMonitoringSlotAsc()) {
            if (warehouse.getMonitoringSlot() < 2 || warehouse.getMonitoringSlot() > 4) continue;
            var reading = WarehouseMonitoringService.simulate(warehouse.getMonitoringSlot(), now);
            for (MonitoredShelf shelf : shelves.findByWarehouseId(warehouse.getWarehouseId())) {
                shelves.findForUpdateById(shelf.getId()).ifPresent(locked -> {
                    if (warehouse.getWarehouseId().equals(locked.getWarehouseId())) {
                        evaluate(locked, reading.temperature(), reading.humidity(), true, warehouse.getName());
                    }
                });
            }
        }
    }

    private void evaluate(MonitoredShelf shelf, Double temperature, Double humidity, boolean simulated, String warehouseName) {
        Map<String, ProductEnvironmentAlert> active = new HashMap<>();
        for (ProductEnvironmentAlert alert : alerts.findByShelfIdAndResolvedAtIsNull(shelf.getId())) {
            active.put(alert.getProductId(), alert);
        }
        for (ProductEntity product : products.findAllById(shelf.getProductIds())) {
            if (product.isDeleted()) continue;
            List<String> violations = violations(product, temperature, humidity);
            if (violations.isEmpty()) continue;
            ProductEnvironmentAlert existing = active.get(product.getProductId());
            if (existing != null && existing.isSimulated() == simulated) {
                active.remove(product.getProductId());
                continue;
            }
            ProductEnvironmentAlert alert = new ProductEnvironmentAlert();
            alert.setShelfId(shelf.getId());
            alert.setProductId(product.getProductId());
            alert.setSimulated(simulated);
            alert.setCreatedAt(LocalDateTime.now());
            alert.setMessage((simulated ? "[Giả lập] " : "") + warehouseName + " · Kệ " + shelf.getName() + " · " + product.getName() + " (" + product.getSku()
                    + "): " + String.join("; ", violations));
            alerts.save(alert);
        }
        for (ProductEnvironmentAlert recovered : active.values()) {
            recovered.setResolvedAt(LocalDateTime.now());
            alerts.save(recovered);
        }
    }

    public static List<String> violations(ProductEntity p, Double temperature, Double humidity) {
        List<String> result = new ArrayList<>();
        check(result, "Nhiệt độ", temperature, p.getMinTemperature(), p.getMaxTemperature(), "°C");
        check(result, "Độ ẩm", humidity, p.getMinHumidity(), p.getMaxHumidity(), "%");
        return result;
    }

    private static void check(List<String> result, String label, Double value, Double min, Double max, String unit) {
        if (value == null) return;
        if (min != null && value < min) result.add(label + " " + value + unit + " thấp hơn " + min + unit);
        if (max != null && value > max) result.add(label + " " + value + unit + " cao hơn " + max + unit);
    }
}
