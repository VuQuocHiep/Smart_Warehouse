package com.example.iot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import jakarta.validation.Valid;

import com.example.iot.dto.request.PermissionRequest;
import com.example.iot.entity.PermissionEntity;
import com.example.iot.service.PermissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/permission")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService permissionService;

    @PostMapping("/create")
    public ResponseEntity<PermissionEntity> create(@Valid @RequestBody PermissionRequest request){
        return ResponseEntity.ok(permissionService.create(request));
    }
    @PatchMapping("/update/{id}")
    public ResponseEntity<PermissionEntity> update(@Valid @RequestBody PermissionRequest request,@PathVariable String id){
        return ResponseEntity.ok(permissionService.update(request, id));
    }
    @PatchMapping("/delete/{id}")
    public void delete(@PathVariable String id){
        permissionService.delete(id);
    }
    @GetMapping("/getAll")
    public ResponseEntity<List<PermissionEntity>> getAll(){
        return ResponseEntity.ok(permissionService.getAll());
    }
    @GetMapping("/getPermissionById/{id}")
    public ResponseEntity<PermissionEntity> getPermissionById(@PathVariable String id){
        return ResponseEntity.ok(permissionService.getPermissionById(id));
    }
}
