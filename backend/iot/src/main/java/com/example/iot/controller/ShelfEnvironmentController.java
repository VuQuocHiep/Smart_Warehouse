package com.example.iot.controller;

import com.example.iot.entity.*;
import com.example.iot.repository.*;
import com.example.iot.service.ShelfEnvironmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class ShelfEnvironmentController {
    private final ShelfEnvironmentService service;
    private final ProductEnvironmentAlertRepository alerts;
    private final EnvironmentAlertReceiptRepository receipts;
    @Value("${iot.ingest-key:}")
    private String ingestKey;

    @GetMapping("/api/shelves")
    public List<ShelfEnvironmentService.ShelfView> list() { return service.list(); }

    @PostMapping("/api/shelves")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MonitoredShelf create(@Valid @RequestBody ShelfEnvironmentService.ShelfRequest request) {
        return service.save(null, request);
    }

    @PutMapping("/api/shelves/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public MonitoredShelf update(@PathVariable String id, @Valid @RequestBody ShelfEnvironmentService.ShelfRequest request) {
        return service.save(id, request);
    }

    @PostMapping("/api/sensor/readings")
    public WarehouseEnvironmentReading receive(@RequestHeader(value="X-Device-Key", defaultValue="") String key,
            @Valid @RequestBody ShelfEnvironmentService.Reading reading) {
        if (ingestKey.isBlank() || !MessageDigest.isEqual(ingestKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Khóa thiết bị không hợp lệ");
        }
        return service.receive(reading);
    }

    public record AlertView(ProductEnvironmentAlert alert, boolean read) {}

    @GetMapping("/api/product-alerts")
    public List<AlertView> listAlerts(Principal principal) {
        Set<String> read = new HashSet<>();
        receipts.findByUsername(principal.getName()).forEach(r -> read.add(r.getAlertId()));
        return alerts.findTop100ByOrderByCreatedAtDesc().stream().map(a -> new AlertView(a, read.contains(a.getId()))).toList();
    }

    @PutMapping("/api/product-alerts/{id}/read")
    public void markRead(@PathVariable String id, Principal principal) {
        if (!alerts.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!receipts.existsByUsernameAndAlertId(principal.getName(), id)) {
            EnvironmentAlertReceipt receipt = new EnvironmentAlertReceipt();
            receipt.setAlertId(id);
            receipt.setUsername(principal.getName());
            receipts.save(receipt);
        }
    }
}
