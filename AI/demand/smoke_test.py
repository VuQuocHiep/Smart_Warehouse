"""Train from an actual backend DB snapshot, then prove persisted joblib reuse over HTTP."""
import argparse
import json
import time
import uuid
from pathlib import Path
from unittest.mock import patch
from fastapi.testclient import TestClient
import service


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot', required=True)
    parser.add_argument('--runtime', default=str(Path(__file__).parent / 'runtime/integration-worker'))
    args = parser.parse_args()
    snapshot = json.loads(Path(args.snapshot).read_text(encoding='utf-8'))
    with patch.object(service, 'ROOT', Path(args.runtime).resolve()), patch.object(service, 'TOKEN', 'smoke-token'):
        client = TestClient(service.app)
        headers = {'X-Service-Token': 'smoke-token'}

        def run(action):
            job_id = str(uuid.uuid4())
            response = client.post('/jobs', headers=headers, json={'jobId': job_id, 'action': action, 'snapshot': snapshot})
            response.raise_for_status()
            deadline = time.monotonic() + 600
            while time.monotonic() < deadline:
                state = client.get('/jobs/' + job_id, headers=headers).json()
                if state['status'] in ['SUCCEEDED', 'FAILED', 'INTERRUPTED']:
                    assert state['status'] == 'SUCCEEDED', state
                    return client.get('/forecasts/latest', headers=headers).json()
                time.sleep(.5)
            raise TimeoutError('Job exceeded 600 seconds')

        result = run('TRAIN')
        assert len(result['forecasts']) == len(snapshot['pairs'])*14
        assert all(row['status'] == 'OK' and row['predicted_quantity'] >= 0 for row in result['forecasts'])
        original = service.active_model()
        with patch.object(service, 'train', side_effect=AssertionError('PREDICT must reuse joblib')):
            reused = run('PREDICT')
        assert service.active_model() == original
        assert reused['forecasts'] == result['forecasts']
        print(f"PASS: DB snapshot -> HTTP TRAIN -> joblib -> HTTP PREDICT; {len(result['forecasts'])} forecasts.")
        print(f"Model: {service.ROOT / 'runs' / original['runId'] / 'artifacts/model.joblib'}")


if __name__ == '__main__':
    main()
