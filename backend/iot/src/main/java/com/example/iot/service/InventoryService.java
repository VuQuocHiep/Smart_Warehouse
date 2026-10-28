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
import com.example.iot.entity.MonitoredShelf;
import com.example.iot.entity.ProductEntity;
import com.example.iot.entity.ShelfStockEntity;
import com.example.iot.entity.StockTransactionEntity;
import com.example.iot.entity.StockTransactionEntity.StockType;
import com.example.iot.entity.WarehouseEntity;
import com.example.iot.repository.InventoryRepository;
import com.example.iot.repository.MonitoredShelfRepository;
import com.example.iot.repository.ProductRepository;
import com.example.iot.repository.ShelfStockRepository;
import com.example.iot.repository.StockTransactionRepository;
import com.example.iot.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private static final int DEFAULT_CAPACITY = 100;

    private final InventoryRepository inventoryRepository;
    private final StockTransactionRepository transactionRepository;
    private final ProductRepository productRepository;
    private final MonitoredShelfRepository shelves;
    private final ShelfStockRepository shelfStocks;
    private final WarehouseRepository warehouses;
    private final ScaleService scaleService;
    private final WarehouseStockService warehouseStock;

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
        Map<String,String> names = new HashMap<>();
        warehouses.findAll().forEach(w -> names.put(w.getWarehouseId(), w.getName()));

        return list.stream()
                .map(t -> new StockHistoryItem(
                        t.getProduct().getSku(),
                        t.getProduct().getName(),
                        t.getType().name(),
                        t.getQuantity(),
                        t.getStockAfter(),
                        t.getCreatedBy(),
                        t.getCreatedAt(),
                        t.getNote(), StockHistoryItem.WeightEvidence.from(t.getWeighing()),
                        t.getWarehouseId(), names.get(t.getWarehouseId()), t.getWarehouseStockAfter(), t.getDataSource()))
                .toList();
    }

    /** Danh sách SKU đã từng có giao dịch (dùng để khóa không cho đổi SKU). */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public List<String> getUsedSkus() {
        return transactionRepository.findUsedSkus();
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getWarehouseStock() { return warehouseStock.stocks(); }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public List<Map<String,Object>> getWarehouses() {
        return warehouses.findByDeletedFalse().stream().map(w -> Map.<String,Object>of("warehouseId",w.getWarehouseId(),"name",w.getName())).toList();
    }

    /**
     * Xử lý cả phiếu trong một transaction: nếu một dòng lỗi (SKU không có, xuất quá tồn, kệ hết chỗ)
     * thì toàn bộ phiếu bị hủy, không dòng nào được lưu.
     */
    private List<StockItemResult> apply(StockRequest request, StockType type) {
        String user = currentUser();
        List<StockItemResult> results = new ArrayList<>();
        String warehouseId = request.getWarehouseId();
        if (warehouseId == null || warehouses.findById(warehouseId).filter(w -> !w.isDeleted()).isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Cần chọn kho đang hoạt động.");
        }

        // Acquire all product locks before any shelf locks or mutations.
        var ordered = request.getItems().stream().sorted(Comparator.comparing(i -> i.getSku().trim())).toList();
        Map<String,ProductEntity> lockedProducts = new HashMap<>();
        for (var item : ordered) lockedProducts.computeIfAbsent(item.getSku().trim(), sku -> productRepository.findForUpdateBySku(sku)
                .orElseThrow(() -> new RuntimeException("SKU không tồn tại: " + sku)));
        shelves.findByWarehouseId(warehouseId).stream().sorted(Comparator.comparing(MonitoredShelf::getId))
                .forEach(s -> shelves.findForUpdateById(s.getId()).orElseThrow());
        for (StockRequest.Item item : ordered) {
            ProductEntity product = lockedProducts.get(item.getSku().trim());

            InventoryEntity inventory = inventoryRepository.findByProduct(product)
                    .orElseGet(() -> InventoryEntity.builder().product(product).quantity(0).build());

            int qty = item.getQuantity();
            if (item.isWeighRequired() && (item.getScaleTicketId() == null || item.getScaleTicketId().isBlank())) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Dòng đã chọn cân phải có kết quả cân hợp lệ: " + product.getSku());
            }
            var weighing = item.getScaleTicketId() == null ? null : scaleService.consume(
                    item.getScaleTicketId(), product.getSku(), qty, type.name(), user, product.getUnit_weight());
            int newStock = type == StockType.IN ? inventory.getQuantity() + qty : inventory.getQuantity() - qty;
            if (newStock < 0) {
                throw new RuntimeException("Không đủ tồn kho cho SKU " + product.getSku()
                        + " (hiện có " + inventory.getQuantity() + ", cần xuất " + qty + ")");
            }

            int warehouseAfter = warehouseStock.change(warehouseId, product.getProductId(), type == StockType.IN ? qty : -qty);
            String note = type == StockType.IN
                    ? placeOnShelf(product, qty, item.getShelfId(), warehouseId)
                    : takeFromShelves(product, qty, warehouseId);

            inventory.setQuantity(newStock);
            inventoryRepository.save(inventory);

            transactionRepository.save(StockTransactionEntity.builder()
                    .product(product)
                    .type(type)
                    .quantity(qty)
                    .stockAfter(newStock)
                    .warehouseId(warehouseId)
                    .warehouseStockAfter(warehouseAfter)
                    .dataSource(warehouseStock.source(warehouseId, product.getProductId()))
                    .createdAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
                    .createdBy(user)
                    .note(note)
                    .weighing(weighing)
                    .build());

            results.add(new StockItemResult(product.getSku(), product.getName(), qty, newStock));
        }
        return results;
    }

    /** Nhập kho: xếp hàng vào kệ đã chọn (kiểm tra sức chứa), trả về ghi chú vị trí. */
    private String placeOnShelf(ProductEntity product, int qty, String shelfId, String warehouseId) {
        if (shelfId == null || shelfId.isBlank()) {
            return "Nhập kho, chưa chỉ định kệ";
        }
        MonitoredShelf shelf = shelves.findForUpdateById(shelfId)
                .orElseThrow(() -> new RuntimeException("Kệ không tồn tại"));
        if (!warehouseId.equals(shelf.getWarehouseId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Kệ không thuộc kho đã chọn.");
        int capacity = shelf.getCapacity() == null ? DEFAULT_CAPACITY : shelf.getCapacity();
        long used = shelfStocks.findByShelfId(shelfId).stream().mapToLong(ShelfStockEntity::getQuantity).sum();
        if (used + qty > capacity) {
            throw new RuntimeException("Kệ " + shelf.getName() + " không đủ sức chứa (còn trống "
                    + Math.max(0L, capacity - used) + ", cần " + qty + ")");
        }
        ShelfStockEntity row = shelfStocks.findByShelfIdAndProductId(shelfId, product.getProductId())
                .orElseGet(() -> ShelfStockEntity.builder()
                        .shelfId(shelfId).productId(product.getProductId()).quantity(0).build());
        row.setQuantity(row.getQuantity() + qty);
        shelfStocks.save(row);
        shelf.getProductIds().add(product.getProductId());
        shelves.save(shelf);
        return limit("Xếp vào " + shelfLabel(shelf));
    }

    /** Xuất kho: trừ lần lượt từ các kệ đang chứa nhiều hàng này nhất, trả về ghi chú nơi lấy. */
    private String takeFromShelves(ProductEntity product, int qty, String warehouseId) {
        int remaining = qty;
        List<String> parts = new ArrayList<>();
        for (ShelfStockEntity row : shelfStocks.findByProductIdOrderByQuantityDesc(product.getProductId())) {
            if (shelves.findById(row.getShelfId()).filter(s -> warehouseId.equals(s.getWarehouseId())).isEmpty()) continue;
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, row.getQuantity());
            if (take <= 0) {
                continue;
            }
            row.setQuantity(row.getQuantity() - take);
            shelfStocks.save(row);
            remaining -= take;
            parts.add(shelves.findById(row.getShelfId()).map(MonitoredShelf::getName).orElse("?") + " (-" + take + ")");
        }
        if (parts.isEmpty()) {
            return "Xuất kho";
        }
        String note = "Lấy từ " + String.join(", ", parts);
        if (remaining > 0) {
            note += "; " + remaining + " chưa xếp kệ";
        }
        return limit(note);
    }

    private String shelfLabel(MonitoredShelf shelf) {
        String warehouseName = shelf.getWarehouseId() == null ? "" : warehouses.findById(shelf.getWarehouseId())
                .map(WarehouseEntity::getName).orElse("");
        return warehouseName.isBlank() ? shelf.getName() : shelf.getName() + " · " + warehouseName;
    }

    private static String limit(String text) {
        return text.length() > 255 ? text.substring(0, 255) : text;
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
