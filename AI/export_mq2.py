import argparse
import hashlib
import json
from pathlib import Path

import joblib
import pandas as pd
from forest_export import write_model, write_parity

ROOT = Path(__file__).resolve().parent


def export(source):
    source = Path(source)
    artifact = joblib.load(source)
    if artifact['metadata'].get('input_kind') != 'MQ2_ADC' or not artifact.get('evaluation'):
        raise ValueError('Can model MQ-2 da danh gia tren cac phien do doc lap')
    samples = pd.read_csv(source.with_suffix('.test.csv'))[artifact['feature_names']]
    if samples.empty:
        raise ValueError('Tap test rong')
    target = ROOT.parent / 'backend/iot/src/main/resources/ai/mq2-risk.rf.gz'
    manifest = write_model(artifact, hashlib.sha256(source.read_bytes()).hexdigest(), target)
    write_parity(artifact, samples, ROOT.parent / 'backend/iot/src/test/resources/ai/mq2-parity.csv')
    manifest['parity_samples'] = len(samples)
    target.with_name('mq2-risk.manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, default=ROOT / 'models/mq2_risk_model.joblib')
    args = parser.parse_args()
    export(args.source)
