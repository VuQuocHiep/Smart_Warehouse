import json
from pathlib import Path
import re
import unittest

from sql_seed_to_current_schema import COLLATION, build_sql, literal


ROOT = Path(__file__).resolve().parents[1]


class CurrentSchemaSeedTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tables = json.loads((ROOT / 'postman/warehouse-seed/warehouse_seed.data.json')
                                .read_text(encoding='utf-8'))['tables']
        cls.sql = build_sql(cls.tables)

    def test_generated_file_matches_source_fixture(self):
        actual = (ROOT / 'database/seeds/warehouse_seed_current.sql').read_text(encoding='utf-8')
        self.assertEqual(actual, self.sql)

    def test_literals_roundtrip_unicode_and_sql_metacharacters(self):
        text = "Kho thử nghiệm 'x'; \\ đây"
        encoded = literal(text)
        hex_string = re.fullmatch(r'CONVERT\(0x([0-9a-f]+) USING utf8mb4\) COLLATE ' + COLLATION, encoded)[1]
        self.assertEqual(bytes.fromhex(hex_string).decode('utf-8'), text)
        self.assertEqual(literal(None), 'NULL')
        self.assertEqual(literal(0.125), '0.125')

    def test_only_current_schema_tables_are_written(self):
        counts = {table: len(re.findall(r'INSERT INTO ' + table + r'\s*\(', self.sql))
                  for table in ('product', 'monitored_shelf', 'monitored_shelf_product')}
        self.assertEqual(counts, {'product': 12, 'monitored_shelf': 24, 'monitored_shelf_product': 24})
        self.assertNotRegex(self.sql, r'(?im)^\s*(?:DROP|TRUNCATE|ALTER|CREATE)\s+(?:TABLE|DATABASE)\b')
        self.assertNotRegex(self.sql, r'(?im)^\s*(?:DELETE|UPDATE)\b')
        self.assertNotRegex(self.sql, r'INSERT INTO (?:inventory|inventory_transaction|rfid_tag|iot_device)\b')

    def test_product_and_shelf_identities_preserved(self):
        for p in self.tables['product']:
            for field in ('sku', 'name', 'description', 'category', 'unit_weight', 'created_at'):
                self.assertIn(literal(p[field]), self.sql)
        warehouses = {w['_seedRef']: w for w in self.tables['warehouse']}
        for s in self.tables['shelf']:
            device_code = warehouses[s['warehouse_id']]['code'] + '_' + s['name']
            self.assertIn(literal(device_code), self.sql)
        self.assertIn('monitoring_slot = 1 AND deleted = 0', self.sql)
        self.assertIn('monitoring_slot = 2 AND deleted = 0', self.sql)
        declared = set(re.findall(r'DECLARE (v_\w+)', self.sql))
        referenced = set(re.findall(r'\bv_\w+\b', self.sql))
        self.assertEqual(referenced, declared)

    def test_failure_rolls_back_and_existing_records_are_guarded(self):
        self.assertIn('DECLARE EXIT HANDLER FOR SQLEXCEPTION', self.sql)
        self.assertIn('ROLLBACK;', self.sql)
        self.assertIn('RESIGNAL;', self.sql)
        self.assertEqual(self.sql.count('START TRANSACTION;'), 1)
        self.assertEqual(self.sql.count('COMMIT;'), 1)
        self.assertEqual(self.sql.count('WHERE NOT EXISTS (SELECT 1 FROM product'), 12)
        self.assertEqual(self.sql.count('WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf WHERE'), 24)
        self.assertEqual(self.sql.count('WHERE NOT EXISTS (SELECT 1 FROM monitored_shelf_product'), 24)

    def test_comparisons_do_not_inherit_incompatible_collations(self):
        declarations = re.findall(r'DECLARE v_\w+ VARCHAR\(255\)([^;]*);', self.sql)
        self.assertTrue(declarations)
        for declaration in declarations:
            self.assertIn('CHARACTER SET utf8mb4 COLLATE ' + COLLATION, declaration)
        comparisons = re.findall(r'(?:=|<>)\s+(v_\w+)(\s+COLLATE\s+\w+)?', self.sql)
        self.assertEqual(len(comparisons), 72)  # 24 shelf checks + 48 assignment checks
        for _, tail in comparisons:
            self.assertTrue(tail.startswith(' COLLATE ' + COLLATION), tail)
        for left in ('s.warehouse_id', 'sp.shelf_id', 'p.product_id'):
            self.assertIn(left + ' COLLATE ' + COLLATION + ' = ', self.sql)
        self.assertIn('SET NAMES utf8mb4 COLLATE ' + COLLATION, self.sql)

    def test_metadata_comparison_avoids_internal_utf8mb3_collation(self):
        # INFORMATION_SCHEMA can expose an explicit utf8mb3_tolower_ci collation.
        # Applying an explicit utf8mb4 COLLATE to the other side still fails.
        metadata_check = self.sql.split('FROM information_schema.columns', 1)[1].split('THEN', 1)[0]
        self.assertIn('CAST(table_schema AS BINARY) = CAST(DATABASE() AS BINARY)', metadata_check)
        self.assertNotIn('COLLATE', metadata_check)


if __name__ == '__main__':
    unittest.main()
