ALTER TABLE warehouse ADD COLUMN monitoring_device_code VARCHAR(255) NULL UNIQUE;

UPDATE warehouse w
SET monitoring_device_code = COALESCE(
    (SELECT r.device_code FROM warehouse_environment_reading r
     JOIN monitored_shelf s ON s.device_code = r.device_code
     WHERE s.warehouse_id = w.warehouse_id
     ORDER BY r.recorded_at DESC, r.id DESC LIMIT 1),
    'ESP_ENV_01'
)
WHERE w.monitoring_slot = 1 AND w.deleted = 0;
