# StitchPad Rebrand — "Adire Atelier" Design Spec

**Date:** 2026-05-14
**Branch:** `feature/rebrand-tokens` (and follow-ups)
**Owner:** Daniel Ogunleye
**Related memories:** [[project-rebrand-styleos]], [[project-rebrand-terminology]], [[project-logo-direction]], [[feedback-design-exploration-workflow]], [[reference-webp-assets]], [[feedback-qa-smoke-tests]]

## Goal

Replace StitchPad's current saffron-yellow brand system with a distinctive new visual identity — "Adire Atelier" — anchored on adire indigo, burnt sienna, and warm paper. Ship this rebrand as the foundation for V1 launch, before tailor-tester recruitment expands beyond the initial PM-recruited cohort.

The rebrand covers: palette (semantic tokens), typography (display + body + mono), and the supporting design-token architecture. It does **not** cover: logo redesign, terminology copy shifts, illustrations regeneration, or new screens — each tracked separately (see "Out of scope" below).

## Motivation

A competitor — **StyleOS** (styleos.io, EasyHub Tech Ltd, App Store id 6761619830) — launched in Nigeria with a visual identity that overlaps StitchPad on every axis:

| Axis | StyleOS | Current StitchPad |
|---|---|---|
| Primary color | Vibrant yellow `#FFD700` | Deep saffron `#E8A800` |
| Background | True black `#000000` | Warm dark `#121110` |
| Positioning | "Fashion Business Management — all-in-one app for fashion entrepreneurs" | Same vocabulary |
| Tabs | Home / Clients / Jobs / Finance / More | Dashboard / Customers / Orders / Reports / Settings |
| Target | Nigerian tailors, designers, fabric vendors | Nigerian tailors |
| Distribution | Live on iOS + Android | Pre-launch |

If StitchPad ships its current identity, first-impression confusion is near-certain. The rebrand is launch-blocking.

The Adire Atelier direction was selected from a six-week cross-LLM design exploration (ChatGPT, Manus, parallel Claude agents) and validated through six iterative HTML mockup rounds under `preview/`. The choice rests on:

1. **Adire indigo `#2C3E7C`** carries the Yoruba indigo-dye textile reference — a cultural anchor no productivity-app competitor in this market owns.
2. **Indigo + red status colors don't collide.** Burgundy, terracotta, and the bright-yellow alternative all created status-pill confusion with the existing Overdue red `#D93B3B`. Indigo lives on the opposite side of the color wheel.
3. **Light-first marketing default** further differentiates from StyleOS's dark-first marketing language and is more legible for Nigerian tailors on affordable Android devices in bright sunlight.
4. **Saffron survives as a rare heritage accent** (PRO badges, ★ marks, Verified Tailor chips) — preserving brand continuity without the StyleOS-overlap problem.

## Out of scope

These are each separate efforts, not blocked by this rebrand and not riding it:

- **Logo redesign.** Tracked in [[project-logo-direction]]. Different surface (mark, not system tokens). Needs Figma iteration before any code change. Avoid scissors as primary identity (StyleOS owns it); favor notebook + measuring-tape + stitched-line motifs.
- **Terminology copy shifts** (Customers/Orders/Workshop, not Clients/Jobs/Business). Tracked in [[project-rebrand-terminology]]. Touches `strings.xml`, not theme files. Cleaner as its own PR; can land before or after this rebrand sequence.
- **AI assistant integration.** Tracked in [[project-ai-assistant]]. Separate launch feature, needs its own brainstorming.
- **Custom measurement fields.** Tracked in [[project-custom-measurements]]. Backlog, post-launch.
- **Animation refinements.** Per-screen polish. Lands incrementally as features touch screens.
- **New / missing screens.** When screenshots arrive — separate brainstorming, separate PRs.
- **Marketing website rebrand.** Tracked in [[project-landing-page]]. Different repo (Astro + Tailwind, `~/Desktop/Project/stitchpad-web/`), different toolchain. Will consume the same hex tokens but is its own project.
- **Automated visual-regression testing** (Paparazzi / Roborazzi). The right time to add screenshot diffs is *after* the rebrand stabilizes, not during. Adding them now would invalidate every baseline on day one. Manual QA per [[feedback-qa-smoke-tests]] is sufficient at solo-dev scale.
- **Variable-font axis tuning** (Fraunces `SOFT`, `opsz`). Compose Multiplatform's font-variation support is uneven across platforms (works on Android 26+, partial on iOS Native). V0 of the rebrand uses Fraunces at default axes; tuning is a follow-up refinement, not a launch blocker.

## Brand identity (locked)

### Palette

Light-first by default. Both modes derive from the same primitives via Material3 `ColorScheme` builders — composables never branch on theme.

| Role | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#FAF6EC` | `#14110E` | Warm paper / warm ink |
| `surf` | `#FFFFFF` | `#1E1B17` | Cards |
| `surf-elevated` | `#FFFFFF` | `#26221D` | Cards-in-cards (order rows inside Today card) |
| `text-primary` | `#1C1914` | `#F0EBE3` | Body + headlines |
| `text-muted` | `#5C574E` | `#B8AA98` | Muted / secondary text |
| `border` | `rgba(28,25,20,.08)` | `#3A3228` | Card borders |
| `primary.brand` | `#2C3E7C` | `#7388BF` | Wordmark, links, outlines |
| `primary.solid` | `#1E2B5C` | `#4F65A8` | Filled CTAs, white text rides |
| `primary.pill` | `#EAEEF8` | `#2D3B6B` | Avatar bg, pill backgrounds |
| `primary.accent` (new) | `#5871B8` | `#9CB0DD` | Hero illustration strokes, marketing-hero accent, decorative motifs |
| `secondary` (sienna) | `#B85A30` | `#D9885F` | Section pills (NEXT BEST ACTIONS), action accents |
| `accent.heritage` (saffron) | `#E8A800` | `#E8A800` | PRO badges, ★ marks, Verified Tailor chips — RARE only |

Status palettes, semantic colors (success/error/warning/info), and the 6 avatar pairs from the current `DesignTokens.kt` are **kept verbatim**. They're already off-brand-primary and visually distinct from the new indigo / sienna / saffron values.

### Typography

| Role | Font | Weights | Use |
|---|---|---|---|
| Display | **Fraunces** | 400-700 (variable axes available but unused in V0) | Wordmark, headlines, hero text |
| Body | **Manrope** | 400 / 500 / 600 / 700 | All body text, buttons, labels |
| Mono | **JetBrains Mono** (kept) | 400 / 500 | Measurements, due dates, numeric values |

Plus Jakarta Sans is removed. Fraunces + Manrope + JetBrains Mono is the final pairing — validated against Instrument Serif, DM Serif Display, and Lora in a head-to-head comparison (`preview/rebrand-type-variants.html`). Decision log entry in the appendix.

### Tone of voice (copy direction)

Primary tagline: **"The smart work pad for tailors."**
Marketing hero secondary: **"Replace your paper measurement book."**
Core sentence: **"StitchPad helps Nigerian tailors save measurements, track orders, collect payments, and know what to do next — without paper notebooks."**

Full terminology shift table in [[project-rebrand-terminology]]. Strings migration is a separate PR.

### Brand mode default

Light-first. Marketing surfaces (web, App Store screenshots, onboarding hero) all default light. Dark mode exists in-app for users who want it (already supported via Settings → Appearance) but is not the first-impression mode.

## Architecture — token layers

Three layers. Each has one responsibility. Composables only consume the top.

```
┌─ Layer 1: Primitives ─────────────────────────────────────────┐
│  DesignTokens.kt — raw hex values, single source of truth.    │
│  Indigo50/100/200/300/400/500/700/900, Sienna300/500/700,     │
│  Saffron500, warm neutrals (kept), status palettes (kept).    │
│  NEVER consumed directly by composables.                      │
└────────────────────┬──────────────────────────────────────────┘
                     │
       ┌─────────────┴──────────────┐
       │                            │
┌──────▼──────────────────┐  ┌──────▼───────────────────────────┐
│ Layer 2a: Material3      │  │ Layer 2b: StitchPadColors (new)  │
│ ColorScheme              │  │                                  │
│                          │  │ Brand-specific slots Material3   │
│ stitchPadLightColors()   │  │ doesn't model:                   │
│ stitchPadDarkColors()    │  │  - heritageAccent (saffron)      │
│                          │  │  - brandAccent (lighter indigo)  │
│ Maps primitives to       │  │                                  │
│ Material's named slots   │  │ Provided via CompositionLocal    │
│ (primary, onPrimary,     │  │ alongside MaterialTheme.         │
│ secondary, surface, etc.)│  │                                  │
└──────────┬───────────────┘  └────────────┬─────────────────────┘
           │                               │
           └───────┬───────────────────────┘
                   │
        ┌──────────▼─────────────────────────────┐
        │  Layer 3: StitchPadTheme orchestrator  │
        │  (Theme.kt)                            │
        │                                        │
        │  Provides MaterialTheme +              │
        │  LocalStitchPadColors + Typography     │
        └──────────┬─────────────────────────────┘
                   │
        ┌──────────▼─────────────────────────────┐
        │  Composables consume:                  │
        │   - MaterialTheme.colorScheme.primary  │
        │   - LocalStitchPadColors.current.*     │
        │   - MaterialTheme.typography.*         │
        └────────────────────────────────────────┘
```

### How light/dark dual values resolve

Tokens are single values. The light/dark selection happens at the *scheme* layer, not the *primitive* layer. Composables never branch.

```kotlin
// DesignTokens.kt — single value per name
val indigo500 = Color(0xFF2C3E7C)
val indigo300 = Color(0xFF7388BF)

// Color.kt — schemes pick which primitive to use
fun stitchPadLightColorScheme() = lightColorScheme(
    primary = DesignTokens.indigo500,   // deep
    ...
)
fun stitchPadDarkColorScheme() = darkColorScheme(
    primary = DesignTokens.indigo300,   // light
    ...
)

// Screen code — single source of truth
Text(color = MaterialTheme.colorScheme.primary)  // resolves correctly in both modes
```

### Why a parallel `StitchPadColors` layer instead of overloading Material slots

- **Material3's `tertiary`** is conventionally low-emphasis secondary action color. Mapping our sienna to `tertiary` is fine and we will — sienna lives in Material's `tertiary` slot.
- **Heritage saffron has no Material equivalent.** It's not a secondary, not a tertiary — it's a rare-use accent that shouldn't propagate into Material's own components automatically.
- **`primary.accent`** (lighter indigo for hero illustrations) shouldn't propagate to Material's `secondary` slot either — Material's `secondary` gets used by `OutlinedButton`, `Chip`, and other components automatically. We want explicit, intentional consumption of `brandAccent`, not Material's surprises.

Two slots in `StitchPadColors`: `heritageAccent` + `brandAccent`. Anything else Material covers natively (including `surfaceContainerHigh` for elevated surfaces, `onSurfaceVariant` for muted text).

## File-level changes

### `ui/theme/DesignTokens.kt` — modified

**Removed:**
- The full `primary50…primary900` saffron ramp (kept only `saffron500 = #E8A800` as the heritage value)

**Added:**

```kotlin
// Indigo ramp — adire textile reference
val indigo50  = Color(0xFFEAEEF8)
val indigo100 = Color(0xFFD1D9EE)
val indigo200 = Color(0xFF9CB0DD)  // new — for brandAccent dark
val indigo300 = Color(0xFF7388BF)  // brand on dark
val indigo400 = Color(0xFF5871B8)  // new — for brandAccent light
val indigo500 = Color(0xFF2C3E7C)  // brand on light
val indigo700 = Color(0xFF1E2B5C)  // CTA fill on light
val indigo900 = Color(0xFF121B3B)

// Sienna ramp — workshop bench warmth (only 3 stops)
val sienna300 = Color(0xFFD9885F)
val sienna500 = Color(0xFFB85A30)
val sienna700 = Color(0xFF8E4524)

// Saffron — heritage accent only (single value)
val saffron500 = Color(0xFFE8A800)

// Renamed surface tokens for clarity
val paperLight = Color(0xFFFAF6EC)  // new — replaces neutral50 as bg.light
val inkDark    = Color(0xFF14110E)  // was darkBg
```

**Kept verbatim:**

- All `neutral0…neutral900` warm-gray values
- All status colors (`statusReceived`, `statusCutting`, `statusSewing`, `statusReady`, `statusDelivered`, `statusOverdue`)
- All 6 `avatarColors` pairs
- All semantic colors (`success500`, `error500`, `warning500`, `info500` + dark variants)
- All `space*`, `radius*`, `icon*`, `elevation*`, `duration*` constants
- All `display*` / `heading*` / `body*` / `label*` / `measurement` type-size constants

### `ui/theme/Color.kt` — modified

Rewrite both ColorScheme builders. Function-style instead of val-style so we can layer platform-specific tweaks later (Android 12+ dynamic color) without restructuring.

```kotlin
fun stitchPadLightColorScheme() = lightColorScheme(
    primary = DesignTokens.indigo500,
    onPrimary = Color.White,
    primaryContainer = DesignTokens.indigo50,
    onPrimaryContainer = DesignTokens.indigo700,
    secondary = DesignTokens.indigo700,        // muted brand variant
    onSecondary = Color.White,
    tertiary = DesignTokens.sienna500,         // workshop warmth
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCEFE5),     // sienna50 (inline)
    onTertiaryContainer = DesignTokens.sienna700,
    background = DesignTokens.paperLight,
    onBackground = DesignTokens.neutral800,
    surface = Color.White,
    onSurface = DesignTokens.neutral800,
    surfaceVariant = DesignTokens.neutral100,
    onSurfaceVariant = DesignTokens.neutral600,
    error = DesignTokens.error500,
    onError = Color.White,
    errorContainer = DesignTokens.error50,
    onErrorContainer = DesignTokens.error500,
    outline = DesignTokens.neutral200,
    outlineVariant = DesignTokens.neutral100,
)

fun stitchPadDarkColorScheme() = darkColorScheme(
    primary = DesignTokens.indigo300,          // tonal lift for dark
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D3B6B),      // avatar bg — between 700 and 900
    onPrimaryContainer = DesignTokens.indigo100,
    secondary = DesignTokens.indigo400,
    onSecondary = Color.White,
    tertiary = DesignTokens.sienna300,
    onTertiary = DesignTokens.neutral900,
    background = DesignTokens.inkDark,
    onBackground = DesignTokens.darkText,
    surface = DesignTokens.darkSurface,
    onSurface = DesignTokens.darkText,
    surfaceVariant = DesignTokens.darkSurface2,
    onSurfaceVariant = DesignTokens.darkTextSecondary,
    error = DesignTokens.errorDarkText,
    onError = DesignTokens.neutral900,
    errorContainer = DesignTokens.errorDarkBg,
    onErrorContainer = DesignTokens.errorDarkText,
    outline = DesignTokens.darkBorder,
    outlineVariant = DesignTokens.darkSurface2,
)
```

### `ui/theme/StitchPadColors.kt` — new file

```kotlin
package com.danzucker.stitchpad.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class StitchPadColors(
    val heritageAccent: Color,
    val brandAccent: Color,
)

val LightStitchPadColors = StitchPadColors(
    heritageAccent = DesignTokens.saffron500,
    brandAccent    = DesignTokens.indigo400,  // #5871B8
)

val DarkStitchPadColors = StitchPadColors(
    heritageAccent = DesignTokens.saffron500,  // saffron doesn't tonally shift
    brandAccent    = DesignTokens.indigo200,   // #9CB0DD
)

val LocalStitchPadColors = staticCompositionLocalOf<StitchPadColors> {
    error("StitchPadColors not provided — wrap content in StitchPadTheme")
}
```

### `ui/theme/Theme.kt` — modified

One new `CompositionLocalProvider` line. Everything else identical.

```kotlin
val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun StitchPadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) stitchPadDarkColorScheme() else stitchPadLightColorScheme()
    val stitchPadColors = if (darkTheme) DarkStitchPadColors else LightStitchPadColors

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalStitchPadColors provides stitchPadColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = StitchPadTypography(),
            content = content
        )
    }
}
```

### `ui/theme/Type.kt` — modified

```kotlin
@Composable
fun FrauncesFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_variable, FontWeight.Normal),
    Font(Res.font.fraunces_variable, FontWeight.Medium),
    Font(Res.font.fraunces_variable, FontWeight.SemiBold),
    Font(Res.font.fraunces_variable, FontWeight.Bold),
)

@Composable
fun ManropeFamily(): FontFamily = FontFamily(
    Font(Res.font.manrope_regular,  FontWeight.Normal),
    Font(Res.font.manrope_medium,   FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold,     FontWeight.Bold),
)

@Composable
fun JetBrainsMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_medium,  FontWeight.Medium),
)

@Composable
fun StitchPadTypography(): Typography {
    val fraunces = FrauncesFamily()
    val manrope  = ManropeFamily()
    return Typography(
        // Display + Headline use Fraunces (was PlusJakartaSans)
        displayLarge   = TextStyle(fontFamily = fraunces, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.displayLg, lineHeight = DesignTokens.displayLg * 1.25f),
        displayMedium  = TextStyle(fontFamily = fraunces, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.displayMd, lineHeight = DesignTokens.displayMd * 1.29f),
        headlineLarge  = TextStyle(fontFamily = fraunces, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.headingLg, lineHeight = DesignTokens.headingLg * 1.33f),
        headlineMedium = TextStyle(fontFamily = fraunces, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.headingMd, lineHeight = DesignTokens.headingMd * 1.4f),
        headlineSmall  = TextStyle(fontFamily = fraunces, fontWeight = FontWeight.SemiBold, fontSize = DesignTokens.headingSm, lineHeight = DesignTokens.headingSm * 1.44f),
        // Body + Label use Manrope (was PlusJakartaSans)
        bodyLarge   = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Normal, fontSize = DesignTokens.bodyLg, lineHeight = DesignTokens.bodyLg * 1.5f),
        bodyMedium  = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Normal, fontSize = DesignTokens.bodyMd, lineHeight = DesignTokens.bodyMd * 1.57f),
        bodySmall   = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Normal, fontSize = DesignTokens.bodySm, lineHeight = DesignTokens.bodySm * 1.54f),
        labelLarge  = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = DesignTokens.labelLg, lineHeight = DesignTokens.labelLg * 1.43f),
        labelMedium = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = DesignTokens.labelMd, lineHeight = DesignTokens.labelMd * 1.38f),
        labelSmall  = TextStyle(fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = DesignTokens.labelSm, lineHeight = DesignTokens.labelSm * 1.45f),
    )
}
```

JetBrains Mono is used at the *style* level, not in `MaterialTheme.typography`. Components that need monospace (measurement entry, due-date labels) reference `JetBrainsMonoFamily()` directly.

### Font assets — `composeApp/src/commonMain/composeResources/font/`

| Remove | Add | Keep |
|---|---|---|
| `plus_jakarta_sans_regular.ttf` | `fraunces_variable.ttf` (single variable file) | `jetbrains_mono_regular.ttf` |
| `plus_jakarta_sans_medium.ttf` | `manrope_regular.ttf` | `jetbrains_mono_medium.ttf` |
| `plus_jakarta_sans_semibold.ttf` | `manrope_medium.ttf` | |
| `plus_jakarta_sans_bold.ttf` | `manrope_semibold.ttf` | |
| | `manrope_bold.ttf` | |

Net: -4 files, +5 files. All sourced from Google Fonts (OFL license — safe to redistribute in app).

## Migration plan — PR sequence

The rebrand is too large for a single PR. Three sequential PRs, each independently reviewable and reversible.

### PR-A — Tokens-only rebrand foundation

**Branch:** `feature/rebrand-tokens`

**Files touched:**

- `DesignTokens.kt` (rewrite palette section)
- `Color.kt` (rewrite both schemes)
- `StitchPadColors.kt` (NEW)
- `Theme.kt` (wire `LocalStitchPadColors`)
- `Type.kt` (font swap)
- `composeResources/font/` (asset swap)

**Files NOT touched:**

- `strings.xml` (no copy changes — separate PR)
- `feature/**/*` (no screen migrations — PR-B)
- `ui/components/**/*` (no component changes — PR-B)

**What this PR achieves:** the app still builds and runs. Every screen automatically picks up the new colors via `MaterialTheme.colorScheme.*`. Some screens may have hardcoded color literals or `DesignTokens.primary*` references that now look off — those are catalogued for PR-B, not fixed here.

**Smoke test (per [[feedback-qa-smoke-tests]]):**

Android + iOS, both light and dark mode:

1. App launches without crash
2. Login screen renders — wordmark in indigo, primary CTA in indigo, Fraunces visible in headlines
3. Sign up → Workshop setup → Home navigation works without crash
4. Dashboard tab opens — Today card renders, status pills visible, no token-resolution errors in logs
5. Settings tab opens — looks broadly OK (cosmetic mismatches expected)
6. Toggle Settings → Appearance → Dark, repeat steps 2-5

**Visual mismatches are NOT bugs at this point.** They're PR-B's job. The smoke test is "does the foundation hold."

**Done criteria:**

- App builds + boots on Android and iOS, both modes
- 6 smoke steps pass
- No regressions in existing unit tests
- Detekt + CI green
- Manual smoke notes in PR description
- Visual mismatches catalogued (commit a `docs/rebrand-pr-b-checklist.md` listing screens that need fixing in PR-B)

### PR-B — Screen migration sweep

**Branch:** `feature/rebrand-migration` (branched off updated `main` after PR-A merges)

Mechanical find-and-replace pass across the codebase:

| Find | Replace |
|---|---|
| `DesignTokens.primary500` etc. (renamed in PR-A) | `DesignTokens.indigoXXX` or — preferred — `MaterialTheme.colorScheme.primary` |
| `Color(0xFFE8A800)` (saffron literals) | `LocalStitchPadColors.current.heritageAccent` |
| Any other hardcoded `Color(0xFF...)` in feature code | Audit + replace with the right semantic token |
| `PlusJakartaSansFamily` references | Removed; falls through to `MaterialTheme.typography.*` |

**Scope:** every file under `feature/` and `ui/components/`.

**Output:** zero hardcoded colors in feature code, zero font-family references outside `ui/theme/`.

**Done criteria:**

- All find-and-replace targets resolved (verify by grep: `grep -r "DesignTokens.primary" composeApp/src/` returns nothing outside `ui/theme/`)
- 10 representative screens spot-checked against the deep-dive mockup (`preview/rebrand-variant-a-deep-dive.html`)
- iOS device build clean — per [[feedback-kmp-jvm-only-apis]] and [[feedback-kotlin-native-epoch-days]], run `./gradlew :composeApp:compileKotlinIosArm64` before declaring done
- Saffron only appears on PRO badges / ★ marks / Verified Tailor chips (zero saffron CTAs, zero saffron backgrounds, zero saffron primaries)
- Manual smoke + screen-comparison notes in PR description

### PR-C — Hero illustrations + empty states

**Branch:** `feature/rebrand-illustrations` (branched off updated `main` after PR-B merges)

**Files touched:**

- `composeApp/composeResources/drawable/` (replace/regenerate hero illustrations + empty-state illustrations with indigo-tinted versions)
- Source webp files in `~/Desktop/Project/stitchpad_assets_webp/` (per [[reference-webp-assets]]) — regenerated upstream
- Per-feature empty-state composables (verify they consume the new illustrations)

**Done criteria:**

- Empty states render with new indigo-tinted illustrations
- No saffron in illustration fills (only in heritage badges)
- LoadingDots still appears during async image load (per [[feedback-image-loading-dots]])
- PR description includes before/after screenshots of every refreshed surface

### Branch strategy

Strict sequencing — no merge train, no parallelization. PR-A merges first. PR-B branches off updated main. PR-C off updated main. Each PR is small enough to review independently.

## Verification approach

### Compose `@Preview` coverage

Per CLAUDE.md, every Screen composable already has a `@Preview`. The rebrand needs each preview to:

1. **Render both light + dark explicitly.** Pair every `@Preview` with a `@Preview(darkMode = true)` variant. Don't trust system-default.
2. **Use realistic data**, not placeholders. Brand sustainment is content-dependent.
3. **For PR-A:** verify previews still render without crashing. Visual mismatches expected.
4. **For PR-B:** spot-check 5-10 representative previews against `preview/rebrand-variant-a-deep-dive.html` as a reference.

**New addition:** a `ui/theme/StitchPadThemePreview.kt` file with a single preview function rendering a color-swatch grid + type specimen using only theme tokens. This is the "is the theme working" smoke test runnable in Android Studio in <1 second without launching the emulator. Lands as part of PR-A.

### CI gates (no new ones needed)

Existing CI already covers this:

| Check | Catches |
|---|---|
| `detekt` | Style + complexity regressions |
| Unit tests | ViewModel / repository regressions (theme doesn't touch business logic — should stay green throughout) |
| `build-android` | Compose compile + Android packaging |
| `build-ios` | KMP iOS compile — critical for font-family / variable-axis issues |
| `secrets-scan` | Standard hygiene |

No screenshot-test CI added during this rebrand (see Out of scope).

### Definition of done

| PR | "Done" means |
|---|---|
| **PR-A** | Tokens-only foundation. App builds + boots both platforms, both modes. 6 smoke steps pass. Visual mismatches catalogued for PR-B. Unit tests green. CI green. |
| **PR-B** | Screen migration complete. Grep verifies zero `DesignTokens.primary*` and zero `PlusJakartaSans` outside `ui/theme/`. 10 screens spot-checked. iOS device build clean. |
| **PR-C** | Illustrations refreshed. Empty states match deep-dive mockup. LoadingDots verified. Before/after screenshots in PR description. |

### Smoke test scripts per PR

Each PR description must include the screen-by-screen smoke steps from the relevant "Done criteria" section, formatted as a numbered checklist that Daniel (QA, per memory) can run through.

## Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| New tokens render poorly on real device after PR-A | Medium | PR-A is tokens-only by design — discover before committing to screen migration. If hex values feel off in Compose, adjust before merging. Cheap to undo. |
| Fraunces variable axis behavior differs on iOS Native | Medium | V0 uses Fraunces at default axes (no `SOFT` / `opsz` tuning). If static-weight loading fails, fall back to multi-file static weights per [[feedback-kmp-jvm-only-apis]] discipline. Test PR-A on iPhone 17 / 17 Pro sims per [[reference-test-environment]] before merging. |
| `DesignTokens.primary500` references litter feature code | High (almost certain) | PR-B's grep-based find-and-replace catches them all. CI build fails on any miss after the rename. |
| Status colors collide with new brand | Verified low | Already validated — indigo lives on the opposite side of the color wheel from all red-family status pills. Burgundy/sienna primary candidates were eliminated specifically for this reason. |
| Cross-feature PR conflicts during migration | Medium | Strict sequencing (A → B → C, no parallelization). Each PR small enough to review and merge in one sitting. |
| Light-first marketing differs from user expectation (Nigerian tailors used to dark UI) | Low | App's dark mode is preserved and toggleable via Settings → Appearance. Light is just the *default* / first-impression mode. Tester feedback during PM-recruited cohort will validate. |
| Saffron-as-rare-accent rule violated during PR-B | Medium | Done-criteria check: explicitly verify zero saffron CTAs / backgrounds / primaries before merging PR-B. Searchable rule. |
| Brand reads as "generic Western luxury" without cultural anchor | Low | The deep-dive mockup validated brand sustainment on 5 surfaces. Surface 2 (empty state) is the most fragile — depends on illustration quality (PR-C). If PR-C illustrations fall short, the cultural anchor weakens. PR-C done-criteria explicitly references the mockup for visual reference. |

## Decision log

### Why indigo over copper, burgundy, terracotta, jade, or kept-saffron

- **Copper** (round-1 my recommendation, `#B87333`): warm and far from yellow, but still in the orange family — felt like "a polite step away from saffron" rather than a real rebrand. Lacked cultural anchor.
- **Burgundy** (PM intern's suggestion, `#722F37`): beautiful color, but lives in the same red family as `statusOverdue` `#D93B3B`. Light-mode collision is subtle; **dark-mode collision is worse** (burgundy tonally lifts to a rose `~#C97987` that reads as a sibling of bright red Overdue, not a contrast). Validated in `preview/rebrand-wine-comparison.html` light-and-dark side-by-side. The hybrid (indigo primary + burgundy secondary) inherits a smaller version of the same problem — burgundy as section-pill color sits next to Overdue on the same dashboard card.
- **Terracotta / sienna as primary** (Variant B in `rebrand-palette-comparison.html`): collides with `statusCutting` orange `#E07B20`. Same hue family — two oranges shouting.
- **Jade / deep green** (Manus suggestion): reads as finance/health-app, not fashion. Lacks the Nigerian-craft anchor.
- **Kept saffron** (ChatGPT's "Thread Saffron" `#D99400` reduced-dominance proposal): violates the explicit project memory ("move off yellow-gold-on-dark entirely. Don't just shift a hue"). Nigerian tailors on phone-sized screens won't see the nuance between `#D99400` and StyleOS's `#FFD700` — both read "yellow brand."
- **Indigo `#2C3E7C`** (chosen): references Yoruba adire indigo-dye textile tradition — a cultural anchor no competitor owns. Opposite side of the color wheel from all red-family status pills (no collisions). Authentic adire dye sits in the `#1A1A4A` to `#4A6FA5` range; `#2C3E7C` is on the darker end of authentic, signaling "heavily-dyed, well-crafted" rather than "freshly tinted."

### Why Fraunces over Instrument Serif, DM Serif Display, or Lora

Validated head-to-head in `preview/rebrand-type-variants.html`:

- **Instrument Serif:** single weight — creative jail in product UI. Beautiful for marketing, fragile for daily-use screens.
- **DM Serif Display:** too loud for a freemium tailor tool. "Feels like a perfume bottle, not a tool."
- **Lora:** safe but forgettable. Reads like "any thoughtful B2B SaaS."
- **Fraunces:** the only one with a personality dial (variable `SOFT` axis 0-100). Same brand can read "serious" on a packing slip and "warm" on a marketing hero without swapping families. Variable-axis tuning is V0-out-of-scope but the runway is there.

Acknowledged: Fraunces is becoming common in product design in 2024-2025. Newsreader was considered as a less-trendy alternative but the marginal uniqueness gain (~10-15%) wasn't worth another iteration round. Typography is one of six brand levers — the load-bearing ones are color (indigo + adire reference), treatment (warm paper, light-first), and copy (workshop positioning). Revisit at month 6 with real user signal.

### Why light-first marketing default

- Nigerian tailors use affordable Android devices in bright sunlight — light mode is more legible
- StyleOS leads dark — light-first differentiates immediately on first scroll
- ChatGPT's design research and Manus reports both converged on this independently
- App's dark mode is preserved; users can switch via Settings → Appearance

### Why saffron stays as a rare heritage accent

- Brand continuity — abandoning saffron entirely loses one connection to the current build
- Saffron has no Material slot, so it's the natural fit for `StitchPadColors.heritageAccent`
- Validates the "rare accent" rule — if saffron shows up everywhere, the rule isn't real
- Production deep-dive mockup's Surface 5 (PRO upsell) demonstrated saffron works on PRO badges, ★ marks, Verified Tailor chips, achievement moments — places where "this is special" reads correctly

### Why a new `brandAccent` slot (lighter indigo `#5871B8`)

- The locked indigo `#2C3E7C` is intentionally dark — authentic adire — and this carries cost on illustrations / hero animations where vibrancy matters
- Rather than shifting the brand color brighter (which would tilt toward StyleOS-adjacent SaaS palettes), add a release-valve slot for vibrant-context usage
- Standard design-system pattern (Material's tonal palettes work this way)
- Light: `#5871B8`. Dark: `#9CB0DD`. Both pass AA on their respective backgrounds.

## Appendix

### Reference mockup files (in `preview/`)

All under `/Users/danzucker/Desktop/Project/StitchPad/preview/`:

- `rebrand-palette-comparison.html` — original 3-variant comparison (Adire, Sienna-led, Saffron Reduced) + light/dark
- `rebrand-variant-a-deep-dive.html` — 5 real app surfaces in Adire Atelier (auth, empty, populated dashboard, measurement entry, PRO upsell), light + dark
- `rebrand-variant-a-polished.html` — single surface with full polish (avatar fix, paper grain, gradient saffron badges, fade-in stagger), light + dark
- `rebrand-tone-variants.html` — 4 indigo / paper tone variations (Baseline, Royal violet, Bright periwinkle, Cool linen), light only
- `rebrand-type-variants.html` — 4 display fonts on same Adire surface (Fraunces, Instrument Serif, DM Serif Display, Lora)
- `rebrand-wine-comparison.html` — 3-direction comparison for PM intern review (Adire / Wine primary / Adire+Wine hybrid), light + dark

These remain in `preview/` as the visual reference during implementation. They are NOT shipped with the app.

### Source research artifacts (on desktop)

- `/Users/danzucker/Desktop/StyleOS Design Research.md` — competitor visual identity audit
- `/Users/danzucker/Desktop/Nigerian Fashion Tech Design Trends & UI Patterns.md` — market design pattern analysis
- `/Users/danzucker/Desktop/Stitchpad Design Recommendations Report.md` — full Manus design strategy report

Don't re-research the same ground in future work — these capture the cross-LLM design exploration that informed this spec.
