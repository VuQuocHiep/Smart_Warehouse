package com.example.iot.service;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.example.iot.dto.request.ProductRequest;
import com.example.iot.entity.ProductEntity;
import com.example.iot.exception.DuplicateException;
import com.example.iot.mapper.ProductMapper;
import com.example.iot.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductEntity create(ProductRequest request){
        if(productRepository.existsBySkuAndDeletedFalse(request.getSku())){
            throw new DuplicateException("SKU đã tồn tại!");
        }
        if(productRepository.existsByNameAndDeletedFalse(request.getName())){
            throw new DuplicateException("Sản phẩm đã tồn tại!");
        }
        ProductEntity productEntity = productMapper.toEntity(request);
        return productRepository.save(productEntity);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductEntity update(ProductRequest request,String id){
        ProductEntity productEntity = productRepository.findByProductIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
        productRepository.findBySkuAndDeletedFalse(request.getSku()).ifPresent(existingProduct -> {
            if (!existingProduct.getProductId().equals(id)) {
                throw new DuplicateException("SKU đã tồn tại!");
            }
        });
        productRepository.findByNameAndDeletedFalse(request.getName()).ifPresent(existingProduct -> {
            if (!existingProduct.getProductId().equals(id)) {
                throw new DuplicateException("Sản phẩm đã tồn tại!");
            }
        });
        productMapper.updateEntity(request, productEntity);
        return productRepository.save(productEntity);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(String id){
        ProductEntity productEntity = productRepository.findByProductIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
        productEntity.setDeleted(true);
        productRepository.save(productEntity);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<ProductEntity> getAll(){
        return productRepository.findByDeletedFalse();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductEntity getProductByName(String name){
        return productRepository.findByNameAndDeletedFalse(name).orElseThrow(()->new RuntimeException("Không tồn tại!"));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductEntity getProductBySku(String sku){
        return productRepository.findBySkuAndDeletedFalse(sku).orElseThrow(()->new RuntimeException("Không tồn tại!"));
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProductEntity getProductById(String id){
        return productRepository.findByProductIdAndDeletedFalse(id).orElseThrow(()->new RuntimeException("Không tồn tại!"));
    }
}
