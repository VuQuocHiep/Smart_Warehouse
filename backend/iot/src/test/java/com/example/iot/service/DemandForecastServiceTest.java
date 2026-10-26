package com.example.iot.service;

import com.example.iot.entity.*;
import com.example.iot.repository.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemandForecastServiceTest {
    final WarehouseRepository warehouses = mock(WarehouseRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final MonitoredShelfRepository shelves = mock(MonitoredShelfRepository.class);

    void catalog() {
        var warehouse = new WarehouseEntity();
        warehouse.setWarehouseId("w-uuid"); warehouse.setName("Kho 1");
        var product = new ProductEntity();
        product.setProductId("p-uuid"); product.setSku("SKU-A"); product.setName("Product A");
        var shelf = new MonitoredShelf();
        shelf.setWarehouseId("w-uuid"); shelf.setProductIds(Set.of("p-uuid", "deleted-product"));
        when(warehouses.findByDeletedFalse()).thenReturn(List.of(warehouse));
        when(products.findByDeletedFalse()).thenReturn(List.of(product));
        when(shelves.findAll()).thenReturn(List.of(shelf, shelf));
    }

    @Test void usesUuidAndDeduplicatesProductsAcrossShelves() {
        catalog();
        var service = new DemandForecastService(warehouses, products, shelves, "http://localhost", "", false);
        assertEquals(List.of(new DemandForecastService.Pair("w-uuid", "Kho 1", "p-uuid", "SKU-A", "Product A")), service.catalog());
    }

    @Test void disabledServiceFailsWithClearStatus() {
        var service = new DemandForecastService(warehouses, products, shelves, "http://localhost", "", false);
        assertEquals(503, assertThrows(ResponseStatusException.class, service::latest).getStatusCode().value());
    }

    @Test void emptyCatalogDoesNotStartDemo() {
        when(warehouses.findByDeletedFalse()).thenReturn(List.of());
        when(products.findByDeletedFalse()).thenReturn(List.of());
        when(shelves.findAll()).thenReturn(List.of());
        var service = new DemandForecastService(warehouses, products, shelves, "http://localhost", "token", true);
        assertEquals(400, assertThrows(ResponseStatusException.class, service::startDemo).getStatusCode().value());
    }

    @Test void sendsActualCatalogWithInternalTokenAndHandlesBusy() throws Exception {
        catalog();
        var body = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jobs/demo", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            byte[] response = "{\"jobId\":\"demo\",\"status\":\"QUEUED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(202, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/forecasts/latest", exchange -> {
            exchange.sendResponseHeaders(409, -1); exchange.close();
        });
        server.start();
        try {
            var service = new DemandForecastService(warehouses, products, shelves,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "private-token", true);
            assertTrue(service.startDemo().contains("QUEUED"));
            assertEquals("private-token", token.get());
            assertTrue(body.get().contains("w-uuid"));
            assertTrue(body.get().contains("p-uuid"));
            assertFalse(body.get().contains("deleted-product"));
            assertEquals(409, assertThrows(ResponseStatusException.class, service::latest).getStatusCode().value());
        } finally { server.stop(0); }
    }
}
