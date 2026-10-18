import argparse
import csv
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import re
import time

ROOT = Path(__file__).resolve().parent
DATA = ROOT / 'data/mq2_readings.csv'
FIELDS = ['timestamp', 'deviceCode', 'temperature', 'humidity', 'gasAdc', 'uptimeMs', 'session', 'label', 'labelNote']
LABELS = {'UNLABELED': -1, 'NORMAL': 0, 'WARNING': 1, 'DANGER': 2, 'EMERGENCY': 3}


def parse_sample(line):
    if not line.startswith('MQ2_SAMPLE:'):
        return None
    sample = json.loads(line[len('MQ2_SAMPLE:'):])
    if not isinstance(sample, dict):
        raise ValueError('Mau du lieu phai la JSON object')
    values = [float(sample[key]) for key in ('temperature', 'humidity', 'gas')]
    if not all(math.isfinite(value) for value in values):
        raise ValueError('So do khong huu han')
    temperature, humidity, gas = values
    if not -100 <= temperature <= 200 or not 0 <= humidity <= 100 or not 0 <= gas <= 4095 or not gas.is_integer():
        raise ValueError('So do nam ngoai mien ADC 12-bit/DHT')
    if not isinstance(sample.get('deviceCode'), str) or not sample['deviceCode'].strip():
        raise ValueError('Thieu deviceCode')
    return {'timestamp': datetime.now(timezone.utc).isoformat(), 'deviceCode': sample['deviceCode'],
            'temperature': temperature, 'humidity': humidity, 'gasAdc': int(gas),
            'uptimeMs': int(sample.get('uptimeMs', 0))}


def rows_from(path):
    if not path.exists():
        return []
    with path.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != FIELDS:
            raise ValueError('CSV khong dung dinh dang MQ-2')
        return list(reader)


def collect(args):
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', args.session):
        raise ValueError('Ten session chi gom chu, so, _ va -')
    if any(row['session'] == args.session for row in rows_from(args.output)):
        raise ValueError('Session da ton tai; dung ten moi cho moi phien do doc lap')
    if args.label != 'UNLABELED' and not args.note.strip():
        raise ValueError('Can --note mo ta co so gan nhan thuc te')
    if args.log:
        stream = args.log.open(encoding='utf-8')
    else:
        import serial
        stream = serial.Serial(args.port, 115200, timeout=1)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    device = None
    start = time.monotonic()
    try:
        with stream, args.output.open('a', encoding='utf-8', newline='') as output:
            writer = csv.DictWriter(output, fieldnames=FIELDS)
            if output.tell() == 0:
                writer.writeheader()
            while not args.seconds or time.monotonic() - start < args.seconds:
                line = stream.readline()
                if not line:
                    if args.log:
                        break
                    continue
                if isinstance(line, bytes):
                    line = line.decode('utf-8', errors='replace')
                try:
                    sample = parse_sample(line.strip())
                except (ValueError, KeyError, TypeError) as error:
                    print(f'Bo qua mau loi: {error}')
                    continue
                if sample is None:
                    continue
                device = device or sample['deviceCode']
                if sample['deviceCode'] != device:
                    raise ValueError('Mot session chi duoc chua mot thiet bi')
                writer.writerow({**sample, 'session': args.session, 'label': args.label, 'labelNote': args.note})
                output.flush()
                count += 1
                print(f"{count}: T={sample['temperature']} H={sample['humidity']} ADC={sample['gasAdc']}")
    except KeyboardInterrupt:
        pass
    print(f'Da luu {count} mau: {args.output}')


def label_session(args):
    rows = rows_from(args.input)
    if not args.note.strip():
        raise ValueError('Can --note mo ta co so gan nhan')
    found = 0
    for row in rows:
        if row['session'] == args.session:
            row['label'] = args.label
            row['labelNote'] = args.note
            found += 1
    if not found:
        raise ValueError('Khong tim thay session')
    temporary = args.input.with_suffix('.label.tmp')
    with temporary.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    os.replace(temporary, args.input)
    print(f'Da gan {args.label} cho {found} mau cua {args.session}')


def main():
    parser = argparse.ArgumentParser(description='Thu du lieu va train AI tu MQ-2 ADC thuc te')
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('ports')
    collector = commands.add_parser('collect')
    source = collector.add_mutually_exclusive_group(required=True)
    source.add_argument('--port')
    source.add_argument('--log', type=Path)
    collector.add_argument('--session', required=True)
    collector.add_argument('--label', choices=LABELS, default='UNLABELED')
    collector.add_argument('--note', default='')
    collector.add_argument('--seconds', type=int, default=0)
    collector.add_argument('--output', type=Path, default=DATA)
    label = commands.add_parser('label')
    label.add_argument('--input', type=Path, default=DATA)
    label.add_argument('--session', required=True)
    label.add_argument('--label', choices=LABELS, required=True)
    label.add_argument('--note', required=True)
    status = commands.add_parser('status')
    status.add_argument('--input', type=Path, default=DATA)
    trainer = commands.add_parser('train')
    trainer.add_argument('--input', type=Path, default=DATA)
    trainer.add_argument('--output', type=Path, default=ROOT / 'models/mq2_risk_model.joblib')
    args = parser.parse_args()
    try:
        if args.command == 'ports':
            from serial.tools import list_ports
            for port in list_ports.comports():
                print(f'{port.device}: {port.description}')
        elif args.command == 'collect':
            if args.seconds < 0:
                raise ValueError('--seconds phai >= 0')
            collect(args)
        elif args.command == 'label':
            label_session(args)
        elif args.command == 'status':
            sessions = {}
            for row in rows_from(args.input):
                key = (row['session'], row['deviceCode'], row['label'])
                sessions[key] = sessions.get(key, 0) + 1
            for (session, device, label), count in sorted(sessions.items()):
                print(f'{session}: {device} | {label} | {count} mau')
            if not sessions:
                print('Chua co du lieu MQ-2')
        else:
            from train_mq2 import train_dataset
            print(json.dumps(train_dataset(args.input, args.output), indent=2, ensure_ascii=False))
    except (ValueError, FileNotFoundError, ModuleNotFoundError) as error:
        parser.exit(1, f'{error}\n')
    except OSError as error:
        parser.exit(1, f'Khong truy cap duoc cong/file: {error}\nDong Serial Monitor va ung dung dang giu cong COM, roi thu lai.\n')


if __name__ == '__main__':
    main()
