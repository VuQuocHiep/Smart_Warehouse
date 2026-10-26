-- Generated from warehouse_seed.sql; SIMULATED catalog data only.
-- Target: existing current JPA schema, migrated through V7 (B1 + V2-V7 on a fresh DB).
-- Does NOT create/drop tables, change Flyway history, or import legacy transactions.
-- Uses existing Kho 1/2; never renames or deletes an existing warehouse.
-- Repeatable: reuse compatible active products/shelves and add missing assignments.
-- Run the ENTIRE file in MySQL Workbench. Requires CREATE ROUTINE privilege.
-- Stop the backend while seeding. Change USE below if your database has another name.
USE `iot`;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Explicit COLLATE on comparisons also supports tables with utf8mb4_0900_ai_ci.

DELIMITER $$
DROP PROCEDURE IF EXISTS seed_current_warehouse_demo_v1$$
CREATE PROCEDURE seed_current_warehouse_demo_v1()
BEGIN
    DECLARE v_wh1 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_wh2 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p1 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p2 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p3 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p4 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p5 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p6 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p7 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p8 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p9 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p10 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p11 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_p12 VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE v_shelf VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF (SELECT COUNT(*) FROM information_schema.columns
        WHERE CAST(table_schema AS BINARY) = CAST(DATABASE() AS BINARY) AND ((table_name = 'product' AND column_name = 'product_id')
        OR (table_name = 'warehouse' AND column_name = 'monitoring_slot')
        OR (table_name = 'monitored_shelf' AND column_name = 'device_code')
        OR (table_name = 'monitored_shelf_product' AND column_name = 'product_id'))) <> 4 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Current UUID schema missing. Start backend migrations before running this seed.';
    END IF;
    START TRANSACTION;
    -- SIM_WH01 / Simulation warehouse 1 -> existing Kho 1
    IF (SELECT COUNT(*) FROM warehouse WHERE monitoring_slot = 1 AND deleted = 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Active Kho 1 missing. Run application migrations first.';
    END IF;
    SELECT warehouse_id INTO v_wh1 FROM warehouse WHERE monitoring_slot = 1 AND deleted = 0;

    -- SIM_WH02 / Simulation warehouse 2 -> existing Kho 2
    IF (SELECT COUNT(*) FROM warehouse WHERE monitoring_slot = 2 AND deleted = 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Active Kho 2 missing. Run application migrations first.';
    END IF;
    SELECT warehouse_id INTO v_wh2 FROM warehouse WHERE monitoring_slot = 2 AND deleted = 0;

    -- SIM_SKU001 / Demo product 01 / source profile: stable
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU001. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203031 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.125) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU001 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203031 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x737461626c65 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.125, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU002 / Demo product 02 / source profile: stable
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU002. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203032 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.15) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU002 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203032 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x737461626c65 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.15, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p2 FROM product WHERE sku = CONVERT(0x53494d5f534b55303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU003 / Demo product 03 / source profile: weekly
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU003. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203033 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.175) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU003 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203033 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x7765656b6c79 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.175, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p3 FROM product WHERE sku = CONVERT(0x53494d5f534b55303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU004 / Demo product 04 / source profile: weekly
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU004. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203034 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.2) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU004 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203034 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x7765656b6c79 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.2, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p4 FROM product WHERE sku = CONVERT(0x53494d5f534b55303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU005 / Demo product 05 / source profile: growth
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU005. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203035 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.225) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU005 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203035 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x67726f777468 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.225, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p5 FROM product WHERE sku = CONVERT(0x53494d5f534b55303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU006 / Demo product 06 / source profile: growth
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU006. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203036 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.25) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU006 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203036 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x67726f777468 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.25, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p6 FROM product WHERE sku = CONVERT(0x53494d5f534b55303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU007 / Demo product 07 / source profile: decline
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU007. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203037 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.275) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU007 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203037 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x6465636c696e65 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.275, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p7 FROM product WHERE sku = CONVERT(0x53494d5f534b55303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU008 / Demo product 08 / source profile: decline
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU008. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203038 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.3) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU008 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203038 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x6465636c696e65 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.3, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p8 FROM product WHERE sku = CONVERT(0x53494d5f534b55303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU009 / Demo product 09 / source profile: seasonal
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU009. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203039 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.325) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU009 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203039 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x736561736f6e616c USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.325, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p9 FROM product WHERE sku = CONVERT(0x53494d5f534b55303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU010 / Demo product 10 / source profile: seasonal
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU010. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203130 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.35) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU010 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203130 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x736561736f6e616c USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.35, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p10 FROM product WHERE sku = CONVERT(0x53494d5f534b55303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU011 / Demo product 11 / source profile: intermittent
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU011. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203131 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.375) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU011 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203131 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x696e7465726d697474656e74 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.375, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p11 FROM product WHERE sku = CONVERT(0x53494d5f534b55303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_SKU012 / Demo product 12 / source profile: intermittent
    IF (SELECT COUNT(*) FROM product WHERE sku = CONVERT(0x53494d5f534b55303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0) > 1
       OR EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product SIM_SKU012. Resolve manually before seeding.';
    END IF;
    IF EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0
        AND (NOT (name <=> CONVERT(0x44656d6f2070726f64756374203132 USING utf8mb4) COLLATE utf8mb4_unicode_ci) OR NOT (category <=> CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        OR ABS(unit_weight - 0.4) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing SIM_SKU012 differs from seed. No data overwritten.';
    END IF;
    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)
    SELECT UUID(), CONVERT(0x53494d5f534b55303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x44656d6f2070726f64756374203132 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d554c4154494f4e USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x696e7465726d697474656e74 USING utf8mb4) COLLATE utf8mb4_unicode_ci, 0.4, 'ITEM', 0, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x323032342d31302d31392030303a30303a3030 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = CONVERT(0x53494d5f534b55303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0);
    SELECT product_id INTO v_p12 FROM product WHERE sku = CONVERT(0x53494d5f534b55303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci AND deleted = 0;

    -- SIM_WH01 / SIM_SHELF_001
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_001. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p1
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p1 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_001
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_001. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p1
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p1 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_002
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_002. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p2
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p2 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_002
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_002. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p2
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p2 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_003
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_003. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p3
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p3 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_003
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_003. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p3
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p3 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_004
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_004. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p4
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p4 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_004
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_004. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p4
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p4 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_005
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_005. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p5
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p5 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_005
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_005. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p5
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p5 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_006
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_006. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p6
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p6 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_006
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_006. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p6
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p6 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_007
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_007. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p7
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p7 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_007
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_007. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p7
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p7 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_008
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_008. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p8
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p8 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_008
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_008. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p8
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p8 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_009
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_009. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p9
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p9 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_009
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_009. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p9
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p9 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_010
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_010. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p10
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p10 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_010
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_010. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p10
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p10 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_011
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_011. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p11
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p11 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_011
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_011. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p11
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p11 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH01 / SIM_SHELF_012
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh1 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH01_SIM_SHELF_012. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh1, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830315f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p12
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p12 COLLATE utf8mb4_unicode_ci);

    -- SIM_WH02 / SIM_SHELF_012
    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci
        AND (warehouse_id <> v_wh2 COLLATE utf8mb4_unicode_ci OR name <> CONVERT(0x53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf SIM_WH02_SIM_SHELF_012. No shelf moved or renamed.';
    END IF;
    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)
    SELECT UUID(), CONVERT(0x53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci, v_wh2, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci);
    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = CONVERT(0x53494d5f574830325f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci;
    INSERT INTO monitored_shelf_product (shelf_id, product_id)
    SELECT v_shelf, v_p12
    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE utf8mb4_unicode_ci AND product_id = v_p12 COLLATE utf8mb4_unicode_ci);

    COMMIT;
END$$
CALL seed_current_warehouse_demo_v1()$$
DROP PROCEDURE seed_current_warehouse_demo_v1$$
DELIMITER ;

-- Expected: 12 active seed products.
SELECT COUNT(*) AS seeded_products FROM product WHERE deleted = 0 AND sku IN (
    CONVERT(0x53494d5f534b55303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci);

-- Expected: 12 shelves / 12 product assignments per warehouse (24 total).
SELECT w.name, w.monitoring_slot, COUNT(DISTINCT s.id) AS seeded_shelves,
       COUNT(sp.product_id) AS seed_product_assignments
FROM warehouse w JOIN monitored_shelf s ON s.warehouse_id COLLATE utf8mb4_unicode_ci = w.warehouse_id
JOIN monitored_shelf_product sp ON sp.shelf_id COLLATE utf8mb4_unicode_ci = s.id
JOIN product p ON p.product_id COLLATE utf8mb4_unicode_ci = sp.product_id
WHERE w.deleted = 0 AND p.deleted = 0 AND s.device_code IN (
    CONVERT(0x53494d5f574830315f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830315f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f574830325f53494d5f5348454c465f303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci)
AND p.sku IN (CONVERT(0x53494d5f534b55303031 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303032 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303033 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303034 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303035 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303036 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303037 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303038 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303039 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303130 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303131 USING utf8mb4) COLLATE utf8mb4_unicode_ci, CONVERT(0x53494d5f534b55303132 USING utf8mb4) COLLATE utf8mb4_unicode_ci)
GROUP BY w.warehouse_id, w.name, w.monitoring_slot ORDER BY w.monitoring_slot;

-- Legacy history is retained in postman/warehouse-seed/warehouse_seed.data.json.
-- There are no inventory_transaction, inventory, RFID or load-cell tables in the current JPA schema.
-- Demand demo training will generate fresh simulated history; it does not train on the source SQL transactions.
