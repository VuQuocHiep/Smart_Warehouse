-- Presentation/catalog cleanup. Original data_source and seed lineage stay intact.
-- Preserve old business codes so existing barcodes continue resolving to the same product UUID.
CREATE TABLE product_sku_alias (
    old_sku VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    new_sku VARCHAR(255) NOT NULL UNIQUE,
    INDEX idx_product_sku_alias_product (product_id)
) ENGINE=InnoDB;

START TRANSACTION;
SET @catalog_sku_base = (SELECT COALESCE(MAX(CAST(SUBSTRING(sku,3) AS UNSIGNED)),0)
                        FROM product WHERE sku REGEXP '^HH[0-9]+$');
INSERT INTO product_sku_alias (old_sku,product_id,new_sku)
SELECT sku,product_id,CONCAT('HH',LPAD(CAST(@catalog_sku_base + n AS UNSIGNED),
       GREATEST(3,CHAR_LENGTH(CAST(@catalog_sku_base + n AS UNSIGNED))),'0'))
FROM (SELECT sku,product_id,ROW_NUMBER() OVER (ORDER BY product_id) AS n
      FROM product WHERE sku REGEXP '^(DEMAND_V13_|SIM_SKU)[0-9]{3}$') catalog;

UPDATE product p JOIN product_sku_alias a ON a.product_id=p.product_id SET p.sku=a.new_sku;
UPDATE product SET
    name=CASE WHEN name LIKE 'Demo nhu cầu %' THEN CONCAT('Hàng hóa ',RIGHT(product_id,2)) ELSE name END,
    category=CASE WHEN category='DEMAND_DEMO' THEN 'Hàng hóa' ELSE category END,
    description=CASE WHEN description='SIMULATED - Flyway V13' THEN 'Quản lý nhập, xuất và tồn theo kho.' ELSE description END
WHERE product_id REGEXP '^d13-p[0-9]{2}$';

UPDATE product p JOIN product_sku_alias a ON a.product_id=p.product_id SET
    p.name=CASE WHEN p.name REGEXP '^Demo product [0-9]+$' THEN CONCAT('Hàng hóa ',RIGHT(a.old_sku,3)) ELSE p.name END,
    p.category=CASE WHEN p.category IN ('SIMULATION','DEMO') THEN 'Hàng hóa' ELSE p.category END,
    p.description=CASE WHEN p.description REGEXP '(SIMULAT|DEMO|seed|profile)' THEN 'Quản lý nhập, xuất và tồn theo kho.' ELSE p.description END
WHERE a.old_sku REGEXP '^SIM_SKU[0-9]{3}$';

UPDATE monitored_shelf SET name=CONCAT('Kệ hàng ',RIGHT(id,2))
WHERE id REGEXP '^d13-s[12]-[0-9]{2}$' AND name LIKE 'Kệ demo V13 %';
UPDATE monitored_shelf SET name=CONCAT('Kệ hàng ',RIGHT(name,3))
WHERE name REGEXP '^SIM_SHELF_[0-9]{3}$';
UPDATE stock_transaction SET note=CASE WHEN type='IN' THEN 'Nhập kho' ELSE 'Xuất kho' END
WHERE seed_batch_id='DEMAND_V13' AND note='SIMULATED fixture';
UPDATE stock_transaction SET created_by='Hệ thống'
WHERE seed_batch_id='DEMAND_V13' AND created_by='DEMAND_V13';
COMMIT;
