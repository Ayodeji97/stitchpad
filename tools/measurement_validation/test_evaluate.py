import copy
import unittest
from evaluate import evaluate


def ledger():
    return {
        'unit': 'mm', 'tolerances_mm': {'waist': 10, 'hip': 10},
        'attempts': [{
            'participant_id': 'synthetic-a', 'attempt_id': '1',
            'engine_version': 'synthetic-only-v1', 'split': 'evaluation',
            'status': 'completed',
            'measurements': {
                'waist': {'reference_mm': 800, 'estimate_mm': 810},
                'hip': {'reference_mm': 1000, 'estimate_mm': 995},
            },
        }],
    }


class EvaluationTests(unittest.TestCase):
    def summary(self, data):
        return evaluate(data)['engines']['synthetic-only-v1']

    def test_exact_tolerance_counts_and_signed_bias_is_preserved(self):
        summary = self.summary(ledger())
        self.assertEqual(summary['essential_set_rate_all_attempts'], 1)
        self.assertEqual(summary['fields']['waist']['bias_mm'], 10)
        self.assertEqual(summary['fields']['hip']['bias_mm'], -5)

    def test_one_bad_essential_field_fails_whole_set(self):
        data = ledger()
        data['attempts'][0]['measurements']['hip']['estimate_mm'] = 1011
        self.assertEqual(self.summary(data)['essential_sets_within_tolerance'], 0)

    def test_failures_and_abandonment_remain_in_denominator(self):
        data = ledger()
        for index, status in enumerate(('failed', 'abandoned'), 2):
            row = {**data['attempts'][0], 'attempt_id': str(index), 'status': status, 'measurements': {}}
            data['attempts'].append(row)
        summary = self.summary(data)
        self.assertEqual(summary['essential_set_rate_all_attempts'], 1 / 3)
        self.assertEqual(summary['essential_set_rate_completed_attempts'], 1)
        self.assertEqual(summary['failed_count'], 1)
        self.assertEqual(summary['abandoned_count'], 1)

    def test_missing_field_does_not_become_zero_or_pass(self):
        data = ledger()
        del data['attempts'][0]['measurements']['hip']
        summary = self.summary(data)
        self.assertEqual(summary['essential_sets_within_tolerance'], 0)
        self.assertIsNone(summary['fields']['hip']['mae_mm'])
        self.assertEqual(summary['fields']['hip']['completed_attempts_missing_field'], 1)

    def test_nonfinite_negative_and_boolean_values_are_rejected(self):
        for invalid in (float('nan'), float('inf'), -1, 0, True, '800'):
            data = ledger()
            data['attempts'][0]['measurements']['waist']['estimate_mm'] = invalid
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                evaluate(data)

    def test_participant_leakage_is_rejected(self):
        data = ledger()
        extra = copy.deepcopy(data['attempts'][0])
        extra.update(split='calibration', attempt_id='2')
        data['attempts'].append(extra)
        with self.assertRaisesRegex(ValueError, 'leakage'):
            evaluate(data)

    def test_duplicate_attempt_is_rejected(self):
        data = ledger()
        data['attempts'].append(copy.deepcopy(data['attempts'][0]))
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            evaluate(data)

    def test_unknown_units_and_measurement_ids_are_rejected(self):
        data = ledger()
        data['unit'] = 'inches'
        with self.assertRaises(ValueError):
            evaluate(data)
        data = ledger()
        data['attempts'][0]['measurements']['wast'] = {'reference_mm': 800, 'estimate_mm': 800}
        with self.assertRaises(ValueError):
            evaluate(data)

    def test_versions_remain_separate(self):
        data = ledger()
        second = copy.deepcopy(data['attempts'][0])
        second['engine_version'] = 'different-version'
        second['measurements']['waist']['estimate_mm'] = 900
        data['attempts'].append(second)
        result = evaluate(data)['engines']
        self.assertEqual(result['synthetic-only-v1']['essential_sets_within_tolerance'], 1)
        self.assertEqual(result['different-version']['essential_sets_within_tolerance'], 0)

    def test_all_failed_has_no_fake_zero_error(self):
        data = ledger()
        data['attempts'][0].update(status='failed', measurements={})
        result = self.summary(data)
        self.assertEqual(result['essential_set_rate_all_attempts'], 0)
        self.assertIsNone(result['essential_set_rate_completed_attempts'])
        self.assertIsNone(result['fields']['waist']['mae_mm'])


if __name__ == '__main__':
    unittest.main()
