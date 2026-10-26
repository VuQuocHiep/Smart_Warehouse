import tempfile
import unittest
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
import lightgbm

from demand_model import load_data, samples, metrics, forecast, FORMAT_VERSION, select_methods, METHODS, fit


def fixture(days=120, sku='SIM_A'):
    return pd.DataFrame({'date': pd.date_range('2025-01-01', periods=days),
                         'warehouse_id': 'SIM_WH', 'product_id': sku,
                         'export_quantity': np.arange(days), 'data_quality': 'COMPLETE', 'data_source': 'SIMULATED'})


class DemandTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, frame):
        path = self.root / 'daily.csv'
        frame.to_csv(path, index=False)
        return path

    def artifact(self):
        path = self.root / 'model.joblib'
        joblib.dump(fit(samples(fixture())) | {'format_version': FORMAT_VERSION, 'sklearn_version': sklearn.__version__,
                     'lightgbm_version': lightgbm.__version__,
                     'data_cutoff': '2025-01-01', 'data_source': 'SIMULATED',
                     'selected_method': 'seasonal_naive', 'model_version': 'test',
                     'known_pairs': [('SIM_WH', 'SIM_A')]}, path)
        return path

    def test_features_do_not_see_future(self):
        frame = fixture()
        origin = frame.date.iloc[99]
        before = samples(frame)
        frame.loc[frame.date > origin, 'export_quantity'] = 9999
        after = samples(frame)
        columns = [c for c in before.columns if c != 'actual']
        pd.testing.assert_frame_equal(before.loc[before.origin == origin, columns],
                                      after.loc[after.origin == origin, columns])
        row = before[(before.origin == origin) & (before.horizon == 1)].iloc[0]
        self.assertEqual(row.latest, 99)
        self.assertEqual(row.lag_7, 92)
        self.assertEqual(row.mean_7, 96)
        self.assertEqual(row.seasonal_naive, 93)
        self.assertEqual(row.actual, 100)

    def test_missing_duplicate_negative_and_quality_rejected(self):
        frame = fixture()
        bad_frames = [frame.drop(index=5), pd.concat([frame, frame.iloc[[0]]])]
        bad = frame.copy()
        bad.loc[0, 'export_quantity'] = -1
        bad_frames.append(bad)
        bad = frame.copy()
        bad.loc[0, 'data_quality'] = 'MISSING'
        bad_frames.append(bad)
        for bad in bad_frames:
            with self.subTest(), self.assertRaises(ValueError):
                load_data(self.write(bad))

    def test_future_stock_columns_are_excluded(self):
        frame = fixture()
        frame['closing_stock'] = 1234
        loaded = load_data(self.write(frame))
        self.assertNotIn('closing_stock', loaded.columns)

    def test_train_cutoff_is_by_target(self):
        frame = samples(fixture())
        cutoff = pd.Timestamp('2025-04-15')
        train = frame[frame.target_date < cutoff]
        self.assertTrue((train.target_date < cutoff).all())
        self.assertTrue((train.origin < train.target_date).all())
        self.assertFalse(train.empty)

    def test_lightgbm_forecast_and_saved_artifact(self):
        result = forecast(self.write(fixture()), self.artifact(), self.root / 'forecast.csv')
        self.assertEqual(len(result), 14)
        self.assertTrue(result.status.eq('OK').all())
        self.assertTrue(result.method.eq('lightgbm_poisson').all())
        self.assertTrue(result.predicted_quantity.ge(0).all())
        self.assertTrue(result.predicted_quantity.mod(1).eq(0).all())
        self.assertEqual(result.target_date.iloc[0], '2025-05-01')
        self.assertEqual(result.target_date.iloc[-1], '2025-05-14')

    def test_cold_start_zero_and_insufficient(self):
        for days, expected_status, expected_method in [
                (5, 'INSUFFICIENT_DATA', 'none'), (14, 'INSUFFICIENT_DATA', 'none'),
                (89, 'INSUFFICIENT_DATA', 'none'), (90, 'OK', 'lightgbm_poisson')]:
            result = forecast(self.write(fixture(days, 'SIM_NEW')), self.artifact(), self.root / 'forecast.csv')
            self.assertTrue(result.status.eq(expected_status).all())
            self.assertTrue(result.method.eq(expected_method).all())
        frame = fixture()
        frame.export_quantity = 0
        result = forecast(self.write(frame), self.artifact(), self.root / 'forecast.csv')
        self.assertTrue(result.predicted_quantity.ge(0).all())
        self.assertTrue(result.method.eq('lightgbm_poisson').all())

    def test_stale_data_and_backtest_guard(self):
        result = forecast(self.write(fixture()), self.artifact(), self.root / 'forecast.csv', '2025-05-02')
        self.assertTrue(result.status.eq('STALE_DATA').all())
        with self.assertRaises(ValueError):
            forecast(self.write(fixture()), self.artifact(), self.root / 'forecast.csv', '2024-12-31')

    def test_zero_wape_is_undefined(self):
        self.assertIsNone(metrics([0, 0], [0, 1])['wape_percent'])

    def test_selection_uses_ml_even_when_baseline_is_better(self):
        rows = []
        for sku, winner in [('A', 'weekday_mean_28'), ('B', 'lightgbm_l1')]:
            row = {'warehouse_id': 'W', 'product_id': sku, 'actual': 10}
            row.update({method: 20 for method in METHODS})
            row[winner] = 10
            rows.append(row)
        selected = select_methods(pd.DataFrame(rows))
        self.assertEqual(selected[('W', 'A')], 'lightgbm_poisson')
        self.assertEqual(selected[('W', 'B')], 'lightgbm_l1')
        rows[1]['mean_28'], rows[1]['lightgbm_l1'] = 11, 10.99
        self.assertEqual(select_methods(pd.DataFrame(rows))[('W', 'B')], 'lightgbm_l1')

    def test_artifact_cannot_select_a_baseline(self):
        frame = fixture()
        frame.export_quantity = frame.date.dt.dayofweek * 3 + 2
        model_path = self.artifact()
        bundle = joblib.load(model_path)
        bundle['selected_method'] = 'per_series'
        bundle['selected_methods'] = {('SIM_WH', 'SIM_A'): 'weekday_mean_28'}
        joblib.dump(bundle, model_path)
        with self.assertRaisesRegex(ValueError, 'non-LightGBM'):
            forecast(self.write(frame), model_path, self.root / 'forecast.csv')

    def test_legacy_artifact_requires_retraining(self):
        model_path = self.artifact()
        bundle = joblib.load(model_path)
        bundle['format_version'] = 1
        joblib.dump(bundle, model_path)
        with self.assertRaisesRegex(ValueError, 'retrain'):
            forecast(self.write(fixture()), model_path, self.root / 'forecast.csv')


if __name__ == '__main__':
    unittest.main()
