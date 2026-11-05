# Automatic measurement research checkpoint

User requested remaining work and thorough verification before asking for smoke tests.
Implemented local pose/mask-based outline suggestions with pinned MediaPipe 0.10.32/full
pose model, an owned reviewed two-view ellipse fitter, source/pipeline provenance and
manual fallback. No expensive API introduced; no photos uploaded. Model/data assets
are ignored and fetched with digest verification. Full feature is not production-ready.

Actual synthetic front/side detector-to-estimate/export path passed in Chromium and
WebKit mobile emulation. 56 offline tests pass. Missing models, uncertain bodies,
editing/undo review invalidation, corruption, stale/reset behaviour and export were
checked. See `docs/qa/2026-11-05-measurement-verification.md` for evidence and limitations.

Study importer reconciles registered attempts with blinded repeated readings, retains
failures and refuses synthetic/unvalidated-status manipulation. It cannot verify consent
or reference truth, authenticate an operator, or discover an omitted initiation registry.
Manual/automatic/edited source versions must not be pooled in evaluations.

Real tailoring accuracy remains unverified; a trained tailor and consented reference
cohort are required. Field-specific tolerances and pilot decisions remain in QUESTIONS.md.
Do not enable automatic import, claim accuracy, train on customer records or deploy a
production capture flow based on synthetic tests. Native integration/discovery/invite/
storage adapters remain after the agreed accuracy gate. No new smoke test was requested
from Daniel for accuracy; software fixtures cannot substitute for the reference study.

Original workspace and its dirty handoff/research files were preserved. Work is in
`/private/tmp/stitchpad-remote-measurement-research`, branch `feat/measurement-auto-detection`.
