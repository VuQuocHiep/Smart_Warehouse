package com.example.iot.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_environment_alert")
@Data
public class ProductEnvironmentAlert {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String shelfId;
    private String productId;
    private boolean simulated;
    @Column(columnDefinition = "TEXT")
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
