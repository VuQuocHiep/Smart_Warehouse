import json
from pathlib import Path
import re
import subprocess
import unittest

from sql_seed_to_postman import parse_seed, values


ROOT = Path(__file__).resolve().parents[1]


class SeedConversionTest(unittest.TestCase):
    def test_sql_strings_with_delimiters(self):
        self.assertEqual(values(r"('comma, semicolon; (text)', 'O''Brien', 'a\'b', NULL, -3, 0.125)"),
                         [['comma, semicolon; (text)', "O'Brien", "a'b", None, -3, 0.125]])

    def test_semicolon_does_not_end_insert_inside_a_string(self):
        tables, refs = parse_seed("INSERT INTO product (name) VALUES ('demo; test');\n"
                                 "SET @sim_p1 = LAST_INSERT_ID();\n")
        self.assertEqual(tables['product'][0]['name'], 'demo; test')
        self.assertIs(refs['@sim_p1'], tables['product'][0])

    def test_broken_inventory_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Inventory balance mismatch'):
            parse_seed("INSERT INTO shelf (name) VALUES ('s');\n"
                       "SET @s = LAST_INSERT_ID();\n"
                       "INSERT INTO inventory (shelf_id,system_quantity) VALUES (@s,7);\n")

    def test_exported_data_and_references(self):
        data = json.loads((ROOT / 'postman/warehouse-seed/warehouse_seed.data.json').read_text(encoding='utf-8'))
        expected = {'warehouse': 2, 'iot_device': 4, 'product': 12, 'shelf': 24,
                    'rfid_tag': 24, 'inventory': 24, 'inventory_transaction': 16569}
        self.assertEqual(data['metadata']['counts'], expected)
        tables = data['tables']
        self.assertEqual({key: len(rows) for key, rows in tables.items()}, expected)
        refs = {r['_seedRef'] for rows in tables.values() for r in rows if '_seedRef' in r}
        for rows in tables.values():
            for row in rows:
                for value in row.values():
                    if isinstance(value, str) and value.startswith('@'):
                        self.assertIn(value, refs)
        for inventory in tables['inventory']:
            total = sum(t['quantity'] for t in tables['inventory_transaction']
                        if t['shelf_id'] == inventory['shelf_id'] and t['status'] == 'SUCCESS')
            self.assertEqual(total, inventory['system_quantity'])

    def test_collection_variables_and_javascript(self):
        path = ROOT / 'postman/warehouse-seed/warehouse_seed.postman_collection.json'
        collection = json.loads(path.read_text(encoding='utf-8'))
        self.assertEqual([len(folder['item']) for folder in collection['item']], [1, 1, 12, 24, 4])
        defined = {v['key'] for v in collection['variable']}
        used = set(re.findall(r'\{\{([^{}]+)\}\}', json.dumps(collection)))
        self.assertFalse(used - defined)
        self.assertEqual(next(v['value'] for v in collection['variable'] if v['key'] == 'password'), '')
        # Compile the actual exported Postman scripts, rather than copies.
        scripts = ['\n'.join(event['script']['exec']) for folder in collection['item']
                   for item in folder['item'] for event in item.get('event', [])]
        result = subprocess.run(['node', '-'], input='const scripts = ' + json.dumps(scripts)
                                + '; for (const script of scripts) new Function(script);',
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
