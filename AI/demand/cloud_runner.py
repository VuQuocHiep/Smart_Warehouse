"""One-shot cloud runner. All durable state lives behind the authenticated backend API."""
import hashlib
import json
import os
import tempfile
import time
import uuid
from pathlib import Path
from urllib.parse import urlsplit

import httpx
import service


class CloudRunner:
    def __init__(self, base_url, token, client=None):
        parsed = urlsplit(base_url)
        if parsed.scheme != 'https' and not (parsed.scheme == 'http' and parsed.hostname in ('localhost', '127.0.0.1')):
            raise ValueError('Use HTTPS for a remote backend')
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError('Backend URL must not contain credentials, query or fragment')
        if len(token) < 32:
            raise ValueError('DEMAND_BATCH_TOKEN must contain at least 32 characters')
        self.client = client or httpx.Client(base_url=base_url.rstrip('/'), timeout=90, follow_redirects=False)
        self.headers = {'X-Batch-Token': token, 'X-Runner-Id': str(uuid.uuid4())}

    def request(self, method, path, **kwargs):
        """Cold starts and lost completion responses are safe to retry with the same lease."""
        headers = self.headers | kwargs.pop('headers', {})
        for attempt in range(5):
            try:
                response = self.client.request(method, '/internal/demand' + path, headers=headers, **kwargs)
                if response.status_code < 500 and response.status_code != 429:
                    response.raise_for_status()
                    return response
            except httpx.TransportError:
                pass
            if attempt < 4:
                time.sleep(min(5 * 2**attempt, 40))
        # Never print headers, tokens, snapshots, or infrastructure error bodies.
        raise RuntimeError('Backend unavailable after retries; inspect service health')

    def execute(self, claim, root):
        service.ROOT = Path(root)
        service.ACTIVE_JOB = None
        request = service.JobRequest(jobId=claim['jobId'], action=claim['action'], snapshot=claim['snapshot'])
        headers = {'X-Claim-Token': claim['claimToken']}
        path = '/jobs/' + str(request.jobId)
        if request.action == 'PREDICT':
            model = claim['modelMetadata']
            # Metadata controls a path, so restrict it before creating any files.
            run_id = str(uuid.UUID(model['runId']))
            artifact = self.request('GET', path + '/model', headers=headers).content
            if hashlib.sha256(artifact).hexdigest() != claim['modelSha256']:
                raise ValueError('Downloaded model checksum mismatch')
            model_file = service.ROOT / 'runs' / run_id / 'artifacts/model.joblib'
            model_file.parent.mkdir(parents=True)
            model_file.write_bytes(artifact)
            service.write_json(model_file.with_name('metrics.json'), model['metrics'])
            service.write_json(service.ROOT / 'active.json', {'model': model})

        service.execute_job(request)
        folder = service.ROOT / 'runs' / str(request.jobId)
        state = service.read_json(folder / 'job.json')
        if state['status'] != 'SUCCEEDED':
            raise RuntimeError('Model job failed; see preceding Python error')
        result = service.read_json(folder / 'result.json')
        result['generatedAt'] = service.utc_now().isoformat()
        metadata = service.read_json(folder / 'artifacts/metadata.json')
        metadata['metrics'] = result['metrics']
        files = {}
        if request.action == 'TRAIN':
            artifact = (folder / 'artifacts/model.joblib').read_bytes()
            metadata['artifactSha256'] = hashlib.sha256(artifact).hexdigest()
            files['model'] = ('model.joblib', artifact, 'application/octet-stream')
        # Explicit multipart for PREDICT too, when no artifact is uploaded.
        files['result'] = (None, json.dumps(result, allow_nan=False), 'application/json')
        files['metadata'] = (None, json.dumps(metadata, allow_nan=False), 'application/json')
        self.request('POST', path + '/complete', headers=headers, files=files)
        print(f"Completed {request.action} job {request.jobId}", flush=True)

    def run(self):
        failures = 0
        # Enough for a train, a queued manual prediction, and bounded retry attempts.
        for _ in range(5):
            claim = self.request('POST', '/claim').json()
            if claim.get('status') == 'IDLE':
                if failures:
                    raise RuntimeError('One or more jobs failed; check backend job status')
                print('No pending demand job', flush=True)
                return
            try:
                with tempfile.TemporaryDirectory(prefix='warehouse-demand-') as folder:
                    self.execute(claim, folder)
                failures = 0
            except Exception as exc:
                failures += 1
                # Only log an exception type: HTTP exceptions can expose request details.
                print(f"Job {claim['jobId']} failed ({type(exc).__name__})", flush=True)
                self.request('POST', '/jobs/' + claim['jobId'] + '/fail', headers={'X-Claim-Token': claim['claimToken']})
        raise RuntimeError('Runner iteration limit reached; inspect pending jobs')


def main():
    runner = CloudRunner(os.environ['WAREHOUSE_BACKEND_URL'], os.environ['DEMAND_BATCH_TOKEN'])
    try:
        runner.run()
    finally:
        runner.client.close()


if __name__ == '__main__':
    main()
