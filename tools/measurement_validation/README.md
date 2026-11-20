# Offline measurement validation

This tool evaluates a **pseudonymous** paired scan/tape attempt ledger. It does not
estimate bodies, upload images, train models or certify tailoring accuracy. Python's
standard library is sufficient; no service credentials are used.

Run from the repository root:

```sh
python3 -m unittest discover -s tools/measurement_validation -v
python3 tools/measurement_validation/evaluate.py /path/to/private/pilot.json
```

Keep participant data outside version control. Store the identity lookup separately
from the evaluation ledger. The report contains aggregate counts/errors, not participant IDs.

Input shape (the numbers below are **synthetic schema examples**, not pilot results
or approved tolerances):

```json
{
  "unit": "mm",
  "tolerances_mm": {"waist_natural_v1": 10, "hip_fullest_v1": 10},
  "attempts": [
    {
      "participant_id": "synthetic-001",
      "attempt_id": "capture-1",
      "engine_version": "synthetic-fixture-only",
      "split": "evaluation",
      "status": "completed",
      "measurements": {
        "waist_natural_v1": {"reference_mm": 800, "estimate_mm": 808},
        "hip_fullest_v1": {"reference_mm": 1000, "estimate_mm": 1012}
      }
    },
    {
      "participant_id": "synthetic-002",
      "attempt_id": "capture-1",
      "engine_version": "synthetic-fixture-only",
      "split": "evaluation",
      "status": "failed",
      "measurements": {}
    }
  ]
}
```

- `tolerances_mm` defines the complete essential field set for **one frozen garment
  protocol**. Do not mix garment requirements or device cohorts within an evaluation
  document. Tolerances must come from a pre-agreed protocol before seeing held-out errors.
- `reference_mm` is the consensus/resolved tape reference obtained independently under
  that definition. Keep individual measurer readings and adjudication in the private
  reference ledger. This tool does not establish reference quality.
- `split` is development, calibration or evaluation. Reports use held-out evaluation
  attempts only, and reject participants crossing partitions.
- Every initiated attempt must be included, even failed/abandoned attempts and retries.
  Reconcile the ledger with capture initiation records before drawing conclusions;
  the tool cannot discover attempts omitted by the caller.
- `completed` may omit a field: omission is counted and fails essential-set acceptance.
  Invalid/negative/non-finite values are rejected instead of quietly excluded. Unknown
  fields, mixed units and duplicate engine/participant/attempt keys are rejected.
- Each engine version is reported separately. One failed essential field fails a set.
  All attempts are in the end-to-end denominator; a separate completed-only rate makes
  the selection effect visible.
- Percentiles use nearest rank. Available-reading error summaries are **attempt weighted**;
  frequent repeat participants can dominate them. Report the participant count and use
  a balanced repetition protocol. Do not interpret repeated attempts as independent
  people. This tool deliberately computes no independence-based confidence intervals.
- This is an initial descriptive evaluator. Before a release decision, add participant-
  clustered uncertainty, repeated-measure agreement analysis, subgroup/device reports,
  independently audited reference readings and actual garment/alteration outcomes.
  No sample size or observed passing percentage here automatically approves a release.
