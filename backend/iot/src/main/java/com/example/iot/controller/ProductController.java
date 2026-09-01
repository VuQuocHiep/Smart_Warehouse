package com.example.iot.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.iot.dto.request.ProductRequest;
import com.example.iot.entity.ProductEntity;
import com.example.iot.service.ProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    @PostMapping("/create")
    public ResponseEntity<ProductEntity> create(@Valid @RequestBody ProductRequest request){
        return ResponseEntity.ok(productService.create(request));
    }
    @PatchMapping("/update/{id}")
    public ResponseEntity<ProductEntity> update(@Valid @RequestBody ProductRequest request,@PathVariable String id){
        return ResponseEntity.ok(productService.update(request, id));
    }
    @PatchMapping("/delete/{id}")
    public void delete(@PathVariable String id){
        productService.delete(id);
    }
    @GetMapping("/getAll")
    public ResponseEntity<List<ProductEntity>> getAll(){
        return ResponseEntity.ok(productService.getAll());
    }
    @GetMapping("/getProductById/{id}")
    public ResponseEntity<ProductEntity> getProductById(@PathVariable String id){
        return ResponseEntity.ok(productService.getProductById(id));
    }
    @GetMapping("/getProductByName/{name}")
    public ResponseEntity<ProductEntity> getProductByName(@PathVariable String name){
        return ResponseEntity.ok(productService.getProductByName(name));
    }
    @GetMapping("/getProductBySku/{sku}")
    public ResponseEntity<ProductEntity> getProductBySku(@PathVariable String sku){
        return ResponseEntity.ok(productService.getProductBySku(sku));
    }
}
