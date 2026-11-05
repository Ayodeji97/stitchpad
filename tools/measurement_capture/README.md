# Guided measurement capture — local research prototype

Run from the repository root (Python 3 and a modern browser; no dependencies):

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory tools/measurement_capture
```

Open <http://127.0.0.1:8765>. The server serves this folder only. There is no upload
endpoint, paid API, image persistence, analytics or external font. Optional pose ML
runs locally after installing the pinned assets below.
The prototype deliberately runs outside the production app until accuracy is established.
Camera capture is a browser hint, not a guarantee; file selection remains available.
Do not expose this development server on a public network or use it as a pilot backend.

## Smoke test without personal photographs

1. Choose either journey, then **Try a synthetic demo**. It preloads artificial views
   and markers but does not accept consent or capture checks on your behalf.
2. Confirm local processing, begin, and inspect the front outline. Confirm checks.
3. Continue to the side outline, confirm checks and continue to comparison.
4. Enter a tape reference (for example 100 cm) and check the signed difference.
5. Switch EN/FR at any step. Check a narrow 390 px viewport and keyboard operation.
6. Download a result. It contains provisional numbers and optional tape readings,
   not photographs, identities, customer IDs or verified measurements. Synthetic
   results are flagged and must never enter a real accuracy cohort.
7. Clear the session. Height, consent, photos, markers and tape readings disappear.

For a consenting personal test, select JPEG/PNG/WebP images with at least 600 pixels
on each side. Mark head/floor and left/right torso edges at chest/bust, natural waist
and full hips in both views. Arms and clothing must not define the torso outline.
Numeric point editors provide a keyboard alternative to clicking the image.
Nothing is saved to customer records. Do not use these estimates to cut a garment.

## What the owned baseline actually does

`engine.mjs` normalizes each reviewed front/side diameter by known barefoot
height, then approximates each horizontal body cross-section as an ellipse using
Ramanujan's perimeter approximation. Each view is independently scaled; image
resizing must not change output. It does **not** infer height from pixels, reconstruct
an Anny mesh, automatically identify landmarks, segment a person, correct clothing,
calibrate a lens, infer an obscured contour or establish anatomical accuracy.
It is independent of the native Anny template experiment.

All outputs have `validatedForTailoring: false`, `canSaveToCustomer: false`, no
confidence percentage and explicit manual-verification status. The method name and
version are exported for reproducibility. Known-height error, perspective, posture,
annotation and non-elliptical anatomy can all bias the result. No precision claim.
Reported millimetres are a wire unit, not millimetre accuracy.

## Quality gates and limitations

The UI rejects unreadable, over-15-MB, over-40-megapixel and very dark/washed-out
images. File dimensions and full-body framing are checked. The engine rejects
missing/nonfinite/swapped/tilted/out-of-order points, inconsistent cross-view levels,
missing consent and unconfirmed capture checks. These are conservative **heuristics**,
not validated automatic pose, exposure, clothing or uncertainty models. Self-reported
checks do not prove capture quality. Input height bounds (80–250 cm) are prototype
limits, not validated population coverage or suitability for children.

Decoded photos are downscaled in memory; object URLs are revoked after decoding.
Reset/page exit removes browser references; there is no secure-memory-erasure claim.
The original photograph remains wherever the owner saved it. A manually downloaded
result remains on disk and should be handled as personal measurement data.

## Tests

```sh
node --test tools/measurement_capture/test_engine.mjs
python3 -m unittest discover -s tools/measurement_engine -p 'test_*.py'
python3 -m unittest discover -s tools/measurement_validation -p 'test_*.py'
```

Tests cover analytic geometry, two journeys, scale invariance, known-height scaling,
consent, framing, invalid inputs, cross-view mismatch, immutable results and explicit
unvalidated/synthetic status. Browser smoke additionally covered consent/check gates,
real file-decoding with a synthetic fixture, corrupted replacement invalidation,
French mobile flow, comparison/export and reset.

## Next accuracy gate

Keep human anatomical review until the automatic segmentation/landmark baseline
has been validated independently. Collect explicitly consented repeated captures
and independent blinded tape readings with an experienced tailor. Use one anatomical
protocol across marking and tape measurement. Keep participant partitions separate,
record retakes/failures, and publish field-specific error and subgroup coverage.
This export requires the supervised `build_ledger.py` adapter with attempt IDs,
participant/partition provenance, independent tape readings and failure records.
Do not drop failed attempts or mix synthetic and real test cohorts.

Production invite identity, public designer discovery, authenticated storage,
retention/deletion, native camera UX and the request lifecycle adapters are not wired
by this research surface. The two choices model capture entry points, not live services.

Browser API references: [capture](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Attributes/capture),
[object URL cleanup](https://developer.mozilla.org/en-US/docs/Web/API/URL/revokeObjectURL_static).

## Automatic outline baseline (5 November 2026)

Install/check the pinned local detector before starting the server:

```sh
python3 tools/measurement_capture/setup_assets.py
python3 tools/measurement_capture/setup_assets.py --check
```

The lock pins MediaPipe Tasks Vision **0.10.32**, the full pose model
`pose_landmarker_full/float16/1`, SHA-256 digests, sizes, model card and licence.
Eight ignored assets total 33,457,739 bytes. No floating `latest` dependency and no
runtime CDN. The source model card identifies Apache 2.0; do not substitute a GHUM
mesh licence or claim the detector is validated for metric body dimensions.

**Suggest outlines automatically** runs local CPU inference, uses the connected person
mask around the torso, and proposes chest/waist/hip rows from pose landmarks. Those
rows are heuristics, especially natural waist/full hips: review every point. The
mask excludes detached noise, rejects frame contact and unstable contours and checks
for arm overlap. Side captures with arms covering the torso are refused; arrange a
clear silhouette and review posture with a designer rather than accepting arm width.
These gates can reject legitimate bodies/poses and do not establish population coverage.

Missing assets, zero/multiple detected people, poor pose or ambiguous masks leave
manual marking available. A model score is not measurement accuracy. Reset cancels
stale UI results and closes the detector. Editing points clears the capture-confirmation
checkbox. The SDK runs synchronously after loading; worker/native inference and actual
low-end-phone responsiveness remain future work.

For file uploads, mark **synthetic image or test fixture** when appropriate. The built-in
synthetic demo is permanently marked synthetic. Uploaded image origin is a declaration,
not forensic verification. No real accuracy claims may be based on synthetic outputs.
Exports include a `pipelineVersion` that separates automatic/manual/edited sources and
a model checksum for automatic sources. The fitter is still the reviewed two-view
ellipse baseline, **not** Anny photo reconstruction or automatic anatomical truth.

### Repeatable browser verification

With the local server running, use Playwright CLI:

```sh
playwright-cli open http://127.0.0.1:8765
playwright-cli run-code --filename=tools/measurement_capture/verify_browser.cjs
```

This covers both journeys, consent/height/check gates, point-edit invalidation,
comparison/export, real detector rejection on a nonhuman drawing, corrupted file
replacement, reset during model loading, French mobile layout and blocked external
requests. It uses no real photographs.

Optional positive detector integration uses native Anny synthetic renders:

```sh
# In a separate Python 3.12 environment:
pip install -r tools/measurement_engine/requirements.txt Pillow==12.1.1
python3 tools/measurement_capture/generate_fixtures.py
playwright-cli run-code --filename=tools/measurement_capture/verify_detector.cjs
```

Fixtures/weights remain in ignored `assets/`; no binaries are committed. Both real
WASM detector views, source checksums, reviewed fitting and synthetic export passed
on Chromium and WebKit with mobile viewport emulation. An overlapping-arm side pose was correctly rejected;
a clear-arm synthetic pose passed. These tests verify plumbing and selected failure
gates, **not** real-person accuracy, pose invariance, demographic coverage or Safari.
Measured local synthetic inference-plus-outline runs were about 105 ms front / 53 ms
side in one run; these are not mobile latency or production cost guarantees.

Use `node --test tools/measurement_capture/test_*.mjs` for all baseline/outline tests.
The companion study importer is `tools/measurement_validation/build_ledger.py`.
Actual cohort references must be blinded; the UI comparison fields are not ground truth.
