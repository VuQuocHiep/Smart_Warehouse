package com.example.iot.service;

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
    @Test void disabledServiceFailsWithClearStatus() {
        var service=new DemandForecastService(mock(DemandDataService.class),"http://localhost","",false);
        assertEquals(503,assertThrows(ResponseStatusException.class,service::latest).getStatusCode().value());
    }
    @Test void sendsDatabaseSnapshotWithTokenAndPreservesWorkerJobId() throws Exception {
        var body=new AtomicReference<String>(); var token=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/jobs",exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            byte[] response="{\"jobId\":\"worker-id\",\"status\":\"QUEUED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(202,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        try {
            var service=new DemandForecastService(mock(DemandDataService.class),"http://127.0.0.1:"+server.getAddress().getPort(),"secret",true);
            var snapshot=new DemandDataService.Snapshot(List.of(new DemandDataService.Pair("w","Warehouse","p","SKU","Product")),
                List.of(Map.of("date","2026-10-01","warehouse_id","w","product_id","p","export_quantity",9,"data_quality","COMPLETE","data_source","REAL")),"2026-10-01","REAL","REAL-fixture");
            assertEquals("QUEUED",service.submit(UUID.randomUUID().toString(),"PREDICT",snapshot).get("status"));
            assertEquals("secret",token.get());assertTrue(body.get().contains("export_quantity"));assertTrue(body.get().contains("REAL-fixture"));
            assertFalse(body.get().contains("seed\":"));
        } finally { server.stop(0); }
    }
}
