package com.example.iot.repository;

import com.example.iot.entity.MonitoredShelf;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MonitoredShelfRepository extends JpaRepository<MonitoredShelf, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MonitoredShelf s where s.id = :id")
    Optional<MonitoredShelf> findForUpdateById(@org.springframework.data.repository.query.Param("id") String id);
    java.util.List<MonitoredShelf> findByWarehouseId(String warehouseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MonitoredShelf> findByDeviceCode(String deviceCode);
}
