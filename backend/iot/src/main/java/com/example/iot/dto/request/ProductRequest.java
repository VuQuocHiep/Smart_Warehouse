package com.example.iot.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductRequest {
    @NotBlank
    String sku;
    @NotBlank
    String name;
    @NotBlank
    String category;
    String description;
    String image_url;
    @DecimalMin(value = "0.0")
    double unit_weight;
    String unit;
}
