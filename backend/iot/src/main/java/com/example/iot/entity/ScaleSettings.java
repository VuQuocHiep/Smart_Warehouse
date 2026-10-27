package com.example.iot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "scale_settings")
@Getter @Setter
public class ScaleSettings {
    @Id
    private int id = 1;
    private double absoluteGrams = 50;
    private double percent = 0;
}
