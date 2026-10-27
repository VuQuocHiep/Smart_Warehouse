-- Sức chứa mỗi kệ (số đơn vị hàng), mặc định 100
ALTER TABLE monitored_shelf ADD COLUMN capacity INT NULL DEFAULT 100;

-- Hàng nào đang nằm ở kệ nào, bao nhiêu
CREATE TABLE IF NOT EXISTS shelf_stock (
    id         VARCHAR(255) NOT NULL,
    shelf_id   VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity   INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shelf_stock (shelf_id, product_id),
    INDEX idx_shelf_stock_product (product_id)
) ENGINE=InnoDB;