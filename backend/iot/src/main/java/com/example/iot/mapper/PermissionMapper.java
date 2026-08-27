package com.example.iot.mapper;

import org.mapstruct.Mapper;

import com.example.iot.dto.request.PermissionRequest;
import com.example.iot.entity.PermissionEntity;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    PermissionEntity toPermissionEntity(PermissionRequest request);
}
