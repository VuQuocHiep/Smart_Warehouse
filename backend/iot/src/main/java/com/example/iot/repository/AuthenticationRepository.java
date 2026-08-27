package com.example.iot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iot.entity.UserEntity;

public interface AuthenticationRepository extends JpaRepository<UserEntity,String>{
    boolean existsByEmailAndDeletedFalse(String email);
}
