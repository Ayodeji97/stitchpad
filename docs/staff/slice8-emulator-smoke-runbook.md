# Slice 8 — local emulator staff smoke-test runbook

Exercise the full **staff** experience against real, seeded data on the **local
Firebase emulators** — including the future Slice-8e `allow list` rule that lights
staff up — **without touching production**. This is the safe way to catch staff-UI
issues on real data before any prod flip.

## What this gives you
- Firestore + Auth emulators running locally (isolated, throwaway).
- The **Slice-8e-preview rules** (`firestore.emulator.rules`: members may LIST).
- Seeded workshop: Fola (owner) with 4 customers + 6 orders across every stage, in
  the full dual-write shape; Gabby (staff) with the `role=staff` claim + an active
  membership doc.
- A debug app pointed at the emulators, so you can sign in as **Gabby** and see the
  staff dashboard / Orders / Customers populated with real data.

## Prerequisites
- Firebase CLI installed and logged in (`firebase login`).
- `functions/` deps installed (`cd functions && npm ci`).
- Java (for the Firestore emulator).

## Steps

### 1. Start the emulators (one terminal, repo root)
```
firebase emulators:start --config firebase.emulator.json
```
This serves Firestore on `:8080`, Auth on `:9099`, and the Emulator UI on `:4000`,
using `firestore.emulator.rules` (the 8e-preview rules). Leave it running.

### 2. Seed users, claims, membership, and data (second terminal)
```
cd functions && node scripts/emulatorSetupStaff.js
```
Creates Fola + Gabby (email-verified, known passwords), Gabby's staff claim +
active membership, and Fola's seeded customers/orders. Re-runnable (idempotent).

### 3. Point the app at the emulators and build a DEBUG app
In `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/config/EmulatorConfig.kt`,
flip:
```kotlin
const val USE_FIREBASE_EMULATOR = true
```
Then build + install a **debug** build:
- **iOS sim:** build the `iosApp` debug scheme to a booted simulator, install, launch.
  (iOS sim reaches the emulators at `127.0.0.1`.)
- **Android emulator:** `./gradlew :composeApp:installDebug` to a running AVD.
  (Android reaches the host at `10.0.2.2` — handled automatically.)

The connection is doubly guarded (`isDebugBuild && USE_FIREBASE_EMULATOR`), so a
release build can never talk to the emulator.

### 4. Smoke-test
- Sign in as **gabby@gmail.com / gabby123** → the **staff** experience with real
  data: money-free dashboard (overdue/due-today/pipeline), read-only Orders &
  Customers, reduced Settings, Leave workshop. Exercise every screen; watch for
  crashes/layout issues that only appear with real data.
- Sign in as **fola@gmail.com / fola123** → the **owner** view (money via `/private`).
- Try the **kill-switch** (#326): in the Emulator UI (`:4000`) set
  `config/app.staffFeatureEnabled = false` → Gabby drops to the owner-of-self empty
  shell. Set it back to `true` → staff experience returns.

### 5. Clean up
- Flip `USE_FIREBASE_EMULATOR` back to `false` before committing / building anything
  for real. **It must be `false` on `main`.**
- Stop the emulators (Ctrl-C). Emulator data is in-memory and discarded on stop
  (add `--export-on-exit ./emulator-data` + `--import ./emulator-data` if you want
  to persist a session).

## Notes
- The emulator base docs still carry money (no Slice-8d strip here); the staff UI
  strips money at render, so this faithfully tests the staff *experience*. The
  money-free-base guarantee is covered separately by the rules unit tests.
- `firestore.emulator.rules` is the ONLY place the 8e `allow list` flip lives — it
  is deliberately NOT in the deployed `firestore.rules` until Slice 8d/8e ship.
