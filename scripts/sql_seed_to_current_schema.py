"""Generate a Workbench seed for the existing UUID schema, without executing SQL."""
import argparse
from pathlib import Path

from sql_seed_to_postman import parse_seed

COLLATION = 'utf8mb4_unicode_ci'


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, (int, float)):
        return str(value)
    # Hex text literals avoid dependence on NO_BACKSLASH_ESCAPES or file encoding.
    return "CONVERT(0x" + value.encode('utf-8').hex() + f" USING utf8mb4) COLLATE {COLLATION}"


def build_sql(tables):
    warehouses, products, shelves = (tables[t] for t in ('warehouse', 'product', 'shelf'))
    if len(warehouses) != 2 or len(products) != 12 or len(shelves) != 24:
        raise ValueError('Expected the 2-warehouse / 12-product / 24-shelf demo seed')
    warehouse_vars = {w['_seedRef']: f'v_wh{i}' for i, w in enumerate(warehouses, 1)}
    product_vars = {p['_seedRef']: f'v_p{i}' for i, p in enumerate(products, 1)}
    declarations = [f'    DECLARE {v} VARCHAR(255) CHARACTER SET utf8mb4 COLLATE {COLLATION};'
                    for v in [*warehouse_vars.values(), *product_vars.values(), 'v_shelf']]
    sql = [
        '-- Generated from warehouse_seed.sql; SIMULATED catalog data only.',
        '-- Target: existing current JPA schema, migrated through V7 (B1 + V2-V7 on a fresh DB).',
        '-- Does NOT create/drop tables, change Flyway history, or import legacy transactions.',
        '-- Uses existing Kho 1/2; never renames or deletes an existing warehouse.',
        '-- Repeatable: reuse compatible active products/shelves and add missing assignments.',
        '-- Run the ENTIRE file in MySQL Workbench. Requires CREATE ROUTINE privilege.',
        '-- Stop the backend while seeding. Change USE below if your database has another name.',
        'USE `iot`;',
        f'SET NAMES utf8mb4 COLLATE {COLLATION};',
        '-- Explicit COLLATE on comparisons also supports tables with utf8mb4_0900_ai_ci.',
        '',
        'DELIMITER $$',
        'DROP PROCEDURE IF EXISTS seed_current_warehouse_demo_v1$$',
        'CREATE PROCEDURE seed_current_warehouse_demo_v1()',
        'BEGIN',
        *declarations,
        '    DECLARE EXIT HANDLER FOR SQLEXCEPTION',
        '    BEGIN',
        '        ROLLBACK;',
        '        RESIGNAL;',
        '    END;',
        '',
        "    IF (SELECT COUNT(*) FROM information_schema.columns",
        "        WHERE CAST(table_schema AS BINARY) = CAST(DATABASE() AS BINARY) AND ((table_name = 'product' AND column_name = 'product_id')",
        "        OR (table_name = 'warehouse' AND column_name = 'monitoring_slot')",
        "        OR (table_name = 'monitored_shelf' AND column_name = 'device_code')",
        "        OR (table_name = 'monitored_shelf_product' AND column_name = 'product_id'))) <> 4 THEN",
        "        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Current UUID schema missing. Start backend migrations before running this seed.';",
        '    END IF;',
        '    START TRANSACTION;',
    ]
    for slot, w in enumerate(warehouses, 1):
        var = warehouse_vars[w['_seedRef']]
        sql += [f"    -- {w['code']} / {w['name']} -> existing Kho {slot}",
                f'    IF (SELECT COUNT(*) FROM warehouse WHERE monitoring_slot = {slot} AND deleted = 0) <> 1 THEN',
                f"        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Active Kho {slot} missing. Run application migrations first.';",
                '    END IF;',
                f'    SELECT warehouse_id INTO {var} FROM warehouse WHERE monitoring_slot = {slot} AND deleted = 0;', '']
    for p in products:
        sku = literal(p['sku'])
        var = product_vars[p['_seedRef']]
        sql += [f"    -- {p['sku']} / {p['name']} / source profile: {p['description']}",
                f'    IF (SELECT COUNT(*) FROM product WHERE sku = {sku} AND deleted = 0) > 1',
                f'       OR EXISTS (SELECT 1 FROM product WHERE sku = {sku} AND deleted = 1) THEN',
                f"        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ambiguous or deleted product {p['sku']}. Resolve manually before seeding.';",
                '    END IF;',
                f'    IF EXISTS (SELECT 1 FROM product WHERE sku = {sku} AND deleted = 0',
                f"        AND (NOT (name <=> {literal(p['name'])}) OR NOT (category <=> {literal(p['category'])})",
                f"        OR ABS(unit_weight - {literal(p['unit_weight'])}) > 0.000000001 OR NOT (unit <=> 'ITEM'))) THEN",
                f"        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing {p['sku']} differs from seed. No data overwritten.';",
                '    END IF;',
                '    INSERT INTO product (product_id, sku, name, category, description, unit_weight, unit, deleted, created_at, updated_at)',
                '    SELECT UUID(), ' + ', '.join(literal(p[k]) for k in ('sku', 'name', 'category', 'description', 'unit_weight'))
                + ", 'ITEM', 0, " + literal(p['created_at']) + ', ' + literal(p['created_at']),
                f'    WHERE NOT EXISTS (SELECT 1 FROM product WHERE sku = {sku} AND deleted = 0);',
                f'    SELECT product_id INTO {var} FROM product WHERE sku = {sku} AND deleted = 0;', '']
    warehouse_by_ref = {w['_seedRef']: w for w in warehouses}
    codes = []
    for s in shelves:
        w = warehouse_by_ref[s['warehouse_id']]
        whvar = warehouse_vars[s['warehouse_id']]
        pvar = product_vars[s['product_id']]
        code = f'{w["code"]}_{s["name"]}'
        codes.append(code)
        sql += [f"    -- {w['code']} / {s['name']}",
                f'    IF EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = {literal(code)}',
                f"        AND (warehouse_id <> {whvar} COLLATE {COLLATION} OR name <> {literal(s['name'])})) THEN",
                f"        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting shelf {code}. No shelf moved or renamed.';",
                '    END IF;',
                '    INSERT INTO monitored_shelf (id, name, warehouse_id, device_code)',
                f"    SELECT UUID(), {literal(s['name'])}, {whvar}, {literal(code)}",
                f'    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE device_code = {literal(code)});',
                f'    SELECT id INTO v_shelf FROM monitored_shelf WHERE device_code = {literal(code)};',
                '    INSERT INTO monitored_shelf_product (shelf_id, product_id)',
                f'    SELECT v_shelf, {pvar}',
                f'    WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product WHERE shelf_id = v_shelf COLLATE {COLLATION} AND product_id = {pvar} COLLATE {COLLATION});', '']
    sql += [
        '    COMMIT;',
        'END$$',
        'CALL seed_current_warehouse_demo_v1()$$',
        'DROP PROCEDURE seed_current_warehouse_demo_v1$$',
        'DELIMITER ;',
        '',
        '-- Expected: 12 active seed products.',
        'SELECT COUNT(*) AS seeded_products FROM product WHERE deleted = 0 AND sku IN (',
        '    ' + ', '.join(literal(p['sku']) for p in products) + ');',
        '',
        '-- Expected: 12 shelves / 12 product assignments per warehouse (24 total).',
        'SELECT w.name, w.monitoring_slot, COUNT(DISTINCT s.id) AS seeded_shelves,',
        '       COUNT(sp.product_id) AS seed_product_assignments',
        f'FROM warehouse w JOIN monitored_shelf s ON s.warehouse_id COLLATE {COLLATION} = w.warehouse_id',
        f'JOIN monitored_shelf_product sp ON sp.shelf_id COLLATE {COLLATION} = s.id',
        f'JOIN product p ON p.product_id COLLATE {COLLATION} = sp.product_id',
        'WHERE w.deleted = 0 AND p.deleted = 0 AND s.device_code IN (',
        '    ' + ', '.join(literal(code) for code in codes) + ')',
        'AND p.sku IN (' + ', '.join(literal(p['sku']) for p in products) + ')',
        'GROUP BY w.warehouse_id, w.name, w.monitoring_slot ORDER BY w.monitoring_slot;',
        '',
        '-- Legacy history is retained in postman/warehouse-seed/warehouse_seed.data.json.',
        '-- There are no inventory_transaction, inventory, RFID or load-cell tables in the current JPA schema.',
        '-- Demand demo training will generate fresh simulated history; it does not train on the source SQL transactions.',
    ]
    return '\n'.join(sql) + '\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--output', type=Path, default=Path('database/seeds/warehouse_seed_current.sql'))
    args = parser.parse_args()
    tables, _ = parse_seed(args.source.read_text(encoding='utf-8-sig'))
    output = build_sql(tables)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(output, encoding='utf-8')
    print(f'Created {args.output}: 12 products, 24 shelves, 24 assignments; existing Kho 1/2.')


if __name__ == '__main__':
    main()
