import copy
import unittest
from build_ledger import build_ledger, FIELDS
from evaluate import evaluate


def bundle():
    # Entirely fabricated schema fixture, not a real participant or approved tolerance.
    return {'unit': 'mm', 'protocol': {'frozen': True, 'approved_by': 'synthetic-test-only',
        'fields': {f: {'definition_id': f + '_fixture_v1', 'tolerance_mm': 10, 'reference_spread_limit_mm': 5} for f in FIELDS}},
        'participants': [{'participant_id': 'fixture-1', 'split': 'evaluation', 'consent_id': 'fixture-consent',
            'references': {f: [{'measurer_id': m, 'repeat': r, 'value_mm': 900, 'blinded': True}
                               for m in ['a', 'b'] for r in [1, 2]] for f in FIELDS}}],
        'registered_attempts': [{'attempt_id': a, 'participant_id': 'fixture-1', 'engine_version': 'fixture-engine'}
                               for a in ['ok', 'missing', 'abandoned']],
        'outcomes': [{'attempt_id': 'ok', 'status': 'completed', 'result': {
            'synthetic': False, 'pipelineVersion': 'fixture-engine', 'validatedForTailoring': False,
            'canSaveToCustomer': False, 'estimates': {f: {'valueMm': 901} for f in FIELDS}}},
            {'attempt_id': 'abandoned', 'status': 'abandoned'}]}


class BuildLedgerTests(unittest.TestCase):
    def test_registered_missing_and_abandoned_attempts_stay_in_denominator(self):
        result = build_ledger(bundle())
        self.assertEqual([x['status'] for x in result['attempts']], ['completed', 'failed', 'abandoned'])
        report = evaluate(result)
        self.assertTrue(report)

    def test_original_bundle_not_mutated(self):
        value = bundle()
        before = copy.deepcopy(value)
        build_ledger(value)
        self.assertEqual(value, before)

    def test_rejects_unfrozen_or_unapproved_protocol(self):
        for key, value in [('frozen', False), ('approved_by', '')]:
            b = bundle(); b['protocol'][key] = value
            with self.assertRaises(ValueError): build_ledger(b)

    def test_rejects_nonindependent_or_inconsistent_tape(self):
        for key, value in [('blinded', False), ('value_mm', 1000), ('value_mm', float('nan'))]:
            b = bundle(); b['participants'][0]['references']['waist'][0][key] = value
            with self.assertRaises(ValueError): build_ledger(b)
        b = bundle(); b['participants'][0]['references']['waist'] = b['participants'][0]['references']['waist'][:2]
        with self.assertRaises(ValueError): build_ledger(b)

    def test_duplicate_participant_or_reference_rejected(self):
        b = bundle(); b['participants'].append(copy.deepcopy(b['participants'][0]))
        with self.assertRaises(ValueError): build_ledger(b)
        b = bundle(); readings = b['participants'][0]['references']['hips']; readings.append(readings[0])
        with self.assertRaises(ValueError): build_ledger(b)

    def test_rejects_unregistered_duplicate_and_engine_mismatch(self):
        b = bundle(); b['outcomes'][0]['attempt_id'] = 'unknown'
        with self.assertRaises(ValueError): build_ledger(b)
        b = bundle(); b['outcomes'].append(copy.deepcopy(b['outcomes'][0]))
        with self.assertRaises(ValueError): build_ledger(b)
        b = bundle(); b['outcomes'][0]['result']['pipelineVersion'] = 'other'
        with self.assertRaises(ValueError): build_ledger(b)

    def test_synthetic_results_and_false_verified_claims_cannot_enter_real_cohort(self):
        for key, value in [('synthetic', True), ('canSaveToCustomer', True), ('validatedForTailoring', True)]:
            b = bundle(); b['outcomes'][0]['result'][key] = value
            with self.assertRaises(ValueError): build_ledger(b)

    def test_incomplete_result_keeps_missing_field(self):
        b = bundle(); del b['outcomes'][0]['result']['estimates']['waist']
        ledger = build_ledger(b)
        self.assertEqual(len(ledger['attempts'][0]['measurements']), 2)
        evaluate(ledger)

    def test_wrong_units_and_unknown_fields_rejected(self):
        b = bundle(); b['unit'] = 'cm'
        with self.assertRaises(ValueError): build_ledger(b)
        b = bundle(); b['outcomes'][0]['result']['estimates']['unknown'] = {'valueMm': 10}
        with self.assertRaises(ValueError): build_ledger(b)
