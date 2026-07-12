# Referral debug actions — design

**Date:** 2026-07-12
**Status:** Approved (design), pending implementation
**Goal:** Add in-app Debug-menu actions to exercise the referral flow on-device, so the
referral feature can be validated before the release that turns the marketing team on.

## Context & constraint

Referral state lives in **admin-only, top-level server collections** (`referrals/{uid}`,
`marketers/`, `referralCodes/`, `referralDevices/`) that Firestore rules forbid the client
from reading or writing. Therefore the debug actions split into two groups:

- **Group A — client-side**: works for any tester on a debug build, on their own account.
  Uses only paths a normal user is allowed: the `recordReferralAttribution` callable, writes
  to `users/{uid}` and `users/{uid}/customers`, and local `ReferralPreferences`.
- **Group B — admin-only**: calls the already-deployed debug Cloud Functions
  (`debugReconcileReferrals`, `debugConfirmReferralPayouts`, `debugSweepDeletedReferralUsers`).
  These are `admin:true`-gated, so they no-op with a clear "admin only" error for normal testers.

**No new server code.** Steps with no client/callable path today (expire the 7-day hold, set a
fraud flag, mark-paid, per-referral status readout) stay on the CLI scripts / `admin.html`
dashboard. This keeps the release change client-only.

## Actions

### Group A (client)
1. **Attribute with code** — `attributeWithCode(code)`: calls
   `ReferralRepository.recordAttribution(code, deviceId, ReferralSource.MANUAL)` with
   `deviceId = ReferralPreferencesStore.getOrCreateDeviceId()`. Surfaces the real outcome
   (`marketerId` / `alreadyAttributed`) or the mapped `ReferralError` (e.g. `CODE_NOT_FOUND`).
   Lets a tester exercise capture→server on-device without a real `/r/` link.
2. **Seed qualification** — `seedQualification(nowMs)`: writes `users/{uid}.businessName`
   (default `"QA Test Workshop"`) and creates 4 customers with `createdAt` = `now`, `now+1d`,
   `now+2d`, `now+3d` — 4 distinct Africa/Lagos days inside the qualification window. Uses
   `now()` as the base because the client cannot read/refresh `referrals/{uid}`; valid whenever
   ≥4 days remain in the window (always true for a fresh signup). After this, Group-B "Run grader"
   (or the nightly grader) promotes attributed→qualified→pending.
3. **Reset capture state** — `resetCaptureState()`: calls
   `ReferralPreferencesStore.resetForDebug()` (clears attributed + checked flags; stable device
   id is intentionally kept). Lets capture be re-run.

### Group B (admin-only; reuse deployed callables)
4. **Run grader** — `runGrader()` → `httpsCallable("debugReconcileReferrals")`.
5. **Run confirm payouts** — `runConfirmPayouts()` → `httpsCallable("debugConfirmReferralPayouts")`.
6. **Run deleted-user sweep** — `runSweep()` → `httpsCallable("debugSweepDeletedReferralUsers")`.

Each returns `DebugActionResult.Success` (with any returned counts in the message) or
`.Failure` — a `PERMISSION_DENIED` from a non-admin account maps to a clear "admin only" message.

## Components

- **`core/debug/ReferralDebugActions.kt`** — `interface ReferralDebugActions` +
  `class DefaultReferralDebugActions`, mirroring `FreemiumDebugActions` (Group A, client writes)
  and `DigestDebugActions` (Group B, callable invocation). Reuses the existing
  `DebugActionResult` sealed type. Constructor deps: `ReferralRepository`,
  `ReferralPreferencesStore`, `CustomerRepository`, `AuthRepository`, `FirebaseFirestore`,
  `FirebaseFunctions`.
- **`DebugMenuAction`** — new actions: `OnReferralAttributeClick` (opens dialog),
  `OnReferralAttributeDismiss`, `OnReferralAttributeCodeChange(value)`,
  `OnReferralAttributeConfirm`, `OnReferralSeedQualificationClick`,
  `OnReferralResetCaptureClick`, `OnReferralRunGraderClick`,
  `OnReferralRunConfirmClick`, `OnReferralRunSweepClick`.
- **`DebugMenuState`** — `referralAttribute: ReferralAttributeDialogState?` holding the code
  text field (valid when non-blank after normalization).
- **`DebugMenuViewModel`** — new `referralActions: ReferralDebugActions` dep; handlers run each
  action under the existing `isWorking` guard and emit the existing result event
  (snackbar) on success/failure.
- **`DebugMenuScreen`** — a new **"Referral"** section: "Attribute with code…" (opens the
  dialog), "Seed qualification (business + 4 days)", "Reset capture state", then an
  "Admin only (needs admin:true)" subgroup with "Run grader", "Run confirm payouts",
  "Run deleted-user sweep". Section carries a one-line note about the read/hold limits.
- **`DebugModule`** — provide `ReferralDebugActions` and pass it into the `DebugMenuViewModel`
  factory.

## Data flow

Attribute: Screen → `OnReferralAttributeConfirm` → VM → `referralActions.attributeWithCode(code)`
→ `ReferralRepository.recordAttribution` (callable) → result event → snackbar.
Seed: VM → `seedQualification(now())` → firestore `users/{uid}` merge + 4×`createCustomer` →
snackbar. Grade later via Group B or nightly.
Group B: VM → `referralActions.runGrader()` → `functions.httpsCallable(...)` → snackbar.

## Error handling

- Not signed in → `DebugActionResult.Failure("Not signed in")` (matches existing actions).
- Attribution: reuse `ReferralRepository`'s `Result<…, ReferralError>`; map error to a readable
  string (code not found, unauthenticated, network).
- Group B non-admin: `FirebaseFunctionsException` PERMISSION_DENIED → `Failure("Admin only")`;
  other codes → `Failure(message)`.
- All wrapped in try/catch → `AppLogger.e` + `Failure`, same as `FreemiumDebugActions`.

## Testing

`DefaultReferralDebugActionsTest` (commonTest, kotlin.test + Turbine style used elsewhere) with
fakes: fake `ReferralRepository` (assert code/source/deviceHash passed; success + error paths),
fake `CustomerRepository` (assert 4 customers on 4 distinct Lagos day-keys + businessName write),
fake prefs (assert `resetForDebug` called), fake functions/callable results for Group B
(success + PERMISSION_DENIED → "Admin only"). `now` injected as `() -> Long` for deterministic
day math (mirrors `FreemiumDebugActions` + `DebugModule.nowEpochMs`).

## Known limits (documented in the section UI)

- No per-referral status in-app — reading `referrals/{uid}` is admin-only; feedback comes from
  Group-B callable results + the Firebase console.
- Testing **confirm** still needs the 7-day hold expired via `referralQaTweak.js --expire-hold`
  (no client/callable path). Seed→grade→(wait 7d or expire-hold)→confirm.
- Debug-build gated like the rest of the menu (`DebugBuild`).

## Out of scope

New admin Cloud Functions for expire-hold / set-flag / mark-paid / status readout; per-tailor
marketer self-service; any change to the production referral lifecycle.
