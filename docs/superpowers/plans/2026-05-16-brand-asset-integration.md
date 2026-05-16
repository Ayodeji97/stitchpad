# Brand Asset Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the real Measure Ledger mark into StitchPad across launcher icons (Android adaptive + iOS Light/Dark/Tinted), in-app `StitchPadMark` composable (replacing both placeholder `StitchPadLogo` files including one that draws scissors — a brand-rule violation), iOS LaunchScreen storyboard, Android 12+ Splash Screen API, and an animated in-app `SplashScreen` reveal.

**Architecture:** Single PR on `feature/brand-integration` (worktree at `.claude/worktrees/feature+brand-integration/`). 7 commits, vertical-slice (each touchpoint verified before the next). Mark is defined as a parameterized `ImageVector` in Kotlin (not a webp asset) so the same composable handles light/dark/inverted variants via color params.

**Tech Stack:** Kotlin + Compose Multiplatform (Compose 1.8 era), Android resources (mipmaps + vector drawables + Theme.SplashScreen), iOS Asset Catalog + UIKit storyboard, Figma Plugin API for asset exports.

**Reference spec:** `docs/superpowers/specs/2026-05-16-brand-asset-integration-design.md` — read this first if any task needs more context.

---

## File Structure Overview

```
composeApp/src/androidMain/res/
  mipmap-{m,h,x,xx,xxx}hdpi/ic_launcher.png      ← REPLACE × 5
  mipmap-{m,h,x,xx,xxx}hdpi/ic_launcher_round.png ← REPLACE × 5
  drawable/ic_launcher_background.xml             ← REWRITE
  drawable-v24/ic_launcher_foreground.xml         ← REWRITE
  values/styles.xml                               ← CREATE/MODIFY
  AndroidManifest.xml                             ← MODIFY (theme ref)

iosApp/iosApp/
  Assets.xcassets/AppIcon.appiconset/
    Contents.json                                 ← REWRITE
    app-icon-light.png                            ← NEW (replaces app-icon-1024.png)
    app-icon-dark.png                             ← NEW
    app-icon-tinted.png                           ← NEW
  Assets.xcassets/BrandMark.imageset/
    Contents.json                                 ← NEW
    brand-mark@1x.png, @2x, @3x                   ← NEW
  Base.lproj/LaunchScreen.storyboard              ← NEW (or REWRITE)

composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/
  ui/components/StitchPadMark.kt                  ← NEW (ImageVector + KDoc)
  feature/auth/presentation/components/
    StitchPadLogo.kt                              ← DELETE
    AuthHero.kt                                   ← MODIFY (use inverted variant)
  feature/onboarding/presentation/
    SplashScreen.kt                               ← MODIFY (mark + animation)
    components/StitchPadLogo.kt                   ← DELETE (scissors violation)
```

Total: ~22 file changes across 7 task commits.

---

## Task 1: Android launcher icons (mipmap PNGs + adaptive XML)

**Goal:** Replace the default-Android-Studio-green launcher template with the Measure Ledger mark across all 5 mipmap densities + adaptive icon foreground/background XMLs.

**Files:**
- Replace: `composeApp/src/androidMain/res/mipmap-mdpi/ic_launcher.png` (48×48)
- Replace: `composeApp/src/androidMain/res/mipmap-mdpi/ic_launcher_round.png` (48×48)
- Replace: `composeApp/src/androidMain/res/mipmap-hdpi/ic_launcher.png` (72×72)
- Replace: `composeApp/src/androidMain/res/mipmap-hdpi/ic_launcher_round.png` (72×72)
- Replace: `composeApp/src/androidMain/res/mipmap-xhdpi/ic_launcher.png` (96×96)
- Replace: `composeApp/src/androidMain/res/mipmap-xhdpi/ic_launcher_round.png` (96×96)
- Replace: `composeApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher.png` (144×144)
- Replace: `composeApp/src/androidMain/res/mipmap-xxhdpi/ic_launcher_round.png` (144×144)
- Replace: `composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher.png` (192×192)
- Replace: `composeApp/src/androidMain/res/mipmap-xxxhdpi/ic_launcher_round.png` (192×192)
- Rewrite: `composeApp/src/androidMain/res/drawable/ic_launcher_background.xml`
- Rewrite: `composeApp/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml`

**Note on round icons:** For our notebook silhouette, a circular crop would chop off corners. So `ic_launcher_round.png` will be the SAME image as `ic_launcher.png` (no separate round version). Most Android 8+ launchers use the adaptive icon (XML) anyway; the bitmap mipmaps are legacy fallback for Android 7 and below.

- [ ] **Step 1.1: Add Android mipmap export presets to the Figma iOS Light icon**

Open the Figma file (https://www.figma.com/design/vtoN4SvhU1utiuXJTG2i4i) → Icons page → click the `iOS App Icon — Light` frame (node id 47:2).

In the right panel → Export section → click `+` to add the following preset rows. Daniel does this manually since the Figma MCP has rate limits:

| Suffix | Constraint | Output size |
|---|---|---|
| `-mdpi` | Width 48 | 48×48 |
| `-hdpi` | Width 72 | 72×72 |
| `-xhdpi` | Width 96 | 96×96 |
| `-xxhdpi` | Width 144 | 144×144 |
| `-xxxhdpi` | Width 192 | 192×192 |

Keep the existing 1× (1024) preset too — needed for iOS later.

- [ ] **Step 1.2: Export the launcher PNGs from Figma**

Select the `iOS App Icon — Light` frame → right panel → Export → click "Export iOS App Icon — Light".

Figma downloads 6 PNGs: `iOS App Icon — Light-mdpi.png` through `iOS App Icon — Light-xxxhdpi.png` plus the 1024.

Save them to a temp directory like `~/Downloads/stitchpad-icons/`.

- [ ] **Step 1.3: Copy launcher PNGs into the worktree mipmap directories**

Run from worktree root:

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/feature+brand-integration
DEST=composeApp/src/androidMain/res
SRC=~/Downloads/stitchpad-icons

# Square ic_launcher
cp "$SRC/iOS App Icon — Light-mdpi.png"    "$DEST/mipmap-mdpi/ic_launcher.png"
cp "$SRC/iOS App Icon — Light-hdpi.png"    "$DEST/mipmap-hdpi/ic_launcher.png"
cp "$SRC/iOS App Icon — Light-xhdpi.png"   "$DEST/mipmap-xhdpi/ic_launcher.png"
cp "$SRC/iOS App Icon — Light-xxhdpi.png"  "$DEST/mipmap-xxhdpi/ic_launcher.png"
cp "$SRC/iOS App Icon — Light-xxxhdpi.png" "$DEST/mipmap-xxxhdpi/ic_launcher.png"

# Round ic_launcher (same image — legacy fallback only)
cp "$SRC/iOS App Icon — Light-mdpi.png"    "$DEST/mipmap-mdpi/ic_launcher_round.png"
cp "$SRC/iOS App Icon — Light-hdpi.png"    "$DEST/mipmap-hdpi/ic_launcher_round.png"
cp "$SRC/iOS App Icon — Light-xhdpi.png"   "$DEST/mipmap-xhdpi/ic_launcher_round.png"
cp "$SRC/iOS App Icon — Light-xxhdpi.png"  "$DEST/mipmap-xxhdpi/ic_launcher_round.png"
cp "$SRC/iOS App Icon — Light-xxxhdpi.png" "$DEST/mipmap-xxxhdpi/ic_launcher_round.png"
```

Verify: `ls -la composeApp/src/androidMain/res/mipmap-xxxhdpi/` should show both files at 192×192 size.

- [ ] **Step 1.4: Rewrite `ic_launcher_background.xml`**

Replace the entire content of `composeApp/src/androidMain/res/drawable/ic_launcher_background.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FAF6EC"
        android:pathData="M0,0h108v108h-108z" />
</vector>
```

This solid `paperLight` background is what the Android 12+ system splash will use as the splash bg color too.

- [ ] **Step 1.5: Rewrite `ic_launcher_foreground.xml`**

Replace the entire content of `composeApp/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml` with the Measure Ledger mark in Android Vector Drawable XML.

The 108dp viewport must position the mark within the inner 72dp safe zone (per Android adaptive icon guidelines) so launcher masks don't crop it.

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Back cover (depth) — indigo700 #1E2B5C, offset right+down -->
    <path
        android:fillColor="#1E2B5C"
        android:pathData="M 28.5 23 L 65.5 23 A 3 3 0 0 1 68.5 26 L 68.5 82 A 3 3 0 0 1 65.5 85 L 28.5 85 A 3 3 0 0 1 25.5 82 L 25.5 26 A 3 3 0 0 1 28.5 23 Z" />

    <!-- Front cover — indigo500 #2C3E7C -->
    <path
        android:fillColor="#2C3E7C"
        android:pathData="M 24.5 19 L 61.5 19 A 3 3 0 0 1 64.5 22 L 64.5 78 A 3 3 0 0 1 61.5 81 L 24.5 81 A 3 3 0 0 1 21.5 78 L 21.5 22 A 3 3 0 0 1 24.5 19 Z" />

    <!-- Ruler ticks 1, 4, 7, 10 (long, 5.3dp) at x=25.3 — paperLight -->
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,25.3 h5.3 v0.6 h-5.3 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,40.6 h5.3 v0.6 h-5.3 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,55.9 h5.3 v0.6 h-5.3 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,71.2 h5.3 v0.6 h-5.3 z" />

    <!-- Ruler ticks 2, 3, 5, 7-ish (short, 3.2dp) — paperLight -->
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,30.4 h3.2 v0.6 h-3.2 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,35.5 h3.2 v0.6 h-3.2 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,45.7 h3.2 v0.6 h-3.2 z" />
    <!-- Tick 6 — saffron heritage #E8A800 -->
    <path android:fillColor="#E8A800" android:pathData="M25.3,50.8 h3.2 v0.6 h-3.2 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,61.0 h3.2 v0.6 h-3.2 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,66.1 h3.2 v0.6 h-3.2 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M25.3,76.3 h3.2 v0.6 h-3.2 z" />

    <!-- Stitch dashes at x=58 (vertical binding) — paperLight, 14 dashes 2.7dp tall, 1.5dp gap -->
    <path android:fillColor="#FAF6EC" android:pathData="M58,25.3 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,29.4 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,33.5 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,37.6 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,41.7 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,45.8 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,49.9 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,54.0 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,58.1 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,62.2 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,66.3 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,70.4 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,74.5 h0.6 v2.7 h-0.6 z" />
    <path android:fillColor="#FAF6EC" android:pathData="M58,78.6 h0.6 v2.7 h-0.6 z" />
</vector>
```

Coordinates are scaled from the 1024×1024 Figma source to the 108×108 Android viewport: divide each Figma coordinate by ~9.48 (1024/108). The mark sits within the inner ~72×72 safe zone.

- [ ] **Step 1.6: Build + verify on Android emulator**

Run from worktree root:

```bash
./gradlew :composeApp:assembleDebug
```

Expected: build succeeds, no errors. Install on a running Android emulator:

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Open the app drawer / home screen. The StitchPad icon should now show the indigo notebook on warm paper background (no longer the green Android grid template). Verify both:
- Square launcher icon shows the mark
- Round launcher icon (long-press app → some launchers force round mask) — the silhouette should still be recognizable

- [ ] **Step 1.7: Commit**

```bash
git add composeApp/src/androidMain/res/mipmap-*/ic_launcher.png \
        composeApp/src/androidMain/res/mipmap-*/ic_launcher_round.png \
        composeApp/src/androidMain/res/drawable/ic_launcher_background.xml \
        composeApp/src/androidMain/res/drawable-v24/ic_launcher_foreground.xml
git commit -m "$(cat <<'EOF'
feat(brand): Android launcher icons — Measure Ledger mark

Replace the default Android Studio green grid template across 5 mipmap
densities (mdpi → xxxhdpi). Rewrite ic_launcher_background.xml to solid
paperLight #FAF6EC and ic_launcher_foreground.xml as a vector drawable
of the notebook silhouette (indigo cover + back-cover depth + ruler ticks
with one saffron heritage tick + binding stitches).

Round icon variants use the same PNG as square — circular cropping would
chop the notebook silhouette. Adaptive icon (XML) covers Android 8+, the
bitmap fallbacks are for older launchers.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Run `git status` — working tree clean. `git log --oneline -1` shows the new commit.

---

## Task 2: iOS app icons (Light + Dark + Tinted)

**Goal:** Replace placeholder iOS app icon with three appearance variants (Light, Dark, Tinted) declared in the AppIcon Asset Catalog.

**Files:**
- Replace: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png` → split into 3 new files
- Add: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-light.png`
- Add: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-dark.png`
- Add: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-tinted.png`
- Rewrite: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json`

- [ ] **Step 2.1: Export the 3 iOS icon variants from Figma**

In Figma, on the Icons page, select these frames one at a time and click Export in the right panel:

- `iOS App Icon — Light` (node 47:2) → produces 1 PNG at 1024×1024
- `iOS App Icon — Dark` (node 47:31) → produces 1 PNG at 1024×1024
- `iOS App Icon — Tinted` (node 58:2) → produces 1 PNG at 1024×1024 with transparency

Save to `~/Downloads/stitchpad-icons/`.

- [ ] **Step 2.2: Copy iOS icon PNGs into AppIcon.appiconset**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/feature+brand-integration
DEST=iosApp/iosApp/Assets.xcassets/AppIcon.appiconset
SRC=~/Downloads/stitchpad-icons

# Remove old placeholder
rm "$DEST/app-icon-1024.png"

# Copy new variants
cp "$SRC/iOS App Icon — Light.png"  "$DEST/app-icon-light.png"
cp "$SRC/iOS App Icon — Dark.png"   "$DEST/app-icon-dark.png"
cp "$SRC/iOS App Icon — Tinted.png" "$DEST/app-icon-tinted.png"
```

- [ ] **Step 2.3: Rewrite Contents.json with appearance declarations**

Replace the entire content of `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json` with:

```json
{
  "images": [
    {
      "filename": "app-icon-light.png",
      "idiom": "universal",
      "platform": "ios",
      "size": "1024x1024"
    },
    {
      "appearances": [
        {
          "appearance": "luminosity",
          "value": "dark"
        }
      ],
      "filename": "app-icon-dark.png",
      "idiom": "universal",
      "platform": "ios",
      "size": "1024x1024"
    },
    {
      "appearances": [
        {
          "appearance": "luminosity",
          "value": "tinted"
        }
      ],
      "filename": "app-icon-tinted.png",
      "idiom": "universal",
      "platform": "ios",
      "size": "1024x1024"
    }
  ],
  "info": {
    "author": "xcode",
    "version": 1
  }
}
```

The `appearances` array binds Light (default, no appearance entry), Dark (`luminosity: dark`), and Tinted (`luminosity: tinted`) to the appropriate PNG.

- [ ] **Step 2.4: Verify on iOS simulator**

Open the Xcode project:

```bash
open iosApp/iosApp.xcodeproj
```

In Xcode: Product → Clean Build Folder (⇧⌘K), then Run (⌘R) on an iPhone 17 simulator (or whichever sim is configured per [[reference-test-environment]]).

After install, return to the iOS Home Screen. The StitchPad icon should show the Light variant by default.

- [ ] **Step 2.5: Test Dark + Tinted appearances**

In iOS Settings:
- Settings → Display & Brightness → Dark → returns to home screen → icon should now show the Dark variant (warm-ink bg, lifted indigo notebook).
- Long-press the home screen → Edit Home Screen → tap "Customize" (top-left ⊙ icon if on iOS 18+) → switch to "Tinted" → adjust tint slider → StitchPad icon should show the monochrome silhouette tinted by the chosen color.

Verify all three appearances render. If any falls back to the wrong icon, double-check the `Contents.json` `appearances` keys (must match exactly: `"appearance": "luminosity"`, `"value": "dark"` or `"tinted"`).

- [ ] **Step 2.6: Commit**

```bash
git add iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/
git commit -m "$(cat <<'EOF'
feat(brand): iOS app icon — Light + Dark + Tinted variants

Replace placeholder app-icon-1024.png with three appearance variants
declared in Contents.json. Light is the warm-paper bg + indigo notebook
default. Dark uses inkDark bg + lifted indigo400 covers. Tinted is the
monochrome silhouette (front cover 100% white, back cover 60% white
for depth) that iOS recolors with the user's chosen tint.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `StitchPadMark` composable + delete duplicate placeholders

**Goal:** Create one shared `StitchPadMark` composable using ImageVector (no webp), update both call sites (AuthHero + SplashScreen), and delete both duplicate `StitchPadLogo` files (including the one drawing scissors).

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadMark.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/AuthHero.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/SplashScreen.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/StitchPadLogo.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/components/StitchPadLogo.kt`

- [ ] **Step 3.1: Create `StitchPadMark.kt`**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadMark.kt` with:

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors

/**
 * The StitchPad Measure Ledger mark — notebook silhouette with ruler ticks
 * along the left edge and a single saffron heritage accent.
 *
 * Lockup rules:
 * - Minimum size: 24.dp. Below this, ruler ticks merge.
 * - Never stretch: always 1:1 square via [size].
 * - Mark + wordmark spacing in horizontal lockups: 12.5% of mark width.
 * - Optical alignment: wordmark x-height sits on mark's vertical midpoint.
 *
 * Accessibility (WCAG):
 * - Default colors meet AA Large on paperLight (8.4:1) and inkDark (4.7:1).
 * - Saffron tick at AA Large on indigo cover (4.1:1) — decorative accent.
 *
 * Inverted variant (for use on dark photo backgrounds, e.g. AuthHero):
 * pass `coverColor = Color.White`, `coverDepthColor = neutral200`,
 * `detailColor = MaterialTheme.colorScheme.primary`.
 */
@Composable
fun StitchPadMark(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    coverColor: Color = MaterialTheme.colorScheme.primary,
    coverDepthColor: Color = MaterialTheme.colorScheme.secondary,
    detailColor: Color = DesignTokens.paperLight,
    accentColor: Color = LocalStitchPadColors.current.heritageAccent,
    contentDescription: String? = "StitchPad",
) {
    val vector = remember(coverColor, coverDepthColor, detailColor, accentColor) {
        buildStitchPadMarkVector(
            coverColor = coverColor,
            coverDepthColor = coverDepthColor,
            detailColor = detailColor,
            accentColor = accentColor,
        )
    }
    Image(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

private fun buildStitchPadMarkVector(
    coverColor: Color,
    coverDepthColor: Color,
    detailColor: Color,
    accentColor: Color,
): ImageVector = ImageVector.Builder(
    name = "StitchPadMark",
    defaultWidth = 1024.dp,
    defaultHeight = 1024.dp,
    viewportWidth = 1024f,
    viewportHeight = 1024f,
).apply {
    // Back cover (depth) — offset right + down
    addRoundedRect(x = 240f, y = 180f, w = 560f, h = 720f, r = 28f, fill = coverDepthColor)
    // Front cover
    addRoundedRect(x = 200f, y = 140f, w = 560f, h = 720f, r = 28f, fill = coverColor)
    // 12 ruler ticks at x=220, every 50px starting y=200
    // Index 0,3,6,9 are long (50px), others short (30px). Index 5 is saffron.
    for (i in 0 until 12) {
        val isLong = i % 3 == 0
        val length = if (isLong) 50f else 30f
        val fill = if (i == 5) accentColor else detailColor
        addRoundedRect(x = 220f, y = 200f + i * 50f, w = length, h = 6f, r = 3f, fill = fill)
    }
    // 14 stitch dashes at x=700, every 40px starting y=200 (26 tall + 14 gap)
    for (i in 0 until 14) {
        addRoundedRect(x = 700f, y = 200f + i * 40f, w = 6f, h = 26f, r = 3f, fill = detailColor)
    }
}.build()

private fun ImageVector.Builder.addRoundedRect(
    x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color,
) {
    addPath(
        pathData = PathBuilder().apply {
            moveTo(x + r, y)
            horizontalLineTo(x + w - r)
            arcTo(r, r, 0f, false, true, x + w, y + r)
            verticalLineTo(y + h - r)
            arcTo(r, r, 0f, false, true, x + w - r, y + h)
            horizontalLineTo(x + r)
            arcTo(r, r, 0f, false, true, x, y + h - r)
            verticalLineTo(y + r)
            arcTo(r, r, 0f, false, true, x + r, y)
            close()
        }.getNodes(),
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(fill),
    )
}
```

- [ ] **Step 3.2: Add a @Preview function for visual verification**

Append to the end of `StitchPadMark.kt`:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@Preview
@Composable
private fun StitchPadMarkPreview() {
    StitchPadTheme {
        Column(
            modifier = Modifier
                .background(DesignTokens.paperLight)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                StitchPadMark(size = 24.dp)
                StitchPadMark(size = 48.dp)
                StitchPadMark(size = 80.dp)
                StitchPadMark(size = 120.dp)
            }
            // Inverted variant (for AuthHero photo bg)
            Box(modifier = Modifier.background(DesignTokens.neutral900).padding(24.dp)) {
                StitchPadMark(
                    size = 80.dp,
                    coverColor = Color.White,
                    coverDepthColor = DesignTokens.neutral200,
                    detailColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
```

Run the preview in Android Studio (open the file, click the green "Run" icon next to `@Preview` in the gutter). Verify:
- Mark renders at 24, 48, 80, 120dp with correct silhouette
- Saffron tick visible at 80dp+
- Inverted variant: white notebook with indigo stitches/ticks on the dark neutral900 background — saffron still visible

- [ ] **Step 3.3: Update `AuthHero.kt` to use inverted `StitchPadMark`**

Find the current import in `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/AuthHero.kt`:

```kotlin
import com.danzucker.stitchpad.feature.auth.presentation.components.StitchPadLogo
```

Replace with:

```kotlin
import com.danzucker.stitchpad.ui.components.StitchPadMark
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import com.danzucker.stitchpad.ui.theme.DesignTokens
```

Find the call site (was `StitchPadLogo(diameter = logoDiameter)`):

```kotlin
StitchPadLogo(diameter = logoDiameter)
```

Replace with the inverted variant:

```kotlin
StitchPadMark(
    size = logoDiameter,
    coverColor = Color.White,
    coverDepthColor = DesignTokens.neutral200,
    detailColor = MaterialTheme.colorScheme.primary,
)
```

- [ ] **Step 3.4: Update `SplashScreen.kt` to use default `StitchPadMark`**

Find the current import in `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/SplashScreen.kt`:

```kotlin
import com.danzucker.stitchpad.feature.onboarding.presentation.components.StitchPadLogo
```

Replace with:

```kotlin
import com.danzucker.stitchpad.ui.components.StitchPadMark
```

Find the call site (was `StitchPadLogo(size = 100.dp)`):

```kotlin
StitchPadLogo(size = 100.dp)
```

Replace with:

```kotlin
StitchPadMark(size = 100.dp)
```

(Animation will be added in Task 4 — for now this is the static version.)

- [ ] **Step 3.5: Delete both placeholder StitchPadLogo files**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/feature+brand-integration
git rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/StitchPadLogo.kt
git rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/components/StitchPadLogo.kt
```

- [ ] **Step 3.6: Build + verify no stale references**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: build succeeds.

```bash
grep -rn "StitchPadLogo" composeApp/src/
```

Expected: NO matches (the two files are deleted and both call sites updated).

```bash
grep -rn "scissors\|drawScissors" composeApp/src/
```

Expected: NO matches (the scissors-rendering code was in the deleted file).

- [ ] **Step 3.7: Visual verification on emulator**

Run the app on an Android emulator. Observe:
- Splash screen shows the new Measure Ledger mark (notebook silhouette, indigo on paperLight, saffron heritage tick on the ruler)
- Login screen AuthHero shows the inverted mark (white cover, indigo stitches/ticks, saffron preserved) over the photo background

- [ ] **Step 3.8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadMark.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/presentation/components/AuthHero.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/SplashScreen.kt
git commit -m "$(cat <<'EOF'
feat(brand): StitchPadMark composable + delete placeholder logos

New shared composable in ui/components/StitchPadMark.kt using ImageVector
with parameterized colors (cover, depth, detail, accent). Defaults use
MaterialTheme.colorScheme.primary + LocalStitchPadColors.heritageAccent.

Update AuthHero to pass inverted variant params (white cover + indigo
stitches) for the photo-background hero. Update SplashScreen to default
variant (no animation yet — that's the next commit).

Delete both placeholder StitchPadLogo.kt files — the onboarding variant
contained a drawScissors() helper that violated the locked brand rule
(StyleOS owns scissors per project-logo-direction memory).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: SplashScreen animation — staggered brand reveal

**Goal:** Add staggered fade + scale + slide animations to the SplashScreen so mark + wordmark + tagline reveal in a polished cascade before navigation.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/SplashScreen.kt`

- [ ] **Step 4.1: Read the current SplashScreen.kt**

Run from worktree root:

```bash
cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/SplashScreen.kt
```

Note the existing structure: where the mark, wordmark (if present), and tagline are rendered, and where the navigation `LaunchedEffect(...)` lives. We'll wrap each visible element with an animation layer.

- [ ] **Step 4.2: Add animation state variables**

Add these imports to the top of `SplashScreen.kt`:

```kotlin
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
```

In the Splash composable body, before the rendering tree, add:

```kotlin
var markVisible by remember { mutableStateOf(false) }
var wordmarkVisible by remember { mutableStateOf(false) }
var taglineVisible by remember { mutableStateOf(false) }

LaunchedEffect(Unit) {
    markVisible = true
    delay(200)
    wordmarkVisible = true
    delay(200)
    taglineVisible = true
}

val markAlpha by animateFloatAsState(
    targetValue = if (markVisible) 1f else 0f,
    animationSpec = tween(durationMillis = 300, easing = EaseOut),
    label = "markAlpha",
)
val markScale by animateFloatAsState(
    targetValue = if (markVisible) 1f else 0.92f,
    animationSpec = tween(durationMillis = 300, easing = EaseOut),
    label = "markScale",
)
val wordmarkAlpha by animateFloatAsState(
    targetValue = if (wordmarkVisible) 1f else 0f,
    animationSpec = tween(durationMillis = 300, easing = EaseOut),
    label = "wordmarkAlpha",
)
val wordmarkOffsetY by animateFloatAsState(
    targetValue = if (wordmarkVisible) 0f else 8f,
    animationSpec = tween(durationMillis = 300, easing = EaseOut),
    label = "wordmarkOffsetY",
)
val taglineAlpha by animateFloatAsState(
    targetValue = if (taglineVisible) 1f else 0f,
    animationSpec = tween(durationMillis = 300, easing = EaseOut),
    label = "taglineAlpha",
)
```

- [ ] **Step 4.3: Apply animations to the mark**

Find the `StitchPadMark(size = 100.dp)` call (from Task 3) and wrap with a `Modifier.graphicsLayer(...)`:

```kotlin
StitchPadMark(
    size = 100.dp,
    modifier = Modifier.graphicsLayer(
        alpha = markAlpha,
        scaleX = markScale,
        scaleY = markScale,
    ),
)
```

- [ ] **Step 4.4: Apply animations to the wordmark (if present)**

If the SplashScreen renders a "StitchPad" wordmark `Text(...)`, wrap with:

```kotlin
Text(
    text = "StitchPad",
    style = MaterialTheme.typography.displayLarge,  // or whatever existing style
    modifier = Modifier.graphicsLayer(
        alpha = wordmarkAlpha,
        translationY = wordmarkOffsetY * LocalDensity.current.density,  // 8.dp converted to px
    ),
)
```

(Add `import androidx.compose.ui.platform.LocalDensity` if not present.)

If the SplashScreen does not currently render a wordmark text, add one between the mark and the tagline:

```kotlin
Text(
    text = "StitchPad",
    style = MaterialTheme.typography.displayLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.graphicsLayer(
        alpha = wordmarkAlpha,
        translationY = wordmarkOffsetY * LocalDensity.current.density,
    ),
)
```

- [ ] **Step 4.5: Apply animation to the tagline**

Find the existing tagline text (if any). If present, wrap with:

```kotlin
Text(
    text = "The smart work pad for tailors.",  // or stringResource(Res.string.splash_tagline)
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.graphicsLayer(alpha = taglineAlpha),
)
```

If no tagline is currently present, add one below the wordmark with the same `taglineAlpha` graphicsLayer.

- [ ] **Step 4.6: Verify navigation timing still works**

The existing navigation `LaunchedEffect` should fire ~1700ms after splash starts. If the current splash has a shorter timer (e.g., 1000ms), extend it so the animation has time to play. Look for code like:

```kotlin
LaunchedEffect(Unit) {
    delay(1500)
    onSplashFinished()
}
```

Adjust the delay to 1700ms (matching the design spec):

```kotlin
LaunchedEffect(Unit) {
    delay(1700)
    onSplashFinished()
}
```

- [ ] **Step 4.7: Run + verify timing on Android + iOS**

```bash
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Cold-launch the app. Verify the timing feels right:
- 0ms: blank
- 0–300ms: mark fades in + scales up subtly
- 200–500ms: wordmark fades in + slides up
- 400–700ms: tagline fades in
- 1700ms: navigates to Login (or Dashboard if logged in)

For iOS, open Xcode and run on simulator:

```bash
open iosApp/iosApp.xcodeproj
# Build + run via Xcode UI
```

Same timing should apply. iOS sim may render slower than emulator — if it feels janky, increase tween duration from 300 to 400ms.

- [ ] **Step 4.8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/onboarding/presentation/SplashScreen.kt
git commit -m "$(cat <<'EOF'
feat(splash): animated brand reveal in SplashScreen

Add staggered fade + scale + translate animations to the mark, wordmark,
and tagline. Mark fades in at t=0 with scale 0.92→1.0. Wordmark fades in
at t=200ms with translateY 8dp→0dp. Tagline fades in at t=400ms. All
animations use 300ms ease-out. Navigation fires at t=1700ms so the user
has ~1s to register the reveal.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: iOS LaunchScreen storyboard

**Goal:** Create a UIKit storyboard that shows the mark centered on paperLight during cold launch, before Compose UI loads.

**Files:**
- Create: `iosApp/iosApp/Assets.xcassets/BrandMark.imageset/Contents.json`
- Create: `iosApp/iosApp/Assets.xcassets/BrandMark.imageset/brand-mark.png`
- Create: `iosApp/iosApp/Assets.xcassets/BrandMark.imageset/brand-mark@2x.png`
- Create: `iosApp/iosApp/Assets.xcassets/BrandMark.imageset/brand-mark@3x.png`
- Create or replace: `iosApp/iosApp/Base.lproj/LaunchScreen.storyboard`

- [ ] **Step 5.1: Export brand mark at 3 resolutions from Figma**

In Figma → Icons page → click `Splash Mark` (node 48:33, the 512×512 transparent splash mark).

Add export presets in the right panel (Daniel does this manually):
- `@1x` — Width 256
- `@2x` — Width 512
- `@3x` — Width 768

Export → save to `~/Downloads/stitchpad-icons/`.

- [ ] **Step 5.2: Create BrandMark.imageset directory + Contents.json**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/feature+brand-integration
mkdir -p iosApp/iosApp/Assets.xcassets/BrandMark.imageset
```

Create `iosApp/iosApp/Assets.xcassets/BrandMark.imageset/Contents.json` with:

```json
{
  "images": [
    {
      "filename": "brand-mark.png",
      "idiom": "universal",
      "scale": "1x"
    },
    {
      "filename": "brand-mark@2x.png",
      "idiom": "universal",
      "scale": "2x"
    },
    {
      "filename": "brand-mark@3x.png",
      "idiom": "universal",
      "scale": "3x"
    }
  ],
  "info": {
    "author": "xcode",
    "version": 1
  }
}
```

- [ ] **Step 5.3: Copy the exported PNGs into the imageset**

```bash
DEST=iosApp/iosApp/Assets.xcassets/BrandMark.imageset
SRC=~/Downloads/stitchpad-icons

cp "$SRC/Splash Mark@1x.png" "$DEST/brand-mark.png"
cp "$SRC/Splash Mark@2x.png" "$DEST/brand-mark@2x.png"
cp "$SRC/Splash Mark@3x.png" "$DEST/brand-mark@3x.png"
```

Verify: `ls -la iosApp/iosApp/Assets.xcassets/BrandMark.imageset/` shows 4 files (Contents.json + 3 PNGs).

- [ ] **Step 5.4: Create LaunchScreen.storyboard**

Check whether `iosApp/iosApp/Base.lproj/LaunchScreen.storyboard` already exists:

```bash
ls iosApp/iosApp/Base.lproj/
```

If it exists, back it up first: `cp iosApp/iosApp/Base.lproj/LaunchScreen.storyboard /tmp/LaunchScreen.storyboard.bak`.

Then create/replace `iosApp/iosApp/Base.lproj/LaunchScreen.storyboard` with:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<document type="com.apple.InterfaceBuilder3.CocoaTouch.Storyboard.XIB" version="3.0" toolsVersion="22151" targetRuntime="iOS.CocoaTouch" propertyAccessControl="none" useAutolayout="YES" launchScreen="YES" useTraitCollections="YES" useSafeAreas="YES" colorMatched="YES" initialViewController="01J-lp-oVM">
    <device id="retina6_12" orientation="portrait" appearance="light"/>
    <dependencies>
        <plugIn identifier="com.apple.InterfaceBuilder.IBCocoaTouchPlugin" version="22131"/>
        <capability name="Safe area layout guides" minToolsVersion="9.0"/>
        <capability name="documents saved in the Xcode 8 format" minToolsVersion="8.0"/>
    </dependencies>
    <scenes>
        <scene sceneID="EHf-IW-A2E">
            <objects>
                <viewController id="01J-lp-oVM" sceneMemberID="viewController">
                    <view key="view" contentMode="scaleToFill" id="Ze5-6b-2t3">
                        <rect key="frame" x="0.0" y="0.0" width="393" height="852"/>
                        <autoresizingMask key="autoresizingMask" widthSizable="YES" heightSizable="YES"/>
                        <subviews>
                            <imageView clipsSubviews="YES" userInteractionEnabled="NO" contentMode="scaleAspectFit" image="BrandMark" translatesAutoresizingMaskIntoConstraints="NO" id="bMa-rk-001">
                                <rect key="frame" x="146.5" y="376" width="100" height="100"/>
                                <constraints>
                                    <constraint firstAttribute="width" constant="100" id="Wid-th-100"/>
                                    <constraint firstAttribute="height" constant="100" id="Hei-ght-100"/>
                                </constraints>
                            </imageView>
                        </subviews>
                        <viewLayoutGuide key="safeArea" id="6Tk-OE-BBY"/>
                        <color key="backgroundColor" red="0.98039215686" green="0.96470588235" blue="0.92549019608" alpha="1" colorSpace="custom" customColorSpace="sRGB"/>
                        <constraints>
                            <constraint firstItem="bMa-rk-001" firstAttribute="centerX" secondItem="Ze5-6b-2t3" secondAttribute="centerX" id="cen-ter-X"/>
                            <constraint firstItem="bMa-rk-001" firstAttribute="centerY" secondItem="Ze5-6b-2t3" secondAttribute="centerY" id="cen-ter-Y"/>
                        </constraints>
                    </view>
                </viewController>
                <placeholder placeholderIdentifier="IBFirstResponder" id="iYj-Kq-Ea1" userLabel="First Responder" sceneMemberID="firstResponder"/>
            </objects>
            <point key="canvasLocation" x="53" y="375"/>
        </scene>
    </scenes>
    <resources>
        <image name="BrandMark" width="100" height="100"/>
    </resources>
</document>
```

Key details: backgroundColor is `paperLight` (`#FAF6EC` = `0.98, 0.965, 0.925` RGB), UIImageView references `BrandMark` (matches the imageset name), sized 100×100 and centered with auto-layout constraints.

- [ ] **Step 5.5: Verify Info.plist references LaunchScreen.storyboard**

```bash
grep -A 1 "UILaunchStoryboardName" iosApp/iosApp/Info.plist
```

Expected output should include the line:

```xml
<key>UILaunchStoryboardName</key>
<string>LaunchScreen</string>
```

If missing, add it to `Info.plist`. If the project uses an `Info.plist` reference in the Xcode build settings instead, no change needed (Xcode auto-detects `LaunchScreen.storyboard` by default).

- [ ] **Step 5.6: Build + verify on iOS simulator**

In Xcode:
- Product → Clean Build Folder (⇧⌘K)
- Run on iPhone simulator

**Important — iOS LaunchScreen cache:** iOS aggressively caches storyboards. If you don't see the new launch screen:
1. Stop the app
2. Long-press the StitchPad icon in the sim → Remove App → Delete App
3. Build & run again

Cold-launch the app. You should see:
- ~100-300ms: paperLight background with centered notebook mark (the LaunchScreen.storyboard)
- Then transition to Compose SplashScreen (which now has animated reveal from Task 4)

Verify the transition feels seamless — both have the same paperLight background and roughly the same mark position, so there's no visible jump.

- [ ] **Step 5.7: Commit**

```bash
git add iosApp/iosApp/Assets.xcassets/BrandMark.imageset/ \
        iosApp/iosApp/Base.lproj/LaunchScreen.storyboard \
        iosApp/iosApp/Info.plist
git commit -m "$(cat <<'EOF'
feat(launch): iOS LaunchScreen storyboard — branded splash

Replace default Xcode LaunchScreen with UIKit storyboard rendering the
Measure Ledger mark centered on paperLight (#FAF6EC). New BrandMark
imageset with @1x/@2x/@3x PNGs (256/512/768). Mark image sized 100×100
in points, anchored to view centerX/centerY via auto-layout constraints.

Pairs with the Compose SplashScreen animation (Task 4) to give a
seamless cold-launch transition — storyboard's static mark dissolves
into Compose's animated reveal.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Android 12+ Splash Screen API

**Goal:** Configure the Android system splash to use paperLight background + adaptive icon foreground, so cold launches on Android 12+ show a branded splash before Compose UI loads.

**Files:**
- Create or modify: `composeApp/src/androidMain/res/values/styles.xml`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 6.1: Verify or create styles.xml**

```bash
ls composeApp/src/androidMain/res/values/styles.xml 2>/dev/null && echo "exists" || echo "missing"
```

If missing, create `composeApp/src/androidMain/res/values/styles.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.StitchPad.SplashScreen" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#FAF6EC</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
        <item name="postSplashScreenTheme">@style/Theme.StitchPad</item>
    </style>
</resources>
```

If `styles.xml` exists, add only the `<style>` block above (inside the existing `<resources>` element). Note: `postSplashScreenTheme` references `Theme.StitchPad` — verify this is the existing app theme name; if the app uses a different theme name (check `AndroidManifest.xml`), substitute accordingly.

- [ ] **Step 6.2: Update AndroidManifest.xml to use the splash theme**

Open `composeApp/src/androidMain/AndroidManifest.xml`. Find the `<application>` element. Find its `android:theme` attribute (currently probably `@style/Theme.StitchPad` or similar).

Replace the theme attribute value with `@style/Theme.StitchPad.SplashScreen`:

```xml
<application
    android:name=".StitchPadApplication"
    android:theme="@style/Theme.StitchPad.SplashScreen"
    ... >
```

This sets the splash theme as the application launch theme. The `postSplashScreenTheme` declaration in `styles.xml` ensures the app theme replaces it after splash exits.

- [ ] **Step 6.3: Build + verify cold launch on Android 12+ emulator**

```bash
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am force-stop com.danzucker.stitchpad
adb shell am start -n com.danzucker.stitchpad/.MainActivity
```

Cold launch should show:
- ~200-500ms: paperLight background with the adaptive icon foreground (notebook mark) centered, system applies built-in fade-and-scale animation
- Then Compose UI loads (with SplashScreen animation from Task 4)

Verify on an Android 12+ emulator (API 31+). On older Android, this theme falls back to the regular `postSplashScreenTheme` — no harm done.

**Note on circle crop:** Some Android launchers/system splashes apply a circular mask to the foreground icon. The notebook silhouette in the 72dp safe zone (from Task 1's `ic_launcher_foreground.xml`) should survive the crop. If it doesn't, you may need to tighten the safe zone in the foreground vector.

- [ ] **Step 6.4: Commit**

```bash
git add composeApp/src/androidMain/res/values/styles.xml \
        composeApp/src/androidMain/AndroidManifest.xml
git commit -m "$(cat <<'EOF'
feat(launch): Android 12+ Splash Screen API configuration

Add Theme.StitchPad.SplashScreen extending Theme.SplashScreen with
windowSplashScreenBackground=#FAF6EC (paperLight) and the adaptive icon
foreground as the splash icon. postSplashScreenTheme bridges back to
the regular app theme after splash exits.

Update AndroidManifest to use the splash theme as the application launch
theme. Android 12+ devices get the branded splash with built-in fade-and-
scale animation; older Android falls back to the app theme directly with
no visual harm.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Smoke test, PR description, push, open PR

**Goal:** Verify the full end-to-end launch flow on both platforms, confirm no regressions, push the branch, and open a PR with the smoke test checklist.

**Files:** none changed in this task — verification and PR creation only.

- [ ] **Step 7.1: Run grep verifications**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/.claude/worktrees/feature+brand-integration

echo "=== StitchPadLogo references (must be empty) ==="
grep -rn "StitchPadLogo" composeApp/src/ || echo "(none)"

echo ""
echo "=== Scissors references (must be empty) ==="
grep -rn "scissors\|drawScissors" composeApp/src/ || echo "(none)"

echo ""
echo "=== Verify StitchPadMark imports ==="
grep -rn "import com.danzucker.stitchpad.ui.components.StitchPadMark" composeApp/src/ | head -5
```

Expected: first two grep blocks return "(none)". Third block shows at least 2 imports (AuthHero + SplashScreen).

- [ ] **Step 7.2: Run detekt + unit tests**

```bash
./gradlew detekt :composeApp:allTests
```

Expected: both pass with no failures or detekt violations.

- [ ] **Step 7.3: Verify iOS compile (per [[feedback-kmp-jvm-only-apis]])**

```bash
./gradlew :composeApp:compileKotlinIosArm64
```

Expected: compile succeeds. If it fails, check for JVM-only API usage (e.g., `String.format`) in the new code — there shouldn't be any since `StitchPadMark.kt` is pure Compose.

- [ ] **Step 7.4: Full smoke test on both platforms**

**Android emulator (API 33+ recommended):**

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am force-stop com.danzucker.stitchpad
adb shell am start -n com.danzucker.stitchpad/.MainActivity
```

Verify in order:
1. Cold launch shows Android 12+ system splash (paperLight bg + foreground icon centered)
2. System splash transitions to Compose SplashScreen (mark fades in + scales, wordmark slides+fades, tagline fades)
3. Navigates to Login at ~1.7s
4. Login screen AuthHero shows inverted mark (white cover + indigo stitches/ticks + saffron heritage tick) over photo background
5. Tap Sign Up — AuthHero same on SignUp screen
6. Force-quit the app, re-launch → repeat steps 1-5 in dark system mode (Settings → Display → Dark theme on emulator)

**iOS simulator (iPhone 17 sim per [[reference-test-environment]]):**

In Xcode: Run on iPhone 17 simulator.

Verify in order:
1. Cold launch shows UIKit LaunchScreen (paperLight bg + mark centered, static)
2. Transitions to Compose SplashScreen with animation
3. Navigates to Login at ~1.7s
4. AuthHero inverted mark visible
5. Settings → Developer → Dark Appearance → re-launch app → repeat 1-4 in dark mode → launcher icon should switch to Dark variant (visible after returning to home screen)
6. Long-press home screen → Edit → Customize → Tinted → verify Tinted icon shows clean monochrome silhouette

**Verify launcher icons on both home screens:**
- Android: app drawer shows StitchPad with indigo notebook on warm paper, not the green grid template
- iOS: home screen shows StitchPad with indigo notebook on warm paper

- [ ] **Step 7.5: Push branch to origin**

```bash
git push -u origin feature/brand-integration
```

If git asks about setting upstream, accept. The branch is now visible on GitHub.

- [ ] **Step 7.6: Open PR via gh CLI**

```bash
gh pr create \
  --title "feat(brand): Adire Atelier brand asset integration" \
  --body "$(cat <<'EOF'
## Summary

Wires the real Measure Ledger mark from the Adire Atelier Figma brand kit into the StitchPad codebase across every launch-flow and in-app touchpoint. Closes the rebrand sequence after PR-A (tokens), PR-B (screen migration), and PR-C (dashboard illustrations).

**What ships in this PR:**
- Android launcher icons (5 mipmap densities + adaptive XML) — replaces the default Android Studio green template
- iOS app icons (Light + Dark + Tinted) with appearance declarations in `Contents.json`
- New `ui/components/StitchPadMark.kt` ImageVector composable with parameterized colors
- Both duplicate `StitchPadLogo.kt` placeholders deleted (one drew scissors — a brand-rule violation per `project-logo-direction`)
- iOS LaunchScreen storyboard with UIImageView on paperLight
- Android 12+ Splash Screen API config (paperLight bg + adaptive icon foreground)
- Compose animation in `SplashScreen` — mark fade+scale, wordmark slide+fade, tagline fade (staggered, 1.7s total)

**Spec:** `docs/superpowers/specs/2026-05-16-brand-asset-integration-design.md`

## Explicitly out of scope (queued follow-up tickets)

- `feature/brand-receipts` — receipt + PDF template rebrand (has its own header/total-color decisions)
- `feature/brand-onboarding-photos` — regenerate the 3 onboarding `.jpg`s in Adire palette
- `stitchpad-web` dependency note — pull updated hex values from `DesignTokens.kt` after this merges

## Test plan

- [ ] **Android cold launch (emulator, API 33+):** system splash with paperLight bg + foreground icon → Compose splash with animation → Login at ~1.7s
- [ ] **iOS cold launch (iPhone 17 sim):** LaunchScreen storyboard → Compose splash with animation → Login at ~1.7s
- [ ] **AuthHero on Login + SignUp:** inverted mark (white cover + indigo stitches) visible against photo bg
- [ ] **Saffron heritage tick:** visible on the ruler in all default-color contexts
- [ ] **iOS Dark appearance:** launcher icon switches to Dark variant via Settings → Display & Brightness → Dark
- [ ] **iOS Tinted:** monochrome silhouette tints cleanly via Edit Home Screen → Tinted
- [ ] **Android grep verifications:** `grep -rn "StitchPadLogo" composeApp/src/` → empty; `grep -rn "scissors\|drawScissors" composeApp/src/` → empty
- [ ] **Detekt + unit tests + iOS compile:** all green
- [ ] **WCAG contrast verified:** indigo500 on paperLight (8.4:1), indigo400 on inkDark (4.7:1), saffron on indigo500 (4.1:1 — AA Large for decorative accent)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

The PR URL is printed — verify CI starts running and report the URL back.

---

## Self-Review

Spec coverage check:

- ✓ Android launcher icons (Task 1) — 5 mipmap densities + adaptive XML
- ✓ iOS app icons Light/Dark/Tinted (Task 2) — Contents.json with appearance declarations
- ✓ New `StitchPadMark` composable (Task 3) — ImageVector with parameterized colors, KDoc with lockup rules + accessibility notes
- ✓ Delete both placeholder StitchPadLogo files (Task 3) — explicit grep verification for scissors
- ✓ AuthHero inverted variant (Task 3) — color params for white cover + indigo stitches
- ✓ SplashScreen animation (Task 4) — staggered fade + scale + translate with exact timing from spec
- ✓ iOS LaunchScreen storyboard (Task 5) — UIImageView centered on paperLight bg
- ✓ Android 12+ Splash Screen API (Task 6) — Theme.SplashScreen with windowSplashScreenBackground
- ✓ Smoke test (Task 7) — full launch flow on both platforms + grep verifications + WCAG checks
- ✓ PR with structured description + follow-up tickets noted

Placeholder scan: no TBD/TODO/handwave instructions. Every step has exact code or commands.

Type consistency: `StitchPadMark` signature is the same in Task 3 (definition) and Tasks 3/4 (call sites). Color parameter names (`coverColor`, `coverDepthColor`, `detailColor`, `accentColor`) are consistent throughout. Method names match.

No ambiguity: each step is one action with one outcome.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-16-brand-asset-integration.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration. Best for this plan because the 7 tasks have different domains (Android XML, iOS storyboard, Compose, CI) and dispatching a fresh agent per task keeps the context focused.

2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints. Slower but you can watch every step in this conversation.

Which approach?
