package com.example.iot.controller;

import com.example.iot.entity.WarehouseEnvironmentReading;
import com.example.iot.repository.WarehouseEnvironmentReadingRepository;
import com.example.iot.service.WarehouseMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class WarehouseMonitoringController {
    private final WarehouseMonitoringService monitoring;
    private final WarehouseEnvironmentReadingRepository readings;

    @GetMapping("/api/warehouses/monitoring")
    public List<WarehouseMonitoringService.WarehouseView> overview() { return monitoring.overview(); }

    @GetMapping("/api/sensor/latest")
    public WarehouseEnvironmentReading latest() { return readings.findTopByOrderByRecordedAtDesc().orElse(null); }

    @GetMapping("/api/sensor/history")
    public List<WarehouseEnvironmentReading> history(@RequestParam(defaultValue = "60") int limit) {
        List<WarehouseEnvironmentReading> result = new ArrayList<>(readings.findAllByOrderByRecordedAtDesc(PageRequest.of(0, Math.max(1, Math.min(300, limit)))));
        Collections.reverse(result);
        return result;
    }
}
