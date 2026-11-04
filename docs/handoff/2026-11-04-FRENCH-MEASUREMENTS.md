# Autonomous French and measurement checkpoint

French branch: `feat/french-localization`, stacked on `feat/dashboard-earnings-followup`
so the approved compact earnings UI is preserved. Device language controls French;
workshop country and order currency remain independent. Resource coverage/format
validator is included in CI. Client translations are machine-assisted and require
French-speaking tailoring review. Auth, daily digest/nudges/push and AI drafts support
French; legacy gift, campaign and subscription emails remain an explicit follow-up.
Smoke procedure: `docs/qa/2026-11-04-french-smoke.md`.

Measurement research: PR #381. Dormant lifecycle/native geometry foundation: PR #382,
stacked on research. Both journeys are modeled. No production endpoint, image upload,
photo fitting or validated measurement engine is enabled. Native Anny synthetic CPU
experiment succeeded; it is not evidence of photo accuracy. Six geometry tests,
ten evaluation ledger tests and 992 backend tests pass. Real tape-reference cohort
validation is required before customer release. No paid API dependency introduced.

No production deployment, store release or trial/read-only activation performed.
Daniel requested autonomous implementation, with contact only for smoke tests.
Preserve the original workspace's dirty handoff docs and unrelated research outputs.
