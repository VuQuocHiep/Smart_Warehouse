package com.example.iot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.iot.entity.ShelfStockEntity;

@Repository
public interface ShelfStockRepository extends JpaRepository<ShelfStockEntity, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<ShelfStockEntity> findByShelfIdAndProductId(String shelfId, String productId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<ShelfStockEntity> findByProductIdOrderByQuantityDesc(String productId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<ShelfStockEntity> findByShelfId(String shelfId);

    @Query("select coalesce(sum(s.quantity), 0L) from ShelfStockEntity s where s.shelfId = :shelfId")
    long sumQuantity(@Param("shelfId") String shelfId);
}
