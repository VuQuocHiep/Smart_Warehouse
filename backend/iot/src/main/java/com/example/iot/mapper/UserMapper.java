package com.example.iot.mapper;

import java.util.HashSet;
import java.util.Set;

import org.mapstruct.Mapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.iot.dto.request.UserRequest;
import com.example.iot.entity.RoleEntity;
import com.example.iot.entity.UserEntity;
import com.example.iot.enums.StatusUser;
import com.example.iot.repository.RoleRepository;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Builder
public class UserMapper {
    private final RoleRepository roleRepository;
    public UserEntity toEntity(UserRequest request){
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        Set<RoleEntity> role = new HashSet<>();
        for(String x:request.getRole()){
            RoleEntity a = roleRepository.findByNameAndDeletedFalse(x).orElseThrow(()->new RuntimeException("Không tồn tại!"));
            role.add(a);
        }
        UserEntity userEntity = UserEntity.builder()
                                        .email(request.getEmail())
                                        .password(passwordEncoder.encode(request.getPassword()))
                                        .firstname(request.getFirstname())
                                        .lastname(request.getLastname())
                                        .phone(request.getPhone())
                                        .role(role)
                                        .statusUser(StatusUser.ACTIVE)
                                        .build();
        return userEntity;
    }
    public void updateEntity(UserRequest request, UserEntity userEntity){
        userEntity.setEmail(request.getEmail());
        userEntity.setFirstname(request.getFirstname());
        userEntity.setLastname(request.getLastname());
        userEntity.setPhone(request.getPhone());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
            userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        Set<RoleEntity> role = new HashSet<>();
        for(String x:request.getRole()){
            RoleEntity a = roleRepository.findByNameAndDeletedFalse(x).orElseThrow(()->new RuntimeException("Không tồn tại!"));
            role.add(a);
        }
        userEntity.setRole(role);
    }
}
