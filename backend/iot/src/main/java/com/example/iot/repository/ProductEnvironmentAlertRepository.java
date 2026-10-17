package com.example.iot.repository;

import com.example.iot.entity.ProductEnvironmentAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductEnvironmentAlertRepository extends JpaRepository<ProductEnvironmentAlert, String> {
    List<ProductEnvironmentAlert> findByShelfIdAndResolvedAtIsNull(String shelfId);
    List<ProductEnvironmentAlert> findTop100ByOrderByCreatedAtDesc();
}
