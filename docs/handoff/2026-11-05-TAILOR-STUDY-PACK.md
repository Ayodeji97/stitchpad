# Tailor study delivery

User asked for everything needed to invite a tailor to test the measurement feature.

Built two eight-page printable EN/FR booklets, a bilingual invitation, a portable ZIP
with pinned local model assets, Mac/Windows launch instructions, coordinator notes,
a deliberately unapproved protocol template and nine pipeline plan IDs derived from
actual fitter exports. No participant data, signatures, synthetic-render fixtures or
photo uploads are included. Generated delivery files are local artifacts, not Git assets.

Delivery folder in the original workspace: `output/measurement-study/`.
The ZIP is approximately 17 MB. Python 3 on the coordinator laptop is required;
no SDK/Node installation is required for the tailor. The link is laptop-only localhost,
not a remotely shared website. No public hosting/deployment or messages sent to others.

The booklets cover consent, actual contact/withdrawal/deletion dates, anatomical/pose
protocol approval, unfilled tolerance limits, two independent measurers with two readings
each, all attempts/retakes, blind review and deidentified return/deletion. One tailor
alone can rehearse usability but cannot fulfill this protocol's independent accuracy gate.
A disagreement needs a third blinded independent measurer or remains unresolved.

The importer now supports independent blinded repeated adjudication while preserving
original references. Added regression tests reject reused/unblinded adjudicators,
inconsistent repeats and unknown fields. Do not silently remove unresolved participants.

Verified all 16 rendered pages, PDF page/text counts, ZIP CRC and all included asset
hashes. Checked launcher shell syntax/execution in a path with spaces, loopback serving,
no uploads/listings, and the extracted kit browser workflow on its own server. Windows
batch was provided but no Windows runtime session was available. 63 tool tests pass.

The prototype is still experimental. This first rehearsal is development data, not
representative accuracy evidence. The tailor/coordinator must fill the real agreement
fields and approve definitions, poses and limits before collecting accuracy data.
There is no model-training consent, production release or automatic customer import.
Source builder/instructions: `tools/measurement_study/README.md`.
