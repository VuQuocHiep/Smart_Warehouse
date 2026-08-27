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

import com.example.iot.dto.request.UserRequest;
import com.example.iot.entity.UserEntity;
import com.example.iot.service.UserService;
import com.rabbitmq.client.RpcClient.Response;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @PostMapping("/create")
    public ResponseEntity<UserEntity> create(@Valid @RequestBody UserRequest request){
        return ResponseEntity.ok(userService.create(request));
    }
    @PatchMapping("/update/{id}")
    public ResponseEntity<UserEntity> update(@Valid @RequestBody UserRequest request,@PathVariable String id){
        return ResponseEntity.ok(userService.update(request, id));
    }
    @PatchMapping("/delete/{id}")
    public void delete(@PathVariable String id){
        userService.delete(id);
    }
    @GetMapping("/getAll")
    public ResponseEntity<List<UserEntity>> getAll(){
        return ResponseEntity.ok(userService.getAll());
    }
    @GetMapping("/getUserById/{id}")
    public ResponseEntity<UserEntity> getUserById(@PathVariable String id){
        return ResponseEntity.ok(userService.getUserById(id));
    }
}
