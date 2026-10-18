import argparse
import contextlib
import csv
import hashlib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline

from forest_export import write_model, write_parity
from mq2 import FIELDS, collect, label_session, parse_sample, rows_from
from train_mq2 import load_dataset, split_sessions, train_dataset


def test_rows():
    return [dict(timestamp='2026-10-17T00:00:00Z', deviceCode='TEST_ONLY_MQ2', temperature=26,
                 humidity=55, gasAdc=(200 if label == 'NORMAL' else 1200) + sample,
                 uptimeMs=sample * 5000, session=f'{label}_{session}', label=label,
                 labelNote='Synthetic unit test only, not hardware observations')
            for label in ['NORMAL', 'DANGER'] for session in range(3) for sample in range(20)]


def save_csv(path, rows):
    with path.open('w', newline='', encoding='utf-8') as out:
        writer = csv.DictWriter(out, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)


def write_java_fixtures():
    frame = pd.DataFrame(test_rows()).rename(columns={'gasAdc': 'gas'})
    features = ['temperature', 'humidity', 'gas']
    pipeline = Pipeline([('imputer', SimpleImputer(strategy='median')),
                         ('classifier', RandomForestClassifier(n_estimators=7, bootstrap=False,
                            max_features=None, random_state=42))])
    pipeline.fit(frame[features], frame.label.map({'NORMAL': 0, 'DANGER': 2}))
    artifact = {'model': pipeline, 'feature_names': features,
                'metadata': {'input_kind': 'MQ2_ADC', 'device_codes': ['TEST_ONLY_MQ2']}}
    folder = Path(__file__).resolve().parent.parent / 'backend/iot/src/test/resources/ai'
    write_model(artifact, hashlib.sha256(b'synthetic test only').hexdigest(), folder / 'mq2-test.rf.gz')
    samples = pd.DataFrame([[26, 55, gas] for gas in [0, 200, 215, 339, 700, 1199, 1219, 4095]], columns=features)
    write_parity(artifact, samples, folder / 'mq2-test-parity.csv')


class Mq2Tests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.source = self.folder / 'readings.csv'
        save_csv(self.source, test_rows())

    def test_parses_only_sample_lines_and_validates_adc(self):
        self.assertIsNone(parse_sample('HTTP status: 200'))
        for value in [-1, 4096, 20.5, float('nan')]:
            with self.assertRaises(ValueError):
                parse_sample('MQ2_SAMPLE:' + json.dumps({'deviceCode': 'test', 'temperature': 26, 'humidity': 55, 'gas': value}))
        result = parse_sample('MQ2_SAMPLE:{"deviceCode":"test","temperature":26,"humidity":55,"gas":339}')
        self.assertEqual(339, result['gasAdc'])

    def test_collect_and_label_session_without_using_backend(self):
        log = self.folder / 'serial.log'
        log.write_text('HTTP status: 401\nMQ2_SAMPLE:{"deviceCode":"test","temperature":26,"humidity":55,"gas":339}\n', encoding='utf-8')
        output = self.folder / 'capture.csv'
        args = SimpleNamespace(session='one', output=output, label='UNLABELED', note='', log=log, seconds=0)
        with contextlib.redirect_stdout(io.StringIO()):
            collect(args)
            label_session(SimpleNamespace(input=output, session='one', label='NORMAL', note='Observed normal environment'))
        self.assertEqual('NORMAL', rows_from(output)[0]['label'])
        with self.assertRaisesRegex(ValueError, 'Session da ton tai'):
            collect(args)

    def test_split_has_no_session_leakage_and_keeps_all_labels(self):
        data, _ = load_dataset(self.source)
        partitions = split_sessions(data)
        groups = [set(rows.session) for rows in partitions.values()]
        self.assertFalse(groups[0] & groups[1] or groups[0] & groups[2] or groups[1] & groups[2])
        for rows in partitions.values():
            self.assertEqual({0, 2}, set(rows.target))

    def test_rejects_single_class_instead_of_inventing_labels(self):
        save_csv(self.source, [row for row in test_rows() if row['label'] == 'NORMAL'])
        with self.assertRaisesRegex(ValueError, 'Can NORMAL'):
            load_dataset(self.source)

    def test_rejects_too_few_independent_sessions(self):
        save_csv(self.source, [row for row in test_rows() if not row['session'].endswith('_2')])
        data, _ = load_dataset(self.source)
        with self.assertRaisesRegex(ValueError, '3 phien'):
            split_sessions(data)

    def test_rejects_mixed_labels_and_different_devices(self):
        rows = test_rows()
        rows[0]['deviceCode'] = 'other'
        save_csv(self.source, rows)
        with self.assertRaisesRegex(ValueError, 'deviceCode'):
            load_dataset(self.source)
        rows = test_rows()
        rows[0]['label'] = 'DANGER'
        save_csv(self.source, rows)
        with self.assertRaisesRegex(ValueError, 'Moi session'):
            load_dataset(self.source)

    def test_train_uses_adc_and_preserves_nonconsecutive_class_ids(self):
        target = self.folder / 'model.joblib'
        report = train_dataset(self.source, target)
        artifact = joblib.load(target)
        self.assertEqual([0, 2], report['classes'])
        self.assertEqual('MQ2_ADC', artifact['metadata']['input_kind'])
        predictions = artifact['model'].predict(pd.DataFrame([[26, 55, 200], [26, 55, 1200]], columns=['temperature', 'humidity', 'gas']))
        self.assertEqual([0, 2], predictions.tolist())
        manifest = write_model(artifact, 'a' * 64, self.folder / 'model.rf.gz')
        self.assertEqual([0, 2], manifest['classes'])
        self.assertEqual({'TEST_ONLY_MQ2'}, set(manifest['device_codes']))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--write-java-fixtures', action='store_true')
    args = parser.parse_args()
    if args.write_java_fixtures:
        write_java_fixtures()
    unittest.main(argv=['test_mq2'])
