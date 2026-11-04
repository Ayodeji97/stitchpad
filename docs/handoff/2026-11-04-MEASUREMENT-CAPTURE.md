# Measurement capture prototype checkpoint

Branch: `feat/measurement-capture-prototype`, stacked on
`feat/remote-measurement-foundation` (PR #382; research in #381).

## Delivered

Local EN/FR capture flow for existing customer and designer-discovery entry points.
Consent, known barefoot height, front/side images, manual anatomical annotation,
framing/brightness/input checks, experimental chest/waist/hips estimates, optional
tape comparisons and image-free JSON export. No photos leave the browser. No records,
invites or public discovery are connected. This is outside the production app.

The owned two-view ellipse baseline is a geometric control, not ML, automatic
segmentation, Anny photo fitting or a validated tailoring engine. No API fees or
new inference-model dependencies. Synthetic demonstration is explicitly flagged.

## Verification

12 engine tests, 6 mesh-section tests and 10 validation-ledger tests pass. Browser
smoke covered both journeys, consent/check gates, EN desktop, FR at 390 px, file
loading with a synthetic image, corrupt replacement invalidation, signed comparison,
download and reset. Desktop and mobile screenshots inspected. No app launched.

## Next implementation

Select/evaluate commercially usable automatic segmentation and pose/landmark models
against the manual annotation control. Then fit a body/cross-section model, record
quality failures and evaluate against consented independent repeated tape readings.
Do not substitute this ellipse experiment for a trained, evaluated engine. Native
KMP capture, authenticated lifecycle/storage and production review come later.

Smoke instructions: `tools/measurement_capture/README.md`. Run the local server
from that worktree; no Functions deployment or native build is required.
