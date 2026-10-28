package com.example.iot.service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplenishmentServiceTest {
    @Test void simulatedForecastCannotGenerateRealSourceAlerts() {
        var worker=org.mockito.Mockito.mock(DemandForecastService.class);
        var data=org.mockito.Mockito.mock(DemandDataService.class);
        org.mockito.Mockito.when(worker.latest()).thenReturn("{\"forecastDate\":\""+LocalDate.now(DemandDataService.ZONE)+"\",\"dataSource\":\"SIMULATED\",\"forecasts\":[]}");
        org.mockito.Mockito.when(data.dataSource()).thenReturn("REAL");
        org.mockito.Mockito.when(data.catalog()).thenReturn(List.of(new DemandDataService.Pair("w","Kho","p","SKU","Product")));
        org.mockito.Mockito.when(data.balances()).thenReturn(List.of(Map.of("warehouse_id","w","product_id","p","reconciled",1,"stock",0,"safety_stock",0,"exported_today",0)));
        var result=new ReplenishmentService(worker,data).latest();
        assertTrue(((List<?>)result.get("todayAlerts")).isEmpty());
        assertTrue(((List<?>)result.get("weekAlerts")).isEmpty());
    }
    LocalDate today=LocalDate.of(2026,12,29);
    List<Map<String,Object>> predictions(double... quantities) {
        var result=new ArrayList<Map<String,Object>>();
        for(int i=0;i<7;i++) result.add(Map.of("target_date",today.plusDays(i).toString(),"status","OK","predicted_quantity",i<quantities.length?quantities[i]:0));
        return result;
    }
    @Test void incrementalImportsDoNotDoubleCountAndCrossYearCorrectly() {
        var days=ReplenishmentService.calculate(predictions(8,9,10),20,0,0,today);
        assertEquals(List.of(0L,0L,7L,0L,0L,0L,0L),days.stream().map(r -> r.get("required_quantity")).toList());
        assertEquals("2027-01-04",days.get(6).get("target_date"));
        assertEquals(7L,days.get(6).get("cumulative_required"));
    }
    @Test void remainingTodayAndSafetyAreAppliedOnceAndRoundingIsConservative() {
        var days=ReplenishmentService.calculate(predictions(8.2,9.2,10.2),4,2,5,today);
        assertEquals(2L,days.get(0).get("required_quantity"));
        assertEquals(21L,days.get(6).get("cumulative_required"));
        assertEquals(21L,days.stream().mapToLong(r -> ((Number)r.get("required_quantity")).longValue()).sum());
    }
    @Test void missingOrInvalidForecastIsUnknownInsteadOfZeroNeed() {
        assertTrue(ReplenishmentService.calculate(List.of(),0,0,0,today).isEmpty());
        var rows=predictions(3);rows.set(5,Map.of("target_date",today.plusDays(5).toString(),"status","STALE_DATA"));
        assertTrue(ReplenishmentService.calculate(rows,0,0,0,today).isEmpty());
    }
    @Test void weeklyScheduleIsMondayFiveEvenWhenBootstrapIsMidweek() {
        var monday=ZonedDateTime.of(2026,10,26,4,59,0,0,DemandDataService.ZONE);
        assertEquals(LocalDate.of(2026,10,19),DemandJobService.trainingPeriod(monday).toLocalDate());
        assertEquals(LocalDate.of(2026,10,26),DemandJobService.trainingPeriod(monday.plusMinutes(1)).toLocalDate());
        assertEquals(5,DemandJobService.trainingPeriod(monday.plusDays(3)).getHour());
    }
}
