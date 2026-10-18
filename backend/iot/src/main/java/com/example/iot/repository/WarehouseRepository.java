package com.example.iot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iot.entity.WarehouseEntity;

public interface WarehouseRepository extends JpaRepository<WarehouseEntity,String>{
    Optional<WarehouseEntity> findByMonitoringDeviceCodeAndDeletedFalse(String deviceCode);
    List<WarehouseEntity> findByMonitoringSlotIsNotNullAndDeletedFalseOrderByMonitoringSlotAsc();
    boolean existsByNameAndDeletedFalse(String name);
    boolean existsByWarehouseIdAndDeletedFalse(String id);
    Optional<WarehouseEntity> findByWarehouseIdAndDeletedFalse(String id);
    List<WarehouseEntity> findByDeletedFalse();
    Optional<WarehouseEntity> findByNameAndDeletedFalse(String name);
}
