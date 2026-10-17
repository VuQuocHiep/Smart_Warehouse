import gzip
import hashlib
import json
from pathlib import Path
import struct

import joblib
import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / 'models/smoke_risk_model.joblib'
TARGET = ROOT.parent / 'backend/iot/src/main/resources/ai/smoke-risk.rf.gz'
FIXTURES = ROOT.parent / 'backend/iot/src/test/resources/ai/parity.csv'


def main():
    artifact = joblib.load(SOURCE)
    pipeline = artifact['model']
    forest = pipeline.named_steps['classifier']
    assert artifact['feature_names'] == ['temperature', 'humidity', 'gas']
    assert list(forest.classes_) == [0, 1, 2, 3]
    assert artifact['metadata']['gas_proxy_column'] == 'TVOC[ppb]'
    version = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(TARGET, 'wb') as out:
        out.write(b'SWRF')
        out.write(struct.pack('>i', 1))
        out.write(version.encode('ascii'))
        out.write(struct.pack('>3d', *pipeline.named_steps['imputer'].statistics_))
        out.write(struct.pack('>i', len(forest.estimators_)))
        for estimator in forest.estimators_:
            tree = estimator.tree_
            out.write(struct.pack('>i', tree.node_count))
            for node in range(tree.node_count):
                probability = tree.value[node, 0].astype(float)
                probability /= probability.sum()
                out.write(struct.pack('>iiid4d', int(tree.children_left[node]),
                                      int(tree.children_right[node]), int(tree.feature[node]),
                                      tree.threshold[node], *probability))

    data = pd.read_csv(ROOT / 'data/smoke_detection_iot.csv')
    frames = []
    for alarm in [0, 1]:
        frames.append(data[data['Fire Alarm'] == alarm].sample(80, random_state=42))
    samples = pd.concat(frames)[['Temperature[C]', 'Humidity[%]', 'TVOC[ppb]']].copy()
    samples.columns = artifact['feature_names']
    edges = pd.DataFrame([[31.6, 69.1, 339], [20, 50, np.nan],
                          [30, 50, 1800], [35, 60, 2500], [40, 80, 3200]],
                         columns=artifact['feature_names'])
    samples = pd.concat([samples, edges], ignore_index=True)
    probabilities = pipeline.predict_proba(samples)
    FIXTURES.parent.mkdir(parents=True, exist_ok=True)
    with FIXTURES.open('w', encoding='utf-8') as out:
        for values, probability in zip(samples.to_numpy(), probabilities):
            level = int(np.argmax(probability))
            out.write(','.join(str(v) for v in [*values, level, probability[level]]) + '\n')
    manifest = {'sha256': version, 'trees': len(forest.estimators_),
                'features': artifact['feature_names'], 'gas_unit': 'TVOC[ppb]',
                'parity_samples': len(samples), 'classes_tested': sorted(set(pipeline.predict(samples).tolist()))}
    (TARGET.parent / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(json.dumps(manifest))
    print(f'Exported {TARGET.stat().st_size} bytes')


if __name__ == '__main__':
    main()
