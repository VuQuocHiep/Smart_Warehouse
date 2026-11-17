package com.example.iot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.iot.dto.request.StockRequest;
import com.example.iot.entity.MonitoredShelf;
import com.example.iot.entity.ProductEntity;
import com.example.iot.entity.ProductEnvironmentAlert;
import com.example.iot.entity.StockTransactionEntity.StockType;
import com.example.iot.entity.WarehouseEntity;
import com.example.iot.repository.MonitoredShelfRepository;
import com.example.iot.repository.ProductEnvironmentAlertRepository;
import com.example.iot.repository.ProductRepository;
import com.example.iot.repository.ShelfStockRepository;
import com.example.iot.repository.StockTransactionRepository;
import com.example.iot.repository.WarehouseRepository;

class PlacementServiceTest {

    private MonitoredShelfRepository shelves;
    private WarehouseRepository warehouses;
    private ProductRepository products;
    private ShelfStockRepository shelfStocks;
    private ProductEnvironmentAlertRepository alerts;
    private StockTransactionRepository transactions;
    private PlacementService service;

    @BeforeEach
    void setUp() {
        shelves = mock(MonitoredShelfRepository.class);
        warehouses = mock(WarehouseRepository.class);
        products = mock(ProductRepository.class);
        shelfStocks = mock(ShelfStockRepository.class);
        alerts = mock(ProductEnvironmentAlertRepository.class);
        transactions = mock(StockTransactionRepository.class);

        service = new PlacementService(shelves, warehouses, products, shelfStocks, alerts, transactions);
    }

    private ProductEntity createProduct(String id, String sku, Double minT, Double maxT, Double minH, Double maxH) {
        ProductEntity p = new ProductEntity();
        p.setProductId(id);
        p.setSku(sku);
        p.setName("Product " + sku);
        p.setMinTemperature(minT);
        p.setMaxTemperature(maxT);
        p.setMinHumidity(minH);
        p.setMaxHumidity(maxH);
        return p;
    }

    private MonitoredShelf createShelf(String id, String name, String warehouseId, Integer capacity, Double temp, Double hum) {
        MonitoredShelf s = new MonitoredShelf();
        s.setId(id);
        s.setName(name);
        s.setWarehouseId(warehouseId);
        s.setCapacity(capacity);
        s.setTemperature(temp);
        s.setHumidity(hum);
        s.setProductIds(new java.util.HashSet<>());
        return s;
    }

    private WarehouseEntity createWarehouse(String id, String name, Integer slot) {
        WarehouseEntity w = new WarehouseEntity();
        w.setWarehouseId(id);
        w.setName(name);
        w.setMonitoringSlot(slot);
        return w;
    }

    @Test
    void testBasicPlacementSelection() {
        String whId = "wh-1";
        WarehouseEntity wh = createWarehouse(whId, "Kho 1", 1);
        ProductEntity prod = createProduct("p1", "SKU01", 15.0, 25.0, 40.0, 70.0);

        MonitoredShelf s1 = createShelf("s1", "Kệ A", whId, 100, 20.0, 50.0);
        MonitoredShelf s2 = createShelf("s2", "Kệ B", whId, 100, 35.0, 80.0); // vi phạm nhiệt độ & độ ẩm
        MonitoredShelf sOtherWh = createShelf("s3", "Kệ C", "wh-2", 100, 20.0, 50.0);

        when(shelves.findAll()).thenReturn(List.of(s1, s2, sOtherWh));
        when(warehouses.findAllById(any())).thenReturn(List.of(wh));
        when(products.findBySkuAndDeletedFalse("SKU01")).thenReturn(Optional.of(prod));
        when(transactions.sumOutSince(eq("p1"), eq(StockType.OUT), any(LocalDateTime.class))).thenReturn(0L);
        when(shelfStocks.sumQuantity("s1")).thenReturn(10L);
        when(shelfStocks.sumQuantity("s2")).thenReturn(10L);
        when(shelfStocks.sumQuantityByShelfAndProduct(any(), any())).thenReturn(0L);
        when(alerts.findByShelfIdAndResolvedAtIsNull(any())).thenReturn(List.of());

        StockRequest.Item item = new StockRequest.Item();
        item.setSku("SKU01");
        item.setQuantity(20);

        StockRequest req = new StockRequest();
        req.setWarehouseId(whId);
        req.setItems(List.of(item));

        List<PlacementService.Result> results = service.suggest(req);
        assertEquals(1, results.size());

        PlacementService.Result res = results.get(0);
        assertEquals("SKU01", res.sku());
        assertEquals(2, res.options().size()); // Only shelves in wh-1

        PlacementService.Option opt1 = res.options().get(0);
        assertEquals("s1", opt1.shelfId());
        assertTrue(opt1.eligible());
        assertTrue(opt1.recommended());
        assertTrue(opt1.score() > 0);

        PlacementService.Option opt2 = res.options().get(1);
        assertEquals("s2", opt2.shelfId());
        assertFalse(opt2.eligible());
        assertFalse(opt2.recommended());
        assertEquals(0, opt2.score());
        assertTrue(opt2.reasons().stream().anyMatch(r -> r.contains("Môi trường không phù hợp")));
    }

    @Test
    void testCapacityExceededMarksIneligible() {
        String whId = "wh-1";
        WarehouseEntity wh = createWarehouse(whId, "Kho 1", 1);
        ProductEntity prod = createProduct("p1", "SKU01", null, null, null, null);

        MonitoredShelf s1 = createShelf("s1", "Kệ A", whId, 50, 20.0, 50.0);

        when(shelves.findAll()).thenReturn(List.of(s1));
        when(warehouses.findAllById(any())).thenReturn(List.of(wh));
        when(products.findBySkuAndDeletedFalse("SKU01")).thenReturn(Optional.of(prod));
        when(shelfStocks.sumQuantity("s1")).thenReturn(45L); // còn trống 5
        when(shelfStocks.sumQuantityByShelfAndProduct(any(), any())).thenReturn(0L);
        when(alerts.findByShelfIdAndResolvedAtIsNull(any())).thenReturn(List.of());

        StockRequest.Item item = new StockRequest.Item();
        item.setSku("SKU01");
        item.setQuantity(10); // Cần 10 > 5

        StockRequest req = new StockRequest();
        req.setWarehouseId(whId);
        req.setItems(List.of(item));

        List<PlacementService.Result> results = service.suggest(req);
        PlacementService.Option opt = results.get(0).options().get(0);

        assertFalse(opt.eligible());
        assertFalse(opt.recommended());
        assertEquals(0, opt.score());
        assertTrue(opt.reasons().stream().anyMatch(r -> r.contains("Không đủ sức chứa")));
    }

    @Test
    void testScoringBonusesGroupingVelocityAndAlerts() {
        String whId = "wh-1";
        WarehouseEntity wh = createWarehouse(whId, "Kho 1", 1);
        ProductEntity prod = createProduct("p1", "SKU01", 10.0, 30.0, 30.0, 80.0);

        MonitoredShelf s1 = createShelf("s1", "Kệ 01 (Đầu dãy)", whId, 100, 20.0, 50.0);
        s1.setProductIds(Set.of("p1"));

        MonitoredShelf s2 = createShelf("s2", "Kệ 02 (Giữa dãy)", whId, 100, 20.0, 50.0);

        when(shelves.findAll()).thenReturn(List.of(s1, s2));
        when(warehouses.findAllById(any())).thenReturn(List.of(wh));
        when(products.findBySkuAndDeletedFalse("SKU01")).thenReturn(Optional.of(prod));

        // High frequency out in 30 days
        when(transactions.sumOutSince(eq("p1"), eq(StockType.OUT), any(LocalDateTime.class))).thenReturn(50L);

        // s1 already has some products on shelf
        when(shelfStocks.sumQuantity("s1")).thenReturn(20L);
        when(shelfStocks.sumQuantityByShelfAndProduct("s1", "p1")).thenReturn(15L);

        // s2 has active alerts
        when(shelfStocks.sumQuantity("s2")).thenReturn(10L);
        when(shelfStocks.sumQuantityByShelfAndProduct("s2", "p1")).thenReturn(0L);
        ProductEnvironmentAlert alert = new ProductEnvironmentAlert();
        alert.setShelfId("s2");
        when(alerts.findByShelfIdAndResolvedAtIsNull("s2")).thenReturn(List.of(alert));
        when(alerts.findByShelfIdAndResolvedAtIsNull("s1")).thenReturn(List.of());

        StockRequest.Item item = new StockRequest.Item();
        item.setSku("SKU01");
        item.setQuantity(10);

        StockRequest req = new StockRequest();
        req.setWarehouseId(whId);
        req.setItems(List.of(item));

        List<PlacementService.Result> results = service.suggest(req);
        PlacementService.Option opt1 = results.get(0).options().get(0);
        PlacementService.Option opt2 = results.get(0).options().get(1);

        assertEquals("s1", opt1.shelfId());
        assertTrue(opt1.recommended());
        // Check reasons
        assertTrue(opt1.reasons().stream().anyMatch(r -> r.contains("Kệ đã có sẵn 15 hàng này, gom cùng chỗ")));
        assertTrue(opt1.reasons().stream().anyMatch(r -> r.contains("Kệ được gán cho sản phẩm này")));
        assertTrue(opt1.reasons().stream().anyMatch(r -> r.contains("Hàng xuất nhiều")));

        assertEquals("s2", opt2.shelfId());
        assertTrue(opt2.reasons().stream().anyMatch(r -> r.contains("cảnh báo môi trường")));
        assertTrue(opt1.score() > opt2.score());
    }
}
