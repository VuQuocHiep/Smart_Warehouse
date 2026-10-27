CREATE TABLE scale_settings (
    id INT PRIMARY KEY,
    absolute_grams DOUBLE NOT NULL,
    percent DOUBLE NOT NULL
) ENGINE=InnoDB;
INSERT INTO scale_settings VALUES (1, 5, 2);

CREATE TABLE scale_ticket (
    id VARCHAR(255) PRIMARY KEY,
    sku VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    stock_type VARCHAR(255) NOT NULL,
    captured_by VARCHAR(255) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    weight_grams DOUBLE NOT NULL,
    gross_grams DOUBLE NOT NULL,
    expected_grams DOUBLE NOT NULL,
    tolerance_grams DOUBLE NOT NULL,
    consumed BIT NOT NULL
) ENGINE=InnoDB;
ALTER TABLE stock_transaction ADD COLUMN scale_ticket_id VARCHAR(255) NULL,
    ADD CONSTRAINT uk_stock_scale_ticket UNIQUE (scale_ticket_id),
    ADD CONSTRAINT fk_stock_scale_ticket FOREIGN KEY (scale_ticket_id) REFERENCES scale_ticket(id);
