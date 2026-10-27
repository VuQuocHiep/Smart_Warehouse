package com.example.iot.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Immutable weighing evidence after capture; consumed atomically with stock movement. */
@Entity
@Table(name = "scale_ticket")
@Getter @Setter
public class ScaleTicket {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String sku;
    private int quantity;
    private String stockType;
    private String capturedBy;
    private Instant capturedAt;
    private double weightGrams;
    private double grossGrams;
    private double expectedGrams;
    private double toleranceGrams;
    private boolean consumed;
}
