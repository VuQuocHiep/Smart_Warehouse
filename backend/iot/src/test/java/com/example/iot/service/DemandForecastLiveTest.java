package com.example.iot.service;

import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Optional end-to-end test against a migrated disposable DB and a dedicated Python worker. */
@EnabledIfEnvironmentVariable(named="DEMAND_LIVE_TEST_URL",matches=".+")
class DemandForecastLiveTest {
    @Test @SuppressWarnings("unchecked")
    void databaseToJavaHttpToPythonJoblibToReplenishment() throws Exception {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(System.getenv("MYSQL_TEST_URL"),System.getenv("MYSQL_TEST_USER"),System.getenv("MYSQL_TEST_PASSWORD")));
        var data=new DemandDataService(jdbc);
        var worker=new DemandForecastService(data,System.getenv("DEMAND_LIVE_TEST_URL"),System.getenv("DEMAND_SERVICE_TOKEN"),true);
        var snapshot=data.snapshot();
        String id=UUID.randomUUID().toString();
        worker.submit(id,"TRAIN",snapshot);
        Map<String,Object> status=Map.of();
        long deadline=System.nanoTime()+Duration.ofMinutes(5).toNanos();
        while(System.nanoTime()<deadline) {
            status=DemandForecastService.parse(worker.job(UUID.fromString(id)));
            if(Set.of("SUCCEEDED","FAILED","INTERRUPTED").contains(status.get("status"))) break;
            Thread.sleep(500);
        }
        assertEquals("SUCCEEDED",status.get("status"),status.toString());
        var result=DemandForecastService.parse(worker.latest());
        assertEquals(336,((List<?>)result.get("forecasts")).size());
        var replenishment=new ReplenishmentService(worker,data).latest();
        assertFalse(((List<?>)replenishment.get("todayAlerts")).isEmpty());
        assertFalse(((List<?>)replenishment.get("weekAlerts")).isEmpty());
        for(var item:(List<Map<String,Object>>)replenishment.get("items")) {
            if(!"OK".equals(item.get("status"))) continue;
            var days=(List<Map<String,Object>>)item.get("days");
            assertEquals(7,days.size());
            assertEquals(((Number)item.get("requiredWeek")).longValue(),days.stream().mapToLong(d -> ((Number)d.get("required_quantity")).longValue()).sum());
        }
        String modelRun=Objects.toString(result.get("modelTrainedAt"));
        String predictId=UUID.randomUUID().toString();
        worker.submit(predictId,"PREDICT",snapshot);
        deadline=System.nanoTime()+Duration.ofMinutes(1).toNanos();
        while(System.nanoTime()<deadline) {
            status=DemandForecastService.parse(worker.job(UUID.fromString(predictId)));
            if(Set.of("SUCCEEDED","FAILED").contains(status.get("status"))) break;
            Thread.sleep(250);
        }
        assertEquals("SUCCEEDED",status.get("status"));
        result=DemandForecastService.parse(worker.latest());
        assertEquals("REUSED",result.get("modelAction"));assertEquals(modelRun,result.get("modelTrainedAt"));
    }
}
