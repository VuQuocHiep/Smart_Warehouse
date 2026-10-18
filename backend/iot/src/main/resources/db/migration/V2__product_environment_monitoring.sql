ALTER TABLE product
    ADD COLUMN min_temperature DOUBLE NULL,
    ADD COLUMN max_temperature DOUBLE NULL,
    ADD COLUMN min_humidity DOUBLE NULL,
    ADD COLUMN max_humidity DOUBLE NULL,
    ADD CONSTRAINT chk_product_temperature_range CHECK (min_temperature IS NULL OR max_temperature IS NULL OR min_temperature <= max_temperature),
    ADD CONSTRAINT chk_product_humidity_range CHECK ((min_humidity IS NULL OR min_humidity BETWEEN 0 AND 100) AND (max_humidity IS NULL OR max_humidity BETWEEN 0 AND 100) AND (min_humidity IS NULL OR max_humidity IS NULL OR min_humidity <= max_humidity));

-- Separate monitoring shelves support multiple SKUs; legacy load-cell shelves remain single-SKU.
CREATE TABLE monitored_shelf (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    warehouse_id VARCHAR(255) NOT NULL,
    device_code VARCHAR(255) NOT NULL UNIQUE,
    temperature DOUBLE NULL,
    humidity DOUBLE NULL,
    recorded_at DATETIME(6) NULL
);
CREATE TABLE monitored_shelf_product (
    shelf_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (shelf_id, product_id),
    FOREIGN KEY (shelf_id) REFERENCES monitored_shelf(id)
);
CREATE TABLE product_environment_alert (
    id VARCHAR(255) PRIMARY KEY,
    shelf_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    INDEX idx_product_alert_active (shelf_id, resolved_at),
    INDEX idx_product_alert_time (created_at)
);
CREATE TABLE environment_alert_receipt (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    alert_id VARCHAR(255) NOT NULL,
    UNIQUE (username, alert_id)
);
