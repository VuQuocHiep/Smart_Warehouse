package com.example.iot.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.iot.dto.request.StockRequest;
import com.example.iot.dto.response.StockItemResult;
import com.example.iot.entity.InventoryEntity;
import com.example.iot.entity.ProductEntity;
import com.example.iot.entity.StockTransactionEntity;
import com.example.iot.entity.StockTransactionEntity.StockType;
import com.example.iot.repository.InventoryRepository;
import com.example.iot.repository.ProductRepository;
import com.example.iot.repository.StockTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository transactionRepository;
    private final ProductRepository productRepository;

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Transactional
    public List<StockItemResult> stockIn(StockRequest request) {
        return apply(request, StockType.IN);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Transactional
    public List<StockItemResult> stockOut(StockRequest request) {
        return apply(request, StockType.OUT);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public List<StockItemResult> getAllStock() {
        return inventoryRepository.findAll().stream()
                .map(i -> new StockItemResult(i.getProduct().getSku(), i.getProduct().getName(), 0, i.getQuantity()))
                .toList();
    }

    /**
     * Xử lý cả phiếu trong một transaction: nếu một dòng lỗi (SKU không có, xuất quá tồn)
     * thì toàn bộ phiếu bị hủy, không dòng nào được lưu.
     */
    private List<StockItemResult> apply(StockRequest request, StockType type) {
        String user = currentUser();
        List<StockItemResult> results = new ArrayList<>();

        for (StockRequest.Item item : request.getItems()) {
            ProductEntity product = productRepository.findBySkuAndDeletedFalse(item.getSku().trim())
                    .orElseThrow(() -> new RuntimeException("SKU không tồn tại: " + item.getSku()));

            InventoryEntity inventory = inventoryRepository.findByProduct(product)
                    .orElseGet(() -> InventoryEntity.builder().product(product).quantity(0).build());

            int qty = item.getQuantity();
            int newStock = type == StockType.IN ? inventory.getQuantity() + qty : inventory.getQuantity() - qty;
            if (newStock < 0) {
                throw new RuntimeException("Không đủ tồn kho cho SKU " + product.getSku()
                        + " (hiện có " + inventory.getQuantity() + ", cần xuất " + qty + ")");
            }

            inventory.setQuantity(newStock);
            inventoryRepository.save(inventory);

            transactionRepository.save(StockTransactionEntity.builder()
                    .product(product)
                    .type(type)
                    .quantity(qty)
                    .stockAfter(newStock)
                    .createdBy(user)
                    .note("Quét camera")
                    .build());

            results.add(new StockItemResult(product.getSku(), product.getName(), qty, newStock));
        }
        return results;
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}