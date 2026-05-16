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

1. **iOS Phase 3 auth PR landed.** Adds Apple Sign-in entitlement, GoogleSignIn SPM dependency, and Google URL scheme to the Xcode project. App Store Guideline 4.8 requires Apple Sign-in as a peer to any third-party SSO; external TestFlight review will reject a build without it. Tracked separately; see `reference_ios_auth_setup_gaps` memory.
2. **App Store Connect app record** exists for bundle ID `com.danzucker.stitchpad`, primary language English, with beta test info (what to test, contact email) populated.
3. **App Store Connect API key (.p8)** generated under App Manager role. Key file lives at `~/.stitchpad/asc_api_key.p8`, never in the repo.
4. **Android release keystore** generated locally with `keytool`. Lives at `~/.stitchpad/release.jks`, never in the repo. Passwords + alias captured in `composeApp/release-signing.properties` (gitignored), example file checked in.
5. **Google Play service account** created in Play Console → API access. Granted Release Manager role on the app. JSON key downloaded to `~/.stitchpad/play-service-account.json`.
6. **Play Console internal testing track** initialized with the `pilot-tailors` tester list (emails collected by the PM intern). Opt-in URL captured for distribution to testers.
7. **TestFlight external testing group** `pilot-tailors-ios` created in App Store Connect. Apple sends invites to whatever email is added; testers can accept on any Apple ID. The collected email just needs to be one the tester actually checks. Testers must have an iPhone/iPad capable of running iOS 15+ with the TestFlight app installed.

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
- **`versionName` / `MARKETING_VERSION`:** hand-edited in source (`composeApp/build.gradle.kts` and the Xcode project). Format `0.<minor>.<patch>-beta` (e.g. `0.9.0-beta`). This stays manual because it's a semantic decision, not a counter.

### iOS pipeline — `fastlane ios beta`

1. Resolve SPM packages (no-op if cached).
2. Read App Store Connect API key from `APP_STORE_CONNECT_API_KEY_PATH` and authenticate.
3. `increment_build_number_in_xcodeproj` → set build number to current `git rev-list --count HEAD`.
4. Build the KMP shared framework so the Xcode project picks up the latest Kotlin code. Exact gradle task name (e.g. `linkReleaseFrameworkIosArm64` vs `linkPodReleaseFrameworkIosArm64`) is determined by the existing CocoaPods/SPM integration and confirmed during implementation, not in this spec.
5. `gym` → archive + export signed `.ipa` using existing automatic signing (team `7DUJFVWF7W`).
6. `pilot` → upload to TestFlight, distribute to the `pilot-tailors-ios` external group, "What to test" pulled from `RELEASE_NOTES` env var or last commit message.
7. Output: link to the TestFlight build page; lane exits non-zero on any failure.

### Android pipeline — `fastlane android beta`

1. Run `./gradlew detekt :composeApp:test` (fast gates). Lane aborts if either fails.
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

## Failure modes

| Failure | Mitigation |
|---|---|
| Duplicate `versionCode` rejected by Play | Cannot happen — `git rev-list --count` only increases. |
| Apple rejects first external testing review | Most likely cause: missing Apple Sign-in. Gated by the iOS Phase 3 prerequisite; surfaced before any build attempt. |
| Signing cert / provisioning profile drift | Automatic signing handles this for now. Escalate to `fastlane match` only if it becomes recurring pain. |
| Play upload flake (timeout / API blip) | Lane prints the AAB path; Daniel can re-run the lane or upload manually via Play Console as fallback. Runbook documents the path. |
| Detekt or unit tests fail | Android lane aborts before `bundleRelease`. iOS lane runs the same pre-flight against the shared module. |
| Re-running on same HEAD | The lanes refuse to upload if `versionCode` hasn't advanced. Daniel must make a commit (typically a `chore(release): cut beta 0.9.x` commit that bumps `versionName`) before rerunning. |

## Validation (definition of done for the implementation plan)

- [ ] `fastlane ios beta` from a clean checkout produces a TestFlight build that Daniel installs on his iPhone via the TestFlight app.
- [ ] `fastlane android beta` produces a Play internal testing release that PM intern installs from the Play Store on her Android device.
- [ ] Both lanes complete in <8 min on an M-series MacBook with a warm Gradle cache.
- [ ] `./gradlew :composeApp:bundleRelease` works standalone (no Fastlane in the loop) and produces a signed AAB.
- [ ] Re-running `fastlane android beta` without a new commit fails fast with a clear "no new commits since last upload" error.
- [ ] `docs/release-process.md` contains: prereq checklist, one-time setup commands, day-to-day release commands, common failure recovery. Tested by Daniel running through it after two weeks of context switching to another task.

## Open items deferred to follow-up specs

- **GitHub Actions CD on `git tag v*`** — Phase 2, separate spec.
- **Crashlytics dSYM upload** — wire `upload_symbols_to_crashlytics` into the iOS lane (tied to `project_crashlytics_remote_logging` backlog).
- **Android closed → open testing track promotion** — when freemium ships and we want broader cohorts.
- **Per-PR ephemeral builds** — only worth it once we have non-Daniel reviewers regularly testing UI changes.
