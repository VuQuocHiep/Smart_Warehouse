"""Internal demand service. Run one Uvicorn worker on loopback."""
import hashlib
import json
import logging
import os
import secrets
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Annotated

import numpy as np
import pandas as pd
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field, model_validator

from demand_model import train, forecast, FORMAT_VERSION, round_quantity
import lightgbm

LOGGER = logging.getLogger(__name__)
ROOT = Path(os.getenv('DEMAND_DATA_DIR', Path(__file__).resolve().parent / 'runtime')).resolve()
TOKEN = os.getenv('DEMAND_SERVICE_TOKEN', '')
LOCK = threading.Lock()
EXECUTOR = ThreadPoolExecutor(max_workers=1, thread_name_prefix='demand')
ACTIVE_JOB = None
RETRAIN_INTERVAL = timedelta(days=7)


def utc_now():
    return datetime.now(timezone.utc)


def active_model():
    pointer = ROOT / 'active.json'
    if not pointer.exists():
        return None
    # Old runs without training metadata are migrated by training once.
    return read_json(pointer).get('model')


def model_due(model, now):
    return (model is None or
            model.get('formatVersion') != FORMAT_VERSION or
            model.get('lightgbmVersion') != lightgbm.__version__ or
            now >= datetime.fromisoformat(model['trainedAt']) + RETRAIN_INTERVAL or
            not (ROOT / 'runs' / model['runId'] / 'artifacts/model.joblib').is_file())


def authorize(x_service_token: Annotated[str, Header()] = ''):
    if not TOKEN:
        raise HTTPException(503, 'DEMAND_SERVICE_TOKEN is not configured')
    if not secrets.compare_digest(TOKEN.encode(), x_service_token.encode()):
        raise HTTPException(401, 'Invalid service token')


app = FastAPI(title='Warehouse demand forecasting', dependencies=[Depends(authorize)])


class Pair(BaseModel):
    warehouseId: str = Field(min_length=1, max_length=255)
    warehouseName: str = Field(min_length=1, max_length=255)
    productId: str = Field(min_length=1, max_length=255)
    sku: str = Field(min_length=1, max_length=255)
    productName: str = Field(min_length=1, max_length=255)


class DemoRequest(BaseModel):
    pairs: list[Pair] = Field(min_length=1, max_length=64)
    seed: int = Field(default=42, ge=0, le=2147483647)

    @model_validator(mode='after')
    def unique_pairs(self):
        keys = {(pair.warehouseId, pair.productId) for pair in self.pairs}
        if len(keys) != len(self.pairs):
            raise ValueError('Duplicate warehouse/product pairs')
        return self


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, allow_nan=False), encoding='utf-8')
    temporary.replace(path)


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def simulate(request, cutoff):
    dates = pd.date_range(pd.Timestamp(cutoff) - pd.Timedelta(days=729), cutoff)
    rows = []
    for pair in sorted(request.pairs, key=lambda p: (p.warehouseId, p.productId)):
        identity = f'{pair.warehouseId}:{pair.productId}:{request.seed}'
        stable = int.from_bytes(hashlib.sha256(identity.encode()).digest()[:8], 'big')
        profile = stable % 6
        base = 12 + stable % 20
        for day in dates:
            # A date's observation must not change when the rolling window moves.
            rng = np.random.default_rng([stable, day.toordinal()])
            trend = np.clip((day - pd.Timestamp('2025-01-01')).days / 730, 0, 2)
            multiplier = 1.0
            if profile == 1:
                multiplier = [1.4, 1.2, 1, 1, 1.3, .65, .45][day.dayofweek]
            elif profile == 2:
                multiplier = .6 + trend
            elif profile == 3:
                multiplier = max(.2, 1.5 - .9 * trend)
            elif profile == 4:
                multiplier = 1 + .55 * np.sin(2 * np.pi * day.dayofyear / 365.25)
            mean = base * multiplier
            quantity = int(rng.negative_binomial(25, 25 / (25 + mean)))
            if profile == 5:
                quantity = 0 if rng.random() < .72 else quantity * 3
            rows.append({'date': str(day.date()), 'warehouse_id': pair.warehouseId,
                         'product_id': pair.productId, 'export_quantity': quantity,
                         'data_quality': 'COMPLETE', 'data_source': 'SIMULATED'})
    return pd.DataFrame(rows)


def execute_job(job_id, request, cutoff):
    global ACTIVE_JOB
    folder = ROOT / 'runs' / job_id
    status = {'jobId': job_id, 'status': 'RUNNING', 'dataSource': 'SIMULATED', 'dataCutoff': cutoff}
    try:
        write_json(folder / 'job.json', status)
        data = simulate(request, cutoff)
        csv_path = folder / 'daily.csv'
        data.to_csv(csv_path, index=False)
        model = active_model()
        if model and model.get('seed') != request.seed and not model_due(model, utc_now()):
            raise ValueError('Changing the simulation seed requires waiting for the next training cycle.')
        if model_due(model, utc_now()):
            train(csv_path, folder / 'artifacts')
            model = {'runId': job_id, 'trainedAt': utc_now().isoformat(), 'seed': request.seed,
                     'formatVersion': FORMAT_VERSION, 'lightgbmVersion': lightgbm.__version__}
            status['modelAction'] = 'TRAINED'
        else:
            forecast(csv_path, ROOT / 'runs' / model['runId'] / 'artifacts/model.joblib',
                     folder / 'artifacts/forecast_14_days.csv')
            status['modelAction'] = 'REUSED'
        predictions = pd.read_csv(folder / 'artifacts' / 'forecast_14_days.csv',
                               dtype={'warehouse_id': str, 'product_id': str})
        metrics = read_json(ROOT / 'runs' / model['runId'] / 'artifacts/metrics.json')
        recent = data.groupby(['warehouse_id', 'product_id'], sort=False).tail(28)
        result = {'runId': job_id, 'dataSource': 'SIMULATED', 'dataCutoff': cutoff,
                  'forecastDate': str((pd.Timestamp(cutoff) + pd.Timedelta(days=1)).date()),
                  'pairs': [pair.model_dump() for pair in request.pairs],
                  'metrics': metrics, 'history': recent.to_dict(orient='records'),
                  'forecasts': predictions.to_dict(orient='records'),
                  'modelTrainedAt': model['trainedAt'],
                  'nextTrainingAt': (datetime.fromisoformat(model['trainedAt']) + RETRAIN_INTERVAL).isoformat(),
                  'modelAction': status['modelAction']}
        write_json(folder / 'result.json', result)
        # Publish pointer last; failed training never replaces the prior good run.
        write_json(ROOT / 'active.json', {'runId': job_id, 'model': model})
        status['status'] = 'SUCCEEDED'
    except Exception:
        LOGGER.exception('Demand job failed: %s', job_id)
        status.update(status='FAILED', message='Forecast job failed. Check demand service logs; previous results are retained.')
    finally:
        write_json(folder / 'job.json', status)
        with LOCK:
            ACTIVE_JOB = None


@app.get('/health')
def health():
    return {'status': 'UP', 'mode': 'SIMULATED', 'maxPairs': 64}


@app.post('/jobs/demo', status_code=202)
def demo(request: DemoRequest):
    global ACTIVE_JOB
    with LOCK:
        if ACTIVE_JOB is not None:
            active_folder = ROOT / 'runs' / ACTIVE_JOB
            request_path = active_folder / 'request.json'
            if request_path.exists() and read_json(request_path) == request.model_dump():
                return read_json(active_folder / 'job.json')
            raise HTTPException(409, 'A training job is already running')
        job_id = str(uuid.uuid4())
        cutoff = str(datetime.now(timezone(timedelta(hours=7))).date() - timedelta(days=1))
        status = {'jobId': job_id, 'status': 'QUEUED', 'dataSource': 'SIMULATED', 'dataCutoff': cutoff}
        write_json(ROOT / 'runs' / job_id / 'job.json', status)
        write_json(ROOT / 'runs' / job_id / 'request.json', request.model_dump())
        ACTIVE_JOB = job_id
        EXECUTOR.submit(execute_job, job_id, request, cutoff)
    return status


@app.get('/jobs/{job_id}')
def job(job_id: uuid.UUID):
    path = ROOT / 'runs' / str(job_id) / 'job.json'
    if not path.exists():
        raise HTTPException(404, 'Job not found')
    result = read_json(path)
    if result['status'] in ['QUEUED', 'RUNNING'] and ACTIVE_JOB != str(job_id):
        result.update(status='INTERRUPTED', message='Service restarted; submit a new job.')
    return result


@app.get('/forecasts/latest')
def latest():
    pointer = ROOT / 'active.json'
    if not pointer.exists():
        return {'dataSource': 'SIMULATED', 'forecasts': [], 'history': [], 'pairs': []}
    result = read_json(ROOT / 'runs' / read_json(pointer)['runId'] / 'result.json')
    for row in result.get('forecasts', []):
        row['predicted_quantity'] = round_quantity(row.get('predicted_quantity'))
    return result
