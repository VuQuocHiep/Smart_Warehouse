package com.example.iot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iot.entity.RoleEntity;
import com.example.iot.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity,String>{
    boolean existsByEmailAndDeletedFalse(String email);
    boolean existsByUserIdAndDeletedFalse(String id);
    Optional<UserEntity> findByUserIdAndDeletedFalse(String id);
    List<UserEntity> findByDeletedFalse();
    Optional<UserEntity> findByEmailAndDeletedFalse(String email);
}
