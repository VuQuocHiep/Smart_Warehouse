"""End-to-end HTTP contract test with real training and fixture UUID catalog."""
import tempfile
import time
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient
import service


def main():
    with tempfile.TemporaryDirectory() as folder, patch.object(service, 'ROOT', Path(folder)), \
            patch.object(service, 'TOKEN', 'smoke-test-token'):
        client = TestClient(service.app)
        headers = {'X-Service-Token': 'smoke-test-token'}
        pairs = [{'warehouseId': f'warehouse-uuid-{w}', 'warehouseName': f'Kho {w}',
                  'productId': f'product-uuid-{p}', 'sku': f'SKU-{p}', 'productName': f'Sản phẩm {p}'}
                 for w in [1, 2] for p in [1, 2]]
        response = client.post('/jobs/demo', headers=headers, json={'pairs': pairs})
        assert response.status_code == 202, response.text
        job_id = response.json()['jobId']
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            job = client.get('/jobs/' + job_id, headers=headers).json()
            if job['status'] in ['SUCCEEDED', 'FAILED']:
                break
            time.sleep(.2)
        assert job['status'] == 'SUCCEEDED', job
        result = client.get('/forecasts/latest', headers=headers).json()
        assert result['runId'] == job_id
        assert result['dataSource'] == 'SIMULATED'
        assert len(result['forecasts']) == 56
        assert len(result['history']) == 112
        assert all(row['predicted_quantity'] >= 0 and row['status'] == 'OK' for row in result['forecasts'])
        assert {row['warehouse_id'] for row in result['forecasts']} == {'warehouse-uuid-1', 'warehouse-uuid-2'}
        model = service.active_model()
        with patch.object(service, 'train', side_effect=AssertionError('Must reuse saved joblib')):
            service.execute_job('reuse-smoke', service.DemoRequest(pairs=pairs), result['dataCutoff'])
        reused = client.get('/forecasts/latest', headers=headers).json()
        assert reused['runId'] == 'reuse-smoke'
        assert reused['modelAction'] == 'REUSED'
        assert reused['forecasts'] == result['forecasts']
        assert service.active_model() == model
        print('PASS: authenticated HTTP job -> simulated UUID history -> actual training -> 56 forecasts -> published result.')
        print('PASS: next run loads persisted joblib with identical forecasts and no training.')


if __name__ == '__main__':
    main()
