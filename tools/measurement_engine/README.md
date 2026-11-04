# Owned measurement engine: offline foundation

This is an experiment, not a photo measurement product. It does not accept photographs,
fit a body to images, or produce validated customer measurements.

`sections.py` computes a closed mesh cross-section perimeter and refuses ambiguous,
open or multiple loops. The caller must supply the correct anatomical region, pose,
scale and units. Geometry correctness does not establish anatomical accuracy.

Run the analytic geometry tests without dependencies:

```sh
python3 -m unittest discover -s tools/measurement_engine -p 'test_*.py'
```

For the optional synthetic template experiment, use a separate Python 3.12 environment,
install `requirements.txt`, then run `anny_smoke.py`. Anny downloads native assets to
its cache; do not commit models or cache contents. Only native Anny topology/rig is used.
No SMPL-X assets or noncommercial Multi-HMR checkpoints are used.

A local CPU experiment on 4 November 2026 exercised Anny 0.6.2 with Torch 2.10.0.
It produced 13,718 vertices / 27,420 faces. Cached load took about 1.4 seconds and
synthetic forward/geometry about 0.04 seconds. These are template diagnostics,
not photo inference benchmarks, customer accuracy or production cost estimates.

The dormant TypeScript workflow in `functions/src/remoteMeasurements/workflow.ts`
models both journeys, consent, expiry, stale callback rejection and manual verification.
It is not exported as a callable or deployed. Future adapters must authenticate actors,
resolve workshop membership, enforce server-owned validation policy, and persist
transactions atomically. No engine/field is approved by this experiment.

Next gates: licensed photo-fitting model, capture quality checks, consent/storage
implementation, and independent tape-reference validation across intended customers.
See `docs/research/owned-measurement-engine.md` and the evaluation harness.
