import hashlib
import json
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

import httpx
import service
from cloud_runner import CloudRunner
from test_service import snapshot


class CloudRunnerTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.download = b''

        def backend(request):
            self.calls.append(request)
            if request.method == 'GET':
                return httpx.Response(200, content=self.download)
            return httpx.Response(200, json={'status': 'SUCCEEDED'})

        self.client = httpx.Client(base_url='https://backend.test', transport=httpx.MockTransport(backend))
        self.addCleanup(self.client.close)
        self.runner = CloudRunner('https://backend.test', 'x' * 32, self.client)
        for name in ('ROOT', 'ACTIVE_JOB'):
            change = patch.object(service, name, getattr(service, name))
            change.start()
            self.addCleanup(change.stop)

    def claim(self, action):
        return {'jobId': str(uuid.uuid4()), 'action': action, 'snapshot': snapshot(), 'claimToken': 'attempt-one'}

    def test_train_uploads_artifact_then_fresh_process_predicts_with_downloaded_model(self):
        from test_service import DemandServiceTests
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            with patch.object(service, 'train', side_effect=DemandServiceTests().fake_train) as training:
                train = self.claim('TRAIN')
                self.runner.execute(train, first)
                folder = Path(first) / 'runs' / train['jobId']
                self.download = (folder / 'artifacts/model.joblib').read_bytes()
                metadata = service.read_json(folder / 'artifacts/metadata.json')
                metadata['metrics'] = service.read_json(folder / 'result.json')['metrics']
                claim = self.claim('PREDICT') | {'modelMetadata': metadata,
                    'modelSha256': hashlib.sha256(self.download).hexdigest(), 'modelVersion': 'fixture'}
                self.runner.execute(claim, second)
                self.assertEqual(1, training.call_count)
                self.assertEqual('REUSED', service.read_json(Path(second) / 'runs' / claim['jobId'] / 'result.json')['modelAction'])
        uploads = [r for r in self.calls if r.method == 'POST']
        self.assertEqual(2, len(uploads))
        self.assertIn(b'name="model"', uploads[0].content)
        self.assertNotIn(b'name="model"', uploads[1].content)
        self.assertIn(b'generatedAt', uploads[1].content)
        self.assertTrue(all(r.headers['X-Batch-Token'] == 'x' * 32 for r in self.calls))

    def test_corrupted_model_is_rejected_before_deserialization(self):
        claim = self.claim('PREDICT') | {'modelMetadata': {'runId': str(uuid.uuid4())}, 'modelSha256': 'bad'}
        with tempfile.TemporaryDirectory() as folder, patch('joblib.load') as load:
            with self.assertRaisesRegex(ValueError, 'checksum'):
                self.runner.execute(claim, folder)
            load.assert_not_called()

    def test_no_work_does_not_train(self):
        with patch.object(self.runner, 'request', return_value=httpx.Response(200, json={'status': 'IDLE'})), patch.object(service, 'train') as train:
            self.runner.run()
            train.assert_not_called()

    def test_retry_preserves_runner_and_attempt_headers(self):
        calls = []
        def backend(request):
            calls.append(request)
            return httpx.Response(503 if len(calls) == 1 else 200, json={})
        with httpx.Client(base_url='https://backend.test', transport=httpx.MockTransport(backend)) as client:
            runner = CloudRunner('https://backend.test', 'x' * 32, client)
            with patch('cloud_runner.time.sleep'):
                runner.request('POST', '/claim', headers={'X-Claim-Token': 'lease'})
        self.assertEqual(calls[0].headers['X-Runner-Id'], calls[1].headers['X-Runner-Id'])
        self.assertEqual('lease', calls[1].headers['X-Claim-Token'])

    def test_rejects_insecure_remote_and_embedded_credentials(self):
        for url in ('http://example.com', 'https://user:pass@example.com', 'https://example.com?token=secret'):
            with self.assertRaises(ValueError):
                CloudRunner(url, 'x' * 32)


if __name__ == '__main__':
    unittest.main()
