# Slice 8c — version floor runbook

Raise the force-update floor so every **active** user is on a build that reads
money/contact from the `/private` sub-docs (Slice 8a). This is the gate that makes
the base-strip (Slice 8d) safe: once the floor is set and old clients have updated
(or are blocked), no running app reads money from the base doc, so stripping it can
never show anyone ₦0.

## How the floor works

`config/app` holds `minSupportedBuildAndroid` and `minSupportedBuildIos` (Int, build
numbers). The client's `AppGate.evaluate` (see `core/config/domain/AppGate.kt`)
force-updates any build whose number is **below** the floor — a blocking screen with
an "Update" button. It is **fail-open**: a null floor, an unreadable config, or an
app that can't read its own build never gates. Per-platform because the stores review
on different timelines.

This is a **hard gate** — below-floor users cannot use the app until they update.

## Prerequisites

1. **Slice 8a (#323) is released to both stores** and has had time to reach most
   users organically. Watch store version-adoption; set the floor when the tail is
   small, so few people actually hit the wall.
2. **Slice 8b backfill has run** (so every doc has `/private` + `ownerId`).
3. `config/app` has a working **`updateUrlAndroid` / `updateUrlIos`** and
   **`forceUpdateMessage`** (else the blocking screen has no button / default copy).
   The script warns if any are unset.
4. Application-default credentials: `gcloud auth application-default login`.

## What build numbers to use

The floor is the **8a release's** build number per platform:

- **Android** — the `versionCode` of the 8a release AAB (see `composeApp/build.gradle.kts`).
- **iOS** — the `CURRENT_PROJECT_VERSION` (CFBundleVersion) of the 8a release
  (`iosApp/Configuration/Config.xcconfig`).

Record the exact numbers from the 8a release before running.

## Procedure

### 1. Dry run

```
cd functions
GOOGLE_CLOUD_PROJECT=stitchpad-30607 ANDROID_FLOOR=<8a-versionCode> IOS_FLOOR=<8a-CFBundleVersion> \
  node scripts/setUpdateFloor.js
```

Prints current vs new floors and warns about any missing update URL / message.

### 2. Apply

```
GOOGLE_CLOUD_PROJECT=stitchpad-30607 ANDROID_FLOOR=<8a-versionCode> IOS_FLOOR=<8a-CFBundleVersion> \
  node scripts/setUpdateFloor.js --commit
```

### 3. Verify

- Launch a **below-floor** build (or a sim/emulator with an older build number) →
  it should show the force-update screen.
- Launch an **8a+** build → normal operation.

### Rollback

Clear the floor (fail-open) at any time:

```
ANDROID_FLOOR=null IOS_FLOOR=null GOOGLE_CLOUD_PROJECT=stitchpad-30607 \
  node scripts/setUpdateFloor.js --commit
```

## Safety notes

- Setting the floor changes **nothing about the data** — it only gates old clients.
- It is fully reversible (clear the floor).
- **Do not proceed to 8d (base-strip) until the floor is set and enforced.** 8d is
  the only irreversible step, and it must run only after this gate guarantees no
  active client reads money from the base doc. Take a Firestore export before 8d.

## Where this sits in Slice 8

8a (owner reads /private) → 8b (backfill) → **8c (this floor)** → wait for adoption →
8d (strip base) → 8e (flip `allow list`; staff data lights up + work-queue scoping +
delivery toggle).
