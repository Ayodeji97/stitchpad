# Slice 8 — local emulator staff smoke-test runbook

Exercise the full **staff** experience against real, seeded data on the **local
Firebase emulators** — including the Slice-8e `allow list` rule that unblocks staff
LIST reads — **without touching production**. This is the safe way to catch staff-UI
issues on real data before any prod flip.

## What this gives you
- Firestore + Auth + Storage emulators running locally (isolated, throwaway).
- The emulator rules (`firestore.emulator.rules`), byte-identical to the deployed
  `firestore.rules` as of Slice 8e Task 3: active members may LIST orders/customers.
  The two-file mechanism (emulator rules vs. deployed rules) stays in place for
  previewing future rule changes, even though the two files currently match.
- Seeded workshop: Fola (owner) with 4 customers + 6 orders across every stage, in
  the post-8d stripped shape (base docs carry no money/contact — those live only in
  `/private/money` and `/private/contact`, matching what the client writes); Gabby
  (staff) with the `role=staff` claim + an active membership doc; and Fola's own
  `team/{folaUid}` roster row (`kind:'owner'`) — the same doc
  `TeamRosterRepository.ensureOwnerMember` lazily writes client-side (Phase 2b
  Task 5), seeded here so smoke doesn't depend on that write firing first.
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
This serves Firestore on `:8080`, Auth on `:9099`, Storage on `:9199`, and the
Emulator UI on `:4000`, using `firestore.emulator.rules` (byte-identical to the
deployed `firestore.rules`) and `storage.rules`. Leave it running.

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
release build can never talk to the emulator. No local file surgery needed: the
cleartext network-security config that lets a debug build reach `10.0.2.2` /
`127.0.0.1` / `localhost` over plain HTTP is committed permanently in the
`debug` build-type source set (`composeApp/src/debug/`) and merges into every
debug build automatically. Release builds never see the
`networkSecurityConfig` manifest attribute.

### 4. Smoke-test
- Sign in as **gabby@gmail.com / gabby123** → the **staff** experience with real
  data: money-free dashboard (overdue/due-today/pipeline), and **populated,
  read-only Orders & Customers lists** (the 4 seeded customers, 6 seeded orders).
  No money amounts appear anywhere (no totals, no deposits/balances, no payments),
  and no contact fields (`phone`/`email`/`address`) appear on customer rows —
  because the seeded base docs no longer carry them. Reduced Settings, Leave
  workshop. Exercise every screen; watch for crashes/layout issues that only
  appear with real data.
- Sign in as **fola@gmail.com / fola123** → the **owner** view still sees money
  (totals, deposits, balances) via `/private/money`, and full contact info via
  `/private/contact`.
- Try the **kill-switch** (#326): in the Emulator UI (`:4000`) set
  `config/app.staffFeatureEnabled = false` → Gabby drops to the owner-of-self empty
  shell. Set it back to `true` → staff experience returns.
- If staff lists are empty, check the membership doc status is `active` and the
  claims were set by the seeder.
- Image uploads (e.g. adding a style/fabric photo) now go to the local Storage
  emulator instead of production — check the Emulator UI's Storage tab (`:4000`)
  to confirm the upload landed under `users/{workshopUid}/orders/...` instead of
  failing with a 403 (emulator-issued token rejected by production Storage).

### 5. Clean up
- Flip `USE_FIREBASE_EMULATOR` back to `false` before committing / building anything
  for real. **It must be `false` on `main`.**
- Stop the emulators (Ctrl-C). Emulator data is in-memory and discarded on stop
  (add `--export-on-exit ./emulator-data` + `--import ./emulator-data` if you want
  to persist a session).

## Notes
- The seeded base docs are money/contact-free (Slice 8d strip applied at seed time),
  matching what the client actually writes — so this faithfully tests the staff
  *and* owner read paths, not just the UI's own money-stripping. The money-free-base
  guarantee is covered separately by the rules unit tests.
- `firestore.emulator.rules` and `firestore.rules` are byte-identical as of Slice 8e
  Task 3 — the `allow list` for active members is live in both. The separate
  emulator-rules file is kept as a mechanism for previewing future rule changes
  before they're promoted to the deployed file, not because the two currently
  differ.
