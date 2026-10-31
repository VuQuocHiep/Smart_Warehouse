package com.example.iot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.iot.entity.ProductEntity;

public interface ProductRepository extends JpaRepository<ProductEntity,String>{
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from ProductEntity p where p.deleted=false and (p.sku=:sku or exists (select a from ProductSkuAlias a where a.productId=p.productId and a.oldSku=:sku))")
    Optional<ProductEntity> findForUpdateBySku(@org.springframework.data.repository.query.Param("sku") String sku);
    boolean existsByNameAndDeletedFalse(String name);
    @org.springframework.data.jpa.repository.Query("select (count(p)>0) from ProductEntity p where p.deleted=false and (p.sku=:sku or exists (select a from ProductSkuAlias a where a.productId=p.productId and a.oldSku=:sku))")
    boolean existsBySkuAndDeletedFalse(@org.springframework.data.repository.query.Param("sku") String sku);
    boolean existsByProductIdAndDeletedFalse(String id);
    Optional<ProductEntity> findByProductIdAndDeletedFalse(String id);
    List<ProductEntity> findByDeletedFalse();
    Optional<ProductEntity> findByNameAndDeletedFalse(String name);
    @org.springframework.data.jpa.repository.Query("select p from ProductEntity p where p.deleted=false and (p.sku=:sku or exists (select a from ProductSkuAlias a where a.productId=p.productId and a.oldSku=:sku))")
    Optional<ProductEntity> findBySkuAndDeletedFalse(@org.springframework.data.repository.query.Param("sku") String sku);
}
