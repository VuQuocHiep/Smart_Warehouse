package com.example.iot.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Map;
import com.example.iot.entity.*;
import com.example.iot.service.ScaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ScaleController {
    private final ScaleService service;
    @Value("${iot.ingest-key:}") private String ingestKey;
    public record Capture(String sku, int quantity, String type) {}
    public record CommandRequest(String type, double referenceGrams) {}
    public record SettingsRequest(double absoluteGrams, double percent) {}

    @PostMapping("/api/scale/readings")
    public Map<String, Object> receive(@RequestHeader(value="X-Device-Key", defaultValue="") String key,
            @RequestBody ScaleService.Reading reading) {
        if (ingestKey.isBlank() || !MessageDigest.isEqual(ingestKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Khóa thiết bị không hợp lệ");
        var command = service.receive(reading);
        return command == null ? Map.of() : Map.of("command", command);
    }

    @GetMapping("/api/scale")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ScaleService.Status status() { return service.status(); }

    @PostMapping("/api/scale/capture")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ScaleTicket capture(@RequestBody Capture request, Principal principal) {
        return service.capture(request.sku(), request.quantity(), request.type(), principal.getName());
    }

    @PostMapping("/api/scale/commands")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or (#request.type() == 'TARE' and hasRole('STAFF'))")
    public ScaleService.Command command(@RequestBody CommandRequest request) { return service.command(request.type(), request.referenceGrams()); }

    @PutMapping("/api/scale/settings")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ScaleSettings settings(@RequestBody SettingsRequest request) { return service.saveSettings(request.absoluteGrams(), request.percent()); }
}
