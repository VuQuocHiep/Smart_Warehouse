"""Exercise the real cloud-batch HTTP protocol against a disposable localhost backend.

Needs the AI/demand virtualenv; run after starting a GITHUB_BATCH backend with a test DB.
Creates jobs and one stock-in followed by stock-out. Never targets a remote application.
"""
import argparse
import json
import os
from pathlib import Path
import sys
from urllib.parse import urlsplit

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'AI/demand'))
import httpx
from cloud_runner import CloudRunner


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:18081')
    parser.add_argument('--resume', action='store_true', help='Verify an existing model after backend restart, without expecting initial training')
    args = parser.parse_args()
    if urlsplit(args.url).hostname not in ('127.0.0.1', 'localhost'):
        parser.error('Use a disposable localhost backend, never a cloud or production database')
    token = os.environ['DEMAND_BATCH_TOKEN']
    with httpx.Client(base_url=args.url, timeout=120) as client:
        login = client.post('/auth/login', json={'email': os.environ['SMOKE_ADMIN_EMAIL'], 'password': os.environ['SMOKE_ADMIN_PASSWORD']})
        login.raise_for_status()
        client.headers['Authorization'] = 'Bearer ' + login.json()['token']
        assert client.get('/healthz').status_code == 200
        assert client.post('/internal/demand/claim', headers={'X-Runner-Id': 'unauthorized'}).status_code == 401
        cors = client.options('/api/demand/status', headers={'Origin': 'https://smoke-ui.example', 'Access-Control-Request-Method': 'GET', 'Access-Control-Request-Headers': 'authorization,content-type'})
        assert cors.status_code == 200 and cors.headers['access-control-allow-origin'] == 'https://smoke-ui.example'
        denied = client.options('/api/demand/status', headers={'Origin': 'https://untrusted.example', 'Access-Control-Request-Method': 'GET'})
        assert denied.status_code == 403

        runner = CloudRunner(args.url, token)
        try:
            runner.run()
            first = client.get('/api/demand/forecasts').json()
            assert (args.resume or first['modelAction'] == 'TRAINED') and len(first['forecasts']) == 24 * 14
            job = client.post('/api/demand/forecast-jobs')
            assert job.status_code == 202
            runner.run()
            second = client.get('/api/demand/forecasts').json()
            assert second['modelAction'] == 'REUSED'
            assert second['modelVersion'] == first['modelVersion']
            assert second['modelTrainedAt'] == first['modelTrainedAt']
            assert second['runId'] != first['runId']
            before = client.get('/api/demand/replenishment').json()
            item = next(i for i in before['items'] if i.get('status') == 'OK' and i.get('requiredWeek', 0) > 0)
            request = {'warehouseId': item['warehouse_id'], 'items': [{'sku': item['sku'], 'quantity': 1}]}
            change = client.post('/inventory/stock-in', json=request)
            change.raise_for_status()
            after = client.get('/api/demand/replenishment').json()
            updated = next(i for i in after['items'] if i['warehouse_id'] == item['warehouse_id'] and i['product_id'] == item['product_id'])
            assert updated['stock'] == item['stock'] + 1
            assert updated['requiredWeek'] == max(0, item['requiredWeek'] - 1)
            client.post('/inventory/stock-out', json=request).raise_for_status()
            print(json.dumps({'status': 'PASS', 'modelVersion': second['modelVersion'],
                'forecastRows': len(second['forecasts']), 'liveStockRecalculation': True,
                'corsAllowDeny': True, 'runnerTokenRequired': True}, indent=2))
        finally:
            runner.client.close()


if __name__ == '__main__':
    main()
