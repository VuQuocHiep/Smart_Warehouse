package com.example.iot.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.iot.entity.StockTransactionEntity;

@Repository
public interface StockTransactionRepository extends JpaRepository<StockTransactionEntity, String> {
    List<StockTransactionEntity> findAllByOrderByCreatedAtDesc();

    List<StockTransactionEntity> findTop200ByOrderByCreatedAtDesc();

    List<StockTransactionEntity> findTop200ByCreatedByOrderByCreatedAtDesc(String createdBy);

    /** Các SKU đã từng có giao dịch nhập/xuất. */
    @Query("select distinct t.product.sku from StockTransactionEntity t")
    List<String> findUsedSkus();

    /** Tổng số lượng xuất của một sản phẩm từ một mốc thời gian (để tính tần suất xuất). */
    @Query("select coalesce(sum(t.quantity), 0L) from StockTransactionEntity t "
            + "where t.product.productId = :productId and t.type = :type and t.createdAt >= :since")
    long sumOutSince(@Param("productId") String productId,
            @Param("type") StockTransactionEntity.StockType type,
            @Param("since") LocalDateTime since);
}