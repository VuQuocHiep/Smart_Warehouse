package com.example.iot.mapper;

import java.util.HashSet;
import java.util.Set;

import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

import com.example.iot.dto.request.RoleRequest;
import com.example.iot.entity.PermissionEntity;
import com.example.iot.entity.RoleEntity;
import com.example.iot.repository.PermissionRepository;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Builder
public class RoleMapper {
    private final PermissionRepository permissionRepository;
    public RoleEntity toEntity(RoleRequest request){
        Set<PermissionEntity> permission = new HashSet<>();
        for(String x:request.getPermission()){
            PermissionEntity a = permissionRepository.findByNameAndDeletedFalse(x).orElseThrow(()->new RuntimeException("Không tồn tại!"));
            permission.add(a);
        }
        RoleEntity roleEntity = RoleEntity.builder()
                                .name(request.getName())
                                .description(request.getDescription())
                                .permission(permission)
                                .build();
        return roleEntity;
    }
}
