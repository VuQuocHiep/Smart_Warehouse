package com.example.iot.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;

/** Called inside the inventory transaction while holding the product row lock. */
@Service
@RequiredArgsConstructor
public class WarehouseStockService {
    private final JdbcTemplate jdbc;

    @Transactional
    public int change(String warehouseId, String productId, int delta) {
        jdbc.update("INSERT INTO warehouse_inventory VALUES (?,?,0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE product_id=product_id", warehouseId, productId);
        int count = jdbc.update("UPDATE warehouse_inventory SET quantity=quantity+?,updated_at=UTC_TIMESTAMP(6) WHERE warehouse_id=? AND product_id=? AND quantity+? >= 0",
                delta, warehouseId, productId, delta);
        if (count != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đủ tồn trong kho đã chọn.");
        // Fixtures remain explicitly bounded; a new real ledger is complete from tomorrow.
        if (jdbc.queryForObject("SELECT COUNT(*) FROM demand_series WHERE warehouse_id=? AND product_id=?", Long.class, warehouseId, productId) == 0) {
            jdbc.update("INSERT INTO demand_series VALUES (?,?,'REAL',DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)+INTERVAL 1 DAY,NULL,NULL)", warehouseId, productId);
        }
        return jdbc.queryForObject("SELECT quantity FROM warehouse_inventory WHERE warehouse_id=? AND product_id=?", Integer.class, warehouseId, productId);
    }

    public String source(String warehouseId, String productId) {
        return jdbc.queryForObject("SELECT data_source FROM demand_series WHERE warehouse_id=? AND product_id=?", String.class, warehouseId, productId);
    }

    public List<Map<String, Object>> stocks() {
        return jdbc.queryForList("SELECT wi.warehouse_id AS warehouseId,w.name AS warehouseName,p.sku,p.name,wi.quantity AS stock FROM warehouse_inventory wi JOIN warehouse w ON w.warehouse_id=wi.warehouse_id JOIN product p ON p.product_id=wi.product_id WHERE w.deleted=0 AND p.deleted=0 ORDER BY w.name,p.sku");
    }
}
