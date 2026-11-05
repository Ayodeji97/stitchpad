# Remote measurement verification — 5 November 2026

Branch: `feat/measurement-auto-detection`, stacked on capture PR #385.
No production app code, backend function, customer record or store rollout changed.

## Verified

- 27 Node tests: fitting, source-version separation, EN/FR coverage and outline gates.
- 4 asset tests: offline verification, corrupt/missing assets, path confinement and
  refusing bad downloads. Eight actual local assets passed SHA-256 verification.
- 19 evaluation-ledger/importer tests: consent/partition references, frozen protocol,
  blinded repeated independent readings, adjudication, registry reconciliation,
  failures/abandonments, pipeline matching and synthetic/false verification rejection.
- 6 mesh-section tests from the existing research foundation.
- Browser workflow checks passed on Chromium and WebKit with mobile viewport emulation:
  both journeys, consent/height/check gates, edit/undo confirmation invalidation,
  comparison, image-free export, missing models, actual detector rejection on a
  nonhuman drawing, corrupt replacement, reset races, French layout and no external
  requests. Repeatable CLI scripts are committed with the prototype.
- The actual pinned WASM/model ran on native-Anny synthetic front/side renders in both
  browsers, then fed reviewed contours through fitting and exported model checksums.
  Synthetic flags remained set. This was not mocked inference and not an accuracy cohort.
- A side render with arm overlap was rejected; a clear-arm synthetic pose passed.
  Image aspect ratio was tested to prevent normalized-width orientation errors.
- French capture and comparison screenshots at 390 px were inspected. No horizontal
  overflow observed. This does not verify actual iPhone Safari or low-end Android hardware.

Commands:

```sh
node --test tools/measurement_capture/test_*.mjs
python3 -m unittest discover -s tools/measurement_capture -p 'test_*.py'
python3 -m unittest discover -s tools/measurement_validation -p 'test_*.py'
python3 -m unittest discover -s tools/measurement_engine -p 'test_*.py'
python3 tools/measurement_capture/setup_assets.py --check
playwright-cli run-code --filename=tools/measurement_capture/verify_browser.cjs
playwright-cli run-code --filename=tools/measurement_capture/verify_detector.cjs
```

The positive detector test additionally needs the optional ignored fixtures generated
by `generate_fixtures.py`; see the capture README. No real photographs, identities,
model binaries or cohort data are committed. Backend/mobile builds were not repeated
because their source is unchanged.

## Limits that remain

The owned fitter is still an ellipse approximation, not learned/validated anatomical
reconstruction. Automatic anatomical rows are suggestions; natural waist/full hips
must be reviewed. Rejecting an ambiguous pose does not establish broad body coverage.
Synthetic success establishes software wiring, not garment fit, accuracy, subgroup
coverage or acceptance rates. No error tolerance or production engine is approved.

Before native integration/release: collect independently consented and blinded repeated
tape references with a trained tailor, freeze field definitions/tolerances, audit every
attempt against initiation records, assess subgroup/device and actual garment outcomes,
and improve/drop fields that fail. Native camera, authenticated customer invitation,
public designer discovery, storage/deletion and production review adapters remain unwired.
A manual/edited fallback must be registered as its own pipeline attempt; do not retroactively
rewrite a failed automatic attempt's engine or remove it from the denominator.
