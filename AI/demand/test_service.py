import tempfile
import unittest
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


class DemandServiceTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        root = patch.object(service, 'ROOT', Path(folder.name))
        token = patch.object(service, 'TOKEN', 'test-token')
        root.start()
        token.start()
        self.addCleanup(root.stop)
        self.addCleanup(token.stop)
        self.client = TestClient(service.app)
        self.headers = {'X-Service-Token': 'test-token'}

    def test_requires_internal_token(self):
        self.assertEqual(self.client.get('/forecasts/latest').status_code, 401)
        with patch.object(service, 'TOKEN', ''):
            self.assertEqual(self.client.get('/health').status_code, 503)

    def test_catalog_ids_and_determinism(self):
        request = service.DemoRequest(pairs=[PAIR])
        first = service.simulate(request, '2026-10-18')
        second = service.simulate(request, '2026-10-18')
        self.assertTrue(first.equals(second))
        self.assertEqual(len(first), 730)
        self.assertTrue(first.warehouse_id.eq('warehouse-uuid').all())
        self.assertTrue(first.product_id.eq('product-uuid').all())
        self.assertTrue(first.data_source.eq('SIMULATED').all())

    def test_duplicate_pairs_rejected(self):
        response = self.client.post('/jobs/demo', headers=self.headers, json={'pairs': [PAIR, PAIR]})
        self.assertEqual(response.status_code, 422)

    def test_concurrent_job_rejected(self):
        with patch.object(service, 'ACTIVE_JOB', 'running'):
            response = self.client.post('/jobs/demo', headers=self.headers, json={'pairs': [PAIR]})
            self.assertEqual(response.status_code, 409)

    def test_concurrent_visitors_share_same_job(self):
        request = service.DemoRequest(pairs=[PAIR])
        folder = service.ROOT / 'runs/shared'
        service.write_json(folder / 'request.json', request.model_dump())
        service.write_json(folder / 'job.json', {'jobId': 'shared', 'status': 'RUNNING'})
        with patch.object(service, 'ACTIVE_JOB', 'shared'), patch.object(service.EXECUTOR, 'submit') as submit:
            response = self.client.post('/jobs/demo', headers=self.headers, json={'pairs': [PAIR]})
            self.assertEqual(response.status_code, 202)
            self.assertEqual(response.json()['jobId'], 'shared')
            submit.assert_not_called()

    def test_failure_keeps_previous_result(self):
        service.write_json(service.ROOT / 'active.json', {'runId': 'previous'})
        with patch.object(service, 'train', side_effect=ValueError('test failure')):
            service.execute_job('test-run', service.DemoRequest(pairs=[PAIR]), '2026-10-18')
        self.assertEqual(service.read_json(service.ROOT / 'active.json')['runId'], 'previous')
        self.assertEqual(service.read_json(service.ROOT / 'runs/test-run/job.json')['status'], 'FAILED')

    def test_restart_interrupts_unfinished_job(self):
        identity = '9eb4b6cb-c62e-4dcd-a308-301fe9ef5d78'
        service.write_json(service.ROOT / 'runs' / identity / 'job.json', {'jobId': identity, 'status': 'RUNNING'})
        response = self.client.get('/jobs/' + identity, headers=self.headers)
        self.assertEqual(response.json()['status'], 'INTERRUPTED')

    def fake_train(self, csv_path, output):
        output.mkdir(parents=True, exist_ok=True)
        data = pd.read_csv(csv_path)
        joblib.dump(fit(samples(load_data(csv_path)).tail(500)) | {'format_version': service.FORMAT_VERSION, 'sklearn_version': sklearn.__version__,
                     'lightgbm_version': lightgbm.__version__,
                     'data_cutoff': data.date.max(), 'data_source': 'SIMULATED',
                     'selected_method': 'seasonal_naive', 'model_version': 'fixture',
                     'known_pairs': [('warehouse-uuid', 'product-uuid')]}, output / 'model.joblib')
        service.write_json(output / 'metrics.json', {'selected_method': 'seasonal_naive'})
        service.forecast(csv_path, output / 'model.joblib', output / 'forecast_14_days.csv')

    def test_weekly_training_persists_and_reuses_artifact(self):
        request = service.DemoRequest(pairs=[PAIR])
        now = datetime(2026, 10, 19, tzinfo=timezone.utc)
        with patch.object(service, 'train', side_effect=self.fake_train) as training:
            with patch.object(service, 'utc_now', return_value=now):
                service.execute_job('first', request, '2026-10-18')
            self.assertEqual(training.call_count, 1)
            original = service.active_model()
            with patch.object(service, 'utc_now', return_value=now + timedelta(days=7, microseconds=-1)):
                service.execute_job('reused', request, '2026-10-24')
            self.assertEqual(training.call_count, 1)
            self.assertEqual(service.active_model(), original)
            self.assertEqual(service.latest()['modelAction'], 'REUSED')
            self.assertEqual(service.latest()['forecastDate'], '2026-10-25')
            with patch.object(service, 'utc_now', return_value=now + timedelta(days=7)):
                service.execute_job('weekly', request, '2026-10-25')
            self.assertEqual(training.call_count, 2)
            self.assertEqual(service.active_model()['runId'], 'weekly')

    def test_failed_retraining_retains_model_and_results(self):
        request = service.DemoRequest(pairs=[PAIR])
        now = datetime(2026, 10, 19, tzinfo=timezone.utc)
        with patch.object(service, 'train', side_effect=self.fake_train), patch.object(service, 'utc_now', return_value=now):
            service.execute_job('good', request, '2026-10-18')
        previous = service.read_json(service.ROOT / 'active.json')
        with patch.object(service, 'train', side_effect=RuntimeError('training failed')), \
                patch.object(service, 'utc_now', return_value=now + timedelta(days=7)):
            service.execute_job('bad', request, '2026-10-25')
        self.assertEqual(service.read_json(service.ROOT / 'active.json'), previous)
        self.assertEqual(service.latest()['runId'], 'good')

    def test_simulated_history_stable_across_dates(self):
        request = service.DemoRequest(pairs=[PAIR])
        first = service.simulate(request, '2026-10-18').set_index('date')
        second = service.simulate(request, '2026-10-19').set_index('date')
        shared = first.index.intersection(second.index)
        pd.testing.assert_frame_equal(first.loc[shared], second.loc[shared])

    def test_missing_artifact_requires_training(self):
        now = datetime.now(timezone.utc)
        self.assertTrue(service.model_due({'runId': 'missing', 'trainedAt': now.isoformat()}, now))

    def test_legacy_model_retrains_before_seven_days(self):
        now = datetime.now(timezone.utc)
        path = service.ROOT / 'runs/legacy/artifacts/model.joblib'
        path.parent.mkdir(parents=True)
        path.touch()
        self.assertTrue(service.model_due({'runId': 'legacy', 'trainedAt': now.isoformat()}, now))


if __name__ == '__main__':
    unittest.main()
