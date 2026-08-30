package com.example.iot.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.example.iot.dto.request.RoleRequest;
import com.example.iot.entity.RoleEntity;
import com.example.iot.exception.DuplicateException;
import com.example.iot.mapper.RoleMapper;
import com.example.iot.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;
    @PreAuthorize("hasRole('ADMIN')")
    public RoleEntity create(RoleRequest request){
        if(roleRepository.existsByNameAndDeletedFalse(request.getName())){
            throw new DuplicateException("Role đã tồn tại!");
        }
        RoleEntity roleEntity = roleMapper.toEntity(request);
        return roleRepository.save(roleEntity);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public RoleEntity update(RoleRequest request,String id){
        RoleEntity roleEntity = roleMapper.toEntity(request);
        if(roleRepository.existsByRoleIdAndDeletedFalse(id)){
            throw new RuntimeException("Không tồn tại!");
        }
        return roleRepository.save(roleEntity);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(String id){
        RoleEntity roleEntity = roleRepository.findByRoleIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
        roleEntity.setDeleted(true);
        roleRepository.save(roleEntity);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoleEntity> getAll(){
        return roleRepository.findByDeletedFalse();
    }
    public RoleEntity getRoleById(String id){
        return roleRepository.findByRoleIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
    }
}
