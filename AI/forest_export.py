import gzip
import struct
from pathlib import Path

import numpy as np


def write_model(artifact, source_hash, target):
    pipeline = artifact['model']
    forest = pipeline.named_steps['classifier']
    if artifact['feature_names'] != ['temperature', 'humidity', 'gas']:
        raise ValueError('Sai thu tu feature')
    classes = [int(value) for value in forest.classes_]
    if not set(classes).issubset({0, 1, 2, 3}) or 0 not in classes or len(classes) < 2:
        raise ValueError('Model phai co NORMAL va it nhat mot lop canh bao')
    if artifact['metadata']['input_kind'] != 'MQ2_ADC':
        raise ValueError('Model khong duoc train bang MQ-2 ADC')
    devices = artifact['metadata']['device_codes']
    if not devices:
        raise ValueError('Model MQ-2 phai khai bao device_codes da thu du lieu')
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(target, 'wb') as out:
        out.write(b'SWRF')
        out.write(struct.pack('>i', 2))
        out.write(source_hash.encode('ascii'))
        out.write(struct.pack('>ii', 1, len(devices)))
        for device in devices:
            encoded = device.encode('utf-8')
            out.write(struct.pack('>i', len(encoded)))
            out.write(encoded)
        out.write(struct.pack('>3d', *pipeline.named_steps['imputer'].statistics_))
        out.write(struct.pack('>i', len(forest.estimators_)))
        for estimator in forest.estimators_:
            tree = estimator.tree_
            out.write(struct.pack('>i', tree.node_count))
            for node in range(tree.node_count):
                probability = np.zeros(4)
                raw = tree.value[node, 0].astype(float)
                probability[classes] = raw / raw.sum()
                out.write(struct.pack('>iiid4d', int(tree.children_left[node]),
                                      int(tree.children_right[node]), int(tree.feature[node]),
                                      tree.threshold[node], *probability))
    return {'sha256': source_hash, 'trees': len(forest.estimators_),
            'features': artifact['feature_names'], 'input_kind': 'MQ2_ADC',
            'device_codes': devices, 'classes': classes}


def write_parity(artifact, samples, target):
    pipeline = artifact['model']
    probabilities = pipeline.predict_proba(samples)
    classes = pipeline.named_steps['classifier'].classes_
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open('w', encoding='utf-8') as out:
        for values, probability in zip(samples.to_numpy(), probabilities):
            best = int(np.argmax(probability))
            out.write(','.join(str(v) for v in [*values, int(classes[best]), probability[best]]) + '\n')
