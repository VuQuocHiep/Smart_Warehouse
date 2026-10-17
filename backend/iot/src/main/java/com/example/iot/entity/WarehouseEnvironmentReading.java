package com.example.iot.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_environment_reading")
@Data
public class WarehouseEnvironmentReading {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String deviceCode;
    private Double temperature;
    private Double humidity;
    private Double gas;
    private Double gasTvocPpb;
    private Integer riskLevel;
    private Double confidence;
    private String aiStatus;
    private String actionCode;
    private String modelVersion;
    private String predictionStatus;
    private Integer aiRiskLevel;
    private Integer gasRiskLevel;
    private String decisionSource;
    private LocalDateTime recordedAt;

    public boolean isControlAllowed() {
        return riskLevel != null && "READY".equals(predictionStatus);
    }
}
