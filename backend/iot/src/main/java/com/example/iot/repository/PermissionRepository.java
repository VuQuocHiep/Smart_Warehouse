package com.example.iot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iot.entity.PermissionEntity;

public interface PermissionRepository extends JpaRepository<PermissionEntity,String>{
    boolean existsByNameAndDeletedFalse(String name);
    boolean existsByPermissionIdAndDeletedFalse(String permissionId);
    Optional<PermissionEntity> findByPermissionIdAndDeletedFalse(String permissionId);
    Optional<PermissionEntity> findByNameAndDeletedFalse(String name);
    List<PermissionEntity> findByDeletedFalse();
}
