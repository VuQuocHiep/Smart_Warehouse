package com.example.iot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iot.entity.PermissionEntity;
import com.example.iot.entity.RoleEntity;

public interface RoleRepository extends JpaRepository<RoleEntity,String>{
    boolean existsByNameAndDeletedFalse(String name);
    boolean existsByRoleIdAndDeletedFalse(String id);
    Optional<RoleEntity> findByRoleIdAndDeletedFalse(String id);
    List<RoleEntity> findByDeletedFalse();
    Optional<RoleEntity> findByNameAndDeletedFalse(String name);
}
