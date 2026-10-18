package com.example.iot.repository;

import com.example.iot.entity.EnvironmentAlertReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EnvironmentAlertReceiptRepository extends JpaRepository<EnvironmentAlertReceipt, String> {
    List<EnvironmentAlertReceipt> findByUsername(String username);
    boolean existsByUsernameAndAlertId(String username, String alertId);
}
