#!/usr/bin/env python3
"""Reconcile registered attempts and independent tape readings into an evaluation ledger.
No images, identity lookup, release approval or automatic production import.
"""
import argparse
import json
import math
import statistics
from pathlib import Path

FIELDS = ('chest', 'waist', 'hips')
SPLITS = ('development', 'calibration', 'evaluation')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value) and value > 0


def identifier(value):
    return isinstance(value, str) and bool(value.strip())


def build_ledger(bundle):
    require(bundle.get('unit') == 'mm', 'Use millimetres throughout')
    protocol = bundle.get('protocol', {})
    require(protocol.get('frozen') is True and identifier(protocol.get('approved_by')),
            'Supply a frozen protocol approved before evaluation')
    fields = protocol.get('fields', {})
    require(set(fields) == set(FIELDS), 'Specify all three field definitions')
    definitions = [fields[field].get('definition_id') for field in FIELDS]
    require(all(map(identifier, definitions)) and len(set(definitions)) == len(FIELDS), 'Use distinct definition IDs')
    for settings in fields.values():
        require(number(settings.get('tolerance_mm')) and number(settings.get('reference_spread_limit_mm')),
                'Supply approved error and reference repeatability limits')
    participants = {}
    for participant in bundle.get('participants', []):
        pid = participant.get('participant_id')
        require(identifier(pid) and pid not in participants, 'Duplicate or missing participant ID')
        require(participant.get('split') in SPLITS and identifier(participant.get('consent_id')),
                'Each participant requires a partition and consent reference')
        references = participant.get('references', {})
        require(set(references) == set(FIELDS), 'Independent references required for every field')
        consensus = {}
        for field in FIELDS:
            readings = references[field]
            require(isinstance(readings, list) and len(readings) >= 4, 'Need two readings from each of two measurers')
            measurers, duplicates = {}, set()
            for reading in readings:
                measurer, repeat = reading.get('measurer_id'), reading.get('repeat')
                require(identifier(measurer) and isinstance(repeat, int) and not isinstance(repeat, bool) and repeat > 0,
                        'Invalid measurer/repeat')
                require((measurer, repeat) not in duplicates, 'Duplicate reference reading')
                duplicates.add((measurer, repeat))
                require(reading.get('blinded') is True and number(reading.get('value_mm')),
                        'References must be independent, blinded and finite')
                measurers.setdefault(measurer, []).append(reading['value_mm'])
            require(len(measurers) >= 2 and all(len(values) >= 2 for values in measurers.values()),
                    'Need repeated readings from at least two independent measurers')
            values = [reading['value_mm'] for reading in readings]
            require(max(values) - min(values) <= fields[field]['reference_spread_limit_mm'],
                    'Reference readings require adjudication; do not silently drop the participant')
            # Equal weight per measurer; repeated measurements do not create extra people.
            consensus[field] = statistics.mean(statistics.mean(values) for values in measurers.values())
        participants[pid] = (participant['split'], consensus)
    registry = {}
    for item in bundle.get('registered_attempts', []):
        aid, pid = item.get('attempt_id'), item.get('participant_id')
        require(identifier(aid) and aid not in registry and pid in participants, 'Invalid or duplicate registered attempt')
        require(identifier(item.get('engine_version')), 'Register the engine before capture, including failures')
        registry[aid] = item
    require(bool(registry), 'No registered attempts')
    outcomes = {}
    for outcome in bundle.get('outcomes', []):
        aid = outcome.get('attempt_id')
        require(aid in registry and aid not in outcomes, 'Unregistered or duplicate outcome')
        require(outcome.get('status') in ('completed', 'failed', 'abandoned'), 'Invalid outcome status')
        outcomes[aid] = outcome
    attempts = []
    for aid, registration in registry.items():
        pid, engine = registration['participant_id'], registration['engine_version']
        split, consensus = participants[pid]
        outcome = outcomes.get(aid, {'status': 'failed'})
        status = outcome['status']
        measurements = {}
        result = outcome.get('result')
        if status == 'completed':
            require(isinstance(result, dict) and result.get('synthetic') is False,
                    'Completed real cohort attempts must not be synthetic')
            require(result.get('validatedForTailoring') is False and result.get('canSaveToCustomer') is False,
                    'Only explicitly unvalidated research output is accepted')
            require(result.get('pipelineVersion') == engine, 'Result pipeline differs from registered pipeline (do not pool manual and automatic sources)')
            estimates = result.get('estimates', {})
            require(isinstance(estimates, dict) and set(estimates) <= set(FIELDS), 'Unknown estimated field')
            for field, estimate in estimates.items():
                require(isinstance(estimate, dict) and number(estimate.get('valueMm')), 'Invalid estimate')
                measurements[fields[field]['definition_id']] = {'reference_mm': consensus[field], 'estimate_mm': estimate['valueMm']}
        else:
            require(result is None, 'Failed/abandoned attempts must not carry successful results')
        attempts.append({'participant_id': pid, 'attempt_id': aid, 'engine_version': engine,
                         'split': split, 'status': status, 'measurements': measurements})
    return {'unit': 'mm', 'tolerances_mm': {s['definition_id']: s['tolerance_mm'] for s in fields.values()},
            'attempts': attempts}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    try:
        ledger = build_ledger(json.loads(args.bundle.read_text()))
    except (ValueError, KeyError, TypeError) as error:
        parser.error(str(error))
    # Exclusive creation avoids silently replacing a private study ledger.
    with args.output.open('x') as output:
        json.dump(ledger, output, indent=2, allow_nan=False)
        output.write('\n')


if __name__ == '__main__':
    main()
