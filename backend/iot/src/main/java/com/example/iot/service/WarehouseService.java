package com.example.iot.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.example.iot.dto.request.WarehouseRequest;
import com.example.iot.entity.WarehouseEntity;
import com.example.iot.exception.DuplicateException;
import com.example.iot.mapper.WarehouseMapper;
import com.example.iot.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WarehouseEntity create(WarehouseRequest request){
        if(warehouseRepository.existsByNameAndDeletedFalse(request.getName())){
            throw new DuplicateException("Đã tồn tại!");
        }
        WarehouseEntity warehouseEntity = warehouseMapper.toEntity(request);
        return warehouseRepository.save(warehouseEntity);
    }
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WarehouseEntity update(WarehouseRequest request,String id){
        WarehouseEntity warehouseEntity = warehouseRepository.findByWarehouseIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
        warehouseRepository.findByNameAndDeletedFalse(request.getName()).ifPresent(existingWarehouse -> {
            if (!existingWarehouse.getWarehouseId().equals(id)) {
                throw new DuplicateException("Đã tồn tại!");
            }
        });
        warehouseMapper.updateEntity(request, warehouseEntity);
        return warehouseRepository.save(warehouseEntity);
    }
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(String id){
        WarehouseEntity warehouseEntity = warehouseRepository.findByWarehouseIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
        warehouseEntity.setDeleted(true);
        warehouseRepository.save(warehouseEntity);
    }
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<WarehouseEntity> getAll(){
        return warehouseRepository.findByDeletedFalse();
    }
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WarehouseEntity getWarehouseByName(String name){
        return warehouseRepository.findByNameAndDeletedFalse(name).orElseThrow(()->new RuntimeException("Không tồn tại!"));
    }
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WarehouseEntity getWarehouseById(String id){
        return warehouseRepository.findByWarehouseIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
    }
}
