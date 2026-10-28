"""Read the seed as data only; never execute SQL or call the application."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re


def values(text):
    """Parse VALUES tuples with SQL strings, commas and escaped quotes."""
    rows, row, token = [], None, []
    quoted = False
    i = 0
    while i < len(text):
        char = text[i]
        if quoted:
            token.append(char)
            if char == "\\":
                i += 1
                if i >= len(text):
                    raise ValueError("Incomplete SQL escape")
                token.append(text[i])
            elif char == "'":
                if i + 1 < len(text) and text[i + 1] == "'":
                    token.append("'")
                    i += 1
                else:
                    quoted = False
        elif char == "'":
            quoted = True
            token.append(char)
        elif char == "(":
            if row is not None:
                raise ValueError("Expressions in VALUES are not supported")
            row = []
        elif char in ",)" and row is not None:
            row.append(scalar(''.join(token).strip()))
            token = []
            if char == ")":
                rows.append(row)
                row = None
        elif not char.isspace() or row is not None:
            if row is None:
                if char != ",":
                    raise ValueError(f"Unexpected VALUES character: {char!r}")
            else:
                token.append(char)
        i += 1
    if quoted or row is not None:
        raise ValueError("Incomplete VALUES tuple")
    return rows


def scalar(token):
    if token.startswith("'") and token.endswith("'"):
        value = token[1:-1].replace("''", "'")
        escapes = {"0": "\0", "n": "\n", "r": "\r", "t": "\t", "b": "\b", "Z": "\x1a"}
        return re.sub(r"\\(.)", lambda m: escapes.get(m[1], m[1]), value)
    if token.upper() == "NULL":
        return None
    if re.fullmatch(r"@\w+", token):
        return token
    if re.fullmatch(r"-?\d+", token):
        return int(token)
    if re.fullmatch(r"-?\d+\.\d+", token):
        return float(token)
    raise ValueError(f"Unsupported SQL value: {token!r}")


def parse_seed(sql):
    pattern = re.compile(
        r"^INSERT INTO (\w+)\s*\(([^)]+)\)\s*VALUES\s*((?:'(?:\\.|''|[^'\\])*'|[^';])*);"
        r"|^SET (@\w+) = LAST_INSERT_ID\(\);"
        r"|^UPDATE rfid_tag SET last_scanned_at=('(?:[^']|'')*') WHERE id=(@\w+);",
        re.MULTILINE | re.DOTALL)
    tables, refs = defaultdict(list), {}
    last = None
    insert_count = 0
    for match in pattern.finditer(sql):
        table, columns, raw, ref, timestamp, tag = match.groups()
        if table:
            columns = [c.strip() for c in columns.split(',')]
            for row in values(raw):
                if len(columns) != len(row):
                    raise ValueError(f"Column count mismatch in {table}")
                last = dict(zip(columns, row))
                tables[table].append(last)
            insert_count += 1
        elif ref:
            if last is None or ref in refs:
                raise ValueError(f"Invalid LAST_INSERT_ID reference: {ref}")
            last['_seedRef'] = ref
            refs[ref] = last
        else:
            refs[tag]['last_scanned_at'] = scalar(timestamp)
    if insert_count != len(re.findall(r'^INSERT INTO ', sql, re.MULTILINE)):
        raise ValueError("Not all INSERT statements were parsed")
    for rows in tables.values():
        for row in rows:
            for key, value in row.items():
                if key != '_seedRef' and isinstance(value, str) and value.startswith('@') and value not in refs:
                    raise ValueError(f"Unresolved reference: {value}")
    # Reconcile every final inventory balance with all successful transactions.
    balances = Counter()
    for row in tables['inventory_transaction']:
        if row['status'] == 'SUCCESS':
            balances[row['shelf_id']] += row['quantity']
    for row in tables['inventory']:
        if balances[row['shelf_id']] != row['system_quantity']:
            raise ValueError(f"Inventory balance mismatch: {row['shelf_id']}")
    return dict(tables), refs


def event(script):
    return {'listen': 'test', 'script': {'type': 'text/javascript', 'exec': script.splitlines()}}


def request(name, method, path, body=None, script='', description='', public=False):
    req = {'method': method, 'header': [], 'url': '{{baseUrl}}' + path, 'description': description}
    if public:
        req['auth'] = {'type': 'noauth'}
    if body is not None:
        req['header'] = [{'key': 'Content-Type', 'value': 'application/json'}]
        req['body'] = {'mode': 'raw', 'raw': json.dumps(body, ensure_ascii=False, indent=2),
                       'options': {'raw': {'language': 'json'}}}
    check = "pm.test('HTTP success', () => pm.expect(pm.response.code).to.be.within(200, 299));"
    return {'name': name, 'request': req, 'event': [event(check + '\n' + script)]}


def build_collection(tables):
    products = tables['product']
    product_vars = {p['_seedRef']: f'product_{p["sku"]}' for p in products}
    warehouse_vars = {w['_seedRef']: f'warehouse_{w["code"]}' for w in tables['warehouse']}
    variables = {'baseUrl': 'http://localhost:8080', 'email': 'ADMINPTIT@gmail.com',
                 'password': '', 'accessToken': '', 'jobId': ''}
    login = request('01 Login - set password in collection variables', 'POST', '/auth/login',
                    {'email': '{{email}}', 'password': '{{password}}'},
                    """if (pm.response.code === 200) {
    const data = pm.response.json();
    pm.test('Authenticated', () => pm.expect(data.authentication).to.eql(true));
    pm.test('JWT returned', () => pm.expect(data.token).to.be.a('string').and.not.empty);
    if (data.authentication && data.token) pm.collectionVariables.set('accessToken', data.token);
}""", public=True)
    mapping_script = ["if (pm.response.code === 200) {", 'const rows = pm.response.json();']
    for slot, warehouse in enumerate(tables['warehouse'], 1):
        var = warehouse_vars[warehouse['_seedRef']]
        variables[var] = ''
        mapping_script += [f"const wh{slot} = rows.find(w => w.monitoringSlot === {slot} && !w.deleted);",
                           f"pm.test('Kho {slot} exists', () => pm.expect(wh{slot}).to.be.an('object'));",
                           f"if (wh{slot}) pm.collectionVariables.set('{var}', wh{slot}.warehouseId);"]
    mapping_script.append('}')
    warehouses = request('02 Map SIM_WH01 -> Kho 1, SIM_WH02 -> Kho 2', 'GET', '/warehouse/getAll',
                         script='\n'.join(mapping_script),
                         description='Uses existing monitored warehouses because POST /api/shelves rejects warehouses without monitoringSlot. Source warehouse names/codes remain in warehouse_seed.data.json.')
    product_requests = []
    for p in products:
        var = product_vars[p['_seedRef']]
        variables[var] = ''
        body = {k: p[k] for k in ('sku', 'name', 'category', 'description', 'unit_weight')}
        body['unit'] = 'ITEM'
        product_requests.append(request('Create ' + p['sku'], 'POST', '/product/create', body,
            f"""if (pm.response.code === 200) {{
    const data = pm.response.json();
    pm.test('Product UUID returned', () => pm.expect(data.productId).to.be.a('string').and.not.empty);
    if (data.productId) pm.collectionVariables.set('{var}', data.productId);
}}"""))
    shelf_requests = []
    for s in tables['shelf']:
        warehouse_var = warehouse_vars[s['warehouse_id']]
        product_var = product_vars[s['product_id']]
        source_code = next(w['code'] for w in tables['warehouse'] if w['_seedRef'] == s['warehouse_id'])
        device_code = f'{source_code}_{s["name"]}'
        body = {'name': s['name'], 'warehouseId': '{{' + warehouse_var + '}}',
                'deviceCode': device_code, 'productIds': ['{{' + product_var + '}}']}
        shelf_requests.append(request(f'{source_code} / {s["name"]}', 'POST', '/api/shelves', body,
            """if (pm.response.code === 200) {
    const data = pm.response.json();
    pm.test('Shelf created with product', () => pm.expect(data.productIds).to.have.lengthOf(1));
}""", description='Maps legacy single-SKU shelf to a monitored shelf. Weight/capacity/inventory fields have no corresponding API and remain in the source JSON.'))
    forecast_requests = [
        request('01 Verify demand catalog', 'GET', '/api/demand/catalog', script="""if (pm.response.code === 200) {
    const pairs = pm.response.json();
    const seeded = pairs.filter(p => /^SIM_SKU\\d{3}$/.test(p.sku));
    pm.test('24 seed warehouse/product pairs', () => pm.expect(seeded).to.have.lengthOf(24));
}"""),
        request('02 Update database forecast', 'POST', '/api/demand/forecast-jobs', script="""if (pm.response.code === 202) {
    const data = pm.response.json();
    pm.test('Job ID returned', () => pm.expect(data.jobId).to.be.a('string').and.not.empty);
    if (data.jobId) pm.collectionVariables.set('jobId', data.jobId);
}""", description='Requires V13 and a completed initial training job. Runs PREDICT from DB history using the saved joblib; does not generate history.'),
        request('03 Check job - repeat until SUCCEEDED', 'GET', '/api/demand/jobs/{{jobId}}', script="""if (pm.response.code === 200) {
    pm.test('Job has not failed', () => pm.expect(pm.response.json().status).to.not.be.oneOf(['FAILED', 'INTERRUPTED']));
}"""),
        request('04 Read latest forecast after SUCCEEDED', 'GET', '/api/demand/forecasts')]
    return {
        'info': {'name': 'Smart Warehouse - warehouse_seed.sql',
                 'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
                 'description': 'SIMULATED seed data. Set password and baseUrl, then run folders 01-04 once in order. Maps SQL warehouses to existing Kho 1/Kho 2. Products and shelves are created in the application; rerunning creation folders can produce duplicate errors. Folder 05 tests forecasting separately. SQL inventory/RFID/transactions have no current ingestion endpoints; full source data is in warehouse_seed.data.json.'},
        'auth': {'type': 'bearer', 'bearer': [{'key': 'token', 'value': '{{accessToken}}', 'type': 'string'}]},
        'variable': [{'key': k, 'value': v, 'type': 'string'} for k, v in variables.items()],
        'item': [{'name': '01 Authentication', 'item': [login]},
                 {'name': '02 Existing warehouse mapping', 'item': [warehouses]},
                 {'name': '03 Create 12 products - run once', 'item': product_requests},
                 {'name': '04 Create 24 shelves - run once', 'item': shelf_requests},
                 {'name': '05 Demand forecasting - run manually after seeding', 'item': forecast_requests}]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--output-dir', type=Path, default=Path('postman/warehouse-seed'))
    args = parser.parse_args()
    content = args.source.read_bytes()
    tables, _ = parse_seed(content.decode('utf-8-sig'))
    counts = {table: len(rows) for table, rows in tables.items()}
    args.output_dir.mkdir(parents=True, exist_ok=True)
    data = {'metadata': {'sourceFile': args.source.name, 'sourceSha256': hashlib.sha256(content).hexdigest(),
                         'dataSource': 'SIMULATED', 'counts': counts,
                         'references': 'SQL @variables are preserved. _seedRef identifies their corresponding row; these are NOT backend UUIDs.',
                         'usage': 'Converted SQL fixtures, not an HTTP bulk-import body. Use the accompanying collection for supported API requests.'},
            'tables': tables}
    for name, value in [('warehouse_seed.data.json', data),
                        ('warehouse_seed.postman_collection.json', build_collection(tables))]:
        (args.output_dir / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'output': str(args.output_dir), 'counts': counts,
                      'inventoryBalancesVerified': len(tables['inventory'])}, ensure_ascii=False))


if __name__ == '__main__':
    main()
