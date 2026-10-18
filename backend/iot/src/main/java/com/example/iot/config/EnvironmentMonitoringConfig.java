package com.example.iot.config;

import com.example.iot.service.ShelfEnvironmentService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class EnvironmentMonitoringConfig {
    private final ShelfEnvironmentService service;

    public EnvironmentMonitoringConfig(ShelfEnvironmentService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 5000)
    public void evaluateSimulatedWarehouses() {
        service.refreshSimulatedWarehouses();
    }
}
