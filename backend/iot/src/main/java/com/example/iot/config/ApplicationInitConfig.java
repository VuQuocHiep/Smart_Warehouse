package com.example.iot.config;

import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.iot.entity.RoleEntity;
import com.example.iot.entity.UserEntity;
import com.example.iot.enums.Role;
import com.example.iot.repository.RoleRepository;
import com.example.iot.repository.UserRepository;

@Configuration
public class ApplicationInitConfig {
    @Bean
    ApplicationRunner applicationRunner(UserRepository userRepository,RoleRepository roleRepository,
            @Value("${app.cloud:false}") boolean cloud,
            @Value("${app.bootstrap-admin.email:ADMINPTIT@gmail.com}") String email,
            @Value("${app.bootstrap-admin.password:}") String configuredPassword) {
        return args -> {
            if (cloud && (email.isBlank() || configuredPassword.length() < 12))
                throw new IllegalStateException("Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD (at least 12 characters)");
            for (Role appRole : Role.values()) {
                roleRepository.findByNameAndDeletedFalse(appRole.name()).orElseGet(() -> {
                        RoleEntity role = RoleEntity.builder()
                                .name(appRole.name())
                                .description(appRole.name().toLowerCase())
                                .permission(new HashSet<>())
                                .build();
                        return roleRepository.save(role);
                    });
            }

            RoleEntity adminRole = roleRepository.findByNameAndDeletedFalse(Role.ADMIN.name())
                    .orElseThrow(() -> new RuntimeException("Không tạo được role ADMIN"));

            if (!userRepository.existsByEmailAndDeletedFalse(email)) {
                Set<RoleEntity> x = new HashSet<>();
                x.add(adminRole);
                PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
                UserEntity admin = UserEntity.builder()
                        .email(email)
                        .password(passwordEncoder.encode(configuredPassword.isBlank() && !cloud ? "123456789" : configuredPassword))
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
