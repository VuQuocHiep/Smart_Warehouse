package com.example.iot.mapper;

import org.springframework.stereotype.Component;

import com.example.iot.dto.request.ProductRequest;
import com.example.iot.entity.ProductEntity;
import com.example.iot.enums.UnitProduct;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Builder
public class ProductMapper {
    private UnitProduct getUnit(String unit){
        if(unit == null || unit.isBlank()){
            return UnitProduct.ITEM;
        }
        return UnitProduct.valueOf(unit.trim().toUpperCase());
    }

    public ProductEntity toEntity(ProductRequest request){
        ProductEntity productEntity = ProductEntity.builder()
                                        .sku(request.getSku())
                                        .name(request.getName())
                                        .category(request.getCategory())
                                        .description(request.getDescription())
                                        .image_url(request.getImage_url())
                                        .unit_weight(request.getUnit_weight())
                                        .unit(getUnit(request.getUnit()))
                                        .deleted(false)
                                        .build();
        return productEntity;
    }
    public void updateEntity(ProductRequest request,ProductEntity productEntity){
        productEntity.setSku(request.getSku());
        productEntity.setName(request.getName());
        productEntity.setCategory(request.getCategory());
        productEntity.setDescription(request.getDescription());
        productEntity.setImage_url(request.getImage_url());
        productEntity.setUnit_weight(request.getUnit_weight());
        productEntity.setUnit(getUnit(request.getUnit()));
    }
}
