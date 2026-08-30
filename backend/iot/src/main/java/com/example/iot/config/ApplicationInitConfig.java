package com.example.iot.config;

import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.iot.entity.PermissionEntity;
import com.example.iot.entity.RoleEntity;
import com.example.iot.entity.UserEntity;
import com.example.iot.enums.Role;
import com.example.iot.repository.RoleRepository;
import com.example.iot.repository.UserRepository;

@Configuration
public class ApplicationInitConfig {
    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository,RoleRepository roleRepository) {
        return args -> {
            RoleEntity adminRole = roleRepository.findByNameAndDeletedFalse(Role.ADMIN.name()).orElseGet(() -> {
                        RoleEntity role = RoleEntity.builder()
                                .name(Role.ADMIN.name())
                                .description("admin")
                                .permission(new HashSet<>())
                                .build();
                        return roleRepository.save(role);
                    });

            if (!userRepository.existsByEmailAndDeletedFalse("ADMINPTIT@gmail.com")) {
                Set<RoleEntity> x = new HashSet<>();
                x.add(adminRole);
                PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
                UserEntity admin = UserEntity.builder()
                        .email("ADMINPTIT@gmail.com")
                        .password(passwordEncoder.encode("123456789"))
                        .firstname("Nguyen Van")
                        .lastname("A")
                        .phone("0123456789")
                        .role(x)
                        .build();
                userRepository.save(admin);
            }
        };
    }
}