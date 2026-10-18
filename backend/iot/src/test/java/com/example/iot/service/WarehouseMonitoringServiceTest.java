package com.example.iot.service;

import com.example.iot.entity.WarehouseEntity;
import com.example.iot.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseMonitoringServiceTest {
    @Test void environmentUpdatesOncePerMinute() {
        var minute = java.time.LocalDateTime.of(2026, 10, 17, 12, 30);
        var first = WarehouseMonitoringService.simulate(2, minute);
        assertEquals(first, WarehouseMonitoringService.simulate(2, minute.plusSeconds(59)));
        assertEquals(minute.plusMinutes(1), WarehouseMonitoringService.simulate(2, minute.plusMinutes(1)).recordedAt());
    }

    @Test void otherWarehousesReceiveActualModelPredictionsWithoutEnablingDeviceControl() {
        var warehouses = mock(WarehouseRepository.class);
        var shelves = mock(MonitoredShelfRepository.class);
        var readings = mock(WarehouseEnvironmentReadingRepository.class);
        var risk = mock(HybridRiskService.class);
        var rows = new ArrayList<WarehouseEntity>();
        for (int slot = 1; slot <= 4; slot++) {
            var warehouse = new WarehouseEntity();
            warehouse.setWarehouseId("w" + slot); warehouse.setMonitoringSlot(slot);
            rows.add(warehouse);
            when(shelves.findByWarehouseId("w" + slot)).thenReturn(List.of());
        }
        when(warehouses.findByMonitoringSlotIsNotNullAndDeletedFalseOrderByMonitoringSlotAsc()).thenReturn(rows);
        when(readings.findTopByOrderByRecordedAtDesc()).thenReturn(Optional.empty());
        when(risk.predict(isNull(), anyDouble(), anyDouble(), anyDouble(), isNull())).thenReturn(
                new HybridRiskService.Prediction(2, .8, "DANGER", "RED_LED_FAN", "test-model", "READY", 2, 0, "AI"));
        var result = new WarehouseMonitoringService(warehouses, shelves, readings, risk).overview();
        assertNull(result.get(0).reading().riskLevel());
        for (int index = 1; index < 4; index++) {
            var reading = result.get(index).reading();
            assertEquals("READY", reading.predictionStatus());
            assertEquals(2, reading.riskLevel());
            assertEquals(2, reading.aiRiskLevel());
            assertEquals("test-model", reading.modelVersion());
            assertFalse(reading.controlAllowed());
            assertTrue(reading.simulated());
            assertNotNull(reading.temperature());
        }
        verify(risk, times(3)).predict(isNull(), anyDouble(), anyDouble(), anyDouble(), isNull());
        verify(readings, never()).save(any());
    }
}
