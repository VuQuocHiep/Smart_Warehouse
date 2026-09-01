package com.example.iot.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iot.dto.request.WarehouseRequest;
import com.example.iot.entity.WarehouseEntity;
import com.example.iot.service.WarehouseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/warehouse")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService warehouseService;
    @PostMapping("/create")
    public ResponseEntity<WarehouseEntity> create(@Valid @RequestBody WarehouseRequest request){
        return ResponseEntity.ok(warehouseService.create(request));
    }
    @PatchMapping("/update/{id}")
    public ResponseEntity<WarehouseEntity> update(@Valid @RequestBody WarehouseRequest request,@PathVariable String id){
        return ResponseEntity.ok(warehouseService.update(request, id));
    }
    @PatchMapping("/delete/{id}")
    public void delete(@PathVariable String id){
        warehouseService.delete(id);
    }
    @GetMapping("/getAll")
    public ResponseEntity<List<WarehouseEntity>> getAll(){
        return ResponseEntity.ok(warehouseService.getAll());
    }
    @GetMapping("/getWarehouseById/{id}")
    public ResponseEntity<WarehouseEntity> getWarehouseById(@PathVariable String id){
        return ResponseEntity.ok(warehouseService.getWarehouseById(id));
    }
    @GetMapping("/getWarehouseByName/{name}")
    public ResponseEntity<WarehouseEntity> getWarehouseByName(@PathVariable String name){
        return ResponseEntity.ok(warehouseService.getWarehouseByName(name));
    }
}
