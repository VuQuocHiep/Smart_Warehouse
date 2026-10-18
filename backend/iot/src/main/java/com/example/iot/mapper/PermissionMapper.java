package com.example.iot.mapper;

import org.springframework.stereotype.Component;

import com.example.iot.dto.request.PermissionRequest;
import com.example.iot.entity.PermissionEntity;

@Component
public class PermissionMapper {
    public PermissionEntity toPermissionEntity(PermissionRequest request) {
        if (request == null) return null;
        return PermissionEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
    }
}
