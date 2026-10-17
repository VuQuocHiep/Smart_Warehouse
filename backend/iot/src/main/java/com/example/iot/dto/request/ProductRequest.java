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
    @jakarta.validation.constraints.DecimalMin("-100")
    @jakarta.validation.constraints.DecimalMax("200")
    Double minTemperature;
    @jakarta.validation.constraints.DecimalMin("-100")
    @jakarta.validation.constraints.DecimalMax("200")
    Double maxTemperature;
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.DecimalMax("100")
    Double minHumidity;
    @jakarta.validation.constraints.DecimalMin("0")
    @jakarta.validation.constraints.DecimalMax("100")
    Double maxHumidity;

    @jakarta.validation.constraints.AssertTrue(message = "Ngưỡng tối thiểu phải nhỏ hơn hoặc bằng ngưỡng tối đa")
    public boolean isValidEnvironmentRange() {
        return (minTemperature == null || maxTemperature == null || minTemperature <= maxTemperature)
            && (minHumidity == null || maxHumidity == null || minHumidity <= maxHumidity);
    }
    String description;
    String image_url;
    @DecimalMin(value = "0.0")
    double unit_weight;
    String unit;
}
