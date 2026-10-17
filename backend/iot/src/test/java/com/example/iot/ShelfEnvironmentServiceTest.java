package com.example.iot;

import com.example.iot.entity.*;
import com.example.iot.repository.*;
import com.example.iot.service.ShelfEnvironmentService;
import com.example.iot.service.HybridRiskService;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShelfEnvironmentServiceTest {
    MonitoredShelfRepository shelves = mock(MonitoredShelfRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    WarehouseRepository warehouses = mock(WarehouseRepository.class);
    ProductEnvironmentAlertRepository alerts = mock(ProductEnvironmentAlertRepository.class);
    WarehouseEnvironmentReadingRepository readings = mock(WarehouseEnvironmentReadingRepository.class);
    HybridRiskService ai = mock(HybridRiskService.class);
    ShelfEnvironmentService service = new ShelfEnvironmentService(shelves, products, warehouses, alerts, readings, ai);
    MonitoredShelf shelf;
    ProductEntity product;

    @BeforeEach void setup() {
        when(ai.predict(any(), any(), any(), any(), any())).thenReturn(new HybridRiskService.Prediction(null, null, null, null, "test", "INVALID_SENSOR_DATA"));
        shelf = new MonitoredShelf();
        shelf.setId("s1"); shelf.setName("A1"); shelf.setDeviceCode("ESP1");
        shelf.setWarehouseId("w1");
        WarehouseEntity warehouse = new WarehouseEntity(); warehouse.setMonitoringSlot(1);
        warehouse.setWarehouseId("w1"); warehouse.setMonitoringDeviceCode("ESP1");
        when(warehouses.findByMonitoringDeviceCodeAndDeletedFalse("ESP1")).thenReturn(Optional.of(warehouse));
        when(warehouses.findByWarehouseIdAndDeletedFalse("w1")).thenReturn(Optional.of(warehouse));
        when(shelves.findByWarehouseId("w1")).thenReturn(List.of(shelf));
        shelf.setProductIds(Set.of("p1"));
        product = new ProductEntity();
        product.setProductId("p1"); product.setName("Milk"); product.setSku("MILK");
        product.setMinTemperature(2d); product.setMaxTemperature(8d);
        product.setMinHumidity(40d); product.setMaxHumidity(70d);
        when(shelves.findByDeviceCode("ESP1")).thenReturn(Optional.of(shelf));
        when(products.findAllById(shelf.getProductIds())).thenReturn(List.of(product));
        when(alerts.findByShelfIdAndResolvedAtIsNull("s1")).thenReturn(List.of());
    }

    @Test void boundariesAndMissingThresholdsAreSafe() {
        assertTrue(ShelfEnvironmentService.violations(product, 2d, 70d).isEmpty());
        assertTrue(ShelfEnvironmentService.violations(product, 8d, 40d).isEmpty());
        assertTrue(ShelfEnvironmentService.violations(new ProductEntity(), 90d, 0d).isEmpty());
        assertEquals(2, ShelfEnvironmentService.violations(product, 0d, 80d).size());
    }

    @Test void createsOneCombinedAlertForAffectedProductOnly() {
        service.receive(new ShelfEnvironmentService.Reading("ESP1", 20d, 80d));
        verify(alerts).save(argThat(a -> a.getMessage().contains("Milk")
                && a.getMessage().contains("20.0") && a.getMessage().contains("80.0") && a.getResolvedAt() == null));
    }

    @Test void repeatedUnsafeReadingsDoNotCreateDuplicates() {
        ProductEnvironmentAlert active = new ProductEnvironmentAlert(); active.setProductId("p1");
        when(alerts.findByShelfIdAndResolvedAtIsNull("s1")).thenReturn(List.of(active));
        service.receive(new ShelfEnvironmentService.Reading("ESP1", 20d, 80d));
        verify(alerts, never()).save(any());
        assertNull(active.getResolvedAt());
    }

    @Test void recoveryClosesEpisodeAndNextBreachCreatesNewAlert() {
        ProductEnvironmentAlert active = new ProductEnvironmentAlert(); active.setProductId("p1");
        when(alerts.findByShelfIdAndResolvedAtIsNull("s1")).thenReturn(List.of(active), List.of());
        service.receive(new ShelfEnvironmentService.Reading("ESP1", 8d, 50d));
        assertNotNull(active.getResolvedAt());
        service.receive(new ShelfEnvironmentService.Reading("ESP1", 20d, 50d));
        verify(alerts, times(2)).save(any());
    }

    @Test void checksEachProductUsingItsOwnRange() {
        ProductEntity safe = new ProductEntity(); safe.setProductId("p2"); safe.setMaxTemperature(30d);
        when(products.findAllById(shelf.getProductIds())).thenReturn(List.of(product, safe));
        service.receive(new ShelfEnvironmentService.Reading("ESP1", 20d, 50d));
        verify(alerts).save(argThat(a -> a.getProductId().equals("p1")));
    }

    @Test void unknownDeviceCannotTriggerAlerts() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.receive(new ShelfEnvironmentService.Reading("UNKNOWN", 20d, 80d)));
        verifyNoInteractions(alerts);
    }

    @Test void simulatedWarehouseRejectsEspAndCreatesNoRealAlert() {
        WarehouseEntity simulated = new WarehouseEntity(); simulated.setMonitoringSlot(2);
        when(warehouses.findByMonitoringDeviceCodeAndDeletedFalse("ESP1")).thenReturn(Optional.of(simulated));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.receive(new ShelfEnvironmentService.Reading("ESP1", 20d, 80d)));
        verifyNoInteractions(alerts, readings);
    }

    @Test void khoOneReadingReachesAllShelvesInThatWarehouse() {
        MonitoredShelf second = new MonitoredShelf(); second.setId("s2"); second.setProductIds(Set.of());
        when(shelves.findByWarehouseId("w1")).thenReturn(List.of(shelf, second));
        service.receive(new ShelfEnvironmentService.Reading("ESP1", 8d, 50d, 250d));
        assertEquals(8d, second.getTemperature());
        assertEquals(50d, second.getHumidity());
        assertEquals(shelf.getRecordedAt(), second.getRecordedAt());
        verify(readings).save(argThat(r -> r.getGas().equals(250d)));
        verify(shelves, never()).findByWarehouseId("w2");
    }

    @Test void warehouseReceivesReadingsWithoutAnyShelf() {
        when(shelves.findByWarehouseId("w1")).thenReturn(List.of());
        var result = service.receive(new ShelfEnvironmentService.Reading("ESP1", 32d, 66d, 382d));
        assertEquals("ESP1", result.getDeviceCode());
        assertEquals(32d, result.getTemperature());
        verify(readings).save(result);
        verify(shelves, never()).findByDeviceCode(any());
        verifyNoInteractions(products, alerts);
    }

    @Test void persistsModelPredictionAndReturnsItToEsp() {
        when(ai.predict("ESP1", 31.6, 69.1, 339d, 42d)).thenReturn(
            new HybridRiskService.Prediction(2, .91, "DANGER", "RED_LED_FAN", "model-hash", "READY"));
        var result = service.receive(new ShelfEnvironmentService.Reading("ESP1", 31.6, 69.1, 339d, 42d));
        assertEquals(2, result.getRiskLevel());
        assertEquals(.91, result.getConfidence());
        assertEquals("RED_LED_FAN", result.getActionCode());
        assertEquals(339d, result.getGas());
        assertEquals(42d, result.getGasTvocPpb());
        assertTrue(result.isControlAllowed());
        verify(readings).save(result);
    }

    @Test void rawAdcIsNotPassedToTvocModel() {
        var result = service.receive(new ShelfEnvironmentService.Reading("ESP1", 31.6, 69.1, 339d));
        verify(ai).predict("ESP1", 31.6, 69.1, 339d, null);
        assertNull(result.getRiskLevel());
        assertFalse(result.isControlAllowed());
    }

    private void setupSimulation() {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setWarehouseId("w2"); warehouse.setName("Kho 2"); warehouse.setMonitoringSlot(2);
        WarehouseEntity real = new WarehouseEntity(); real.setMonitoringSlot(1);
        shelf.setWarehouseId("w2");
        when(warehouses.findByMonitoringSlotIsNotNullAndDeletedFalseOrderByMonitoringSlotAsc()).thenReturn(List.of(real, warehouse));
        when(shelves.findByWarehouseId("w2")).thenReturn(List.of(shelf));
        when(shelves.findForUpdateById("s1")).thenReturn(Optional.of(shelf));
    }

    @Test void simulatedBreachCreatesLabeledNotificationWithoutEsp() {
        setupSimulation();
        service.refreshSimulatedWarehouses();
        verify(alerts).save(argThat(a -> a.isSimulated() && a.getMessage().contains("[Giả lập] Kho 2")
            && a.getMessage().contains("Milk") && a.getResolvedAt() == null));
        verifyNoInteractions(readings);
        verify(shelves, never()).findByWarehouseId("w1");
    }

    @Test void simulationDeduplicatesAndResolvesAfterThresholdChange() {
        setupSimulation();
        ProductEnvironmentAlert active = new ProductEnvironmentAlert();
        active.setProductId("p1"); active.setSimulated(true);
        when(alerts.findByShelfIdAndResolvedAtIsNull("s1")).thenReturn(List.of(active));
        service.refreshSimulatedWarehouses();
        verify(alerts, never()).save(any());
        product.setMinTemperature(0d); product.setMaxTemperature(40d);
        product.setMinHumidity(0d); product.setMaxHumidity(100d);
        service.refreshSimulatedWarehouses();
        assertNotNull(active.getResolvedAt());
        verify(alerts).save(active);
    }
}
