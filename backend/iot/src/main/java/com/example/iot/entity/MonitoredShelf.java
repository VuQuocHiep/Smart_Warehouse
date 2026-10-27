package com.example.iot.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "monitored_shelf")
@Data
public class MonitoredShelf {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String name;
    private String warehouseId;
    @Column(unique = true)
    private String deviceCode;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "monitored_shelf_product", joinColumns = @JoinColumn(name = "shelf_id"))
    @Column(name = "product_id")
    private Set<String> productIds = new HashSet<>();
    private Double temperature;
    private Double humidity;
    private LocalDateTime recordedAt;
    private Integer capacity;
}
