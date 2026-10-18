import gzip
import hashlib
import json
from pathlib import Path
import struct

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.metrics import balanced_accuracy_score, classification_report, confusion_matrix
from sklearn.pipeline import Pipeline

ROOT = Path(__file__).resolve().parent
FEATURES = ['temperature', 'humidity']


def main():
    source = ROOT / 'data/smoke_detection_iot.csv'
    data = pd.read_csv(source).sort_values('UTC').reset_index(drop=True)
    data['session'] = data.UTC.diff().gt(60).cumsum()
    data = data.rename(columns={'Temperature[C]': 'temperature', 'Humidity[%]': 'humidity'})
    teacher_path = ROOT / 'models/smoke_risk_model.joblib'
    teacher = joblib.load(teacher_path)
    teacher_features = data[['temperature', 'humidity', 'TVOC[ppb]']].rename(columns={'TVOC[ppb]': 'gas'})
    data['risk_target'] = teacher['model'].predict(teacher_features)
    groups = sorted(data.session.unique())
    if len(groups) < 5 or set(data['Fire Alarm']) != {0, 1}:
        raise ValueError('Can it nhat 5 phien do va nhan Fire Alarm 0/1')
    parts = {'train': data[data.session.isin(groups[:2])],
             'validation': data[data.session.isin(groups[2:3])],
             'test': data[data.session.isin(groups[3:])]}
    pipeline = Pipeline([('imputer', SimpleImputer(strategy='median')),
                         ('classifier', RandomForestClassifier(n_estimators=200, max_depth=18,
                            min_samples_leaf=5, class_weight='balanced', random_state=42, n_jobs=-1))])
    pipeline.fit(parts['train'][FEATURES], parts['train']['risk_target'])
    if list(pipeline.named_steps['classifier'].classes_) != [0, 1, 2, 3]:
        raise ValueError('Training sessions do not contain all four teacher classes')
    report = {'features': FEATURES, 'target': 'Four-class pseudo-labels predicted by existing smoke_risk_model.joblib',
              'label_source': 'MODEL_GENERATED_NOT_MEASURED_SEVERITY',
              'teacher_sha256': hashlib.sha256(teacher_path.read_bytes()).hexdigest(),
              'ai_level_policy': 'student model predicts 0/1/2/3; final level is max(student level, gas threshold level)',
              'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'split': 'chronological recording sessions separated by UTC gaps >60 seconds',
              'limitations': 'Teacher-generated labels, not independently verified severity. Teacher was trained on this dataset. Heldout scores measure imitation agreement, not real-world hazard accuracy. Student has no gas input.',
              'partitions': {}}
    for name, frame in parts.items():
        y = frame['risk_target']
        prediction = pipeline.predict(frame[FEATURES])
        report['partitions'][name] = {'rows': len(frame), 'sessions': frame.session.unique().tolist(),
            'teacher_agreement_balanced_accuracy': float(balanced_accuracy_score(y, prediction)),
            'target_counts': y.value_counts().to_dict(),
            'confusion_matrix': confusion_matrix(y, prediction, labels=[0, 1, 2, 3]).tolist(),
            'classification_report': classification_report(y, prediction, labels=[0, 1, 2, 3], zero_division=0, output_dict=True)}
    artifact = {'model': pipeline, 'feature_names': FEATURES, 'metadata': report}
    path = ROOT / 'models/temperature_humidity_model.joblib'
    path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact, path)
    version = hashlib.sha256(path.read_bytes()).hexdigest()
    target = ROOT.parent / 'backend/iot/src/main/resources/ai/temperature-humidity.rf.gz'
    target.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(target, 'wb') as out:
        out.write(b'SWTH')
        out.write(struct.pack('>i', 2))
        out.write(version.encode('ascii'))
        out.write(struct.pack('>2d', *pipeline.named_steps['imputer'].statistics_))
        forest = pipeline.named_steps['classifier']
        assert list(forest.classes_) == [0, 1, 2, 3]
        out.write(struct.pack('>i', len(forest.estimators_)))
        for estimator in forest.estimators_:
            tree = estimator.tree_
            out.write(struct.pack('>i', tree.node_count))
            for n in range(tree.node_count):
                probabilities = tree.value[n, 0] / tree.value[n, 0].sum()
                out.write(struct.pack('>iiid4d', int(tree.children_left[n]), int(tree.children_right[n]),
                    int(tree.feature[n]), tree.threshold[n], *probabilities))
    fixture = ROOT.parent / 'backend/iot/src/test/resources/ai/temperature-humidity-parity.csv'
    samples = pd.concat([frame.groupby('risk_target').sample(40, random_state=42) for frame in [parts['train'], parts['test']]])
    samples = pd.concat([samples[FEATURES], pd.DataFrame([[32, 66], [32.1, 66.4], [20, 50], [40, 80], [100, 50]], columns=FEATURES)])
    probabilities = pipeline.predict_proba(samples)
    fixture.parent.mkdir(parents=True, exist_ok=True)
    with fixture.open('w', encoding='utf-8') as out:
        for values, probability in zip(samples.to_numpy(), probabilities):
            best = int(np.argmax(probability))
            out.write(','.join(str(v) for v in [*values, best, probability[best]]) + '\n')
    report['model_sha256'] = version
    report['parity_samples'] = len(samples)
    (ROOT / 'reports/temperature_humidity_metrics.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps({name: {'rows': value['rows'], 'teacher_agreement_balanced_accuracy': value['teacher_agreement_balanced_accuracy']}
                      for name, value in report['partitions'].items()}, indent=2))
    print(f'Exported {len(forest.estimators_)} trees, {len(samples)} parity samples; only {FEATURES}')


if __name__ == '__main__':
    main()
