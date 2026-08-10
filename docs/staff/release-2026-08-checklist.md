# Release 2026-08 (v1.2.0) — Go/No-Go Checklist + Post-Approval Runbook

Status as of 2026-08-10, on main `3cc7c92a` (Slice 8e + Phase 2a + Phase 2b):

- **Security audit: SHIP** — 5-surface full-codebase audit, zero findings at the
  confidence-≥8 bar (summary at the bottom).
- **Full-app smoke: PASS** — owner + staff on the two-emulator rig, details below.
- **CI: green** on the Phase 2b merge commit; gitleaks gate clean.
- **8b backfill: COMMITTED in production 2026-08-10** — verification dry run showed
  121/121 orders and 172/172 customers mirrored, 0 to create.

## A. Before store submission (go/no-go)

- [x] Phase 2b merged to main, CI green (`3cc7c92a`).
- [x] 8b backfill committed in production; verification dry run clean.
- [x] Pre-launch security audit → **SHIP** verdict.
- [x] Full-app two-emulator smoke test → PASS (owner money/contact intact; staff
      money-free and contact-free; assignment, claim, status update, kill switch).
- [x] `USE_FIREBASE_EMULATOR = false` on main (`EmulatorConfig.kt`).
- [ ] Versions: both platforms ship **1.2.0**; build numbers derive from git commit
      count in both fastlane lanes (`current_build_number`), so cut the release from
      the commit you intend to ship.
- [ ] `bundle exec fastlane android beta` → Play **alpha (closed)** track. Preflight
      (detekt + `:composeApp:allTests`) runs automatically in `before_all`.
- [ ] `bundle exec fastlane ios beta` → TestFlight. (iOS release build is CI/fastlane
      territory — local Xcode iOS link is known-broken, that's expected.)
- [ ] `RELEASE_NOTES` env var set for both lanes (falls back to last commit subject).
- [ ] Daniel: promote to production in Play Console + submit for App Store review.

**Do NOT deploy the new `firestore.rules`/`storage.rules` yet.** The deploy is
step C-7 below and is hard-gated behind the version floor: a pre-8d-1 client writes
money/contact on every save, so deploying early breaks saving for anyone not yet
updated (runbook: docs/staff/slice8e-rollout-runbook.md).

## B. While the app is in review

Change nothing in production: no rules deploy, no strip, no floor change, no
backfill commit. All of it waits for the released client to be live and adopted.
(A store rejection changes nothing server-side — fix, resubmit.)

## C. After approval — in this order (gates are load-bearing)

The full detail lives in `docs/staff/slice8e-rollout-runbook.md`; this is the
ordered index:

1. **Confirm the release is live on BOTH stores** (listing shows the new build).
2. **Set the 8c version floor** to the released build numbers; verify a
   below-floor build shows the force-update screen:
   `cd functions && ANDROID_FLOOR=<versionCode> IOS_FLOOR=<CFBundleVersion> GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/setUpdateFloor.js --commit`
3. **Adoption wait** (days → ~2 weeks): watch crash/ANR and adoption %.
4. **8b backfill DRY RUN** (`node scripts/backfillSensitiveFields.js`). Expected
   steady state: `ordersAlreadyMirrored` = total orders,
   `customersAlreadyMirrored` = total customers, both `MirrorCreated` = 0.
   Investigate any non-zero before committing.
5. **Firestore export** (the only rollback for the strip — take it BEFORE the
   first `--commit` of this sequence):
   `gcloud firestore export gs://stitchpad-30607.appspot.com/exports/pre-8d-strip-$(date +%Y%m%d) --project=stitchpad-30607`
6. **8b backfill `--commit` re-run** (release-time re-run per runbook — stamps
   every legacy mirror; counts must match the dry run; re-dry-run until 0).
7. **Strip** (irreversible): dry run → investigate every skip warning →
   `--commit` → verify owner devices still show money/contact.
8. **HARD GATE:** a FRESH strip dry run reports all five counts zero
   (`ordersStripped`, `customersStripped`, `ordersSkippedUnstamped`,
   `ordersSkippedLegacyDeposit`, `customersSkippedUnstamped`).
9. **Rules deploy** — Phase 2b's widened Storage rules ride the same flip:
   `firebase deploy --only firestore:rules,storage --project=stitchpad-30607`
10. **Device verify:** staff Orders + Customers lists populate; no money or
    contact anywhere on the staff build; owner create/edit still round-trips
    money/contact. A `permission-denied` on an owner save means a below-floor
    build is in play → roll rules back, re-check the floor.
11. **Post-flip monitoring:** strip DRY RUN after 1–2 weeks; if any count is
    non-zero, export → re-strip → re-check. Repeat until two consecutive clean runs.

**Panic switch:** `config/app.staffFeatureEnabled = false` (staff sessions drop to
owner-of-self). Rules rollback: redeploy previous `firestore.rules`. Strip
rollback: import the step-5 export (loses writes since the export).

## Security audit summary (2026-08-10, full-codebase, 5 parallel reviewers)

**Verdict: SHIP.** Surfaces: Firestore/Storage rules; billing + entitlement
functions (Paystack, Apple IAP, gifts, referrals); all other functions (email,
WhatsApp, notifications, staff, cleanup); client app (Android + iOS); secrets +
CI. Zero findings at the report bar (confidence ≥8, concretely exploitable).

Blocking checks — all pass: storage.rules present/registered/owner-scoped (with
the deliberate staff widening); every collection uid- or membership-scoped with
default-deny; entitlement fields server-only; Paystack/Apple/WhatsApp webhooks
verify signatures over the raw body before any state change; every privileged
callable uses `context.auth.uid`; no secrets tracked in git.

Notes (no action required for this release):

- LOW: `GoogleService-Info.plist` existed in old git history (removed since).
  All values are public-by-design client identifiers. Optional hardening: iOS
  bundle-ID restriction on the API key; history rewrite only if the repo ever
  goes public.
- Below-threshold, documented-deliberate: staff can edit work fields on any
  workshop order (trust model); staff Storage write covers overwrite/delete
  under `orders/**`; `assembleReleaseSmoke` isn't matched by the test-creds
  release guard (dev-machine-only property); Napier logs at debug level in
  release (call sites carry no PII).

## Smoke test summary (2026-08-10, two-emulator rig)

Debug builds against local Firebase emulators (Firestore/Auth/Storage), seeded
via `emulatorSetupStaff.js`. Exercised: onboarding carousel → sign-in (both
accounts); owner dashboard/Orders/order detail/Customers/Reports (money +
contact present everywhere they should be — the `/private` collection-group read
path works against post-strip-shaped docs); owner assigns an order to Gabby from
the assign sheet; staff dashboard (money-free, "Mine" counts live-update),
staff All/My-work filters (All resets correctly), staff order detail (no money
card, no contact buttons), staff claim + production-status advance (rules
allowlist), staff Customers (names only); kill switch off → Gabby drops to
owner-of-self shell, on → staff experience returns; owner Team screen shows
correct workload counts (Gabby 2 open / Unassigned 3) and seat usage.

Fixed during the run: `emulatorSetupStaff.js` now seeds Gabby's
`team/{gabbyUid}` roster row (production writes it in `approveStaffMember`,
which the seeder bypasses — without it staff are active but unassignable).
