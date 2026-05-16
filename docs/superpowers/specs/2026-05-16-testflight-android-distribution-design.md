# TestFlight + Play Internal Testing — Beta Distribution

**Date:** 2026-05-16
**Status:** Design — awaiting plan
**Owner:** Daniel
**Audience:** Solo dev + PM intern; ~5–10 Nigerian tailor pilot testers

## Goal

Ship signed beta builds of StitchPad to a small pilot tester group on both platforms with one command per platform, in under ten minutes each, with no Xcode-export ceremony.

- **iOS** → TestFlight (external testing group)
- **Android** → Google Play Console **Internal Testing** track

## Non-goals

- Public Play / App Store production listing.
- Listing metadata polish (screenshots, descriptions, ratings, store search optimization).
- Paid-tier in-app purchases / subscription wiring.
- Per-PR ephemeral builds for code-review purposes.
- Crashlytics dSYM upload automation (tracked separately in `project_crashlytics_remote_logging`).
- GitHub Actions hosted CD (Phase 2, separate spec).

## Approach summary

Two Fastlane lanes per platform, run locally from Daniel's MacBook in Phase 1. The same `Fastfile` is reusable from a future GitHub Actions workflow (Phase 2) with no logic changes — only the trigger and secret source differ.

Phase 2 is explicitly deferred. We pick local Fastlane first because:

- Lower setup cost (~1 day vs ~2 days).
- No macOS-runner billing on private repos.
- One reproducible script beats Xcode point-and-click; that's the main pain we're solving.

Migration to CI is cheap from this starting point — the lane code stays, only invocation and secrets move.

## Hard prerequisites

These MUST be in place before the first beta build can ship. They are not implemented by this spec; they are gating blockers we verify before kickoff.

1. **Sign in with Apple verified on a release archive.** The entitlement (`com.apple.developer.applesignin` in `iosApp.entitlements`), GoogleSignIn SPM dependency, and `AppleSignInLauncherIos.swift` are already on `main`. What is NOT yet verified is that the flow still works on a code-signed Release archive (GitLive iOS auth has surfaced regressions before; see `feedback_gitlive_ios_nonnull_tokens`). Apple's first external TestFlight review enforces App Store Guideline 4.8 — any third-party SSO requires Apple Sign-in as a peer. Verify on a manual Release archive before the first lane run.
2. **App Store Connect app record** exists for bundle ID `com.danzucker.stitchpad`, primary language English, with beta test info (what to test, contact email) populated.
3. **App Store Connect API key (.p8)** generated under App Manager role. Key file lives at `~/.stitchpad/asc_api_key.p8`, never in the repo.
4. **Android release keystore** generated locally with `keytool`. Lives at `~/.stitchpad/release.jks`, never in the repo. Passwords + alias captured in `composeApp/release-signing.properties` (gitignored), example file checked in.
5. **Google Play service account** created in Play Console → API access. Granted Release Manager role on the app. JSON key downloaded to `~/.stitchpad/play-service-account.json`.
6. **Play Console app record + first-time admin checklist** completed before the first AAB upload: app created and linked to `com.danzucker.stitchpad`, privacy policy URL set, data safety form submitted, content rating questionnaire completed, target audience declared. Play won't accept an upload to any track (internal included) until the app-dashboard checklist is green.
7. **Play Console internal testing track** initialized with the `pilot-tailors` tester list (emails collected by the PM intern). Opt-in URL captured for distribution to testers.
8. **TestFlight external testing group** `pilot-tailors-ios` created in App Store Connect. Apple sends invites to whatever email is added; testers can accept on any Apple ID. The collected email just needs to be one the tester actually checks. Testers must have an iPhone/iPad running **iOS 16.0 or later** — this requires lowering `IPHONEOS_DEPLOYMENT_TARGET` from its current `18.2` to `16.0` (see §Implementation prerequisites below for the change). Picked 16.0 to include refurbished iPhone X / XR / 8 in circulation in Nigeria; otherwise the pilot would lose roughly 10–30% of potential testers.
9. **Firebase release configuration files** present on the release machine: production `google-services.json` at `composeApp/` and production `GoogleService-Info.plist` at `iosApp/iosApp/`. Both are gitignored (per `.gitignore` rule `**/google-services.json` / `**/GoogleService-Info.plist`), so they live only on Daniel's MacBook and any future CI runner.

## Architecture

### Files to add

```
fastlane/
  Appfile                              # bundle ID, Apple team, ASC API key paths
  Fastfile                             # ios beta + android beta lanes
  Pluginfile                           # (no plugins needed; upload_to_play_store is built-in)
  .env.example                         # documents secret names
  .env                                 # gitignored real secrets

composeApp/
  release-signing.properties.example   # template
  release-signing.properties           # gitignored
  build.gradle.kts                     # signingConfigs.release, release buildType wire-up,
                                       # dynamic versionCode from git rev-list

docs/
  release-process.md                   # Daniel's manual runbook

.gitignore                             # add fastlane/.env, *.jks, release-signing.properties,
                                       # ~/.stitchpad/* references in docs only
```

### Versioning

- **Android `versionCode`:** `git rev-list --count HEAD`, evaluated at build time inside `build.gradle.kts`. Monotonically increases per commit; can never collide with itself. The Play store rejects any AAB with a `versionCode` ≤ the highest previously uploaded; using commit count guarantees we never trip that.
- **iOS build number (`CURRENT_PROJECT_VERSION`):** same `git rev-list --count HEAD` value, written into the Xcode project by the Fastlane lane using `increment_build_number_in_xcodeproj`.
- **`versionName` / `MARKETING_VERSION`:** hand-edited in source (`composeApp/build.gradle.kts` and the Xcode project). Format `0.<minor>.<patch>` (period-separated digits only — Apple's `CFBundleShortVersionString` rejects suffixes like `-beta`, and TestFlight upload validation enforces this). **Initial value: `0.9.0`** — climbs through `0.9.1`, `0.9.2`, ... during the pilot, then jumps cleanly to `1.0.0` at public launch. The "beta" status is signaled by the distribution channel (TestFlight external testing, Play internal testing track), not by the version string. This stays manual because it's a semantic decision, not a counter.

### Shared preflight (both lanes)

Both lanes run the same fast gates before any build work. Lane aborts on failure.

1. `./gradlew detekt` — static checks.
2. `./gradlew :composeApp:allTests` — matches the task CI runs (`.github/workflows/ci.yml`), so anything that passes in CI also passes locally. Covers commonTest + JVM + iOS unit tests.
3. **No-new-commits guard:** query the store for the most recently uploaded version code (Play API via `google_play_track_version_codes` for Android; ASC API via `latest_testflight_build_number` for iOS), compare to the current `git rev-list --count HEAD`. If the current count is ≤ the store's latest, the lane fails immediately with: *"No new commits since last upload. Bump `versionName` and commit (e.g. `chore(release): cut 0.9.x`) before re-running."* This prevents uploading a build that the store would silently reject.

### iOS pipeline — `fastlane ios beta`

1. Run shared preflight (above).
2. Read App Store Connect API key from `APP_STORE_CONNECT_API_KEY_PATH` and authenticate.
3. `increment_build_number_in_xcodeproj` → set build number to current `git rev-list --count HEAD`.
4. Build the KMP shared framework via `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` — the same task the existing Xcode "Run Script" build phase invokes (verified in `iosApp.xcodeproj/project.pbxproj`). Keeps the framework path Xcode expects, no divergence from the IDE build.
5. `gym` → archive + export signed `.ipa` using existing automatic signing (team `7DUJFVWF7W`).
6. `pilot` → upload to TestFlight, distribute to the `pilot-tailors-ios` external group, "What to test" pulled from `RELEASE_NOTES` env var or last commit message.
7. Output: link to the TestFlight build page; lane exits non-zero on any failure.

### Android pipeline — `fastlane android beta`

1. Run shared preflight (above).
2. `./gradlew :composeApp:bundleRelease` → produces a signed AAB at `composeApp/build/outputs/bundle/release/composeApp-release.aab`, using `release-signing.properties` for the keystore.
3. `upload_to_play_store` → uploads AAB to the `internal` track, release notes from `RELEASE_NOTES` env var or last commit message, uses `json_key=~/.stitchpad/play-service-account.json`.
4. Output: link to the Play Console internal testing track; lane exits non-zero on any failure.

### Secrets — Phase 1 (local)

All in `fastlane/.env` (gitignored):

| Variable | Purpose |
|---|---|
| `APP_STORE_CONNECT_API_KEY_PATH` | absolute path to .p8 |
| `APP_STORE_CONNECT_API_KEY_ID` | key ID from ASC |
| `APP_STORE_CONNECT_API_ISSUER_ID` | issuer UUID from ASC |
| `PLAY_SERVICE_ACCOUNT_JSON_PATH` | absolute path to play-service-account.json |
| `ANDROID_KEYSTORE_PATH` | absolute path to release.jks |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |
| `RELEASE_NOTES` | optional override; defaults to last commit subject |

The `release-signing.properties` file is read by `build.gradle.kts` (independent of Fastlane) so that `./gradlew bundleRelease` also works standalone.

### Secrets — Phase 2 (CI, deferred)

Identical variable names, sourced from GitHub repo secrets. The `.p8`, keystore, and Play service-account JSON are base64-encoded as secrets and materialized to disk by a workflow step at the start of each run.

## Tester onboarding (owned by PM intern)

1. PM intern runs a short Google Form to collect: tester name, primary email (Apple ID for iOS testers, Google account for Android), platform, phone model.
2. **iOS:** PM intern adds emails to the `pilot-tailors-ios` external testing group in App Store Connect. Apple emails the invite to that address; tester opens the invite on an iPhone, installs the TestFlight app if not already present, accepts in TestFlight, then installs the build.
3. **Android:** PM intern adds emails to the `pilot-tailors` list in Play Console internal testing. Tester clicks the opt-in URL once (delivered via WhatsApp by PM intern), accepts, then installs from the Play Store. Subsequent updates auto-install in the background.
4. Daniel runs both lanes after each notable change. Both cohorts receive a notification (TestFlight push / Play Store update banner).

**Set expectation with testers about the first iOS build:** Apple's Beta App Review runs on the first external TestFlight build of each new `MARKETING_VERSION` (typically 24–48h, occasionally longer). Subsequent build numbers within the same version skip review. Daniel can install on his own device immediately via the lane's archive output while waiting.

## Debug menu on release builds

The existing debug menu (per `2026-05-14-debug-menu-design.md`) is gated on `isDebugBuild` (see `core/debug/DebugBuild.kt` and the four call-sites in `NavGraph`, `SettingsScreen`, `StitchPadApp`). Pilot builds produced by this spec are **release** builds; testers will not have access to seed/reset tooling.

**Decision for Phase 1:** Accept this. Testers walk the PM-led golden path on a fresh signup (the same flow described in `docs/onboarding/PM-Intern-Onboarding.md`). Adding a third "beta" build type or flavor that enables the debug menu in a release-signed artifact is real complexity (separate signing config, separate Play track, separate review timeline) and not justified for 5–10 pilot testers.

**Revisit if:** testers need to repeatedly inspect seeded state or simulate quota/tier scenarios. At that point evaluate a `beta` flavor or a remote-config debug toggle, in a follow-up spec.

## Failure modes

| Failure | Mitigation |
|---|---|
| Duplicate `versionCode` rejected by Play | Cannot happen — `git rev-list --count` only increases. |
| Apple rejects first external testing review | Most likely cause: Sign in with Apple broken on the release archive even though entitlement is present. Verify per prerequisite #1 before the first lane run. Second-most-likely: missing privacy policy URL or beta test info on the ASC app record. |
| Signing cert / provisioning profile drift | Automatic signing handles this for now. Escalate to `fastlane match` only if it becomes recurring pain. |
| Play upload flake (timeout / API blip) | Lane prints the AAB path; Daniel can re-run the lane or upload manually via Play Console as fallback. Runbook documents the path. |
| Detekt or unit tests fail | Both lanes run the shared preflight (`detekt` + `:composeApp:allTests`) and abort before any build work. |
| Re-running on same HEAD | The shared preflight queries the store for the last uploaded version code and fails before any build work if the current `git rev-list --count HEAD` is ≤ that. Daniel commits a release-marker (typically `chore(release): cut beta 0.9.x` bumping `versionName`) and re-runs. |

## Validation (definition of done for the implementation plan)

- [ ] `fastlane ios beta` from a clean checkout produces a TestFlight build that Daniel installs on his iPhone via the TestFlight app.
- [ ] `fastlane android beta` produces a Play internal testing release that PM intern installs from the Play Store on her Android device.
- [ ] Both lanes complete in <8 min on an M-series MacBook with a warm Gradle cache.
- [ ] `./gradlew :composeApp:bundleRelease` works standalone (no Fastlane in the loop) and produces a signed AAB.
- [ ] Re-running `fastlane android beta` without a new commit fails fast with a clear "no new commits since last upload" error.
- [ ] `docs/release-process.md` contains: prereq checklist, one-time setup commands, day-to-day release commands, common failure recovery. Tested by Daniel running through it after two weeks of context switching to another task.

## Open items deferred to follow-up specs

- **GitHub Actions CD on `git tag v*`** — Phase 2, separate spec. When that lands, note that `git rev-list --count HEAD` requires `fetch-depth: 0` in the checkout step (default shallow clone yields the wrong build number); alternatively pass `github.run_number` as the build number override.
- **R8 / proguard for `release` build type** — `isMinifyEnabled = false` is fine for the pilot but should be re-evaluated before public production launch (shrinks AAB, hides Kotlin internals from reverse-engineering).
- **Crashlytics dSYM upload** — wire `upload_symbols_to_crashlytics` into the iOS lane (tied to `project_crashlytics_remote_logging` backlog).
- **Android closed → open testing track promotion** — when freemium ships and we want broader cohorts.
- **Per-PR ephemeral builds** — only worth it once we have non-Daniel reviewers regularly testing UI changes.

## Implementation prerequisites (locked-in decisions)

1. **Lower `IPHONEOS_DEPLOYMENT_TARGET` from 18.2 → 16.0** in `iosApp.xcodeproj/project.pbxproj` (both Debug and Release configurations, currently set on lines 286 and 350). Rationale: refurbished iPhone X / XR / 8 are common in Nigeria; 18.2 would exclude an estimated 10–30% of potential pilot testers. iOS 16.0 covers iPhone 8 and newer. Apple Sign-in is available since iOS 13, so no auth regression. Requires a regression pass on an iOS 16 simulator covering the screens that already have known iOS-platform quirks (`feedback_ios_modal_bottom_sheet_timing`, `feedback_kmp_jvm_only_apis`, `feedback_kotlin_native_epoch_days`).
2. **Set initial `versionName` to `0.9.0`** in `composeApp/build.gradle.kts` (currently `1.0`) and `MARKETING_VERSION` to `0.9.0` in `iosApp.xcodeproj/project.pbxproj`. The build number (`versionCode` / `CURRENT_PROJECT_VERSION`) is auto-derived from `git rev-list --count HEAD` and overrides whatever's in source at lane-run time.

## Docs to update once lanes work

- `docs/onboarding/whatsapp-message.md` — currently says *"Android: I'll send you an .apk over WhatsApp"* and *"iPhone: TestFlight isn't set up yet"* (lines 96, 98). Update Android to the Play opt-in URL flow; update iOS to TestFlight invite flow.
- `docs/onboarding/PM-Intern-Onboarding.md` — similar updates at the install-instructions section (~line 133+).
- These edits are a small follow-up PR, not part of the implementation plan for this spec.
