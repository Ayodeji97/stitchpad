# TestFlight + Play Internal Testing — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `fastlane ios beta` and `fastlane android beta` lane that, from Daniel's MacBook, build signed StitchPad release artifacts and upload them to TestFlight (external testing group) and Google Play (internal testing track) respectively.

**Architecture:** Two Fastlane lanes share a preflight (detekt + `:composeApp:allTests` + a platform-specific "no-new-commits" guard that queries the store API for the last uploaded build number). The Android lane runs `./gradlew :composeApp:bundleRelease` against a new `signingConfigs.release` block backed by a gitignored `composeApp/release-signing.properties` and a keystore at `~/.stitchpad/release.jks`. The iOS lane builds the KMP framework via `embedAndSignAppleFrameworkForXcode` (the same task the Xcode build phase already invokes), archives with `gym` using existing automatic signing (team `7DUJFVWF7W`), and uploads with `pilot` using an App Store Connect API key at `~/.stitchpad/asc_api_key.p8`. Version codes are derived from `git rev-list --count HEAD` at build time and passed as Xcode `xcargs` (so no files are dirtied per build). The marketing version (`0.9.0`) is hand-edited in source.

**Tech Stack:** Fastlane (Ruby), Gradle KTS, Xcode (xcconfig), Google Play Developer API, App Store Connect API, GitLive Firebase KMP.

**Spec:** `docs/superpowers/specs/2026-05-16-testflight-android-distribution-design.md`

**Out of scope (deferred per spec):** GitHub Actions CD, R8/proguard, Crashlytics dSYM upload, Play closed/open tracks, per-PR ephemeral builds, debug-menu-on-release flavor.

**Hard prerequisites (Daniel completes manually — NOT plan tasks):**
1. Sign in with Apple verified on a Release archive (entitlement, SPM, launcher already on `main`).
2. App Store Connect app record created for `com.danzucker.stitchpad`.
3. App Store Connect API key (.p8) downloaded to `~/.stitchpad/asc_api_key.p8`.
4. Android release keystore generated → `~/.stitchpad/release.jks`.
5. Play Console service account JSON → `~/.stitchpad/play-service-account.json`, granted Release Manager.
6. Play Console app dashboard checklist complete (privacy policy, data safety, content rating, target audience).
7. Play internal testing track + TestFlight external group initialized with PM-intern's tester emails.
8. Production `google-services.json` + `GoogleService-Info.plist` present on Daniel's MacBook.

---

## Task 1: Lower iOS deployment target 18.2 → 16.0

**Files:**
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (lines 286 and 350)

**Why:** Excludes 10–30% of Nigerian pilot testers running iOS 16/17 on refurbished iPhone X/XR/8 (spec §Implementation prerequisites).

- [ ] **Step 1: Verify current state**

Run: `grep -n "IPHONEOS_DEPLOYMENT_TARGET" iosApp/iosApp.xcodeproj/project.pbxproj`

Expected: two lines, both `IPHONEOS_DEPLOYMENT_TARGET = 18.2;` (at line 286 and 350).

- [ ] **Step 2: Replace both occurrences**

Edit the file: change both `IPHONEOS_DEPLOYMENT_TARGET = 18.2;` lines to `IPHONEOS_DEPLOYMENT_TARGET = 16.0;`.

- [ ] **Step 3: Verify the change**

Run: `grep -n "IPHONEOS_DEPLOYMENT_TARGET" iosApp/iosApp.xcodeproj/project.pbxproj`

Expected:
```
286:				IPHONEOS_DEPLOYMENT_TARGET = 16.0;
350:				IPHONEOS_DEPLOYMENT_TARGET = 16.0;
```

- [ ] **Step 4: Confirm Xcode still opens the project**

Run: `xcodebuild -project iosApp/iosApp.xcodeproj -list 2>&1 | head -10`

Expected: lists targets including `iosApp` — no parser errors.

- [ ] **Step 5: Commit**

```bash
git add iosApp/iosApp.xcodeproj/project.pbxproj
git commit -m "chore(ios): lower IPHONEOS_DEPLOYMENT_TARGET 18.2 → 16.0

Widens pilot tester eligibility to include refurbished iPhone X/XR/8
in circulation in Nigeria. Apple Sign-in (required by Guideline 4.8)
is available since iOS 13 so no auth regression."
```

---

## Task 2: Set iOS Marketing Version to 0.9.0

**Files:**
- Modify: `iosApp/Configuration/Config.xcconfig`

**Why:** `1.0` is a placeholder that implies a production-ready release; pilot builds should ship as `0.9.0` per spec §Versioning.

- [ ] **Step 1: Verify current state**

Run: `cat iosApp/Configuration/Config.xcconfig`

Expected output includes:
```
CURRENT_PROJECT_VERSION=1
MARKETING_VERSION=1.0
```

- [ ] **Step 2: Update MARKETING_VERSION**

Edit `iosApp/Configuration/Config.xcconfig`. Change `MARKETING_VERSION=1.0` to `MARKETING_VERSION=0.9.0`. Leave `CURRENT_PROJECT_VERSION=1` alone — Fastlane will override it at lane-run time.

- [ ] **Step 3: Verify the change**

Run: `grep "MARKETING_VERSION\|CURRENT_PROJECT_VERSION" iosApp/Configuration/Config.xcconfig`

Expected:
```
CURRENT_PROJECT_VERSION=1
MARKETING_VERSION=0.9.0
```

- [ ] **Step 4: Commit**

```bash
git add iosApp/Configuration/Config.xcconfig
git commit -m "chore(ios): set MARKETING_VERSION to 0.9.0

CURRENT_PROJECT_VERSION (build number) stays at 1; the Fastlane lane
overrides it per-build via gym xcargs at upload time."
```

---

## Task 3: Bump Android versionName + wire dynamic versionCode

**Files:**
- Modify: `composeApp/build.gradle.kts` (defaultConfig block, around lines 108–116)

**Why:** Spec §Versioning — `versionCode` must come from `git rev-list --count HEAD` so it always monotonically increases (avoids Play's "version code already used" rejection). `versionName` bumped to match iOS marketing version.

- [ ] **Step 1: Verify current state**

Run: `grep -n "versionCode\|versionName" composeApp/build.gradle.kts`

Expected:
```
112:        versionCode = 1
113:        versionName = "1.0"
```

- [ ] **Step 2: Add a helper that reads the git commit count**

In `composeApp/build.gradle.kts`, just above the `android { ... }` block (around line 103), add:

```kotlin
val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    workingDir = rootDir
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1
```

**Why `?: 1`:** if the build runs outside a git checkout (zip-extracted sources, etc.) we still produce a buildable AAB. The store-side preflight guard catches collisions, not this default.

- [ ] **Step 3: Replace the static versionCode/versionName**

In the `defaultConfig { ... }` block, replace lines 112–113:

```kotlin
versionCode = 1
versionName = "1.0"
```

with:

```kotlin
versionCode = gitCommitCount
versionName = "0.9.0"
```

- [ ] **Step 4: Verify the changes parse**

Run: `./gradlew :composeApp:tasks --quiet 2>&1 | head -5`

Expected: no "FAILURE", no "Could not compile build file" errors. The task list prints normally.

- [ ] **Step 5: Verify versionCode resolves to a sensible number**

Run: `./gradlew :composeApp:processDebugManifest 2>&1 | tail -3 && grep -h "versionCode" composeApp/build/intermediates/merged_manifest/debug/*/AndroidManifest.xml 2>/dev/null | head -1`

Expected: the manifest shows `android:versionCode="N"` where N is a number > 50 (current `git rev-list --count HEAD` on `main`-ish branches). If you see `versionCode="1"`, the helper didn't apply.

- [ ] **Step 6: Commit**

```bash
git add composeApp/build.gradle.kts
git commit -m "feat(android): derive versionCode from git rev-list, bump versionName

versionCode now equals git rev-list --count HEAD, evaluated at
configuration time. Guarantees monotonically increasing build numbers
so Play never rejects an upload for a stale code. versionName bumped
from placeholder 1.0 to 0.9.0 to match iOS marketing version."
```

---

## Task 4: Add Android release signing config

**Files:**
- Modify: `composeApp/build.gradle.kts` (top imports + `android { signingConfigs { ... } buildTypes { release { ... } } }`)
- Create: `composeApp/release-signing.properties.example`

**Why:** Currently `./gradlew :composeApp:bundleRelease` produces an unsigned AAB which Play will reject. We need a `release` signing config driven by a gitignored properties file.

- [ ] **Step 1: Create the example properties file**

Create `composeApp/release-signing.properties.example`:

```properties
# Copy to release-signing.properties (gitignored) and fill in real values.
# Daniel keeps the keystore at ~/.stitchpad/release.jks — paths are absolute
# so they work regardless of where gradle is invoked from.
storeFile=/Users/danzucker/.stitchpad/release.jks
storePassword=
keyAlias=stitchpad-release
keyPassword=
```

- [ ] **Step 2: Add the signing config to `composeApp/build.gradle.kts`**

In the `android { ... }` block, just BEFORE `buildTypes { ... }`, add:

```kotlin
val releaseSigningProps = Properties().apply {
    val propsFile = layout.projectDirectory.file("release-signing.properties").asFile
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

signingConfigs {
    create("release") {
        val storeFilePath = releaseSigningProps.getProperty("storeFile")
        if (storeFilePath != null) {
            storeFile = file(storeFilePath)
            storePassword = releaseSigningProps.getProperty("storePassword")
            keyAlias = releaseSigningProps.getProperty("keyAlias")
            keyPassword = releaseSigningProps.getProperty("keyPassword")
        }
    }
}
```

**Why guarded:** debug builds shouldn't require release credentials. CI and contributors without the keystore can still run `./gradlew :composeApp:assembleDebug`.

- [ ] **Step 3: Wire the signing config into the release build type**

In the same file, find:

```kotlin
buildTypes {
    getByName("release") {
        isMinifyEnabled = false
    }
}
```

Replace with:

```kotlin
buildTypes {
    getByName("release") {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("release")
    }
}
```

- [ ] **Step 4: Verify the project still configures cleanly**

Run: `./gradlew :composeApp:tasks --quiet 2>&1 | grep -E "FAILURE|bundleRelease" | head -5`

Expected: shows `bundleRelease` in the task list, no FAILURE.

- [ ] **Step 5: Verify release build still aborts cleanly without the props file**

Run: `./gradlew :composeApp:bundleRelease 2>&1 | tail -10`

Expected: one of two outcomes:
- (a) Daniel HAS already placed `composeApp/release-signing.properties` + the keystore → BUILD SUCCESSFUL, AAB at `composeApp/build/outputs/bundle/release/composeApp-release.aab`.
- (b) Daniel has NOT yet → fails with a clear "Keystore file not set" or "storeFile must not be null" message. This is expected; the keystore is a prerequisite, not a plan task.

Either outcome confirms the wiring is correct. Document which outcome occurred in the commit body.

- [ ] **Step 6: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/release-signing.properties.example
git commit -m "feat(android): add release signing config

signingConfigs.release reads from a gitignored release-signing.properties
in composeApp/. An example file is checked in. The block is guarded so
debug builds remain runnable without the keystore (CI, contributors,
fresh clones)."
```

---

## Task 5: Update .gitignore for new release artifacts

**Files:**
- Modify: `.gitignore` (after the existing "Debug menu test creds" section)

**Why:** Real secrets must never reach the repo. Existing gitignore covers `google-services.json`/`GoogleService-Info.plist` but not the new Fastlane + signing artifacts.

- [ ] **Step 1: Verify nothing is staged accidentally**

Run: `git status --porcelain | grep -E "\.env$|release-signing\.properties$|\.jks$"`

Expected: no output (these files don't exist yet).

- [ ] **Step 2: Append the new ignore rules**

Append the following block to `.gitignore` (after the "Debug menu test creds" section, before the `# Misc` line):

```gitignore
# Release signing — keystore + passwords, never committed
composeApp/release-signing.properties
*.jks

# Fastlane secrets and runtime artifacts
fastlane/.env
fastlane/report.xml
fastlane/Preview.html
fastlane/test_output/
fastlane/README.md
```

**Why `fastlane/README.md` ignored:** `fastlane init` generates a boilerplate README full of generic guidance that doesn't reflect *our* lanes. Our actual runbook lives in `docs/release-process.md`.

- [ ] **Step 3: Verify the rules take effect**

Run:
```bash
mkdir -p fastlane && touch fastlane/.env && touch composeApp/release-signing.properties
git check-ignore -v fastlane/.env composeApp/release-signing.properties
```

Expected: both files report as ignored, e.g.:
```
.gitignore:NN:fastlane/.env	fastlane/.env
.gitignore:NN:composeApp/release-signing.properties	composeApp/release-signing.properties
```

Then clean up: `rm fastlane/.env composeApp/release-signing.properties && rmdir fastlane`.

- [ ] **Step 4: Commit**

```bash
git add .gitignore
git commit -m "chore: gitignore release signing + fastlane secrets

Keeps the keystore, release-signing.properties, and fastlane/.env
out of the repo, in line with the existing google-services.json
and GoogleService-Info.plist exclusions."
```

---

## Task 6: Scaffold fastlane/ directory

**Files:**
- Create: `fastlane/Appfile`
- Create: `fastlane/Pluginfile`
- Create: `fastlane/.env.example`

**Why:** Fastlane reads these on every lane invocation. `Appfile` holds non-secret identifiers (bundle ID, Apple team); `.env.example` documents every secret the lanes expect (real `.env` is gitignored).

- [ ] **Step 1: Create `fastlane/Appfile`**

```ruby
# Identifiers shared by all iOS lanes. Non-secret — safe to commit.
app_identifier("com.danzucker.stitchpad")
apple_id(ENV["FASTLANE_APPLE_ID"]) # Optional; only needed for 2FA password fallback.
team_id("7DUJFVWF7W")

# Android identifiers are referenced directly in the Fastfile (no AndroidAppfile in Phase 1).
```

- [ ] **Step 2: Create `fastlane/Pluginfile`**

```ruby
# gem "fastlane-plugin-..."
# upload_to_play_store + pilot are built into Fastlane core — no plugins needed for Phase 1.
```

- [ ] **Step 3: Create `fastlane/.env.example`**

```bash
# Copy to fastlane/.env (gitignored) and fill in real values.
# All paths are absolute so the lane works regardless of CWD.

# --- iOS / App Store Connect ---
APP_STORE_CONNECT_API_KEY_PATH=/Users/danzucker/.stitchpad/asc_api_key.p8
APP_STORE_CONNECT_API_KEY_ID=
APP_STORE_CONNECT_API_ISSUER_ID=

# --- Android / Google Play ---
PLAY_SERVICE_ACCOUNT_JSON_PATH=/Users/danzucker/.stitchpad/play-service-account.json
ANDROID_KEYSTORE_PATH=/Users/danzucker/.stitchpad/release.jks
ANDROID_KEYSTORE_PASSWORD=
ANDROID_KEY_ALIAS=stitchpad-release
ANDROID_KEY_PASSWORD=

# --- Optional ---
# RELEASE_NOTES overrides the lane's default (last commit subject).
# RELEASE_NOTES=
```

- [ ] **Step 4: Verify fastlane discovers the files**

Run: `cd fastlane && fastlane lanes 2>&1 | head -5 && cd -`

Expected: prints "no available lanes" or similar — no "Could not find Appfile" / "syntax error" output. (Fastfile is added in Task 7; an empty-lanes message is fine here.)

If `fastlane` is not installed, install it first: `brew install fastlane`. Document this in `release-process.md` (Task 9).

- [ ] **Step 5: Commit**

```bash
git add fastlane/Appfile fastlane/Pluginfile fastlane/.env.example
git commit -m "feat(release): scaffold fastlane/ with Appfile, Pluginfile, .env.example

Non-secret identifiers committed. Secret env vars documented in
.env.example; the real fastlane/.env is gitignored (per Task 5)."
```

---

## Task 7: Implement `android beta` lane

**Files:**
- Create: `fastlane/Fastfile`

**Why:** First end-to-end lane. Android comes first because the failure modes (signing config, Play API auth) are more deterministic than iOS code-signing and easier to iterate on.

- [ ] **Step 1: Create `fastlane/Fastfile` with the Android lane**

```ruby
# StitchPad release lanes. Phase 1: local execution only.
# See docs/release-process.md for the prerequisite checklist.

# Load fastlane/.env (gitignored) into ENV. Optional — vars can also come
# from the shell environment (e.g. when invoked from CI in Phase 2).
require "dotenv"
Dotenv.load(File.join(__dir__, ".env")) if File.exist?(File.join(__dir__, ".env"))

# --- Shared helpers ---

def current_build_number
  `git rev-list --count HEAD`.strip.to_i
end

def release_notes
  notes = ENV["RELEASE_NOTES"]
  return notes if notes && !notes.empty?
  `git log -1 --pretty=%s`.strip
end

# --- Shared preflight ---

before_all do |lane, options|
  next if lane == :smoke # skip for the lane-parse smoke test below

  UI.message "Running shared preflight (detekt + :composeApp:allTests)…"
  sh "cd .. && ./gradlew detekt :composeApp:allTests --console=plain"
end

# --- Android ---

platform :android do
  package_name = "com.danzucker.stitchpad"

  desc "Build a signed AAB and upload to Play internal testing track"
  lane :beta do
    json_key = ENV.fetch("PLAY_SERVICE_ACCOUNT_JSON_PATH")
    UI.user_error!("Service account JSON not found at #{json_key}") unless File.exist?(json_key)

    current = current_build_number
    UI.message "Current git commit count: #{current}"

    # No-new-commits guard — query Play for the highest versionCode in the internal track.
    track_codes = google_play_track_version_codes(
      package_name: package_name,
      track: "internal",
      json_key: json_key
    ) rescue []
    last = (track_codes || []).max || 0
    UI.message "Last internal-track versionCode on Play: #{last}"

    if current <= last
      UI.user_error!(
        "No new commits since last upload (last versionCode on Play=#{last}, " \
        "current rev-list=#{current}). Bump versionName and commit a release " \
        "marker (e.g. `chore(release): cut 0.9.x`) before re-running."
      )
    end

    # Build signed AAB.
    # --no-configuration-cache forces gitCommitCount in build.gradle.kts to be
    # freshly evaluated, so a warm config cache cannot serve a stale versionCode.
    sh "cd .. && ./gradlew :composeApp:bundleRelease --console=plain --no-configuration-cache"

    aab = "../composeApp/build/outputs/bundle/release/composeApp-release.aab"
    UI.user_error!("AAB not found at #{aab}") unless File.exist?(File.join(__dir__, aab))

    upload_to_play_store(
      package_name: package_name,
      track: "internal",
      aab: aab,
      json_key: json_key,
      release_status: "draft", # promote manually in Play Console after sanity check
      skip_upload_metadata: true,
      skip_upload_changelogs: false,
      skip_upload_images: true,
      skip_upload_screenshots: true
    )

    UI.success "Uploaded AAB versionCode=#{current} to Play internal track. " \
               "Open Play Console → Internal testing → review & roll out."
  end
end

# --- Smoke test — confirms Fastfile parses without running anything real. ---

lane :smoke do
  UI.success "Fastfile parses; build number is #{current_build_number}; notes: #{release_notes}"
end
```

**Why `release_status: "draft"`:** Daniel reviews the release in Play Console before clicking "Start rollout." Avoids accidental pushes to testers if something looks off (wrong AAB, wrong release notes, etc.).

**Why `skip_upload_changelogs: false`:** the lane DOES upload release notes (from `RELEASE_NOTES` env var or last commit subject) — testers see this in the Play Store's "What's new" section.

- [ ] **Step 2: Verify the Fastfile parses (no auth, no build)**

Run: `cd fastlane && fastlane smoke 2>&1 | tail -5 && cd -`

Expected: prints something like `Fastfile parses; build number is N; notes: <last commit subject>` and exits 0.

If `dotenv` gem is missing: `gem install dotenv` (or `bundle add dotenv` if Daniel uses Bundler). Document in `release-process.md`.

- [ ] **Step 3: Dry-run the lane up to the preflight gate**

Daniel must have completed prerequisites #4 (keystore) and #5 (Play service account JSON) for a full run. If not, this step verifies the gate logic up to the AAB build.

Run: `cd fastlane && fastlane android beta 2>&1 | tee /tmp/android-beta.log | tail -30 && cd -`

Possible outcomes:
- (a) **All prereqs present, new commit since last upload** → lane uploads successfully. AAB appears in Play Console. Validation criterion met.
- (b) **No new commit** → lane aborts with the "No new commits since last upload" message. Guard works correctly.
- (c) **Service account JSON missing** → lane aborts with "Service account JSON not found at …". Guard works.
- (d) **Keystore missing** → `bundleRelease` step fails. Make the keystore and re-run.

Any of (a)–(c) confirms the lane logic; (d) is a prerequisite gap, not a code bug.

- [ ] **Step 4: Commit**

```bash
git add fastlane/Fastfile
git commit -m "feat(release): add android beta lane

fastlane android beta runs shared preflight (detekt + :composeApp:allTests),
queries Play's internal track for the last uploaded versionCode and
aborts if HEAD has no new commits, then builds a signed AAB via
:composeApp:bundleRelease and uploads it to the internal track as a
draft (manual rollout). Release notes come from \$RELEASE_NOTES or the
last commit subject."
```

---

## Task 8: Implement `ios beta` lane

**Files:**
- Modify: `fastlane/Fastfile` (add iOS `platform` block after the Android block)

**Why:** Second platform. Reuses the Fastfile helpers + preflight. Pinned to `embedAndSignAppleFrameworkForXcode` (the same gradle task the Xcode build phase invokes — verified in pbxproj line 194) so the framework path matches what `gym` expects from Xcode.

- [ ] **Step 1: Add the iOS lane to `fastlane/Fastfile`**

Insert the following BETWEEN the `platform :android do ... end` block and the `lane :smoke do ... end` line:

```ruby
# --- iOS ---

platform :ios do
  bundle_identifier = "com.danzucker.stitchpad"

  def read_marketing_version
    config = File.read(File.expand_path("../iosApp/Configuration/Config.xcconfig", __dir__))
    m = config.match(/^MARKETING_VERSION=(.+)$/)
    UI.user_error!("MARKETING_VERSION not found in Config.xcconfig") unless m
    m[1].strip
  end

  desc "Build a signed IPA and upload to TestFlight external group"
  lane :beta do
    key_path = ENV.fetch("APP_STORE_CONNECT_API_KEY_PATH")
    UI.user_error!("ASC API key not found at #{key_path}") unless File.exist?(key_path)

    app_store_connect_api_key(
      key_id: ENV.fetch("APP_STORE_CONNECT_API_KEY_ID"),
      issuer_id: ENV.fetch("APP_STORE_CONNECT_API_ISSUER_ID"),
      key_filepath: key_path,
      in_house: false
    )

    marketing = read_marketing_version
    current = current_build_number
    UI.message "Marketing version: #{marketing}, target build number: #{current}"

    # No-new-commits guard — query TestFlight for the highest build number of this marketing version.
    last = latest_testflight_build_number(
      version: marketing,
      app_identifier: bundle_identifier,
      initial_build_number: 0
    ) rescue 0
    UI.message "Last TestFlight build for #{marketing}: #{last}"

    if current <= last
      UI.user_error!(
        "No new commits since last TestFlight upload of #{marketing} " \
        "(last=#{last}, current=#{current}). Bump MARKETING_VERSION in " \
        "Config.xcconfig and commit before re-running."
      )
    end

    # Build the KMP framework using the same task Xcode's build phase invokes.
    # Avoids divergence between IDE builds and lane builds.
    # --no-configuration-cache as above: keeps versionCode and any other
    # git-derived values fresh on every release build.
    sh "cd .. && ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode " \
       "-Pkotlin.native.cocoapods.platform=iphoneos " \
       "-Pkotlin.native.cocoapods.archs=arm64 " \
       "-Pkotlin.native.cocoapods.configuration=Release " \
       "--console=plain --no-configuration-cache"

    # Archive + export signed IPA. Build number passed via xcargs so no files
    # are dirtied per lane run.
    gym(
      project: "../iosApp/iosApp.xcodeproj",
      scheme: "iosApp",
      configuration: "Release",
      output_directory: "../build/ios-archives",
      output_name: "StitchPad-#{marketing}-#{current}.ipa",
      xcargs: "CURRENT_PROJECT_VERSION=#{current}",
      clean: true,
      include_bitcode: false,
      include_symbols: true
    )

    # Upload to TestFlight external group.
    pilot(
      api_key_path: nil, # already authenticated via app_store_connect_api_key above
      app_identifier: bundle_identifier,
      ipa: "../build/ios-archives/StitchPad-#{marketing}-#{current}.ipa",
      distribute_external: true,
      groups: ["pilot-tailors-ios"],
      changelog: release_notes,
      skip_waiting_for_build_processing: false,
      reject_build_waiting_for_review: false
    )

    UI.success "Uploaded build #{current} of #{marketing} to TestFlight " \
               "external group pilot-tailors-ios."
  end
end
```

**Why `kotlin.native.cocoapods.platform=iphoneos` / `archs=arm64` / `configuration=Release` properties:** these are the same parameters Xcode passes to the embed task at archive time. Passing them explicitly from the lane keeps the framework build deterministic regardless of what Xcode last cached.

**Why `release_status` / `distribute_external: true` here but `release_status: "draft"` for Android:** Apple's first external testing build triggers Beta App Review automatically; subsequent build numbers of the same version skip review. There's no draft state to promote — once approved, builds go live to the group. The 24–48h review latency is documented in `release-process.md` (Task 9).

- [ ] **Step 2: Verify the Fastfile still parses**

Run: `cd fastlane && fastlane smoke 2>&1 | tail -3 && cd -`

Expected: same output as Task 7 step 2 (smoke lane prints, exits 0). Confirms the new iOS block didn't introduce syntax errors.

- [ ] **Step 3: List iOS lanes**

Run: `cd fastlane && fastlane lanes 2>&1 | grep -E "ios|android|smoke" && cd -`

Expected (something like):
```
android beta - Build a signed AAB and upload to Play internal testing track
ios beta - Build a signed IPA and upload to TestFlight external group
smoke - <description>
```

- [ ] **Step 4: Dry-run the iOS lane (prereq-dependent)**

Run: `cd fastlane && fastlane ios beta 2>&1 | tee /tmp/ios-beta.log | tail -50 && cd -`

Possible outcomes (same shape as Task 7 step 3):
- (a) All prereqs present → IPA uploaded, TestFlight processes the build (5–15 min), then enters Beta App Review.
- (b) No new commit → "No new commits since last TestFlight upload" error.
- (c) Missing API key file → "ASC API key not found at …" error.
- (d) Code-signing failure → Xcode automatic-signing issue or expired profile; fall back to manually opening the project in Xcode and re-archiving (documented in release-process.md).

Any of (a)–(c) confirms the lane works.

- [ ] **Step 5: Commit**

```bash
git add fastlane/Fastfile
git commit -m "feat(release): add ios beta lane

fastlane ios beta runs shared preflight, queries TestFlight for the
latest build number of the current MARKETING_VERSION and aborts if
HEAD has no new commits, builds the KMP framework via
:composeApp:embedAndSignAppleFrameworkForXcode (mirrors the Xcode
build phase), archives + exports a signed IPA with build number
passed via gym xcargs (no files dirtied), and uploads to the
pilot-tailors-ios external testing group."
```

---

## Task 9: Write `docs/release-process.md` runbook

**Files:**
- Create: `docs/release-process.md`

**Why:** Spec validation requires "Daniel can ship a build after two weeks away from the project". Solo-dev memory decays fast — the runbook holds the prerequisite checklist, one-time setup commands, day-to-day release commands, and common failure recovery.

- [ ] **Step 1: Create `docs/release-process.md`**

```markdown
# StitchPad Release Process

This is the runbook for shipping signed beta builds to pilot testers.

## One-time setup (do once per machine)

### macOS / Ruby / Fastlane

```bash
brew install fastlane
gem install dotenv
```

### Secrets directory

Daniel keeps all release secrets outside the repo at `~/.stitchpad/`:

```bash
mkdir -p ~/.stitchpad
chmod 700 ~/.stitchpad
```

### Android keystore (one-time)

```bash
keytool -genkey -v \
  -keystore ~/.stitchpad/release.jks \
  -alias stitchpad-release \
  -keyalg RSA -keysize 2048 -validity 25000
```

Save the **store password**, **key alias** (`stitchpad-release`), and **key password** in `fastlane/.env` (see "Per-machine env" below) and `composeApp/release-signing.properties` (copy from `.example`).

**Back this keystore up.** If you lose it, you cannot push another update to the same Play Console listing — you'd have to publish a new app.

### App Store Connect API key (one-time)

1. App Store Connect → Users and Access → Integrations → Keys → "Generate API Key"
2. Role: **App Manager**
3. Download the `.p8` file → save as `~/.stitchpad/asc_api_key.p8`
4. Copy the **Key ID** and **Issuer ID** into `fastlane/.env`

### Google Play service account (one-time)

1. Play Console → Setup → API access → "Create new service account"
2. Click through to Google Cloud Console, generate a JSON key
3. Save the JSON → `~/.stitchpad/play-service-account.json`
4. Back in Play Console: grant the service account **Release Manager** role on the StitchPad app.

### Per-machine env

Copy `fastlane/.env.example` to `fastlane/.env` and fill in real values:

```bash
cp fastlane/.env.example fastlane/.env
# Edit fastlane/.env in your editor
```

Copy `composeApp/release-signing.properties.example` to `composeApp/release-signing.properties`:

```bash
cp composeApp/release-signing.properties.example composeApp/release-signing.properties
# Edit composeApp/release-signing.properties — fill in the keystore passwords
```

Both files are gitignored.

### Firebase release configs

Make sure `composeApp/google-services.json` (production) and `iosApp/iosApp/GoogleService-Info.plist` (production) are present. They're gitignored, so they live only on your machine.

## Hard prerequisites (check before the first build)

- [ ] `~/.stitchpad/release.jks` exists
- [ ] `~/.stitchpad/asc_api_key.p8` exists
- [ ] `~/.stitchpad/play-service-account.json` exists
- [ ] `fastlane/.env` filled in
- [ ] `composeApp/release-signing.properties` filled in
- [ ] App Store Connect app record exists for `com.danzucker.stitchpad`
- [ ] App Store Connect beta test info populated (what to test, contact email)
- [ ] Play Console app dashboard checklist green (privacy policy, data safety, content rating, target audience)
- [ ] TestFlight external testing group `pilot-tailors-ios` exists
- [ ] Play Console internal testing tester list populated
- [ ] Sign in with Apple verified working on a manual Release archive built from Xcode

## Day-to-day: ship a beta build

```bash
# From repo root
cd fastlane

# Android → Play internal testing (draft; promote manually after sanity check)
fastlane android beta

# iOS → TestFlight external group
fastlane ios beta
```

Optional: customize the release notes that testers see:

```bash
RELEASE_NOTES="Quick orders flow + measurement editing fixes" fastlane android beta
```

Each lane takes ~5–8 min on a warm cache.

### After `android beta`

1. Open Play Console → Internal testing → review the draft release.
2. Click "Start rollout to internal testing."
3. Testers' Play Store updates automatically within minutes.

### After `ios beta`

- First build of a new `MARKETING_VERSION`: enters Beta App Review (24–48h typical). Pilot group can't install until Apple approves.
- Subsequent build numbers of the same `MARKETING_VERSION`: skip review, go live to the group within ~15 min after TestFlight finishes processing.
- You can install on your own iPhone immediately via the IPA at `build/ios-archives/StitchPad-<version>-<build>.ipa` (drag-and-drop in Xcode → Devices and Simulators).

## Bumping the marketing version

When you ship a meaningful change (new screen, bugfix wave, etc.), bump the marketing version in both places and commit:

```bash
# Android
sed -i '' 's/versionName = "0.9.0"/versionName = "0.9.1"/' composeApp/build.gradle.kts

# iOS
sed -i '' 's/MARKETING_VERSION=0.9.0/MARKETING_VERSION=0.9.1/' iosApp/Configuration/Config.xcconfig

git commit -am "chore(release): cut beta 0.9.1"
```

The build numbers (`versionCode` / `CURRENT_PROJECT_VERSION`) auto-increment from `git rev-list --count HEAD`.

## Common failures and recovery

| Symptom | Cause | Fix |
|---|---|---|
| `No new commits since last upload` | Re-running on the same HEAD as the last upload | Bump marketing version (above) or commit a meaningful change |
| `Service account JSON not found at …` | `PLAY_SERVICE_ACCOUNT_JSON_PATH` wrong in `.env` | Verify the file exists at that path |
| `Keystore file not set` / `storeFile must not be null` | `composeApp/release-signing.properties` missing | Copy from `.example` and fill in |
| `gym` fails with code-sign error | Xcode automatic signing got out of sync | Open `iosApp/iosApp.xcodeproj` in Xcode, archive once manually, retry the lane |
| TestFlight upload hangs at "processing" | Apple's processing queue can take 30+ min | Wait; check App Store Connect → TestFlight tab |
| Play upload fails with "no privacy policy" | Play dashboard checklist incomplete | Play Console → App dashboard → fill in missing items |
| Detekt / tests fail in preflight | Pre-existing issue on HEAD | Fix the underlying issue (don't bypass — the lane gates exist to keep beta builds shippable) |

## What's deferred (not in this release flow)

- GitHub Actions CD (Phase 2 — separate spec)
- R8 / proguard for the release build type
- Crashlytics dSYM upload
- Play closed / open testing promotion
```

- [ ] **Step 2: Verify the runbook renders cleanly**

Run: `head -30 docs/release-process.md`

Expected: valid Markdown, no broken tables, no stray code-fence markers.

- [ ] **Step 3: Commit**

```bash
git add docs/release-process.md
git commit -m "docs(release): add release-process.md runbook

One-time setup, hard prereq checklist, day-to-day release commands,
version-bump recipe, and a failure-recovery table. Targeted at a
solo dev returning to the project after weeks away."
```

---

## Task 10: End-to-end smoke + spec validation

**Files:** none modified — verification only.

**Why:** Spec validation §Validation requires concrete acceptance evidence (lanes complete in <8 min, standalone `bundleRelease` works, runbook is self-sufficient).

- [ ] **Step 1: Verify the Fastfile lanes list**

Run: `cd fastlane && fastlane lanes 2>&1 && cd -`

Expected: both `ios beta` and `android beta` listed with their descriptions.

- [ ] **Step 2: Verify standalone `bundleRelease` works**

Run: `time ./gradlew :composeApp:bundleRelease --console=plain 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL, AAB at `composeApp/build/outputs/bundle/release/composeApp-release.aab`, total wall time logged.

Verify the AAB is signed:

```bash
unzip -p composeApp/build/outputs/bundle/release/composeApp-release.aab META-INF/MANIFEST.MF 2>/dev/null | head -5
```

Expected: a manifest with signed entries (not empty).

- [ ] **Step 3: Verify `android beta` either uploads or fails the no-new-commits guard cleanly**

Already covered in Task 7 Step 3. If the upload succeeded earlier, a second invocation now should print the no-new-commits error within seconds of starting (no Gradle build invoked):

Run: `cd fastlane && time fastlane android beta 2>&1 | tail -10 && cd -`

Expected: aborts at the no-new-commits guard in <30s with the documented error message.

- [ ] **Step 4: Verify wall-clock targets**

Spec target: both lanes <8 min on a warm M-series cache.

Check the times captured in Step 2 (gradle bundleRelease) and the prior lane runs. If significantly over budget, capture the gradle scan URL from `--scan` and file a follow-up issue — not a blocker for this plan.

- [ ] **Step 5: Read the runbook front-to-back**

Read `docs/release-process.md` start to finish. Confirm:
- [ ] Every prereq has a recipe (where to download, where to save)
- [ ] Every env var in `fastlane/.env.example` is explained in the runbook
- [ ] Every failure in the table is something a solo dev could actually hit

Fix any gaps in a follow-up commit.

- [ ] **Step 6: Final commit**

If Step 5 revealed gaps:

```bash
git add docs/release-process.md
git commit -m "docs(release): clarify runbook from end-to-end read-through"
```

If everything passed cleanly, no commit needed — the validation steps don't produce code changes.

---

## Self-review checklist (done — do not re-execute)

- **Spec coverage:** every section of `docs/superpowers/specs/2026-05-16-testflight-android-distribution-design.md` is addressed:
  - Goal / non-goals → Plan header
  - Hard prerequisites #1–9 → called out at the top as Daniel's manual work, validated in Task 9 runbook
  - Files to add → Tasks 4–9
  - Versioning (git rev-list, versionName 0.9.0) → Tasks 2, 3
  - Shared preflight → Task 7 (`before_all`), reused in Task 8
  - iOS pipeline (auth, framework via embedAndSignAppleFrameworkForXcode, gym, pilot) → Task 8
  - Android pipeline (preflight, bundleRelease, upload_to_play_store) → Task 7
  - Secrets (.env, release-signing.properties) → Tasks 4, 6
  - Tester onboarding → covered in Task 9 runbook (one-time setup) and spec; no code task
  - Debug menu on release builds → spec-only decision; no code change in this plan
  - Failure modes → Task 9 runbook table
  - Validation → Task 10
  - Implementation prerequisites (16.0, 0.9.0) → Tasks 1–3
- **Placeholder scan:** no TBD / TODO / "implement appropriate error handling" — every step is concrete code, exact command, or exact file edit.
- **Type consistency:** `current_build_number` / `release_notes` defined in Task 7 are referenced by Task 8; `gitCommitCount` defined in Task 3 used in the same task only. No mismatched names.
