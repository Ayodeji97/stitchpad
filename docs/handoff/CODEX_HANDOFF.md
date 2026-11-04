# StitchPad — Codex handoff

Updated: 2 November 2026.

## Working agreement

Codex implements, Claude Code reviews, Daniel decides. Product questions belong in
`docs/handoff/QUESTIONS.md`. Never commit Firebase config/credentials. Use the project
AGENTS.md patterns (MVI, Root/Screen, Result, UiText, Koin and Compose resources).

## Current development stage

- PR #374 (country foundation) is merged into main at 6e8b5b4f.
- Active integration branch: `feat/africa-currency`, based on the latest main.
- Currency implementation is integrated and automated checks pass. The tested phone-field
  and stable country-picker refinements are preserved here; they were local after #374 merged.
- Currency work includes immutable order denomination, decimal-aware amounts/inputs,
  separate monetary totals per currency, currency-stamped goals, staff workshop market
  resolution, and currency-correct notification/AI draft payloads.
- No trial/read-only enforcement, grant migration, production deployment, or country-store
  opening is part of the currency work.

## Build order and dependencies

1. Complete/review/smoke-test the currency PR before merging.
2. Trial/read-only enforcement: separate branch `codex/africa-pr3-trial-plan` has dormant
   policy helpers and plans only. Do not activate partial enforcement.
3. Play Billing/bank details, server timezone scheduling, full French, market parity,
   garments/analytics/copy and store readiness remain later work under the expansion spec.

The full plan is `docs/superpowers/specs/2026-10-29-africa-expansion-design.md`.
Currency implementation notes are in the dated PR2 plan and
`docs/qa/2026-11-02-currency-smoke.md`.

## Approved product decisions

- Display separate totals per currency; no FX conversion or mandatory currency filter.
- End existing indefinite launch-free grants 30 days after the complete trial/read-only
  rollout deployment, with advance notice. Persist a fixed cutoff so replays do not extend it.
  Preserve paid entitlements. Unpaid accounts retain read-only record access afterwards.
- English is allowed temporarily while full French support is completed.

Other decisions remain in QUESTIONS.md; do not treat recommendations as approval.

## Runtime prerequisites and verification

- Staff new-order creation needs the new membership-checked `getWorkshopMarket` callable.
  It returns market metadata only; owner profile rules remain private. Include it in the
  standard explicit Functions deploy allowlist. Deployment requires the normal reviewed rollout.
- Runtime simulator interaction was unavailable because computer access to Simulator was not
  approved. Automated builds/tests are separate from owner smoke testing.
- Final integrated results: 2,239 Android tests, assembleDebug, iOS simulator source compilation
  and detekt pass; 977 backend tests, TypeScript build and lint pass. See the PR2 plan.

## Parallel work safety

Agent worktrees are at `../StitchPad-agent-worktrees/`; they are separate checkouts.
Do not reset/remove other agent branches/worktrees or overwrite local work.
The original country smoke-test changes were committed locally on `feat/africa-expansion`
at 09a876fe before switching the main workspace to the currency integration branch.
