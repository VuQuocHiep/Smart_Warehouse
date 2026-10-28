"""Internal DB-backed demand worker. Spring owns scheduling; run one Uvicorn worker."""
import hashlib
import json
import logging
import os
import secrets
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Annotated, Literal

import lightgbm
import pandas as pd
import sklearn
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, model_validator
from demand_model import train, forecast, load_data, FORMAT_VERSION

LOGGER = logging.getLogger(__name__)
ROOT = Path(os.getenv('DEMAND_DATA_DIR', Path(__file__).resolve().parent / 'runtime')).resolve()
TOKEN = os.getenv('DEMAND_SERVICE_TOKEN', '')
LOCK = threading.Lock()
EXECUTOR = ThreadPoolExecutor(max_workers=1, thread_name_prefix='demand')
ACTIVE_JOB = None
VN = timezone(timedelta(hours=7))


def utc_now():
    return datetime.now(timezone.utc)


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, allow_nan=False), encoding='utf-8')
    temporary.replace(path)


def active_model():
    pointer = ROOT / 'active.json'
    return read_json(pointer).get('model') if pointer.exists() else None


def model_ready(model):
    return bool(model and model.get('formatVersion') == FORMAT_VERSION
                and model.get('lightgbmVersion') == lightgbm.__version__
                and model.get('sklearnVersion') == sklearn.__version__
                and model.get('datasetId')
                and (ROOT / 'runs' / model['runId'] / 'artifacts/model.joblib').is_file())


def next_training_at():
    now = utc_now().astimezone(VN)
    monday = (now - timedelta(days=now.weekday())).replace(hour=5, minute=0, second=0, microsecond=0)
    if monday <= now:
        monday += timedelta(days=7)
    return monday.isoformat()


def authorize(x_service_token: Annotated[str, Header()] = ''):
    if not TOKEN:
        raise HTTPException(503, 'DEMAND_SERVICE_TOKEN is not configured')
    if not secrets.compare_digest(TOKEN.encode(), x_service_token.encode()):
        raise HTTPException(401, 'Invalid service token')


app = FastAPI(title='Warehouse demand worker', dependencies=[Depends(authorize)])


class Pair(BaseModel):
    warehouseId: str = Field(min_length=1, max_length=255)
    warehouseName: str = Field(min_length=1, max_length=255)
    productId: str = Field(min_length=1, max_length=255)
    sku: str = Field(min_length=1, max_length=255)
    productName: str = Field(min_length=1, max_length=255)


class Observation(BaseModel):
    date: date
    warehouse_id: str = Field(min_length=1, max_length=255)
    product_id: str = Field(min_length=1, max_length=255)
    export_quantity: int = Field(ge=0)
    data_quality: Literal['COMPLETE']
    data_source: Literal['REAL', 'SIMULATED']


class Snapshot(BaseModel):
    pairs: list[Pair] = Field(min_length=1, max_length=64)
    rows: list[Observation] = Field(min_length=1, max_length=500000)
    dataCutoff: date
    dataSource: Literal['REAL', 'SIMULATED']
    datasetId: str = Field(min_length=1, max_length=128, pattern=r'^[A-Za-z0-9_-]+$')

    @model_validator(mode='after')
    def validate_snapshot(self):
        pairs = {(p.warehouseId, p.productId) for p in self.pairs}
        if len(pairs) != len(self.pairs):
            raise ValueError('Duplicate pairs')
        if self.dataCutoff >= utc_now().astimezone(VN).date():
            raise ValueError('Only completed days may be used')
        seen = set()
        for row in self.rows:
            key = (row.warehouse_id, row.product_id)
            identity = (*key, row.date)
            if key not in pairs or row.date > self.dataCutoff or row.data_source != self.dataSource or identity in seen:
                raise ValueError('Snapshot contains unknown pairs, future dates, mixed sources or duplicate days')
            seen.add(identity)
        if {key[:2] for key in seen} != pairs:
            raise ValueError('Every pair needs observations')
        return self


class JobRequest(BaseModel):
    jobId: uuid.UUID
    action: Literal['TRAIN', 'PREDICT']
    snapshot: Snapshot


def execute_job(request):
    global ACTIVE_JOB
    job_id = str(request.jobId)
    folder = ROOT / 'runs' / job_id
    snapshot = request.snapshot
    status = {'jobId': job_id, 'status': 'RUNNING', 'action': request.action,
              'dataSource': snapshot.dataSource, 'dataCutoff': str(snapshot.dataCutoff)}
    try:
        write_json(folder / 'job.json', status)
        csv_path = folder / 'daily.csv'
        pd.DataFrame([row.model_dump(mode='json') for row in snapshot.rows]).to_csv(csv_path, index=False)
        load_data(csv_path)  # Validate continuity and values before touching the active model.
        model = active_model()
        if request.action == 'TRAIN':
            train(csv_path, folder / 'artifacts')
            model = {'runId': job_id, 'trainedAt': utc_now().isoformat(),
                     'formatVersion': FORMAT_VERSION, 'lightgbmVersion': lightgbm.__version__,
                     'sklearnVersion': sklearn.__version__, 'datasetId': snapshot.datasetId,
                     'dataSource': snapshot.dataSource}
        elif not model_ready(model) or model['datasetId'] != snapshot.datasetId or model['dataSource'] != snapshot.dataSource:
            raise ValueError('No compatible model; a separate TRAIN job is required')
        # Explicit cutoff means a bounded fixture cannot silently masquerade as fresh history.
        forecast(csv_path, ROOT / 'runs' / model['runId'] / 'artifacts/model.joblib',
                 folder / 'artifacts/forecast_14_days.csv', as_of=str(snapshot.dataCutoff))
        predictions = pd.read_csv(folder / 'artifacts/forecast_14_days.csv', dtype={'warehouse_id': str, 'product_id': str})
        predictions = predictions.astype(object).where(pd.notna(predictions), None)
        metrics = read_json(ROOT / 'runs' / model['runId'] / 'artifacts/metrics.json')
        version = metrics['model_version']
        data = pd.read_csv(csv_path, dtype={'warehouse_id': str, 'product_id': str})
        digest = hashlib.sha256(csv_path.read_bytes()).hexdigest()
        metadata = model | {'modelVersion': version, 'dataCutoff': str(snapshot.dataCutoff), 'datasetSha256': digest}
        write_json(folder / 'artifacts/metadata.json', metadata)
        result = {'runId': job_id, 'modelVersion': version, 'datasetId': snapshot.datasetId,
                  'dataSource': snapshot.dataSource, 'dataCutoff': str(snapshot.dataCutoff),
                  'forecastDate': str(snapshot.dataCutoff + timedelta(days=1)),
                  'pairs': [p.model_dump() for p in snapshot.pairs], 'metrics': metrics,
                  'history': data.groupby(['warehouse_id', 'product_id']).tail(28).to_dict(orient='records'),
                  'forecasts': predictions.to_dict(orient='records'),
                  'modelTrainedAt': model['trainedAt'], 'nextTrainingAt': next_training_at(),
                  'modelAction': 'TRAINED' if request.action == 'TRAIN' else 'REUSED'}
        write_json(folder / 'result.json', result)
        write_json(ROOT / 'active.json', {'runId': job_id, 'model': model})
        status.update(status='SUCCEEDED', modelVersion=version)
    except Exception as ex:
        LOGGER.exception('Demand job failed: %s', job_id)
        status.update(status='FAILED', message=str(ex)[:1000])
    finally:
        write_json(folder / 'job.json', status)
        with LOCK:
            ACTIVE_JOB = None


@app.get('/health')
def health():
    model = active_model()
    return {'status': 'UP', 'mode': 'DATABASE', 'maxPairs': 64,
            'modelReady': model_ready(model), 'datasetId': model.get('datasetId') if model else None}


@app.post('/jobs', status_code=202)
def submit(request: JobRequest):
    global ACTIVE_JOB
    job_id = str(request.jobId)
    with LOCK:
        folder = ROOT / 'runs' / job_id
        if (folder / 'request.json').exists():
            if read_json(folder / 'request.json') != request.model_dump(mode='json'):
                raise HTTPException(409, 'Job ID already belongs to a different request')
            return job(request.jobId)
        if ACTIVE_JOB is not None:
            raise HTTPException(409, 'A job is already running')
        status = {'jobId': job_id, 'status': 'QUEUED', 'action': request.action}
        write_json(folder / 'request.json', request.model_dump(mode='json'))
        write_json(folder / 'job.json', status)
        ACTIVE_JOB = job_id
        EXECUTOR.submit(execute_job, request)
        return status


@app.get('/jobs/{job_id}')
def job(job_id: uuid.UUID):
    path = ROOT / 'runs' / str(job_id) / 'job.json'
    if not path.exists():
        if (path.parent / 'request.json').exists():
            return {'jobId': str(job_id), 'status': 'INTERRUPTED', 'message': 'Worker stopped before queuing job'}
        raise HTTPException(404, 'Job not found')
    result = read_json(path)
    if result['status'] in ['QUEUED', 'RUNNING'] and ACTIVE_JOB != str(job_id):
        result.update(status='INTERRUPTED', message='Worker restarted; create a retry attempt')
    return result


@app.get('/forecasts/latest')
def latest():
    pointer = ROOT / 'active.json'
    if not pointer.exists():
        return {'forecasts': [], 'history': [], 'pairs': []}
    result = read_json(ROOT / 'runs' / read_json(pointer)['runId'] / 'result.json')
    result['nextTrainingAt'] = next_training_at()
    return result
