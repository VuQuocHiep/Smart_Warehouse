package com.example.iot.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.iot.dto.request.StockRequest;
import com.example.iot.dto.response.StockHistoryItem;
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

    /** Tồn kho của MỌI sản phẩm đang hoạt động (chưa từng nhập thì tồn = 0), sắp xếp theo SKU. */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public List<StockItemResult> getAllStock() {
        Map<String, Integer> stockByProduct = new HashMap<>();
        for (InventoryEntity i : inventoryRepository.findAll()) {
            stockByProduct.put(i.getProduct().getProductId(), i.getQuantity());
        }
        return productRepository.findByDeletedFalse().stream()
                .map(p -> new StockItemResult(p.getSku(), p.getName(), 0,
                        stockByProduct.getOrDefault(p.getProductId(), 0)))
                .sorted(Comparator.comparing(StockItemResult::sku))
                .toList();
    }

        /** 200 giao dịch gần nhất: admin/manager xem toàn bộ, vai trò khác (staff) chỉ xem phần do mình thực hiện. */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public List<StockHistoryItem> getHistory() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean seeAll = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().endsWith("ADMIN") || a.getAuthority().endsWith("MANAGER"));

        List<StockTransactionEntity> list = seeAll
                ? transactionRepository.findTop200ByOrderByCreatedAtDesc()
                : transactionRepository.findTop200ByCreatedByOrderByCreatedAtDesc(auth != null ? auth.getName() : "");

        return list.stream()
                .map(t -> new StockHistoryItem(
                        t.getProduct().getSku(),
                        t.getProduct().getName(),
                        t.getType().name(),
                        t.getQuantity(),
                        t.getStockAfter(),
                        t.getCreatedBy(),
                        t.getCreatedAt()))
                .toList();
    }

    /** Danh sách SKU đã từng có giao dịch (dùng để khóa không cho đổi SKU). */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public List<String> getUsedSkus() {
        return transactionRepository.findUsedSkus();
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