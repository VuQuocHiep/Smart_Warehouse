package com.example.iot.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.iot.enums.UnitProduct;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String productId;
    String sku;
    String name;
    String category;
    @Lob
    @Column(columnDefinition = "TEXT")
    String description;
    @Lob
    @Column(columnDefinition = "TEXT")
    String image_url;
    double unit_weight;
    @Enumerated(EnumType.STRING)
    UnitProduct unit = UnitProduct.ITEM;
    boolean deleted = false;
    @CreationTimestamp
    @Column(updatable = false)
    LocalDateTime createdAt;
    @UpdateTimestamp
    LocalDateTime updatedAt; 
}
