package com.example.iot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iot.entity.ProductEntity;

public interface ProductRepository extends JpaRepository<ProductEntity,String>{
    boolean existsByNameAndDeletedFalse(String name);
    boolean existsBySkuAndDeletedFalse(String sku);
    boolean existsByProductIdAndDeletedFalse(String id);
    Optional<ProductEntity> findByProductIdAndDeletedFalse(String id);
    List<ProductEntity> findByDeletedFalse();
    Optional<ProductEntity> findByNameAndDeletedFalse(String name);
    Optional<ProductEntity> findBySkuAndDeletedFalse(String sku);
}
