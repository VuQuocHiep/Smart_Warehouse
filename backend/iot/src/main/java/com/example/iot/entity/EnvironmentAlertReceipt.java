package com.example.iot.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "environment_alert_receipt", uniqueConstraints = @UniqueConstraint(columnNames = {"username", "alert_id"}))
@Data
public class EnvironmentAlertReceipt {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String username;
    private String alertId;
}
