package com.example.iot.controller;

import com.example.iot.service.DemandForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;

@RestController
@RequestMapping("/api/demand")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class DemandForecastController {
    private final DemandForecastService service;

    @GetMapping("/catalog")
    public List<DemandForecastService.Pair> catalog() { return service.catalog(); }

    @GetMapping(value = "/forecasts", produces = MediaType.APPLICATION_JSON_VALUE)
    public String forecasts() { return service.latest(); }

    @PostMapping(value = "/demo-jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> start() {
        return ResponseEntity.accepted().contentType(MediaType.APPLICATION_JSON).body(service.startDemo());
    }

    @GetMapping(value = "/jobs/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String job(@PathVariable UUID id) { return service.job(id); }

    @ExceptionHandler(ResponseStatusException.class)
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> serviceError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
                error.getReason() == null ? "Dịch vụ dự báo trả lỗi." : error.getReason()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(403).body(Map.of("message", "Chỉ ADMIN/MANAGER được sử dụng dự báo nhu cầu."));
    }
}
