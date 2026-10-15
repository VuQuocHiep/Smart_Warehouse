package com.example.iot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.iot.entity.StockTransactionEntity;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransactionEntity, String> {
    List<StockTransactionEntity> findAllByOrderByCreatedAtDesc();
}