-- Tồn kho hiện tại theo sản phẩm
CREATE TABLE IF NOT EXISTS inventory (
    inventory_id VARCHAR(255) NOT NULL,
    product_id   VARCHAR(255) NOT NULL,
    quantity     INT NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (inventory_id),
    UNIQUE KEY uk_inventory_product (product_id)
) ENGINE=InnoDB;

-- Lịch sử nhập / xuất kho
CREATE TABLE IF NOT EXISTS stock_transaction (
    transaction_id VARCHAR(255) NOT NULL,
    product_id     VARCHAR(255) NOT NULL,
    type           ENUM('IN','OUT') NOT NULL,
    quantity       INT NOT NULL,
    stock_after    INT NOT NULL,
    created_by     VARCHAR(255),
    note           VARCHAR(255),
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (transaction_id),
    INDEX idx_stock_transaction_time (created_at),
    INDEX idx_stock_transaction_product (product_id)
) ENGINE=InnoDB;