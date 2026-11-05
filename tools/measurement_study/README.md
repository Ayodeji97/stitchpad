# Tailor study pack

This folder builds a portable local research kit and two eight-page print booklets
(EN/FR). It contains no participant data. The prototype remains experimental.

## Build

Use a separate environment with `reportlab==5.1.0` and `pypdf==6.21.4`; Node is a
build-time helper only. Install/verify the pinned detector assets first:

```sh
python3 tools/measurement_capture/setup_assets.py
python3 tools/measurement_study/build_pack.py --output /path/to/delivery
```

The kit embeds all eight verified detector assets but excludes synthetic render
fixtures, caches, study records and tests. The model's licence/card are included.
The generated ZIP is about 17 MB. Do not commit it, the model binaries or real data.

`pipeline_plans.mjs` derives all nine A/E/M source pairs from the real fitter so
planned attempts need not guess or retroactively copy an engine version from a result.
Booklet page 6 records the planned pair before capture; the coordinator uses the JSON
plan map when preparing the private registry. Editing/manual fallbacks remain separate.

## Use

A coordinator prepares a laptop with Python 3, unzips the whole kit and launches it.
The tailor needs no development tools. The launcher binds loopback on a free port,
opens the local browser and serves only the capture directory. No upload handler or
directory listing is provided. It is not a remote website; do not expose it publicly.
Windows batch instructions are supplied; no Windows runtime session was available.

Print the chosen language. Read pages 1–2, then copy pages 3–8 per adult participant.
The pack includes operational agreement, blank tolerance/protocol approval fields,
independent reference sheets, every-attempt log and return/deletion checklist. Only
page 7 is opened after both reference sheets are sealed. If needed, copy page 4 for
a third independent blinded adjudicator, labelled C; preserve original readings.
No model-training permission or production measurement approval is granted by the pack.

The coordinator must fill the real contact/withdrawal/deletion dates and get the
lead tailor's agreement on definitions, poses and limits before real accuracy data.
One measurer can rehearse usability, but the protocol's accuracy check requires two
independent measurers, two readings each; disagreement may need a third person.
Do not infer representative accuracy or a release decision from this first rehearsal.

## Verification

All 16 pages were rasterized and visually inspected, including both revised review
pages. PDF text/page counts and print-only form status were checked. ZIP CRC, absence
of personal/synthetic image fixtures, all model hashes and a launcher path containing
spaces were checked. The extracted kit passed the existing browser suite on its own
local server: both journeys, consent/checks, comparison/export, real model rejection,
missing-model retry, reset/corruption and French mobile layout.

```sh
python3 -m unittest discover -s tools/measurement_study -p 'test_*.py'
python3 -m unittest discover -s tools/measurement_validation -p 'test_*.py'
```

The importer now supports reasoned adjudication using a new independent blinded
measurer and at least two consistent readings, without overwriting original references.
This closes the paper-to-tool workflow; it does not verify the truth of operator
consent, blinding or measurement declarations. Keep data outside Git and the kit.
