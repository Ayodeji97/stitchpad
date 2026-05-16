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

Note: `MARKETING_VERSION` MUST be period-separated digits only (`A.B.C`). Suffixes like `-beta` cause App Store Connect to reject the build at upload time (`CFBundleShortVersionString` validation). The "beta" status is implicit in being on TestFlight / Play internal testing.

## Common failures and recovery

| Symptom | Cause | Fix |
|---|---|---|
| `No new commits since last upload` | Re-running on the same HEAD as the last upload | Bump marketing version (above) or commit a meaningful change |
| `Service account JSON not found at …` | `PLAY_SERVICE_ACCOUNT_JSON_PATH` wrong in `.env` | Verify the file exists at that path |
| `Keystore file not set` / `storeFile must not be null` | `composeApp/release-signing.properties` missing | Copy from `.example` and fill in |
| `Keystore was tampered with, or password was incorrect` | Blank password in `release-signing.properties` (file exists but passwords not filled in) | Open `composeApp/release-signing.properties` and fill in the actual `storePassword` + `keyPassword` |
| `Cannot recover key` | Wrong `keyAlias` in `release-signing.properties` (doesn't match the alias used when generating the keystore) | The `keytool` command above uses `stitchpad-release` — make sure the properties file matches |
| `gym` fails with code-sign error | Xcode automatic signing got out of sync | Open `iosApp/iosApp.xcodeproj` in Xcode, archive once manually, retry the lane |
| TestFlight upload hangs at "processing" | Apple's processing queue can take 30+ min | Wait; check App Store Connect → TestFlight tab |
| Play upload fails with "no privacy policy" | Play dashboard checklist incomplete | Play Console → App dashboard → fill in missing items |
| Detekt / tests fail in preflight | Pre-existing issue on HEAD | Fix the underlying issue (don't bypass — the lane gates exist to keep beta builds shippable) |
| `git rev-list failed — are you in a git checkout?` | Lane invoked outside a git working tree | `cd` back to the repo root; the lane requires git |
| `Could not query Play internal track` / `Could not query TestFlight build number` (UI.important log) | Service account or ASC API auth misconfigured, OR genuinely first upload | First-time uploads are fine — the lane proceeds. If you see this every time and uploads also fail later, the API credentials are wrong. |

## What's deferred (not in this release flow)

- GitHub Actions CD (Phase 2 — separate spec)
- R8 / proguard for the release build type
- Crashlytics dSYM upload
- Play closed / open testing promotion
