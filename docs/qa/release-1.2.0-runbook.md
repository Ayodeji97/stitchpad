# StitchPad v1.2.0 — Release Runbook

Prepared 2026-08-05 on a clean, fast-forwarded `main` (HEAD `7eb0cf68`).
Flow: **tester round (iOS TestFlight + Android closed test) → collect results → submit to Play Store production.**

Companion doc for testers: `docs/qa/StitchPad-Release-Test-Plan.pdf`.

---

## 1. What's in this release (vs live 1.1.0 / 1.1.1)

| Area | PRs | User-facing |
|---|---|---|
| Staff roles / multi-user workshop | #304–#329 | Owner invites teammates; staff get a reduced, money-free, read-only work view |
| Founding Tailors leaderboard | #338, #340, #341 | Personal invite link, "Your standing" card, tiered points, monthly board |
| To collect | #312, #314 | Delivered/ready-unpaid to-do list, dashboard card, overdue hero, instant push |
| In-app ratings | #337 | Sentiment-gated native review + Settings "Rate StitchPad" |

Infra also bumped: AGP 9.2.1 / Gradle 9.6.0 / compileSdk 37 (#305), ktor 3.5.1 (#330). Analytics cohorts (#339) are docs-only — nothing for testers.

---

## 2. Verification status (done)

- ✅ `./gradlew detekt :composeApp:testDebugUnitTest :composeApp:assembleDebug` — **BUILD SUCCESSFUL**.
- ✅ Functions `npm run lint` + `npm test` — **lint clean, 637 tests passing**.
- ✅ **Full-repo pre-launch security audit** (5 parallel reviewers) — **no findings ≥8 on any surface**. Rules, payment/entitlement, other functions, client app, secrets/CI all clean. Storage-rules IDOR fix (PR #230) confirmed intact. Verdict: **SHIP**.

---

## 3. Pre-flight — do these BEFORE building the tester builds

1. **Version bump (done in working tree, needs commit):** Android `versionName` and iOS `MARKETING_VERSION` are now `1.2.0` (were `1.1.0` / `1.1.1` — they had drifted). Change if you want a different number.
   - The Android `beta` lane derives `versionCode` from git commit count and **refuses to run if there are no new commits since the last Play upload** — so the version bump **must be committed** (a release-marker commit) before `fastlane android beta`.
2. **iOS push entitlement:** `iosApp/iosApp/iosApp.entitlements` has `aps-environment = development`. **Flip to `production`** for the store/TestFlight build or push notifications (incl. the To-collect instant push) won't work on distributed builds.
3. **Do NOT commit** the untracked `composeApp/src/androidMain/res/xml/network_security_config.xml` — it's a local emulator/QA aid (permits cleartext to loopback only, and isn't wired into the manifest). It's harmless but has no place in a release. Leave it untracked or delete it.
4. **Rules parity check:** the audit reflects repo state, not deployed state. Confirm the live console matches:
   ```
   firebase deploy --only firestore:rules,storage --project stitchpad-30607
   ```
   (No-op if already in sync; otherwise it aligns them.)
5. **Functions deploy** (if any changed function isn't yet live): per PR notes, redeploy only what changed, e.g. `getFoundingTailorsLeaderboard`, `onOrderCollectible`, `dailyDigest`. Use the `deploy --only` allow-list.
6. Local secrets present (gitignored): `composeApp/google-services.json`, `iosApp/iosApp/GoogleService-Info.plist`, `fastlane/.env` with real key paths.

Suggested release-marker commit:
```
git add composeApp/build.gradle.kts iosApp/Configuration/Config.xcconfig iosApp/iosApp/iosApp.entitlements docs/qa/
git commit -m "chore(release): cut 1.2.0 — staff roles, founding tailors, to-collect, ratings"
```
(Do this on a release branch + PR per your workflow, not directly on `main`.)

---

## 4. Send the two tester builds

> These lanes need credentials and have been run locally only (Phase 1). The `before_all` hook runs `allTests`, which has failed locally before — if it blocks you, that's the known preflight gap, not a release problem.

### Android → Play closed testing (alpha)
```
cd fastlane && bundle exec fastlane android beta
```
- Builds a signed AAB (`:composeApp:bundleRelease`, `--no-configuration-cache`) and uploads to the **alpha (closed)** track as a **draft**.
- Then: **Play Console → Closed testing → Alpha → review & roll out** to your tester list.
- Note: closed testing (not internal) is what counts toward Google's 12-testers/14-days production eligibility.

### iOS → TestFlight external group
```
cd fastlane && bundle exec fastlane ios beta
```
- Reads `MARKETING_VERSION` (1.2.0), builds a signed IPA, uploads to the **TestFlight external group**.
- Watch for the ASC API-key `.env` path gotcha (`ASC_API_KEY_P8_PATH`) — a wrong path crashes this lane.
- Public TestFlight link (existing): testflight.apple.com/join/7FhZk4Yy.

Distribute `StitchPad-Release-Test-Plan.pdf` to testers with the builds.

---

## 5. Collect results

- Testers run the PDF plan and report via Tally (`tally.so/r/5BgVVb`) or getstitchpad@gmail.com.
- Triage bugs on your Lane A/B model. Fix + re-cut a build (bump commit again for Android) as needed until the plan passes on both platforms.
- Confirm the crash gate is green after the tester window.

---

## 6. Submit to Play Store production (gated — human-only)

Once testing passes and the 12/14 closed-testing requirement is satisfied:

1. **Play Console → Production → Create new release.**
2. Promote the tested AAB (or upload the release build) — same `versionCode`/`versionName` 1.2.0.
3. Complete the release notes, confirm **Data safety** is current for any new data flows, and roll out (staged % if you prefer).
4. **iOS App Store:** from the TestFlight build, submit for App Store review in App Store Connect (separate from the TestFlight distribution above).

There is **no production fastlane lane** (Phase 1 = local beta only), so the production promotion is done in the consoles by hand — intentionally.

---

## 7. Open items / notes

- iOS device QA for Founding Tailors (share sheet + leaderboard link) was still pending on device per PR #338 — the PDF covers it (FT-04/FT-05).
- Push is on a staged rollout (`rollout.ts STAGING`); To-collect push cases (TC-06/07) only fire for tester-gated accounts — flip when ready for broad rollout.
- `aps-environment` (item 3.2) is the single easiest thing to forget and silently breaks push on the store build.
