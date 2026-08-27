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

import com.example.iot.dto.request.RoleRequest;
import com.example.iot.entity.RoleEntity;
import com.example.iot.service.RoleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;
    @PostMapping("/create")
    public ResponseEntity<RoleEntity> create(@Valid @RequestBody RoleRequest request){
        return ResponseEntity.ok(roleService.create(request));
    }
    @PatchMapping("/update/{id}")
    public ResponseEntity<RoleEntity> update(@Valid @RequestBody RoleRequest request,@PathVariable String id){
        return ResponseEntity.ok(roleService.update(request, id));
    }
    @PatchMapping("/deleted/{id}")
    public void delete(@PathVariable String id){
        roleService.delete(id);
    }
    @GetMapping("/getAll")
    public ResponseEntity<List<RoleEntity>> getAll(){
        return ResponseEntity.ok(roleService.getAll());
    }
    @GetMapping("/getRoleById/{id}")
    public ResponseEntity<RoleEntity> getRoleById(@PathVariable String id){
        return ResponseEntity.ok(roleService.getRoleById(id));
    }
}
