ALTER TABLE warehouse ADD COLUMN monitoring_slot INT NULL UNIQUE;

-- Reuse existing warehouses with these names; never delete or rename existing data.
UPDATE warehouse SET monitoring_slot = 1 WHERE name = 'Kho 1' AND deleted = 0;
UPDATE warehouse SET monitoring_slot = 2 WHERE name = 'Kho 2' AND deleted = 0;
UPDATE warehouse SET monitoring_slot = 3 WHERE name = 'Kho 3' AND deleted = 0;
UPDATE warehouse SET monitoring_slot = 4 WHERE name = 'Kho 4' AND deleted = 0;

INSERT INTO warehouse (warehouse_id, name, monitoring_slot, address, description, deleted, created_at, updated_at)
SELECT UUID(), CONCAT('Kho ', n.slot), n.slot, '',
    CASE WHEN n.slot = 1 THEN 'Giám sát môi trường thực tế từ ESP32' ELSE 'Kho mô phỏng: dữ liệu môi trường giả lập' END,
    0, NOW(), NOW()
FROM (SELECT 1 AS slot UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) n
WHERE NOT EXISTS (SELECT 1 FROM warehouse w WHERE w.monitoring_slot = n.slot);

-- Provide shelves immediately; existing shelves and product assignments stay intact.
INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
SELECT UUID(), CONCAT('Kệ A', n.slot), w.warehouse_id,
    CONCAT('SHELF_', w.monitoring_slot, '_', n.slot, '_', LEFT(w.warehouse_id, 8))
FROM warehouse w CROSS JOIN (SELECT 1 AS slot UNION ALL SELECT 2) n
WHERE w.monitoring_slot IS NOT NULL AND w.deleted = 0
AND NOT EXISTS (SELECT 1 FROM monitored_shelf s WHERE s.warehouse_id = w.warehouse_id);

CREATE TABLE warehouse_environment_reading (
    id VARCHAR(255) PRIMARY KEY,
    device_code VARCHAR(255) NOT NULL,
    temperature DOUBLE NOT NULL,
    humidity DOUBLE NOT NULL,
    gas DOUBLE NULL,
    recorded_at DATETIME(6) NOT NULL,
    INDEX idx_warehouse_environment_time (recorded_at)
);

-- Keep the most recent real reading already received by a shelf in Kho 1.
INSERT INTO warehouse_environment_reading (id, device_code, temperature, humidity, recorded_at)
SELECT UUID(), s.device_code, s.temperature, s.humidity, s.recorded_at
FROM monitored_shelf s JOIN warehouse w ON w.warehouse_id = s.warehouse_id
WHERE w.monitoring_slot = 1 AND s.recorded_at IS NOT NULL
AND s.temperature IS NOT NULL AND s.humidity IS NOT NULL
ORDER BY s.recorded_at DESC LIMIT 1;
