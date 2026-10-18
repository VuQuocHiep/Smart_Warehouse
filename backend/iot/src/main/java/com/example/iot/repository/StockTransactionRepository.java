package com.example.iot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.iot.entity.StockTransactionEntity;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransactionEntity, String> {
    List<StockTransactionEntity> findAllByOrderByCreatedAtDesc();

    List<StockTransactionEntity> findTop200ByOrderByCreatedAtDesc();

    /** Các SKU đã từng có giao dịch nhập/xuất. */
    @Query("select distinct t.product.sku from StockTransactionEntity t")
    List<String> findUsedSkus();
}