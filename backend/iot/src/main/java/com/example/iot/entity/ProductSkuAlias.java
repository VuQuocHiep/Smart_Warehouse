package com.example.iot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="product_sku_alias")
@Getter
@NoArgsConstructor
public class ProductSkuAlias {
    @Id
    private String oldSku;
    private String productId;
    private String newSku;
}
