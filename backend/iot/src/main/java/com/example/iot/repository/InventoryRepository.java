package com.example.iot.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.iot.entity.InventoryEntity;
import com.example.iot.entity.ProductEntity;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<InventoryEntity> findByProduct(ProductEntity product);
}
