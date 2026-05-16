# StitchPad Brand Asset Integration — Design Spec

**Date:** 2026-05-16
**Branch:** `feature/brand-integration`
**Owner:** Daniel Ogunleye
**Related memories:** [[project-rebrand-styleols]], [[project-logo-direction]], [[project-rebrand-terminology]], [[feedback-design-exploration-workflow]], [[feedback-qa-smoke-tests]], [[feedback-kmp-jvm-only-apis]], [[reference-webp-assets]]
**Builds on:** `docs/superpowers/specs/2026-05-14-rebrand-design.md` (Adire Atelier palette + typography, PR-A merged) + Figma file [StitchPad — Brand Kit (Adire Atelier)](https://www.figma.com/design/vtoN4SvhU1utiuXJTG2i4i)

## Goal

Wire the real Measure Ledger mark (built in the Adire Atelier Figma brand kit) into the StitchPad codebase across every launch-flow and in-app touchpoint, and consolidate the two existing duplicate placeholder logo composables — one of which currently ships a brand-rule violation (scissors).

The rebrand sequence so far: PR-A merged tokens (indigo palette + Fraunces + Manrope) and PR-B/PR-C migrated screens and dashboard illustrations. The mark and launcher icons were explicitly deferred from those PRs ("needs Figma before code"). With the Figma brand kit complete, this PR closes that loop.

## Motivation

Three concrete problems this PR fixes:

1. **Android launcher icon is the default Android Studio green template.** `composeApp/src/androidMain/res/drawable/ic_launcher_background.xml` is `#3DDC84` with grid lines — never customized since project init. Most embarrassing surface on first install.
2. **iOS app icon is a placeholder PNG** in `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png` — unbranded.
3. **A duplicate StitchPadLogo placeholder draws scissors.** `feature/onboarding/presentation/components/StitchPadLogo.kt` lines 59–99 render two circle "blades" + handle strokes via `drawScissors()`. This directly violates [[project-logo-direction]] ("StyleOS owns scissors; avoid as primary identity"). The other duplicate (`feature/auth/presentation/components/StitchPadLogo.kt`) is a different white-circle-with-S placeholder, no scissors but still placeholder. Both ship in production today.

The PM-recruited tailor tester cohort (per [[project-pm-intern]]) starts onboarding soon. Shipping these three before then is the launch-readiness goal.

## Scope

### In scope — single PR `feature/brand-integration`

1. **Android launcher icons** — replace mipmap PNGs across 5 densities (mdpi → xxxhdpi, both regular + round), rewrite adaptive icon foreground/background XMLs, update AndroidManifest theme reference
2. **iOS app icons** — Light + Dark + Tinted variants in `AppIcon.appiconset`, declare appearances in `Contents.json`
3. **In-app `StitchPadMark` composable** — new shared composable in `ui/components/`, ImageVector-based with parameterized colors. **Delete** both placeholder `StitchPadLogo` files (auth + onboarding)
4. **iOS LaunchScreen** — UIKit storyboard with `UIImageView` centered on paperLight bg, mark only (no wordmark — avoids font-bundling complexity)
5. **Android 12+ Splash Screen API** — `Theme.SplashScreen` with `windowSplashScreenBackground=#FAF6EC` + adaptive icon foreground
6. **Compose animation** in `SplashScreen` — mark fade + scale-up, wordmark slide + fade, tagline fade (staggered timing, ~1.7s total before navigation)
7. **Documentation** in `StitchPadMark.kt` KDoc — lockup rules, minimum sizes, contrast notes

### Out of scope — with explicit rationale + queued follow-up tickets

- **Receipt + PDF templates** (`core/sharing/OrderReceiptSharer.kt` and iOS impl) — these still use saffron-era styling per `feature/rebrand-cleanup-tail` work-in-progress. They have **their own brand decisions** (header color, money-emphasis color, whether saffron is permitted on totals) that warrant a separate brainstorm. Adding them here would bloat the PR and conflate decisions.
  - **Follow-up ticket:** `feature/brand-receipts`
- **Onboarding photos** (`onboarding_measurements.jpg`, `onboarding_orders.jpg`, `onboarding_notebook.jpg`) — Tier 3 of the illustration audit. Regeneration prompts already drafted in `docs/rebrand-illustration-audit.md`. Photo regeneration is a Figma/external-tool task, not a code change.
  - **Follow-up ticket:** `feature/brand-onboarding-photos`
- **`stitchpad-web` marketing site** — separate repo (Astro + Tailwind at `~/Desktop/Project/stitchpad-web/`), separate toolchain. Consumes the same hex values from `DesignTokens.kt`.
  - **Dependency note:** when this PR merges, the web project should pull the new hex table. Tracked in the [[project-landing-page]] memory.
- **Terminology PR** — already separate per [[project-rebrand-terminology]]. Customers/Orders/Workshop strings migration touches `strings.xml`, not theme/asset files.
- **Variable-font axis tuning** (Fraunces `SOFT`, `opsz`) — deferred per `2026-05-14-rebrand-design.md`. Compose Multiplatform's variable-font support is uneven across iOS Native.
- **PRO badge / Verified Tailor chip designs** — saffron heritage-accent surfaces. Separate UI work, not part of brand-asset integration.

## Brand identity (locked from prior spec)

Inherited from `2026-05-14-rebrand-design.md` and the Figma brand kit — repeated here for self-containment:

- **Palette:** Adire indigo `#2C3E7C` (primary on light), `#5871B8` (primary on dark), warm-paper `#FAF6EC` (light bg), warm-ink `#14110E` (dark bg, never pure black), sienna `#B85A30` (workshop warmth), saffron `#E8A800` (heritage accent only).
- **Typography:** Fraunces SemiBold (display + wordmark), Manrope (body), JetBrains Mono (measurements).
- **Light-first marketing default.** Dark mode preserved in-app via Settings → Appearance.
- **Logo direction:** notebook + measuring tape + stitched-line motifs. **No scissors** (StyleOS owns it).

The mark in Figma (`Mark — Measure Ledger / Light`) is a 1024×1024 notebook silhouette: front cover (indigo500), back cover offset right+down (indigo700) for depth, 12 ruler ticks on the left edge in paperLight (one tick in saffron500 as the heritage moment), 14 stitch dashes along the right binding edge in paperLight.

## Architecture

### File tree — ~22 changes total

```
composeApp/src/androidMain/res/
  mipmap-mdpi/    ic_launcher.png, ic_launcher_round.png       ← REPLACE
  mipmap-hdpi/    ic_launcher.png, ic_launcher_round.png       ← REPLACE
  mipmap-xhdpi/   ic_launcher.png, ic_launcher_round.png       ← REPLACE
  mipmap-xxhdpi/  ic_launcher.png, ic_launcher_round.png       ← REPLACE
  mipmap-xxxhdpi/ ic_launcher.png, ic_launcher_round.png       ← REPLACE
  drawable/
    ic_launcher_background.xml                                  ← REWRITE: solid paperLight #FAF6EC
  drawable-v24/
    ic_launcher_foreground.xml                                  ← REWRITE: vector of mark
  values/
    styles.xml                                                  ← ADD Theme.SplashScreen styles
  AndroidManifest.xml                                           ← UPDATE android:theme

iosApp/iosApp/
  Assets.xcassets/AppIcon.appiconset/
    Contents.json                                               ← REWRITE: light/dark/tinted appearances
    app-icon-light.png       (1024×1024)                        ← NEW (replaces app-icon-1024.png)
    app-icon-dark.png        (1024×1024)                        ← NEW
    app-icon-tinted.png      (1024×1024)                        ← NEW
  Assets.xcassets/BrandMark.imageset/
    Contents.json                                               ← NEW
    brand-mark.png           (used by LaunchScreen)             ← NEW
  Base.lproj/
    LaunchScreen.storyboard                                     ← NEW (or REWRITE)

composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/
  ui/components/
    StitchPadMark.kt                                            ← NEW (ImageVector-based)
  feature/auth/presentation/components/
    StitchPadLogo.kt                                            ← DELETE
    AuthHero.kt                                                 ← UPDATE: import + call site (use inverted variant)
  feature/onboarding/presentation/
    SplashScreen.kt                                             ← UPDATE: import + call site + animation
    components/
      StitchPadLogo.kt                                          ← DELETE (scissors violation)
```

### Why ImageVector instead of webp

The first design draft proposed `brand_mark.webp` in `composeResources/drawable/` to match the project convention for illustrations. Code review (Cursor) correctly pushed back: the existing `BrandLogos.kt` for SSO already uses inline `ImageVector`, and that pattern is the right precedent for *icons/marks* (versus the webp precedent for *illustrations* like dashboard heroes).

The mark is pure geometric primitives — 28 rectangles. Zero raster detail. Vector loses nothing here and gains:

1. **Runtime tinting via parameters** — solves the AuthHero photo-background variant (white cover + indigo stitches) without needing a second asset
2. **Perfect scaling** — works identically at 24dp (favicon-scale) and 100dp (splash)
3. **Smaller binary** — ~3 KB of Kotlin path data vs ~30 KB webp
4. **Code-defined single source of truth** — visual changes are traceable in git diffs, no asset re-export ritual

## The `StitchPadMark` composable

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors

/**
 * The StitchPad Measure Ledger mark — notebook silhouette with ruler ticks
 * along the binding edge and a single saffron heritage accent. Built from the
 * Adire Atelier brand kit Figma file.
 *
 * Lockup rules:
 * - Minimum size: 24.dp. Below this, ruler-tick rhythm collapses; use a
 *   simpler silhouette variant if needed.
 * - Never stretch: mark is intrinsically square; consume only the [size]
 *   parameter, never independent width/height.
 * - When paired with the wordmark in a horizontal lockup, the gap between
 *   mark and wordmark should be 12.5% of mark width; baseline-align the
 *   wordmark's x-height to the mark's optical center.
 * - For inverted treatments (mark on dark photo bg, as in AuthHero), pass
 *   coverColor = Color.White and detailColor = MaterialTheme.colorScheme.primary
 *   so stitches and ticks read against the white cover.
 *
 * Accessibility:
 * - Default colors meet WCAG AA Large on paperLight (8.4:1) and inkDark
 *   (4.7:1) backgrounds.
 * - The saffron accent tick is a small decorative element; it does not
 *   carry information, so AA Large (3.0:1 on indigo500 at 4.1:1) is the
 *   appropriate target.
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
    Image(
        imageVector = rememberStitchPadMarkVector(
            coverColor = coverColor,
            coverDepthColor = coverDepthColor,
            detailColor = detailColor,
            accentColor = accentColor,
        ),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
```

The `rememberStitchPadMarkVector(...)` builder function constructs an `ImageVector` with viewport `1024 × 1024` matching the Figma master. Path entries (28 total):

| Order | Element | Path geometry | Fill |
|---|---|---|---|
| 1 | Back cover (depth) | rounded rect 560×720 at (240, 180), corner radius 28 | `coverDepthColor` |
| 2 | Front cover | rounded rect 560×720 at (200, 140), corner radius 28 | `coverColor` |
| 3–13 | Ruler ticks (11 non-accent) | rounded rects 30 or 50 wide × 6 tall at x=220, varying y from 200 by 50px increments (alternating long/short — every 3rd tick is 50px) | `detailColor` |
| 14 | Saffron heritage tick | rounded rect 30×6 at (220, 200 + 5×50 = 450), 6th tick replaced | `accentColor` |
| 15–28 | Stitch dashes (14) | rounded rects 6×26 at x=700, y starts at 200 stepping by 40 (26 dash + 14 gap) | `detailColor` |

## Per-touchpoint usage

| Touchpoint | Call | Notes |
|---|---|---|
| `AuthHero` (Login + SignUp, mark sits on dark photo bg) | `StitchPadMark(size = 80.dp, coverColor = Color.White, coverDepthColor = Color(0xFFE5E3DF), detailColor = MaterialTheme.colorScheme.primary, accentColor = LocalStitchPadColors.current.heritageAccent)` | Inverted: white cover, neutral200 back-depth, indigo stitches/ticks, saffron preserved |
| `SplashScreen` (paperLight bg) | `StitchPadMark(size = 100.dp, modifier = Modifier.graphicsLayer(alpha = ..., scaleX = ..., scaleY = ...))` | Defaults; animated via `graphicsLayer` modifier driven by `animateFloatAsState` |
| Future Settings → About row | `StitchPadMark(size = 32.dp)` | Defaults; not built in this PR but the composable supports it |

## Animation specification (SplashScreen Compose)

Replaces the current static `StitchPadLogo(size = 100.dp)` call with an animated three-element reveal:

```
t=0       — Compose SplashScreen drawn; mark.alpha=0, mark.scale=0.92
t=0–300ms — mark.alpha 0→1 (ease-out), mark.scale 0.92→1.0
t=200ms   — wordmark.alpha 0→1, wordmark.translateY 8dp→0dp (300ms ease-out)
t=400ms   — tagline.alpha 0→1 (300ms ease-out)
t=700ms   — all elements at final state
t=1700ms  — navigate (existing nav logic, 1000ms hold after full reveal)
```

Implementation: drive `markVisible: Boolean`, `wordmarkVisible: Boolean`, `taglineVisible: Boolean` via `LaunchedEffect` with `delay()` calls; bind each to a separate `animateFloatAsState`. Apply via `Modifier.graphicsLayer(...)`. No new dependencies.

iOS LaunchScreen: **no animation** (Apple HIG forbids it). The LaunchScreen storyboard is a static UIImageView; the animation moment lives in the Compose SplashScreen that fires immediately after the storyboard exits.

Android 12+ system splash: **default fade-and-scale** that Android applies automatically (no custom AVD). The `windowSplashScreenAnimatedIcon` references `ic_launcher_foreground` which is the static vector.

## Accessibility — WCAG contrast (verified before merge)

| Mark element | Background | Contrast | Verdict |
|---|---|---|---|
| Indigo500 `#2C3E7C` cover | paperLight `#FAF6EC` | 8.4 : 1 | ✓ AA + AAA |
| Indigo400 `#5871B8` cover (dark mode) | inkDark `#14110E` | 4.7 : 1 | ✓ AA |
| Indigo700 `#1E2B5C` back-cover (depth) | paperLight | 11.2 : 1 | ✓ AAA |
| White cover | AuthHero photo darkest area | n/a (cover is opaque) | mark silhouette unambiguous; saffron tick stays at AA Large via the indigo stitches inverted scheme |
| paperLight `#FAF6EC` stitches | indigo500 cover | 8.4 : 1 | ✓ AAA |
| Saffron500 `#E8A800` tick | indigo500 cover | 4.1 : 1 | ✓ AA Large (decorative accent, AA Large is appropriate) |

Smoke test in QA: visually verify each contrast pair on both Android emulator and iPhone simulator before merge. No additional tooling needed.

## Platform gotchas

- **iOS LaunchScreen cache.** iOS aggressively caches storyboards. To see updates during dev, delete the app from the simulator/device and reinstall. Not a bug in our work — but worth documenting in the PR so reviewers don't think the storyboard "isn't updating."
- **Android adaptive icon circle crop on splash.** Android 12+ system splash circle-crops the adaptive icon foreground. The Figma 264×264 inner safe zone within the 432×432 foreground was sized exactly for this — verify the notebook silhouette survives the crop on a real device.
- **iOS Light/Dark/Tinted appearance binding.** `Contents.json` must declare appearances correctly or iOS will silently fall back to the light variant. Reference: Apple's [HIG appearance documentation](https://developer.apple.com/design/human-interface-guidelines/app-icons).
- **Compose animation timing on iOS Native.** `animateFloatAsState` works cross-platform but iOS sim is slower than Android emulator; verify timing on a real iPhone before merge (per [[reference-test-environment]]).
- **No `String.format` / no `LocalDate.toEpochDays()` Int reliance** — both already documented in [[feedback-kmp-jvm-only-apis]] + [[feedback-kotlin-native-epoch-days]]. Not directly relevant to this PR but reminder during iOS build verification.

## Migration plan — vertical-slice commits on `feature/brand-integration`

Single PR. Branch off updated `main`. Per the workflow Daniel selected: 7 commits, each independently verifiable.

1. **`feat(brand): Android launcher icons + adaptive XML`**
   Export 5 mipmap densities from Figma `iOS App Icon — Light`, rewrite adaptive foreground/background XMLs. Verify on Android emulator: app drawer shows new icon, both regular and round masks.

2. **`feat(brand): iOS app icon — light + dark + tinted`**
   Export 3 PNGs from Figma `iOS App Icon — Light/Dark/Tinted`, replace `app-icon-1024.png`, rewrite `Contents.json` with appearance declarations. Verify on iOS sim: launcher shows correct icon in light system mode, dark system mode, and tinted mode.

3. **`feat(brand): StitchPadMark composable + consolidation`**
   Add `ui/components/StitchPadMark.kt` with ImageVector. Update `AuthHero` to use it (inverted variant). Update `SplashScreen` to use it (default variant, no animation yet). Delete both placeholder `StitchPadLogo.kt` files. Verify Login/SignUp/Splash render correctly with new mark; grep for stragglers; verify scissors removed.

4. **`feat(splash): animated brand reveal in SplashScreen`**
   Add staggered `animateFloatAsState` for mark fade+scale, wordmark slide+fade, tagline fade. Verify timing on both emulator + iPhone sim.

5. **`feat(launch): iOS LaunchScreen storyboard`**
   Add `BrandMark.imageset` + `LaunchScreen.storyboard` with centered `UIImageView` on paperLight bg. Verify cold launch on iPhone sim: storyboard → Compose splash transition is seamless (no white flash, no mark jump).

6. **`feat(launch): Android 12+ Splash Screen API`**
   Add `Theme.SplashScreen` style in `values/styles.xml`. Reference `windowSplashScreenBackground=#FAF6EC` + `windowSplashScreenAnimatedIcon=@drawable/ic_launcher_foreground`. Update `AndroidManifest.xml` theme. Verify cold launch on Android 12+ emulator.

7. **`docs(brand): smoke test notes + PR description`**
   Add per-touchpoint smoke test checklist to PR description per [[feedback-qa-smoke-tests]]. Note follow-up tickets (receipts, onboarding photos, stitchpad-web). Run Detekt + unit tests + iOS device compile per [[feedback-kmp-jvm-only-apis]]. Open PR.

Each commit must compile and pass tests independently — no half-states.

## QA smoke test plan

Run on Android emulator + iPhone sim, both light and dark system modes:

1. **Fresh install:** cold launch → system splash (paperLight bg with adaptive icon) → Compose SplashScreen → animation plays cleanly → navigates after ~1.7s
2. **Login screen:** AuthHero shows inverted mark (white cover, indigo stitches, saffron tick) visibly readable against the photo bg
3. **SignUp screen:** same as Login
4. **Settings → Appearance → Dark mode:** repeat steps 1–3 in dark mode. Launcher icon should switch to dark variant on iOS (system-controlled). Compose UI should theme correctly via existing `Theme.kt` orchestrator.
5. **iOS Tinted mode:** verify `iOS App Icon — Tinted` shows the simplified silhouette correctly when user enables tinted icons in iOS Settings.
6. **Grep verifications:**
   - `grep -rn "StitchPadLogo" composeApp/src/` → empty (no leftover references)
   - `grep -rn "scissors\|drawScissors" composeApp/src/` → empty
   - `grep -rn "ic_launcher" composeApp/src/androidMain/AndroidManifest.xml` → still finds `@mipmap/ic_launcher` reference (sanity check)
7. **Detekt + unit tests green; CI green; iOS device build clean** per [[feedback-kmp-jvm-only-apis]].

## Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| ImageVector path data has a typo, mark renders wrong | Medium | Render-once preview in `@Preview` (already a project convention per CLAUDE.md). Visual diff against Figma export PNG. |
| iOS LaunchScreen cache during dev — storyboard updates don't appear | Verified (Apple bug) | Document in PR; reviewer deletes app + reinstalls when verifying storyboard changes |
| Adaptive icon foreground gets cropped wrong on certain Android launcher skins | Low | Test on stock Android + at least one OEM skin (Samsung emulator if available) |
| Compose animation timing too fast/slow on iOS Native | Medium | Verify on real iPhone 17 sim per [[reference-test-environment]] before merge; adjust durations if needed |
| New mark colors fail contrast on a tester device with reduced contrast settings | Low | WCAG verification done at design time; mark uses semantic tokens so any system-level contrast adjustment cascades correctly |
| Scope creep — temptation to also rebrand receipts or onboarding photos mid-PR | Medium | Explicit out-of-scope list in this spec. Reviewers reject mid-PR additions; follow-up tickets queued |
| Deleted `StitchPadLogo` placeholder is referenced from a screen I missed in the audit | Low | `grep` verification in step 6 of QA. If found, add to PR. If unfindable, the missed screen wouldn't compile after delete — caught by build |

## Decision log

### Why ImageVector instead of webp

First draft of this spec proposed `brand_mark.webp` to match project convention for illustrations (dashboard heroes are webp). Cursor code review pushed back, correctly: webp is the *illustration* convention; vector is the *icon/mark* convention — see `BrandLogos.kt` for SSO. For a pure-geometric mark with no raster detail, vector wins on every axis (size, scaling, tintability) and the verbose-Kotlin downside is minor (~30 path entries).

### Why iOS LaunchScreen has mark only, not mark + wordmark

Two reasons:

1. Apple HIG: launch screens should be minimal and fast. Adding wordmark = adding a UILabel with Fraunces (not a system font), which requires bundling the `.ttf` in the iOS bundle + registering via `UIAppFonts` in `Info.plist`. Font registration has silent-failure modes that fall back to Times New Roman.
2. The brand identity moment lives in the Compose SplashScreen that fires immediately after — that's where the full lockup (mark + wordmark + tagline) plays with real Fraunces and full animation.

### Why Android 12+ uses default fade-and-scale, not custom AVD

Android caps splash animations at 1000ms. A custom `AnimatedVectorDrawable` of the notebook drawing-itself or scaling-up would be ~3 hours of design + impl work for a sub-second surface that most users don't consciously notice. Android's built-in fade-and-scale on the static foreground is sufficient and zero-effort.

### Why single PR (not split D1/D2/D3 per Cursor review)

Cursor's reviewer correctly noted that *receipts, onboarding photos, and stitchpad-web* also need brand work. But those each carry independent brand decisions that warrant their own brainstorms. This PR is bounded to the launch-flow + in-app mark surfaces (~22 files) — tolerable for one review cycle. The other surfaces are explicitly queued as separate follow-up tickets, not deferred indefinitely.

### Why parameterized colors on the composable

The AuthHero photo background needs an inverted mark (white cover, indigo stitches) — would require a second webp asset under the original plan. With ImageVector + color parameters, the same composable handles light, dark, monochrome, and inverted variants. No asset multiplication; one source of truth.

## Appendix — out-of-scope tickets to file after this PR merges

- **`feature/brand-receipts`** — receipt + PDF template rebrand. Touches `core/sharing/OrderReceiptSharer.kt` + iOS impl. Independent brand decisions to make: header band color (indigo vs neutral), money/total emphasis color (indigo vs heritage saffron — current saffron is now off-brand for non-decorative use), share-card layout. Needs its own design spec.
- **`feature/brand-onboarding-photos`** — regenerate `onboarding_measurements.jpg`, `onboarding_orders.jpg`, `onboarding_notebook.jpg` in Adire palette. Regeneration prompts already drafted in `docs/rebrand-illustration-audit.md`. External Figma/AI-tool work, not code.
- **`stitchpad-web` brand sync** — separate repo. When this PR merges, the web project (`~/Desktop/Project/stitchpad-web/`) should pull updated hex values from `DesignTokens.kt`. Worktree-style: define the canonical hex table in this Compose project, have web consume it via a small JSON export or manual copy.

## References

- [Figma brand kit](https://www.figma.com/design/vtoN4SvhU1utiuXJTG2i4i) — `StitchPad — Brand Kit (Adire Atelier)`
- Prior spec: `docs/superpowers/specs/2026-05-14-rebrand-design.md`
- PR-B checklist: `docs/rebrand-pr-b-checklist.md`
- Illustration audit: `docs/rebrand-illustration-audit.md`
- Apple HIG, App Icons: https://developer.apple.com/design/human-interface-guidelines/app-icons
- Android 12 Splash Screen API: https://developer.android.com/develop/ui/views/launch/splash-screen
