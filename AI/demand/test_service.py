import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch
from datetime import datetime, timedelta, timezone

import joblib
import pandas as pd
import sklearn
import lightgbm
from fastapi.testclient import TestClient
import service
from demand_model import fit, samples, load_data

PAIR = {'warehouseId': 'warehouse-uuid', 'warehouseName': 'Kho 1', 'productId': 'product-uuid',
        'sku': 'SKU-A', 'productName': 'Product A'}


def snapshot():
    end = pd.Timestamp('2026-10-18')
    rows = [{'date': str(day.date()), 'warehouse_id': PAIR['warehouseId'], 'product_id': PAIR['productId'],
             'export_quantity': 10 + index % 7, 'data_quality': 'COMPLETE', 'data_source': 'REAL'}
            for index, day in enumerate(pd.date_range(end-pd.Timedelta(days=729), end))]
    return {'pairs': [PAIR], 'rows': rows, 'dataCutoff': '2026-10-18',
            'dataSource': 'REAL', 'datasetId': 'REAL-fixture'}


class DemandServiceTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        for name, value in [('ROOT', Path(folder.name)), ('TOKEN', 'test-token'), ('ACTIVE_JOB', None)]:
            change = patch.object(service, name, value)
            change.start()
            self.addCleanup(change.stop)
        self.client = TestClient(service.app)
        self.headers = {'X-Service-Token': 'test-token'}

    def request(self, action='TRAIN', data=None):
        return service.JobRequest(jobId=uuid.uuid4(), action=action, snapshot=data or snapshot())

    def fake_train(self, csv_path, output):
        output.mkdir(parents=True, exist_ok=True)
        data = load_data(csv_path)
        joblib.dump(fit(samples(data).tail(500)) | {
            'format_version': service.FORMAT_VERSION, 'sklearn_version': sklearn.__version__,
            'lightgbm_version': lightgbm.__version__, 'data_cutoff': str(data.date.max().date()),
            'data_source': 'REAL', 'selected_method': 'lightgbm_poisson',
            'default_method': 'lightgbm_poisson', 'model_version': 'fixture',
            'known_pairs': [('warehouse-uuid', 'product-uuid')]}, output / 'model.joblib')
        service.write_json(output / 'metrics.json', {'model_version': 'fixture'})

    def test_requires_internal_token_and_has_no_demo_endpoint(self):
        self.assertEqual(self.client.get('/health').status_code, 401)
        self.assertIn(self.client.post('/jobs/demo', headers=self.headers, json={}).status_code, (404, 405))

    def test_rejects_unknown_pairs_duplicate_days_and_mixed_sources(self):
        for mutate in [lambda s: s['rows'].append(s['rows'][0]),
                       lambda s: s['rows'][0].update(data_source='SIMULATED'),
                       lambda s: s['rows'][0].update(product_id='unknown')]:
            data = snapshot()
            mutate(data)
            response = self.client.post('/jobs', headers=self.headers,
                json={'jobId': str(uuid.uuid4()), 'action': 'TRAIN', 'snapshot': data})
            self.assertEqual(response.status_code, 422)

    def test_persistent_request_id_prevents_duplicate_training(self):
        request = self.request()
        with patch.object(service.EXECUTOR, 'submit') as submit:
            first = self.client.post('/jobs', headers=self.headers, json=request.model_dump(mode='json'))
            repeated = self.client.post('/jobs', headers=self.headers, json=request.model_dump(mode='json'))
            self.assertEqual(first.json(), repeated.json())
            self.assertEqual(submit.call_count, 1)
            other = self.client.post('/jobs', headers=self.headers, json=self.request().model_dump(mode='json'))
            self.assertEqual(other.status_code, 409)

    def test_legacy_model_and_results_require_retraining(self):
        with patch.object(service, 'train', side_effect=self.fake_train):
            service.execute_job(self.request())
        pointer = service.read_json(service.ROOT / 'active.json')
        pointer['model']['dataSource'] = 'SIMULATED'
        service.write_json(service.ROOT / 'active.json', pointer)
        result_path = service.ROOT / 'runs' / pointer['runId'] / 'result.json'
        service.write_json(result_path, service.read_json(result_path) | {'dataSource': 'SIMULATED'})
        self.assertFalse(service.health()['modelReady'])
        self.assertEqual(service.latest()['forecasts'], [])
        with patch.object(service, 'train') as training:
            service.execute_job(self.request('PREDICT'))
        training.assert_not_called()

    def test_restart_interrupts_unfinished_job(self):
        request = self.request()
        with patch.object(service.EXECUTOR, 'submit'):
            self.client.post('/jobs', headers=self.headers, json=request.model_dump(mode='json'))
        service.ACTIVE_JOB = None
        self.assertEqual(self.client.get(f'/jobs/{request.jobId}', headers=self.headers).json()['status'], 'INTERRUPTED')

    def test_predict_reuses_saved_joblib_even_after_seven_days(self):
        first = self.request()
        now = datetime(2026, 10, 19, tzinfo=timezone.utc)
        with patch.object(service, 'train', side_effect=self.fake_train) as training:
            with patch.object(service, 'utc_now', return_value=now):
                service.execute_job(first)
            model = service.active_model()
            self.assertTrue(service.model_ready(model))
            with patch.object(service, 'utc_now', return_value=now+timedelta(days=20)):
                service.execute_job(self.request('PREDICT'))
            self.assertEqual(training.call_count, 1)
            self.assertEqual(service.active_model(), model)
            self.assertEqual(service.latest()['modelAction'], 'REUSED')

    def test_failed_training_keeps_previous_model_and_results(self):
        with patch.object(service, 'train', side_effect=self.fake_train):
            service.execute_job(self.request())
        before = service.read_json(service.ROOT / 'active.json')
        request = self.request()
        with patch.object(service, 'train', side_effect=RuntimeError('failure')):
            service.execute_job(request)
        self.assertEqual(service.read_json(service.ROOT / 'active.json'), before)
        self.assertEqual(service.job(request.jobId)['status'], 'FAILED')

    def test_predict_never_trains_when_model_is_missing(self):
        request = self.request('PREDICT')
        with patch.object(service, 'train') as training:
            service.execute_job(request)
        training.assert_not_called()
        self.assertEqual(service.job(request.jobId)['status'], 'FAILED')

    def test_stale_fixture_is_not_extended_with_fake_zero_days(self):
        with patch.object(service, 'train', side_effect=self.fake_train):
            service.execute_job(self.request())
        data = snapshot()
        data['dataCutoff'] = '2026-10-20'
        service.execute_job(self.request('PREDICT', data))
        self.assertEqual({r['status'] for r in service.latest()['forecasts']}, {'STALE_DATA'})
        self.assertTrue(all(r['predicted_quantity'] is None for r in service.latest()['forecasts']))

    def test_next_training_is_monday_five_vietnam_time(self):
        with patch.object(service, 'utc_now', return_value=datetime(2026, 10, 25, 21, 59, tzinfo=timezone.utc)):
            self.assertEqual(service.next_training_at(), '2026-10-26T05:00:00+07:00')
        with patch.object(service, 'utc_now', return_value=datetime(2026, 10, 25, 22, 0, tzinfo=timezone.utc)):
            self.assertEqual(service.next_training_at(), '2026-11-02T05:00:00+07:00')


if __name__ == '__main__':
    unittest.main()
