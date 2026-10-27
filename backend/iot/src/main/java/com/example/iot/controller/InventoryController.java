package com.example.iot.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iot.dto.request.StockRequest;
import com.example.iot.dto.response.StockHistoryItem;
import com.example.iot.dto.response.StockItemResult;
import com.example.iot.service.InventoryService;
import com.example.iot.service.PlacementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;
    private final PlacementService placementService;

    @PostMapping("/stock-in")
    public ResponseEntity<List<StockItemResult>> stockIn(@Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(inventoryService.stockIn(request));
    }

    @PostMapping("/stock-out")
    public ResponseEntity<List<StockItemResult>> stockOut(@Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(inventoryService.stockOut(request));
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<StockItemResult>> getAll() {
        return ResponseEntity.ok(inventoryService.getAllStock());
    }

    @GetMapping("/history")
    public ResponseEntity<List<StockHistoryItem>> history() {
        return ResponseEntity.ok(inventoryService.getHistory());
    }

    @GetMapping("/used-skus")
    public ResponseEntity<List<String>> usedSkus() {
        return ResponseEntity.ok(inventoryService.getUsedSkus());
    }

    /** AI đề xuất vị trí xếp hàng cho các dòng của phiếu nhập. */
    @PostMapping("/placement-suggestions")
    public ResponseEntity<List<PlacementService.Result>> placement(@Valid @RequestBody StockRequest request) {
        return ResponseEntity.ok(placementService.suggest(request));
    }
}