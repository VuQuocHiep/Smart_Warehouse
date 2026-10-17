package com.example.iot.service;

import com.example.iot.entity.*;
import com.example.iot.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class WarehouseMonitoringService {
    private final WarehouseRepository warehouses;
    private final MonitoredShelfRepository shelves;
    private final WarehouseEnvironmentReadingRepository readings;
    private final HybridRiskService risk;

    public WarehouseMonitoringService(WarehouseRepository warehouses, MonitoredShelfRepository shelves,
            WarehouseEnvironmentReadingRepository readings, HybridRiskService risk) {
        this.warehouses = warehouses;
        this.shelves = shelves;
        this.readings = readings;
        this.risk = risk;
    }

    public record Environment(Double temperature, Double humidity, Double gas, LocalDateTime recordedAt, boolean simulated,
            Integer riskLevel, Double confidence, String aiStatus, String actionCode, String modelVersion,
            String predictionStatus, boolean controlAllowed, Integer aiRiskLevel, Integer gasRiskLevel, String decisionSource) {
        public Environment(Double temperature, Double humidity, Double gas, LocalDateTime recordedAt, boolean simulated) {
            this(temperature, humidity, gas, recordedAt, simulated, null, null, null, null, null, "NO_PREDICTION", false, null, null, null);
        }
    }
    public record WarehouseView(WarehouseEntity warehouse, Environment reading, int shelfCount, int productCount) {}

    @Transactional(readOnly = true)
    public List<WarehouseView> overview() {
        WarehouseEnvironmentReading latest = readings.findTopByOrderByRecordedAtDesc().orElse(null);
        LocalDateTime now = LocalDateTime.now();
        return warehouses.findByMonitoringSlotIsNotNullAndDeletedFalseOrderByMonitoringSlotAsc().stream().map(w -> {
            List<MonitoredShelf> items = shelves.findByWarehouseId(w.getWarehouseId());
            int count = (int) items.stream().flatMap(s -> s.getProductIds().stream()).distinct().count();
            Environment environment = w.getMonitoringSlot() == 1
                ? latest == null ? new Environment(null, null, null, null, false)
                    : new Environment(latest.getTemperature(), latest.getHumidity(), latest.getGas(), latest.getRecordedAt(), false,
                        latest.getRiskLevel(), latest.getConfidence(), latest.getAiStatus(), latest.getActionCode(),
                        latest.getModelVersion(), latest.getPredictionStatus(), latest.isControlAllowed(), latest.getAiRiskLevel(), latest.getGasRiskLevel(), latest.getDecisionSource())
                : predictEnvironment(simulate(w.getMonitoringSlot(), now));
            return new WarehouseView(w, environment, items.size(), count);
        }).toList();
    }

    private Environment predictEnvironment(Environment environment) {
        var prediction = risk.predict(null, environment.temperature(), environment.humidity(), environment.gas(), null);
        return new Environment(environment.temperature(), environment.humidity(), environment.gas(), environment.recordedAt(),
                environment.simulated(), prediction.riskLevel(), prediction.confidence(), prediction.aiStatus(), prediction.actionCode(),
                prediction.modelVersion(), prediction.predictionStatus(), false, prediction.aiRiskLevel(), prediction.gasRiskLevel(), prediction.decisionSource());
    }

    public static Environment simulate(int slot, LocalDateTime now) {
        double phase = Math.floorDiv(now.toEpochSecond(ZoneOffset.UTC), 60) / 12.0 + slot;
        double temperature = Math.round((25 + slot * 0.7 + Math.sin(phase) * 0.6) * 10) / 10.0;
        double humidity = Math.round((53 + slot * 1.5 + Math.cos(phase) * 2) * 10) / 10.0;
        double gas = Math.round(200 + slot * 20 + Math.sin(phase) * 15);
        return new Environment(temperature, humidity, gas, now.withSecond(0).withNano(0), true);
    }
}
