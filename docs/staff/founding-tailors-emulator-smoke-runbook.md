# Founding Tailors — local emulator smoke-test runbook

Exercise the full **Founding Tailors leaderboard** pipeline against the **local
Firebase emulators** — link minting, the qualify → aggregate → public-read path,
and the app UI — **without touching production**. Companion to
`slice8-emulator-smoke-runbook.md`.

## What this gives you
- Firestore + Auth + **Functions** emulators running locally (isolated, throwaway).
- Two repeatable backend smoke drivers that run the REAL compiled handlers:
  - `functions/scripts/foundingTailorsSmoke.js` — mint → aggregate → public read.
  - `functions/scripts/foundingTailorsReconcileChainSmoke.js` — the full grader
    chain (attribution → one customer/day + reconcile-the-next-night ×4), proving
    `qualifiedAt` is stamped from the earning day and never overwritten.
- A debug app pointed at the emulators for the dashboard-card / Settings flow.

## Prerequisites
- Firebase CLI installed and logged in (`firebase login`).
- `functions/` deps installed (`cd functions && npm ci`), Java (Firestore emulator).
- Functions BUILT: `cd functions && npm run build` (the scripts load from `lib/`).

---

## Tier 1 — Backend smoke (fast, no device)

### 1. Start the emulators (repo root, one terminal)
```
firebase emulators:start --config firebase.emulator.json
```
Serves Firestore `:8080`, Auth `:9099`, Functions `:5001`, Emulator UI `:4000`.
Leave it running. (If a port is taken, an emulator is already up — reuse it.)

### 2. Run the smoke drivers (second terminal, from `functions/`)
```
export FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GCLOUD_PROJECT=stitchpad-30607
node scripts/foundingTailorsSmoke.js                 # 19 checks
node scripts/foundingTailorsReconcileChainSmoke.js   # 12 checks
```
Each must end in `ALL CHECKS PASSED` (exit 0). Use a SINGLE line for the env vars,
or `export` them as above — a `\` line-continuation with a trailing space silently
splits the command and the script refuses (its emulator guard fired, not a bug).

> The mint/aggregate script's reset WIPES the `marketers` + `referralCodes`
> collections on start. Run the chain script AFTER it, not before.

### 3. Verify the public read the way the app + web page call it
Unauthenticated callable over HTTP, `europe-west1`:
```
curl -s -X POST \
  "http://127.0.0.1:5001/stitchpad-30607/europe-west1/getFoundingTailorsLeaderboard" \
  -H "Content-Type: application/json" -d '{"data":{"code":"CHAINCODE0"}}'
```
Expect ranked `top` rows (NO `marketerId` leaked) and `you: {rank, points}`.

### 4. Eyeball state in the Emulator UI (`:4000`, Firestore tab)
- `marketers/*` → `program: founding_tailors`, `payoutRatePerUser: 0`, `type: user`.
- `referrals/*` → `milestone`, `qualifiedAt` (earning day at 11:00Z / noon Lagos).
- `leaderboards/2026-08` + `leaderboards/current` + `leaderboards/alltime`.

---

## Tier 2 — App smoke (Android / iOS)

### 1. Point the app at the emulators (debug only)
In `core/config/EmulatorConfig.kt`, `USE_FIREBASE_EMULATOR = true`. The connection
is doubly guarded (`isDebugBuild && USE_FIREBASE_EMULATOR`) so release can never
reach the emulator. Android needs the loopback cleartext allowance
(`androidMain/res/xml/network_security_config.xml` + the `networkSecurityConfig`
attr on `<application>`) — the staff runbook's "handled automatically" is WRONG for
Android's founding-tailors path, which also hits Functions.

Build + install a DEBUG build:
- **Android AVD:** `./gradlew :composeApp:installDebug` (reaches the host at `10.0.2.2`).
- **iOS sim:** build the `iosApp` debug scheme to a booted sim (reaches `127.0.0.1`).

### 2. Walk the flow
1. **Sign UP fresh** — emulator Auth is empty and separate from prod
   (`USER_NOT_FOUND` on your prod email is expected).
2. **Provision owner** so the entry points render: Debug Menu →
   **"Seed active workshop"**. Without it a fresh emulator account resolves as
   active-staff / non-owner, so the owner-only "Invite & rewards" section AND the
   dashboard card are HIDDEN. This is an EMULATOR ARTIFACT (server custom claims +
   remote config aren't provisioned locally), not a feature bug.
3. Open **Settings → Invite & rewards → Founding Tailors** (and the dashboard card).
4. First open → link **mints** and shows (watch the Functions emulator log for
   `getOrCreateMyReferralLink`).
5. **Share my invite link** → WhatsApp opens with the message + link (no em dashes).
6. **Reopen** → same code, no second mint (idempotent).
7. Repeat on iOS.

### Known caveats (own tickets — NOT Founding Tailors bugs)
- **"View leaderboard" 404s locally** — it opens the prod
  `getstitchpad.com/founding-tailors` URL, not served by the emulator. Verify that
  leg with the Tier-1 `curl` instead.
- **iOS sign-out → sign-in crash** (`propagateExceptionFinalResort` → SIGABRT).
  Capture the "Uncaught Kotlin exception:" line from the Xcode console to triage.

---

## Clean up (before committing / merging)
- Revert the local emulator-only changes so the PR stays clean:
  `EmulatorConfig.kt` (`USE_FIREBASE_EMULATOR` back to `false`), `StitchPadApp.kt`,
  `firebase.emulator.json`, `AndroidManifest.xml`, and delete
  `network_security_config.xml`. **`USE_FIREBASE_EMULATOR` must be `false` on `main`.**
- Stop the emulators (Ctrl-C). Emulator data is in-memory and discarded on stop
  (add `--export-on-exit ./emulator-data --import ./emulator-data` to persist).

---

## Production deploy & rollout order (do NOT reorder)
The web page calls the `getFoundingTailorsLeaderboard` callable, so the functions
MUST be live before the page ships, or the public page errors.
1. **Functions first:** `cd functions && npm run lint && firebase deploy --only
   functions:reconcileReferrals,functions:getOrCreateMyReferralLink,functions:aggregateFoundingTailorsLeaderboard,functions:getFoundingTailorsLeaderboard`
   (all four are already in the `deploy` allow-list).
2. **Seed one aggregation** so `leaderboards/current` exists before anyone loads the
   page (invoke the aggregator once, or wait for the 04:00 Lagos schedule).
3. **Deploy the web page** (Vercel, via the `stitchpad-web` repo push — PR #33).
4. **Ship the app** through the normal PR + store pipeline (PR #338).
5. Announce in the community with the launch message from the spec.

## Notes
- Backend is unit + integration covered; `qualifiedAt` stamp logic lives in
  `reconcileReferrals.ts` (~L487-505) and stamps the 4th distinct active Lagos day
  at noon Lagos, NOT the grader run instant (the month-boundary fix).
- Aggregator is a pubsub schedule with no debug twin, so emulator sessions trigger
  it via `foundingTailorsSmoke.js` / a one-off `require('../lib/...')` call.
