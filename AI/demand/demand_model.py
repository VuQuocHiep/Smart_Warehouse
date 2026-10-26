"""Offline daily demand training, chronological evaluation and forecasting."""
import argparse
import hashlib
import json
import math
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
import lightgbm
from lightgbm import LGBMRegressor
from sklearn.preprocessing import OrdinalEncoder
from threadpoolctl import threadpool_limits

KEYS = ['warehouse_id', 'product_id']
HORIZON = 14
NUMERIC = ['latest', 'lag_7', 'lag_14', 'lag_28', 'mean_7', 'mean_28',
           'std_7', 'std_28', 'zero_7', 'zero_28', 'horizon', 'weekday',
           'month', 'year_sin', 'year_cos', 'weekday_mean_28']
FORMAT_VERSION = 3
BASELINES = ['mean_28', 'seasonal_naive', 'weekday_mean_28']
ML_METHODS = ['lightgbm_poisson', 'lightgbm_l1']
METHODS = BASELINES + ML_METHODS


def round_quantity(value):
    """Round nonnegative item counts to nearest integer, with .5 rounding up."""
    return None if value is None else math.floor(max(0.0, float(value)) + 0.5)


def load_data(path):
    frame = pd.read_csv(path, dtype={'warehouse_id': str, 'product_id': str})
    required = ['date', *KEYS, 'export_quantity', 'data_quality', 'data_source']
    if set(required) - set(frame.columns):
        raise ValueError(f'Missing columns: {sorted(set(required) - set(frame.columns))}')
    frame = frame[required].copy()  # Never expose future inventory columns to ML.
    if frame[required].isna().any().any() or frame.empty:
        raise ValueError('Input is empty or contains null values.')
    if any(frame[key].str.strip().eq('').any() for key in KEYS):
        raise ValueError('Warehouse/SKU identifiers must not be empty.')
    frame['date'] = pd.to_datetime(frame['date'], format='%Y-%m-%d', errors='raise')
    frame['export_quantity'] = pd.to_numeric(frame['export_quantity'], errors='raise')
    quantities = frame['export_quantity'].to_numpy(dtype=float)
    if not np.isfinite(quantities).all() or (quantities < 0).any() or (quantities % 1 != 0).any():
        raise ValueError('export_quantity must be a finite nonnegative integer.')
    if frame.duplicated(KEYS + ['date']).any():
        raise ValueError('Duplicate date/warehouse/SKU rows.')
    if not frame['data_quality'].eq('COMPLETE').all():
        raise ValueError('This baseline version requires COMPLETE data; do not impute missing/stockout days as zero.')
    if frame.data_source.nunique() != 1 or not frame.data_source.isin(['SIMULATED', 'REAL']).all():
        raise ValueError('Exactly one explicit data_source (SIMULATED or REAL) is required.')
    frame = frame.sort_values(KEYS + ['date']).reset_index(drop=True)
    for key, group in frame.groupby(KEYS):
        if not group['date'].diff().dropna().eq(pd.Timedelta(days=1)).all():
            raise ValueError(f'Missing calendar days in {key}; supply verified zero days explicitly.')
    return frame


def samples(frame, future=False, horizon=HORIZON):
    """Every origin is end-of-day; only observations <= origin enter features."""
    parts = []
    for key, group in frame.groupby(KEYS, sort=True):
        group = group.sort_values('date').reset_index(drop=True)
        y = group['export_quantity'].astype(float)
        base = pd.DataFrame({'origin': group['date'], 'latest': y})
        for lag in [7, 14, 28]:
            base[f'lag_{lag}'] = y.shift(lag)
        for window in [7, 28]:
            base[f'mean_{window}'] = y.rolling(window).mean()
            base[f'std_{window}'] = y.rolling(window).std(ddof=0)
            base[f'zero_{window}'] = y.eq(0).rolling(window).mean()
        for name, value in zip(KEYS, key):
            base[name] = value
        if future:
            indices = [len(group) - 1] if len(group) >= 29 else []
        else:
            # Match the 90-observation minimum before an ML forecast origin.
            indices = np.arange(89, len(group))
        for h in range(1, horizon + 1):
            block = base.iloc[indices].copy()
            block['horizon'] = h
            block['target_date'] = block['origin'] + pd.Timedelta(days=h)
            block['weekday'] = block['target_date'].dt.dayofweek
            block['month'] = block['target_date'].dt.month
            angle = 2 * np.pi * block['target_date'].dt.dayofyear / 365.25
            block['year_sin'], block['year_cos'] = np.sin(angle), np.cos(angle)
            # Same weekday in the last fully observed week; repeat for week 2.
            block['seasonal_naive'] = y.shift((7 - h % 7) % 7).iloc[indices].to_numpy()
            offset = (7 - h % 7) % 7
            block['weekday_mean_28'] = sum(y.shift(offset + 7 * week) for week in range(4)).iloc[indices].to_numpy() / 4
            if not future:
                block['actual'] = y.shift(-h).iloc[indices].to_numpy()
                block = block.dropna(subset=['actual'])
            parts.append(block)
    if not parts:
        return pd.DataFrame()
    return pd.concat(parts, ignore_index=True)


def matrix(frame, encoder):
    return np.column_stack([encoder.transform(frame[KEYS]), frame[NUMERIC].to_numpy(dtype=float)])


def fit(frame):
    if frame.empty or not frame['actual'].gt(0).any():
        raise ValueError('ML training requires eligible history and at least one positive target.')
    encoder = OrdinalEncoder(handle_unknown='use_encoded_value', unknown_value=np.nan)
    encoder.fit(frame[KEYS])
    estimators = {}
    for method, objective in [('lightgbm_poisson', 'poisson'), ('lightgbm_l1', 'regression_l1')]:
        estimator = LGBMRegressor(objective=objective, learning_rate=0.05,
                                  n_estimators=200, num_leaves=15, reg_lambda=1.0,
                                  random_state=42, n_jobs=4, verbosity=-1,
                                  deterministic=True, force_col_wise=True)
        estimator.fit(matrix(frame, encoder), frame['actual'], categorical_feature=[0, 1])
        estimators[method] = estimator
    return {'encoder': encoder, 'estimators': estimators}


def predict_ml(bundle, frame, method='lightgbm_poisson'):
    with threadpool_limits(limits=4):
        estimator = bundle['estimators'][method]
        features = pd.DataFrame(matrix(frame, bundle['encoder']), columns=estimator.feature_name_)
        predictions = estimator.predict(features)
    if not np.isfinite(predictions).all():
        raise ValueError('Model produced non-finite predictions.')
    return np.maximum(0.0, predictions)


def metrics(actual, predicted):
    actual, predicted = np.asarray(actual), np.asarray(predicted)
    error = predicted - actual
    denominator = float(np.abs(actual).sum())
    return {'mae': float(np.abs(error).mean()),
            'wape_percent': float(np.abs(error).sum() / denominator * 100) if denominator else None,
            'bias': float(error.mean()), 'samples': len(actual)}


def evaluate(bundle, frame):
    scored = frame[KEYS + ['origin', 'target_date', 'horizon', 'actual']].copy()
    for method in BASELINES:
        scored[method] = frame[method].to_numpy()
    for method in ML_METHODS:
        scored[method] = predict_ml(bundle, frame, method)
    return scored, score_report(scored)


def score_report(scored):
    methods = METHODS + (['selected'] if 'selected' in scored else [])
    report = {method: metrics(scored.actual, scored[method]) for method in methods}
    totals = {}
    for period in [7, 14]:
        grouped = scored[scored.horizon <= period].groupby(KEYS + ['origin'])
        sums = grouped[['actual', *methods]].sum()
        sums = sums[grouped.size().eq(period)]
        totals[str(period)] = {method: metrics(sums.actual, sums[method]) for method in methods}
    return {'daily': report, 'horizon_totals': totals}


def select_methods(scored):
    selections = {}
    for key, group in scored.groupby(KEYS):
        errors = {method: metrics(group.actual, group[method])['mae'] for method in METHODS}
        ml = min(ML_METHODS, key=errors.get)
        selections[key] = ml
    return selections


def apply_selection(scored, selections):
    scored = scored.copy()
    scored['selected'] = np.nan
    for key, group in scored.groupby(KEYS):
        scored.loc[group.index, 'selected'] = group[selections[key]]
    return scored


def dump_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False, allow_nan=False) + '\n', encoding='utf-8')


def train(csv_path, output):
    data = load_data(csv_path)
    start, end = data.date.min(), data.date.max()
    validation_start = start + pd.DateOffset(months=18)
    test_start = start + pd.DateOffset(months=21)
    if end < test_start + pd.Timedelta(days=28):
        raise ValueError('Offline benchmark needs at least 22 months of data (18 train / 3 validation / >=1 test).')
    history = samples(data)
    train_set = history[history.target_date < validation_start]
    # Origins after the fitting cutoff, complete 14-day forecasts only.
    validation = history[(history.origin >= validation_start - pd.Timedelta(days=1)) &
                         (history.origin + pd.Timedelta(days=14) < test_start)]
    test = history[(history.origin >= test_start - pd.Timedelta(days=1)) &
                   (history.origin + pd.Timedelta(days=14) <= end)]
    if any(part.empty for part in [train_set, validation, test]):
        raise ValueError('Not enough complete history for all evaluation periods.')
    print(f'Training on {len(train_set):,} samples; validation {len(validation):,}; test {len(test):,}.', flush=True)
    validation_scores, folds = [], []
    for month in range(3):
        cutoff = validation_start + pd.DateOffset(months=month)
        next_cutoff = validation_start + pd.DateOffset(months=month + 1)
        window = validation[(validation.origin >= cutoff - pd.Timedelta(days=1)) &
                            (validation.origin + pd.Timedelta(days=14) < next_cutoff)]
        if window.empty:
            raise ValueError('Not enough history for monthly validation folds.')
        candidate = fit(history[history.target_date < cutoff])
        fold_scores, fold_report = evaluate(candidate, window)
        validation_scores.append(fold_scores)
        folds.append({'training_targets_before': str(cutoff.date()), 'metrics': fold_report})
    validation_scores = pd.concat(validation_scores, ignore_index=True)
    selections = select_methods(validation_scores)
    validation_report = score_report(apply_selection(validation_scores, selections))
    selected = 'per_series'
    print('Validation selected methods per warehouse/SKU. Refitting for untouched test period.', flush=True)
    test_model = fit(history[history.target_date < test_start])
    scored, test_report = evaluate(test_model, test)
    scored = apply_selection(scored, selections)
    test_report = score_report(scored)
    by_series = []
    for key, group in scored.groupby(KEYS):
        for method in METHODS + ['selected']:
            by_series.append(dict(zip(KEYS, key)) | {'method': method} | metrics(group.actual, group[method]))
    print('Fitting deployment model on all observed targets.', flush=True)
    final_model = fit(history)
    digest = hashlib.sha256(Path(csv_path).read_bytes()).hexdigest()
    version = f'demand-{end:%Y%m%d}-{digest[:10]}'
    source = str(data.data_source.iloc[0])
    bundle = final_model | {
        'trained_at': datetime.now(timezone.utc).isoformat(),
        'format_version': FORMAT_VERSION, 'model_version': version, 'selected_method': selected,
        'selected_methods': selections, 'lightgbm_version': lightgbm.__version__,
        'default_method': min(ML_METHODS, key=lambda name: validation_report['daily'][name]['mae']),
        'data_cutoff': str(end.date()), 'data_source': source, 'dataset_sha256': digest,
        'sklearn_version': sklearn.__version__, 'features': NUMERIC,
        'known_pairs': sorted(set(map(tuple, history[KEYS].to_numpy())))}
    report = {'model_version': version, 'data_source': source, 'dataset_sha256': digest,
              'selected_method': selected, 'selection_threshold': 'Lowest validation MAE among LightGBM models only, per series',
              'selected_methods': [dict(zip(KEYS, key)) | {'method': method} for key, method in selections.items()],
              'validation_folds': folds,
              'split': {'start': str(start.date()), 'validation_start': str(validation_start.date()),
                        'test_start': str(test_start.date()), 'end': str(end.date())},
              'validation': validation_report, 'test': test_report,
              'evaluation_protocol': 'Three expanding monthly validation folds; per-series selection; fixed model on untouched test; complete 14-day horizons.',
              'limitations': ['Synthetic results do not establish real-world accuracy.',
                             'Forecasts observed exports, not unmet latent demand.',
                             'Final deployment artifact is refitted after test and has no separate held-out score.']}
    output.mkdir(parents=True, exist_ok=True)
    temporary = output / 'model.tmp.joblib'
    joblib.dump(bundle, temporary)
    loaded = joblib.load(temporary)
    assert np.allclose(predict_ml(bundle, test.head(10)), predict_ml(loaded, test.head(10)))
    temporary.replace(output / 'model.joblib')
    dump_json(output / 'metrics.json', report)
    pd.DataFrame(by_series).to_csv(output / 'metrics_by_series.csv', index=False)
    scored.to_csv(output / 'test_predictions.csv', index=False)
    lines = ['# Demand forecast evaluation', '', f'- Source: {source}', f'- Selected: `{selected}`',
             f'- Data cutoff: {end.date()}', '- Selection uses validation only; test is reported without retuning.', '',
             '| Method | Validation MAE | Test MAE | Test WAPE (%) |', '|---|---:|---:|---:|']
    for name in METHODS + ['selected']:
        test_metric = test_report['daily'][name]
        wape = test_metric['wape_percent']
        lines.append(f"| {name} | {validation_report['daily'][name]['mae']:.3f} | {test_metric['mae']:.3f} | {wape:.2f} |" if wape is not None
                     else f"| {name} | {validation_report['daily'][name]['mae']:.3f} | {test_metric['mae']:.3f} | N/A |")
    lines += ['', 'MAE units: items per warehouse/SKU/day/horizon. Each target can appear at multiple origins.',
              'WAPE is an error ratio, not an accuracy percentage.',
              'Test uses fixed model weights and rolling observed history, not weekly retraining.',
              'Final deployment model is fitted on all observations after evaluation.',
              'Synthetic data cannot establish real-world forecast accuracy.', '']
    (output / 'evaluation.md').write_text('\n'.join(lines), encoding='utf-8')
    forecast(csv_path, output / 'model.joblib', output / 'forecast_14_days.csv')
    print(json.dumps({'selected_method': selected, 'test': test_report['daily']['selected']}, indent=2), flush=True)


def forecast(csv_path, model_path, output, as_of=None, horizon=14):
    if not 1 <= horizon <= HORIZON:
        raise ValueError('horizon must be between 1 and 14.')
    data = load_data(csv_path)
    # Only load artifacts produced by this project from a trusted location.
    bundle = joblib.load(model_path)
    if (bundle['format_version'] != FORMAT_VERSION or bundle['sklearn_version'] != sklearn.__version__
            or bundle.get('lightgbm_version') != lightgbm.__version__):
        raise ValueError('Unsupported artifact format or library version; retrain in this environment.')
    input_source = str(data.data_source.iloc[0])
    if input_source != bundle['data_source']:
        raise ValueError('Dataset source does not match the artifact; train a separate model for real data.')
    cutoff = pd.Timestamp(as_of) if as_of else data.date.max()
    if cutoff < pd.Timestamp(bundle['data_cutoff']):
        raise ValueError('Cannot backtest using an artifact trained after the requested cutoff.')
    data = data[data.date <= cutoff]
    output_rows = []
    for key, group in data.groupby(KEYS):
        method = bundle.get('selected_methods', {}).get(key, bundle.get('default_method', 'lightgbm_poisson'))
        if method not in ML_METHODS:
            raise ValueError('Artifact selects a non-LightGBM method; retrain.')
        status = 'OK'
        prediction = None
        if group.date.max() != cutoff:
            status, method = 'STALE_DATA', 'none'
        elif len(group) < 90:
            status, method = 'INSUFFICIENT_DATA', 'none'
        else:
            prediction = predict_ml(bundle, samples(group, future=True, horizon=horizon), method)
        for index in range(horizon):
            output_rows.append(dict(zip(KEYS, key)) | {
                'forecast_date': str((cutoff + pd.Timedelta(days=1)).date()),
                'target_date': str((cutoff + pd.Timedelta(days=index + 1)).date()),
                'horizon': index + 1, 'predicted_quantity': round_quantity(prediction[index]) if prediction is not None else None,
                'method': method, 'status': status, 'model_version': bundle['model_version'],
                'data_source': bundle['data_source'], 'data_cutoff': str(cutoff.date())})
    if not output_rows:
        raise ValueError('No observations at or before cutoff.')
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    result = pd.DataFrame(output_rows)
    temporary = output.with_suffix('.tmp.csv')
    result.to_csv(temporary, index=False)
    temporary.replace(output)
    print(f'Forecast saved: {output} ({len(result)} rows)', flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    training = commands.add_parser('train')
    training.add_argument('--csv', type=Path, required=True)
    training.add_argument('--output-dir', type=Path, default=Path('runtime/offline'))
    predicting = commands.add_parser('predict')
    predicting.add_argument('--csv', type=Path, required=True)
    predicting.add_argument('--model', type=Path, required=True)
    predicting.add_argument('--output', type=Path, required=True)
    predicting.add_argument('--as-of', help='Last fully observed date, YYYY-MM-DD; default latest CSV date.')
    predicting.add_argument('--horizon', type=int, default=14)
    args = parser.parse_args()
    try:
        if args.command == 'train':
            train(args.csv, args.output_dir)
        else:
            forecast(args.csv, args.model, args.output, args.as_of, args.horizon)
    except (ValueError, FileNotFoundError) as exc:
        parser.exit(2, f'Error: {exc}\n')


if __name__ == '__main__':
    main()
