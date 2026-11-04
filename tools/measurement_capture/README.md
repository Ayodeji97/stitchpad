# Guided measurement capture — local research prototype

Run from the repository root (Python 3 and a modern browser; no dependencies):

```sh
python3 -m http.server 8765 --bind 127.0.0.1 --directory tools/measurement_capture
```

Open <http://127.0.0.1:8765>. The server serves this folder only. There is no upload
endpoint, paid API, image persistence, analytics, external font or trained ML model.
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

`engine.mjs` normalizes each manually marked front/side diameter by known barefoot
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

Replace manual annotation only after a commercially usable segmentation/landmark
baseline is selected and evaluated. Collect explicitly consented repeated captures
and independent blinded tape readings with an experienced tailor. Use one anatomical
protocol across marking and tape measurement. Keep participant partitions separate,
record retakes/failures, and publish field-specific error and subgroup coverage.
This export is **not** directly accepted by the validation ledger: a supervised
adapter must supply attempt IDs, participant/partition provenance and failure records.
Do not drop failed attempts or mix synthetic and real test cohorts.

Production invite identity, public designer discovery, authenticated storage,
retention/deletion, native camera UX and the request lifecycle adapters are not wired
by this research surface. The two choices model capture entry points, not live services.

Browser API references: [capture](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Attributes/capture),
[object URL cleanup](https://developer.mozilla.org/en-US/docs/Web/API/URL/revokeObjectURL_static).
