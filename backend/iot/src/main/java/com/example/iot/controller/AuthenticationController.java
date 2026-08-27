package com.example.iot.controller;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.iot.dto.request.AuthenticationRequest;
import com.example.iot.dto.request.IntrospectRequest;
import com.example.iot.dto.request.RefreshTokenRequest;
import com.example.iot.dto.response.AuthenticationResponse;
import com.example.iot.dto.response.IntrospectResponse;
import com.example.iot.service.AuthenticationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@RequestBody AuthenticationRequest request){
        return ResponseEntity.ok(authenticationService.login(request));
    }
    @PostMapping("/refreshToken")
    public ResponseEntity<AuthenticationResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request){
        return ResponseEntity.ok(authenticationService.refreshToken(request));
    }
    @PostMapping("/introspect")
    public ResponseEntity<IntrospectResponse> introspect(@Valid @RequestBody IntrospectRequest request){
        return ResponseEntity.ok(authenticationService.introspect(request));
    }
}
