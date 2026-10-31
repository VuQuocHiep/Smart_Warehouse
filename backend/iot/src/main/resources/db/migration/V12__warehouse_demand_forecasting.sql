-- Legacy transactions remain unallocated: their historical warehouse is unknown.
ALTER TABLE stock_transaction
    ADD COLUMN warehouse_id VARCHAR(255) NULL,
    ADD COLUMN warehouse_stock_after INT NULL,
    ADD COLUMN data_source VARCHAR(16) NOT NULL DEFAULT 'REAL',
    ADD COLUMN seed_batch_id VARCHAR(64) NULL,
    ADD INDEX idx_stock_warehouse_daily (warehouse_id, product_id, type, created_at);

CREATE TABLE warehouse_inventory (
    warehouse_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (warehouse_id, product_id),
    CHECK (quantity >= 0)
) ENGINE=InnoDB;

-- Only physically allocated shelf stock can be backfilled with confidence.
INSERT INTO warehouse_inventory (warehouse_id, product_id, quantity, updated_at)
SELECT s.warehouse_id, ss.product_id, SUM(ss.quantity), UTC_TIMESTAMP(6)
FROM shelf_stock ss JOIN monitored_shelf s ON s.id = ss.shelf_id
GROUP BY s.warehouse_id, ss.product_id;

CREATE TABLE demand_policy (
    warehouse_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    safety_stock INT NOT NULL DEFAULT 0,
    PRIMARY KEY (warehouse_id, product_id),
    CHECK (safety_stock >= 0)
) ENGINE=InnoDB;

CREATE TABLE demand_seed_batch (
    batch_id VARCHAR(64) PRIMARY KEY,
    data_cutoff DATE NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

-- complete_through is bounded for fixtures; NULL means a live ledger since history_start.
CREATE TABLE demand_series (
    warehouse_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    data_source VARCHAR(16) NOT NULL,
    history_start DATE NOT NULL,
    complete_through DATE NULL,
    seed_batch_id VARCHAR(64) NULL,
    PRIMARY KEY (warehouse_id, product_id, data_source)
) ENGINE=InnoDB;

CREATE TABLE demand_job (
    job_id VARCHAR(36) PRIMARY KEY,
    request_key VARCHAR(255) NOT NULL UNIQUE,
    action VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    data_cutoff DATE NOT NULL,
    dataset_id VARCHAR(128) NOT NULL,
    model_version VARCHAR(128) NULL,
    message VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_demand_job_status (status, updated_at)
) ENGINE=InnoDB;

CREATE TABLE demand_scheduler_lock (
    id INT PRIMARY KEY,
    owner VARCHAR(36) NULL,
    lease_until DATETIME(6) NOT NULL
) ENGINE=InnoDB;
INSERT INTO demand_scheduler_lock VALUES (1, NULL, '2000-01-01');
