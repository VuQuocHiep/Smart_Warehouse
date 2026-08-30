package com.example.iot.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.example.iot.dto.request.PermissionRequest;
import com.example.iot.entity.PermissionEntity;
import com.example.iot.exception.DuplicateException;
import com.example.iot.mapper.PermissionMapper;
import com.example.iot.repository.PermissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;
    @PreAuthorize("hasRole('ADMIN')")
    public PermissionEntity create(PermissionRequest request){
        PermissionEntity permissionEntity = permissionMapper.toPermissionEntity(request);
        if(permissionRepository.existsByNameAndDeletedFalse(permissionEntity.getName())){
            throw new DuplicateException("Permission đã tồn tại!");
        }
        return permissionRepository.save(permissionEntity);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PermissionEntity update(PermissionRequest request,String id){
        PermissionEntity permissionEntity = permissionMapper.toPermissionEntity(request);
        if(!permissionRepository.existsByPermissionIdAndDeletedFalse(id)){
            throw new RuntimeException("Không tồn tại!");
        }
        return permissionRepository.save(permissionEntity);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String id){
        PermissionEntity permissionEntity = permissionRepository.findByPermissionIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
        permissionEntity.setDeleted(true);
        permissionRepository.save(permissionEntity);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<PermissionEntity> getAll(){
        return permissionRepository.findByDeletedFalse();
    } 
    public PermissionEntity getPermissionById(String id){
        return permissionRepository.findByPermissionIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
    }
}
