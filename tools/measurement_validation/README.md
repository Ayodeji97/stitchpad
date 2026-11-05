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

## Reconcile captures and independent references before evaluation

`build_ledger.py` converts a private study bundle into the ledger above. Its input has
`unit: "mm"`, a frozen `protocol`, `participants`, `registered_attempts` and `outcomes`.
See `test_build_ledger.py` for **fabricated schema examples only**; the example limits
are not approved tailoring tolerances. Never commit a real bundle or exported study result.

```sh
python3 tools/measurement_validation/build_ledger.py /private/study-bundle.json /private/new-ledger.json
python3 tools/measurement_validation/evaluate.py /private/new-ledger.json
```

- `protocol` needs `frozen: true`, a nonblank `approved_by`, and fields `chest`, `waist`,
  `hips`. Each needs a distinct `definition_id`, approved `tolerance_mm` and
  `reference_spread_limit_mm`. These declarations do not prove external approval.
- Each participant needs one `participant_id`, one `split`, a `consent_id` referencing
  the independently maintained consent record, and `references` for every field.
  Each reference reading contains `measurer_id`, positive integer `repeat`, `value_mm`
  and `blinded: true`. At least two readings from each of two independent measurers
  are required. Excessive spread stops import for adjudication, not silent exclusion.
- Register **every initiated attempt**, including failed/abandoned ones, with unique
  `attempt_id`, `participant_id` and `engine_version`. Use the export's full
  `pipelineVersion`, which includes front/side manual/automatic/edited provenance.
  The importer rejects a changed pipeline rather than mixing it into that group.
- `outcomes` bind registered attempt IDs to `status` and (for completed attempts only)
  the image-free capture `result`. Missing outcomes become failed attempts. Unknown or
  duplicate outcomes are rejected. Incomplete completed estimates retain missing fields.
- Synthetic results and outputs claiming verified/customer-save status are rejected.
  A false synthetic flag is not proof an image was real: a study operator must independently
  verify sources and consent. The tool cannot authenticate participant identity or find
  captures omitted from the initiation registry. Audit that registry against source records.
- Tape references are computed from independent readings, **not** the tape comparison
  box beside the displayed estimate. Do not use unblinded UI entries as ground truth.
- Equal weight is given to each measurer's mean. This initial consensus rule still needs
  a trained tailor's approved measurement protocol and adjudication process.
- Output creation is exclusive; existing ledgers are never silently overwritten.

If references need adjudication, keep every original reading. The participant may
include `adjudications` keyed by `chest`, `waist` or `hips`. Each resolution needs a
new `measurer_id` not used in the originals, `blinded: true`, a nonblank `reason`, and
at least two consistent positive `readings_mm`. Their mean becomes the resolved
reference. The same pre-agreed spread limit still applies. Do not show app estimates
to the adjudicator or change limits after inspecting errors. If no independent
resolution is available, keep the original records unresolved rather than silently
excluding the participant from the study. The printed study pack includes this step.
