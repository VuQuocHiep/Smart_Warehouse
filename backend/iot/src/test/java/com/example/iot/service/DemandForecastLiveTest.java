package com.example.iot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Optional actual Java HTTP client -> Python model test. No application DB access. */
@EnabledIfEnvironmentVariable(named = "DEMAND_LIVE_TEST_URL", matches = ".+")
class DemandForecastLiveTest {
    @Test void trainsThroughRealPythonServiceUsingUuidCatalog() throws Exception {
        var fixture = new DemandForecastServiceTest();
        fixture.catalog();
        var service = new DemandForecastService(fixture.warehouses, fixture.products, fixture.shelves,
                System.getenv("DEMAND_LIVE_TEST_URL"), System.getenv("DEMAND_SERVICE_TOKEN"), true);
        var mapper = JsonMapper.builder().build();
        var started = mapper.readTree(service.startDemo());
        var id = java.util.UUID.fromString(started.get("jobId").asString());
        String status = "";
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < deadline) {
            status = mapper.readTree(service.job(id)).get("status").asString();
            if (status.equals("SUCCEEDED") || status.equals("FAILED")) break;
            Thread.sleep(250);
        }
        assertEquals("SUCCEEDED", status);
        var result = mapper.readTree(service.latest());
        assertEquals("SIMULATED", result.get("dataSource").asString());
        assertEquals(14, result.get("forecasts").size());
        assertEquals("w-uuid", result.get("forecasts").get(0).get("warehouse_id").asString());
        assertEquals("p-uuid", result.get("forecasts").get(0).get("product_id").asString());
    }
}
