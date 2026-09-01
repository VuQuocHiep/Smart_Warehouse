package com.example.iot.mapper;

import org.springframework.stereotype.Component;

import com.example.iot.dto.request.WarehouseRequest;
import com.example.iot.entity.WarehouseEntity;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Builder
public class WarehouseMapper {
    public WarehouseEntity toEntity(WarehouseRequest request){
        WarehouseEntity warehouseEntity = WarehouseEntity.builder()
                                            .name(request.getName())
                                            .address(request.getAddress())
                                            .description(request.getDescription())
                                            .deleted(false)
                                            .build();
        return warehouseEntity;
    }
    public void updateEntity(WarehouseRequest request,WarehouseEntity warehouseEntity){
        warehouseEntity.setName(request.getName());
        warehouseEntity.setAddress(request.getAddress());
        warehouseEntity.setDescription(request.getDescription());
    }
}
