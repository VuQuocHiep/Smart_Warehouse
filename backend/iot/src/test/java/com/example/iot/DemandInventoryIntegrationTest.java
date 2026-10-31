package com.example.iot;

import com.example.iot.dto.request.StockRequest;
import com.example.iot.service.InventoryService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only when explicitly pointed at the disposable integration database. */
@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties={
    "spring.datasource.url=${MYSQL_TEST_URL}","spring.datasource.username=${MYSQL_TEST_USER}",
    "spring.datasource.password=${MYSQL_TEST_PASSWORD}","demand.service.enabled=false"})
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches="jdbc:mysql:.*")
class DemandInventoryIntegrationTest {
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.example.iot.repository.ProductRepository products;
    String warehouse(int slot) { return jdbc.queryForObject("SELECT warehouse_id FROM warehouse WHERE monitoring_slot=?",String.class,slot); }
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("integration",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    @BeforeEach void setup() { authenticate(); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    StockRequest request(String wh,String sku,String shelf) {
        var item=new StockRequest.Item();item.setSku(sku);item.setQuantity(1);item.setShelfId(shelf);
        var request=new StockRequest();request.setWarehouseId(wh);request.setItems(List.of(item));return request;
    }
    int stock(String wh,String pid) { return jdbc.queryForObject("SELECT quantity FROM warehouse_inventory WHERE warehouse_id=? AND product_id=?",Integer.class,wh,pid); }
    @Test void oldBarcodeResolvesToRenamedProductAndRemainsReserved() {
        var product=products.findBySkuAndDeletedFalse("DEMAND_V13_001").orElseThrow();
        assertEquals("d13-p01",product.getProductId());
        assertTrue(product.getSku().matches("HH[0-9]+"));
        assertTrue(products.existsBySkuAndDeletedFalse("DEMAND_V13_001"));
    }
    @Test void importedProductUsesTheSameRealSourceForNewStockTransactions() {
        String wh=warehouse(1),sku="DEMAND_V13_004",pid="d13-p04";
        int before=stock(wh,pid);
        int transactions=jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE warehouse_id=? AND product_id=?",Integer.class,wh,pid);
        inventory.stockIn(request(wh,sku,null));
        inventory.stockOut(request(wh,sku,null));
        assertEquals(before,stock(wh,pid));
        assertEquals(transactions+2,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE warehouse_id=? AND product_id=? AND data_source='REAL'",Integer.class,wh,pid));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM demand_series WHERE warehouse_id=? AND product_id=?",Integer.class,wh,pid));
    }
    @Test void wrongShelfRollsBackWarehouseAndGlobalChanges() {
        String wh=warehouse(1);int before=stock(wh,"d13-p01");
        assertThrows(RuntimeException.class,() -> inventory.stockIn(request(wh,"DEMAND_V13_001","d13-s2-01")));
        assertEquals(before,stock(wh,"d13-p01"));
    }
    @Test void simultaneousExportsCannotConsumeOneItemTwiceOrBorrowFromAnotherWarehouse() throws Exception {
        String wh=warehouse(1),other=warehouse(2);
        inventory.stockIn(request(wh,"DEMAND_V13_001",null));
        assertThrows(RuntimeException.class,() -> inventory.stockOut(request(other,"DEMAND_V13_001",null)));
        var pool=Executors.newFixedThreadPool(2);
        var gate=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++) futures.add(pool.submit(() -> {
                authenticate();gate.await();
                try { inventory.stockOut(request(wh,"DEMAND_V13_001",null));return true; }
                catch(RuntimeException ex) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            gate.countDown();int success=0;
            for(var future:futures) if(future.get(20,TimeUnit.SECONDS)) success++;
            assertEquals(1,success);assertEquals(0,stock(wh,"d13-p01"));assertEquals(0,stock(other,"d13-p01"));
        } finally { pool.shutdownNow(); }
    }
    @Test void differentProductsCannotOverfillOneShelfConcurrently() throws Exception {
        String wh=warehouse(1),shelf="d13-s1-01";
        jdbc.update("UPDATE monitored_shelf SET capacity=1 WHERE id=?",shelf);
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<String>>();
            for(String sku:List.of("DEMAND_V13_001","DEMAND_V13_004")) futures.add(pool.submit(() -> {
                authenticate();gate.await();
                try { inventory.stockIn(request(wh,sku,shelf));return sku; }
                catch(RuntimeException ex) { return ""; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            gate.countDown();var winners=new ArrayList<String>();
            for(var future:futures) { String sku=future.get(20,TimeUnit.SECONDS);if(!sku.isEmpty()) winners.add(sku); }
            assertEquals(1,winners.size());
            assertEquals(1,jdbc.queryForObject("SELECT SUM(quantity) FROM shelf_stock WHERE shelf_id=?",Integer.class,shelf));
            inventory.stockOut(request(wh,winners.get(0),null));
        } finally { pool.shutdownNow();jdbc.update("UPDATE monitored_shelf SET capacity=2000 WHERE id=?",shelf); }
    }
}
