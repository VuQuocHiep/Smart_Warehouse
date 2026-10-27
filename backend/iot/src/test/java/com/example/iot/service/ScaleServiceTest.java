package com.example.iot.service;

import java.time.Instant;
import java.util.Optional;
import com.example.iot.entity.*;
import com.example.iot.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ScaleServiceTest {
    ProductRepository products = mock(ProductRepository.class);
    ScaleTicketRepository tickets = mock(ScaleTicketRepository.class);
    ScaleSettingsRepository settings = mock(ScaleSettingsRepository.class);
    ScaleService service = new ScaleService(products, tickets, settings);

    @BeforeEach void setup() {
        var config = new ScaleSettings();
        config.setAbsoluteGrams(5); config.setPercent(2);
        when(settings.findById(1)).thenReturn(Optional.of(config));
        when(products.findBySkuAndDeletedFalse("SKU")).thenReturn(Optional.of(ProductEntity.builder().sku("SKU").unit_weight(100).build()));
        when(tickets.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    ScaleService.Reading reading(double net, double gross, boolean stable, boolean overload) {
        return new ScaleService.Reading("ESP_ENV_01", net, gross, stable, true, true, overload, "READY", null, null);
    }
    @Test void toleranceUsesLargerLimitAndIncludesExactBoundary() {
        assertEquals(5, ScaleService.tolerance(100, 5, 2));
        assertEquals(20, ScaleService.tolerance(1000, 5, 2));
        for (double actual : new double[]{980, 1000, 1020}) assertTrue(ScaleService.withinTolerance(actual, 1000, 20));
        assertFalse(ScaleService.withinTolerance(1020.001, 1000, 20));
        assertFalse(ScaleService.withinTolerance(Double.NaN, 1000, 20));
    }
    @Test void defaultFiftyGramToleranceAcceptsBothBoundariesAndRejectsExcess() {
        var config = new ScaleSettings();
        assertEquals(50, config.getAbsoluteGrams()); assertEquals(0, config.getPercent());
        when(settings.findById(1)).thenReturn(Optional.of(config));
        for (double weight : new double[]{150, 250}) {
            service.receive(reading(weight, weight, true, false));
            assertEquals(50, service.capture("SKU", 2, "IN", "operator").getToleranceGrams());
        }
        for (double weight : new double[]{149.99, 250.01}) {
            service.receive(reading(weight, weight, true, false));
            assertThrows(ResponseStatusException.class, () -> service.capture("SKU", 2, "IN", "operator"));
        }
    }
    @Test void capturesServerComputedEvidenceAtBoundaryAndRejectsExcessiveError() {
        service.receive(reading(205, 250, true, false));
        var ticket = service.capture("SKU", 2, "IN", "operator");
        assertEquals(200, ticket.getExpectedGrams()); assertEquals(5, ticket.getToleranceGrams());
        assertEquals("operator", ticket.getCapturedBy()); assertFalse(ticket.isConsumed());
        service.receive(reading(205.01, 250, true, false));
        assertThrows(ResponseStatusException.class, () -> service.capture("SKU", 2, "IN", "operator"));
        verify(tickets, times(1)).save(any());
    }
    @Test void rejectsUnstableOverloadedUncalibratedNegativeAndOfflineReadings() {
        for (var reading : new ScaleService.Reading[]{reading(200, 5001, true, false), reading(200, 250, false, false),
                reading(200, 250, true, true), reading(-200, 250, true, false),
                new ScaleService.Reading("ESP_ENV_01", 200d, 250d, true, false, true, false, "READY", null, null)}) {
            service.receive(reading);
            assertThrows(ResponseStatusException.class, () -> service.capture("SKU", 2, "IN", "operator"));
        }
        service.receive(reading(200, 250, true, false));
        ReflectionTestUtils.setField(service, "receivedAt", Instant.now().minusSeconds(9));
        assertFalse(service.status().online());
        assertThrows(ResponseStatusException.class, () -> service.capture("SKU", 2, "IN", "operator"));
        verify(tickets, never()).save(any());
    }
    @Test void rejectsUnexpectedDevicesNonFiniteSettingsAndOversizedBatches() {
        assertThrows(ResponseStatusException.class, () -> service.receive(new ScaleService.Reading("other", 1d, 1d, true, true, true, false, "READY", null, null)));
        assertThrows(ResponseStatusException.class, () -> service.receive(reading(Double.NaN, 200, true, false)));
        assertThrows(ResponseStatusException.class, () -> service.saveSettings(Double.NaN, 2));
        assertThrows(ResponseStatusException.class, () -> service.saveSettings(5, 21));
        service.receive(reading(100, 100, true, false));
        assertThrows(ResponseStatusException.class, () -> service.capture("SKU", 51, "IN", "operator"));
    }
    @Test void commandsInvalidateSamplesAndRequireMatchingAcknowledgement() {
        service.receive(reading(200, 250, true, false));
        var command = service.command("TARE", 0);
        assertFalse(service.status().reading().stable());
        assertThrows(ResponseStatusException.class, () -> service.capture("SKU", 2, "IN", "operator"));
        assertThrows(ResponseStatusException.class, () -> service.command("TARE", 0));
        service.receive(new ScaleService.Reading("ESP_ENV_01", 0d, 250d, false, true, true, false, "SETTLING", "wrong", "OK_TARED"));
        assertNotNull(service.status().pending());
        service.receive(new ScaleService.Reading("ESP_ENV_01", 0d, 250d, false, true, true, false, "SETTLING", command.id(), "OK_TARED"));
        assertNull(service.status().pending());
    }
    ScaleTicket ticket() {
        ScaleTicket ticket = new ScaleTicket();
        ticket.setId("ticket"); ticket.setSku("SKU"); ticket.setQuantity(2); ticket.setStockType("IN");
        ticket.setCapturedBy("operator"); ticket.setCapturedAt(Instant.now()); ticket.setExpectedGrams(200);
        when(tickets.lockById("ticket")).thenReturn(Optional.of(ticket));
        return ticket;
    }
    @Test void weighingCannotBeReusedOrAppliedToDifferentStockMovement() {
        var ticket = ticket();
        assertThrows(ResponseStatusException.class, () -> service.consume("ticket", "SKU", 3, "IN", "operator", 100));
        assertThrows(ResponseStatusException.class, () -> service.consume("ticket", "SKU", 2, "OUT", "operator", 100));
        assertThrows(ResponseStatusException.class, () -> service.consume("ticket", "SKU", 2, "IN", "other", 100));
        assertThrows(ResponseStatusException.class, () -> service.consume("ticket", "SKU", 2, "IN", "operator", 101));
        assertSame(ticket, service.consume("ticket", "SKU", 2, "IN", "operator", 100));
        assertThrows(ResponseStatusException.class, () -> service.consume("ticket", "SKU", 2, "IN", "operator", 100));
    }
    @Test void oldCaptureExpiresEvenWhenDeviceIsOnline() {
        var ticket = ticket(); ticket.setCapturedAt(Instant.now().minusSeconds(601));
        assertThrows(ResponseStatusException.class, () -> service.consume("ticket", "SKU", 2, "IN", "operator", 100));
    }
}
