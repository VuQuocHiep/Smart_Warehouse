package com.example.iot.repository;

import com.example.iot.entity.WarehouseEnvironmentReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface WarehouseEnvironmentReadingRepository extends JpaRepository<WarehouseEnvironmentReading, String> {
    Optional<WarehouseEnvironmentReading> findTopByOrderByRecordedAtDesc();
    List<WarehouseEnvironmentReading> findAllByOrderByRecordedAtDesc(Pageable pageable);
}
