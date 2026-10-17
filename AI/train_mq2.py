import hashlib
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.metrics import balanced_accuracy_score, classification_report, confusion_matrix
from sklearn.pipeline import Pipeline

FEATURES = ['temperature', 'humidity', 'gas']
LABELS = {'NORMAL': 0, 'WARNING': 1, 'DANGER': 2, 'EMERGENCY': 3}
ACTIONS = {0: 'GREEN_LED', 1: 'RED_LED', 2: 'RED_LED_FAN', 3: 'RED_LED_FAN_BUZZER'}


def load_dataset(path):
    data = pd.read_csv(path, keep_default_na=False)
    required = {'temperature', 'humidity', 'gasAdc', 'session', 'label', 'labelNote', 'deviceCode'}
    if not required.issubset(data.columns):
        raise ValueError(f'CSV thieu cot: {sorted(required - set(data.columns))}')
    unknown = set(data.label) - set(LABELS) - {'UNLABELED'}
    if unknown:
        raise ValueError(f'Nhan khong hop le: {sorted(unknown)}')
    ignored = int((data.label == 'UNLABELED').sum())
    data = data[data.label != 'UNLABELED'].copy()
    if data.empty:
        raise ValueError('Chua co du lieu MQ-2 thuc te da gan nhan')
    if (data[['session', 'labelNote', 'deviceCode']].apply(lambda col: col.str.strip()) == '').any().any():
        raise ValueError('Session, deviceCode va co so gan nhan khong duoc trong')
    if data.deviceCode.nunique() != 1:
        raise ValueError('Train rieng cho mot deviceCode; ADC phu thuoc cam bien va mach do')
    for column in ['temperature', 'humidity', 'gasAdc']:
        data[column] = pd.to_numeric(data[column], errors='raise')
    if not np.isfinite(data[['temperature', 'humidity', 'gasAdc']].to_numpy()).all():
        raise ValueError('Du lieu co NaN/Infinity')
    if not (data.temperature.between(-100, 200) & data.humidity.between(0, 100)
            & data.gasAdc.between(0, 4095) & (data.gasAdc % 1 == 0)).all():
        raise ValueError('Du lieu nam ngoai mien ADC 12-bit/DHT')
    if (data.groupby('session').label.nunique() != 1).any():
        raise ValueError('Moi session chi co mot nhan; tach session khi doi dieu kien')
    data['target'] = data.label.map(LABELS)
    if 0 not in set(data.target) or len(set(data.target)) < 2:
        raise ValueError('Can NORMAL va it nhat mot lop canh bao co nhan doc lap')
    data['gas'] = data.gasAdc
    return data, ignored


def split_sessions(data):
    partitions = {'train': [], 'validation': [], 'test': []}
    rng = np.random.default_rng(42)
    for label, rows in data.groupby('label'):
        sessions = sorted(rows.session.unique())
        if len(sessions) < 3:
            raise ValueError(f'{label}: can it nhat 3 phien do doc lap de tach train/validation/test')
        rng.shuffle(sessions)
        heldout = max(1, len(sessions) // 5)
        partitions['test'].extend(sessions[:heldout])
        partitions['validation'].extend(sessions[heldout:2 * heldout])
        partitions['train'].extend(sessions[2 * heldout:])
    return {name: data[data.session.isin(sessions)].copy() for name, sessions in partitions.items()}


def train_dataset(source, target):
    source, target = Path(source), Path(target)
    data, ignored = load_dataset(source)
    partitions = split_sessions(data)
    pipeline = Pipeline([('imputer', SimpleImputer(strategy='median')),
                         ('classifier', RandomForestClassifier(n_estimators=300, min_samples_leaf=2,
                           class_weight='balanced', random_state=42, n_jobs=-1))])
    train = partitions['train']
    pipeline.fit(train[FEATURES], train.target)
    classes = sorted(data.target.unique().tolist())
    report = {'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'device_codes': sorted(data.deviceCode.unique().tolist()), 'input_kind': 'MQ2_ADC',
              'ignored_unlabeled_rows': ignored, 'classes': classes,
              'label_counts': data.label.value_counts().to_dict(), 'partitions': {}}
    for name, rows in partitions.items():
        predictions = pipeline.predict(rows[FEATURES])
        report['partitions'][name] = {'rows': len(rows), 'sessions': sorted(rows.session.unique().tolist()),
            'balanced_accuracy': float(balanced_accuracy_score(rows.target, predictions)),
            'confusion_matrix': confusion_matrix(rows.target, predictions, labels=classes).tolist(),
            'classification_report': classification_report(rows.target, predictions, labels=classes,
                target_names=[list(LABELS)[c] for c in classes], output_dict=True, zero_division=0)}
    metadata = {'input_kind': 'MQ2_ADC', 'gas_proxy_column': 'gasAdc', 'adc_bits': 12,
                'device_codes': report['device_codes'], 'source_sha256': report['source_sha256'],
                'feature_names': FEATURES, 'split_method': 'independent_sessions_per_class'}
    artifact = {'model': pipeline, 'feature_names': FEATURES,
                'label_names': {value: key for key, value in LABELS.items()}, 'action_names': ACTIONS,
                'metadata': metadata, 'evaluation': report}
    target.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact, target)
    target.with_suffix('.metrics.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    partitions['test'].to_csv(target.with_suffix('.test.csv'), index=False)
    return report
