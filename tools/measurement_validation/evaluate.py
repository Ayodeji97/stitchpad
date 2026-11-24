#!/usr/bin/env python3
"""Offline research evaluator. No inference, image upload, or release approval."""
import argparse
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path


def positive_number(value, field):
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError(f'{field} must be a positive finite number in mm')
    if not math.isfinite(value) or value <= 0:
        raise ValueError(f'{field} must be a positive finite number in mm')
    return value


def nonempty_string(value, field):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f'{field} must be a nonempty string')
    return value


def evaluate(document):
    if document.get('unit') != 'mm':
        raise ValueError('unit must explicitly be mm; convert source units before evaluation')
    tolerances = document.get('tolerances_mm')
    if not isinstance(tolerances, dict) or not tolerances:
        raise ValueError('Supply tolerances_mm for all essential measurement IDs')
    for name, tolerance in tolerances.items():
        nonempty_string(name, 'measurement ID')
        positive_number(tolerance, f'tolerance {name}')
    attempts = document.get('attempts')
    if not isinstance(attempts, list) or not attempts:
        raise ValueError('Supply a nonempty complete attempt ledger')
    partitions, seen = {}, set()
    engines = defaultdict(list)
    for index, row in enumerate(attempts):
        if not isinstance(row, dict):
            raise ValueError(f'attempt {index} must be an object')
        person = nonempty_string(row.get('participant_id'), 'participant_id')
        attempt = nonempty_string(row.get('attempt_id'), 'attempt_id')
        engine = nonempty_string(row.get('engine_version'), 'engine_version')
        split = row.get('split')
        if split not in ('development', 'calibration', 'evaluation'):
            raise ValueError('split must be development, calibration, or evaluation')
        if person in partitions and partitions[person] != split:
            raise ValueError('Participant leakage across development/calibration/evaluation')
        partitions[person] = split
        key = (engine, person, attempt)
        if key in seen:
            raise ValueError('Duplicate engine/participant/attempt')
        seen.add(key)
        status = row.get('status')
        if status not in ('completed', 'failed', 'abandoned'):
            raise ValueError('status must be completed, failed, or abandoned')
        readings = row.get('measurements', {})
        if not isinstance(readings, dict) or set(readings) - set(tolerances):
            raise ValueError('measurements must use only configured essential measurement IDs')
        errors = {}
        if status != 'completed' and readings:
            raise ValueError('Failed/abandoned attempts cannot carry completed readings')
        for name, pair in readings.items():
            if not isinstance(pair, dict):
                raise ValueError('Each measurement must contain reference_mm and estimate_mm')
            reference = positive_number(pair.get('reference_mm'), 'reference_mm')
            estimate = positive_number(pair.get('estimate_mm'), 'estimate_mm')
            errors[name] = estimate - reference
        if split == 'evaluation':
            engines[engine].append((person, status, errors))
    if not engines:
        raise ValueError('No held-out evaluation attempts; cannot report evaluation results')

    summaries = {}
    for engine, records in sorted(engines.items()):
        completed = [errors for _, status, errors in records if status == 'completed']
        accepted = sum(
            set(errors) == set(tolerances)
            and all(abs(errors[name]) <= tolerances[name] for name in tolerances)
            for errors in completed
        )
        fields = {}
        for name, tolerance in tolerances.items():
            signed = [errors[name] for errors in completed if name in errors]
            absolute = sorted(abs(value) for value in signed)
            fields[name] = {
                'tolerance_mm': tolerance,
                'reading_count': len(signed),
                'completed_attempts_missing_field': len(completed) - len(signed),
                'bias_mm': statistics.mean(signed) if signed else None,
                'mae_mm': statistics.mean(absolute) if absolute else None,
                'p95_absolute_error_mm': absolute[math.ceil(.95 * len(absolute)) - 1] if absolute else None,
                'within_tolerance_rate_on_available_readings': (
                    sum(value <= tolerance for value in absolute) / len(absolute) if absolute else None
                ),
            }
        summaries[engine] = {
            'attempt_count': len(records),
            'participant_count': len({person for person, _, _ in records}),
            'completed_count': len(completed),
            'failed_count': sum(status == 'failed' for _, status, _ in records),
            'abandoned_count': sum(status == 'abandoned' for _, status, _ in records),
            'essential_sets_within_tolerance': accepted,
            'essential_set_rate_all_attempts': accepted / len(records),
            'essential_set_rate_completed_attempts': accepted / len(completed) if completed else None,
            'fields': fields,
        }
    return {
        'purpose': 'research_summary_only_not_accuracy_certification',
        'percentile_method': 'nearest_rank',
        'confidence_intervals': 'not_computed; repeated attempts are not independent participants',
        'engines': summaries,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path, help='Pseudonymous JSON evaluation ledger')
    args = parser.parse_args()
    try:
        with args.input.open() as stream:
            result = evaluate(json.load(stream))
    except (ValueError, TypeError, AttributeError, OSError) as error:
        parser.error(str(error))
    print(json.dumps(result, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
