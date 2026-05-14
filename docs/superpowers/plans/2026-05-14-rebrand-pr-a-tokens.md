# Rebrand PR-A — Tokens-Only Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace StitchPad's saffron design tokens with the "Adire Atelier" indigo + warm-paper + sienna system, swap the body/display fonts (Plus Jakarta Sans → Fraunces + Manrope), and add the `StitchPadColors` brand-extended semantic layer — without touching any screen-level code. App must build and run on Android + iOS in both light and dark mode after this PR; visual mismatches in feature code are expected and get fixed in PR-B.

**Architecture:** Three-layer token system. Primitives in `DesignTokens.kt` (raw hex values), Material3 `ColorScheme` builders in `Color.kt` (Material's semantic slots), and a parallel `StitchPadColors` data class via `CompositionLocal` for brand-specific slots (heritage saffron, lighter indigo brand-accent) that Material3 doesn't model. Light/dark dual values are resolved at the *scheme* layer, not the primitive layer — composables never branch on theme. The 118 existing `DesignTokens.primary*` callsites are kept compiling via `@Deprecated` aliases that point to the new indigo values; PR-B migrates them away.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.7+, Material3, Compose Resources (auto-generated `Res.font.*` accessors from `composeResources/font/`), Gradle. Fonts sourced from Google Fonts (OFL license).

**Branch:** `feature/rebrand-tokens` (already created, spec + mockups already committed at `d6d3e90`).

**Spec reference:** `docs/superpowers/specs/2026-05-14-rebrand-design.md` — read the "File-level changes" and "Migration plan → PR-A" sections before starting.

---

## File Structure

After this PR, the `ui/theme/` directory has:

| File | Status | Purpose |
|---|---|---|
| `DesignTokens.kt` | modified | Layer 1 primitives. Adds indigo/sienna/saffron ramps + warm paper/ink. Keeps `primary*` as `@Deprecated` aliases to indigo. Keeps neutrals, status, avatars, semantic, spacing, radii, etc. verbatim. |
| `Color.kt` | modified | Layer 2a. Two scheme functions (`stitchPadLightColorScheme()`, `stitchPadDarkColorScheme()`) instead of two vals. Maps primitives to Material's named slots. |
| `StitchPadColors.kt` | **NEW** | Layer 2b. `@Immutable data class` + `LightStitchPadColors` + `DarkStitchPadColors` + `LocalStitchPadColors` CompositionLocal. Two slots only: `heritageAccent`, `brandAccent`. |
| `Theme.kt` | modified | Layer 3 orchestrator. Calls the new scheme functions and provides `LocalStitchPadColors` alongside MaterialTheme. |
| `Type.kt` | modified | Adds `FrauncesFamily()` (display) + `ManropeFamily()` (body). Keeps `JetBrainsMonoFamily()` unchanged. Removes `PlusJakartaSansFamily()`. Updates `StitchPadTypography()` style assignments. |
| `StitchPadThemePreview.kt` | **NEW** | Theme verification preview — color swatch grid + type specimen. Runnable in Android Studio in <1s without launching the emulator. |

Font assets in `composeApp/src/commonMain/composeResources/font/`:
| Action | Files |
|---|---|
| **Add** | `fraunces_variable.ttf` (one file, variable axes), `manrope_regular.ttf`, `manrope_medium.ttf`, `manrope_semibold.ttf`, `manrope_bold.ttf` |
| **Remove** (Task 8) | `plus_jakarta_sans_regular.ttf`, `plus_jakarta_sans_medium.ttf`, `plus_jakarta_sans_semibold.ttf`, `plus_jakarta_sans_bold.ttf` |
| **Keep** | `jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf` |

A PR-B preparation checklist file is created at the end (Task 13): `docs/rebrand-pr-b-checklist.md`.

---

## Task 1: Download + place Fraunces and Manrope font assets

**Files:**
- Create: `composeApp/src/commonMain/composeResources/font/fraunces_variable.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/manrope_regular.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/manrope_medium.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/manrope_semibold.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/manrope_bold.ttf`

- [ ] **Step 1: Download Fraunces variable font**

Go to https://fonts.google.com/specimen/Fraunces, click "Get font" → "Download all", and from the downloaded ZIP, extract `Fraunces[SOFT,WONK,opsz,wght].ttf`. Rename it to `fraunces_variable.ttf`.

Alternative if browser download isn't convenient — fetch directly from the Google Fonts GitHub mirror:

```bash
curl -fL -o composeApp/src/commonMain/composeResources/font/fraunces_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/fraunces/Fraunces%5BSOFT%2CWONK%2Copsz%2Cwght%5D.ttf"
```

If the curl URL fails, fall back to manual download from the Google Fonts site.

- [ ] **Step 2: Download Manrope static weight files**

From https://fonts.google.com/specimen/Manrope, download the ZIP. Inside `static/`, take these four files and rename them lowercased with underscores:
- `Manrope-Regular.ttf` → `manrope_regular.ttf`
- `Manrope-Medium.ttf` → `manrope_medium.ttf`
- `Manrope-SemiBold.ttf` → `manrope_semibold.ttf`
- `Manrope-Bold.ttf` → `manrope_bold.ttf`

Or via curl:

```bash
curl -fL -o composeApp/src/commonMain/composeResources/font/manrope_regular.ttf \
  "https://github.com/google/fonts/raw/main/ofl/manrope/static/Manrope-Regular.ttf"
curl -fL -o composeApp/src/commonMain/composeResources/font/manrope_medium.ttf \
  "https://github.com/google/fonts/raw/main/ofl/manrope/static/Manrope-Medium.ttf"
curl -fL -o composeApp/src/commonMain/composeResources/font/manrope_semibold.ttf \
  "https://github.com/google/fonts/raw/main/ofl/manrope/static/Manrope-SemiBold.ttf"
curl -fL -o composeApp/src/commonMain/composeResources/font/manrope_bold.ttf \
  "https://github.com/google/fonts/raw/main/ofl/manrope/static/Manrope-Bold.ttf"
```

- [ ] **Step 3: Verify all 5 font files exist with reasonable size**

```bash
ls -la composeApp/src/commonMain/composeResources/font/fraunces_variable.ttf \
       composeApp/src/commonMain/composeResources/font/manrope_*.ttf
```

Expected: 5 files listed, each between 50KB and 1.5MB. If a file is <10KB it's likely a 404 HTML page; redownload.

- [ ] **Step 4: Trigger Compose Resource accessor generation**

```bash
./gradlew :composeApp:generateResourceAccessorsForCommonMain
```

Expected: BUILD SUCCESSFUL. This generates `stitchpad.composeapp.generated.resources.Res.font.fraunces_variable` and the Manrope accessors. The old `plus_jakarta_sans_*` accessors are also still generated since those files still exist.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/font/fraunces_variable.ttf \
        composeApp/src/commonMain/composeResources/font/manrope_*.ttf
git commit -m "$(cat <<'EOF'
feat(rebrand): add Fraunces + Manrope font assets

Fraunces (variable) becomes the display family; Manrope (4 static weights)
becomes the body family. Plus Jakarta Sans removal lands in Task 8 after
Type.kt has been rewritten to stop referencing it.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Add new color primitives in DesignTokens.kt

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/DesignTokens.kt:9-22` (the `// Primary Colors (Deep Saffron)` block)

- [ ] **Step 1: Replace the "Primary Colors" block with the new ramps + deprecated aliases**

Open `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/DesignTokens.kt`. Replace lines 9-22 (everything from `// Primary Colors (Deep Saffron)` through `val primary900 = Color(0xFF4F3800)`) with:

```kotlin
    // Indigo ramp — adire textile reference. The brand primary on light
    // surfaces is indigo500; dark mode lifts to indigo300.
    val indigo50  = Color(0xFFEAEEF8)
    val indigo100 = Color(0xFFD1D9EE)
    val indigo200 = Color(0xFF9CB0DD) // brandAccent on dark
    val indigo300 = Color(0xFF7388BF) // brand on dark
    val indigo400 = Color(0xFF5871B8) // brandAccent on light
    val indigo500 = Color(0xFF2C3E7C) // brand on light
    val indigo700 = Color(0xFF1E2B5C) // CTA fill on light
    val indigo900 = Color(0xFF121B3B)

    // Sienna ramp — workshop bench warmth. Three stops only.
    val sienna300 = Color(0xFFD9885F)
    val sienna500 = Color(0xFFB85A30)
    val sienna700 = Color(0xFF8E4524)

    // Saffron — heritage accent only (single value, no full ramp).
    val saffron500 = Color(0xFFE8A800)

    // Warm surfaces — replaces nothing structurally; new semantic names.
    val paperLight = Color(0xFFFAF6EC) // bg on light — "warm paper"
    val inkDark    = Color(0xFF14110E) // bg on dark — "warm ink" (was darkBg)

    // Deprecated saffron-era aliases. The 118 existing call sites in
    // feature/** and ui/components/** still reference these names. PR-A
    // keeps them compiling AND points them at the new indigo values so
    // the app immediately renders the new palette. PR-B migrates the call
    // sites to MaterialTheme.colorScheme.* and then deletes these aliases.
    @Deprecated("Use MaterialTheme.colorScheme.primaryContainer", ReplaceWith("MaterialTheme.colorScheme.primaryContainer"))
    val primary50  = indigo50
    @Deprecated("Use MaterialTheme.colorScheme.primaryContainer", ReplaceWith("MaterialTheme.colorScheme.primaryContainer"))
    val primary100 = indigo100
    @Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primary200 = indigo200
    @Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primary300 = indigo300
    @Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primary400 = indigo400
    @Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primary500 = indigo500
    @Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primary600 = indigo700 // closest mapping — old 600 was pressed state
    @Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primary700 = indigo700
    @Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primary800 = indigo900 // closest mapping
    @Deprecated("Use MaterialTheme.colorScheme.primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primary900 = indigo900

    // The old primaryButtonBorder constant — also aliased.
    @Deprecated("Use MaterialTheme.colorScheme.primary or border tokens", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val primaryButtonBorder = indigo700
```

Note: line 78 of the existing file has `val primaryButtonBorder = Color(0xFFC48E00)` — REMOVE that line as part of this edit (it's superseded by the `primaryButtonBorder` alias above).

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL. The 118 feature/** call sites still compile because the `@Deprecated` aliases still exist. Compiler emits deprecation warnings for each call site — that's expected and PR-B will silence them.

If the build fails with an "unresolved reference" error for `primaryXXX`, you missed an alias — add the missing alias and retry.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/DesignTokens.kt
git commit -m "$(cat <<'EOF'
feat(rebrand): add indigo + sienna + saffron primitives in DesignTokens

Adds the new color ramps that anchor the Adire Atelier palette:
indigo (8 stops including new 200 and 400), sienna (3 stops), saffron
(single heritage value), plus paperLight / inkDark surface tokens.

The 9 saffron-era primaryNN tokens are kept as @Deprecated aliases that
now point to the indigo ramp. This keeps the 118 existing call sites in
feature/** and ui/components/** compiling AND immediately renders the
new palette — PR-B migrates each site to MaterialTheme.colorScheme.*.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Rewrite Color.kt as scheme functions

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Color.kt` (entire file)

- [ ] **Step 1: Replace the entire file with the new scheme functions**

Open `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Color.kt` and replace the entire contents with:

```kotlin
package com.danzucker.stitchpad.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Light Adire Atelier color scheme. Indigo primary, sienna tertiary,
 * warm paper background. Function-style (vs the old val-style) so future
 * platform-specific tweaks (Android 12+ dynamic color) don't require
 * restructuring the Theme.kt orchestrator.
 */
fun stitchPadLightColorScheme(): ColorScheme = lightColorScheme(
    primary = DesignTokens.indigo500,
    onPrimary = Color.White,
    primaryContainer = DesignTokens.indigo50,
    onPrimaryContainer = DesignTokens.indigo700,
    secondary = DesignTokens.indigo700,
    onSecondary = Color.White,
    secondaryContainer = DesignTokens.indigo100,
    onSecondaryContainer = DesignTokens.indigo900,
    tertiary = DesignTokens.sienna500,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCEFE5), // sienna50 inline — not worth a token
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

/**
 * Dark Adire Atelier color scheme. Indigo tonally lifts (indigo500 → indigo300)
 * so the brand color reads clearly on warm-ink backgrounds. Sienna also lifts
 * (sienna500 → sienna300). Background is warm-ink, not pure black.
 */
fun stitchPadDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = DesignTokens.indigo300,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D3B6B), // avatar bg — brighter than surface
    onPrimaryContainer = DesignTokens.indigo100,
    secondary = DesignTokens.indigo400,
    onSecondary = Color.White,
    secondaryContainer = DesignTokens.indigo900,
    onSecondaryContainer = DesignTokens.indigo200,
    tertiary = DesignTokens.sienna300,
    onTertiary = DesignTokens.neutral900,
    tertiaryContainer = Color(0xFF3D2510), // warm dark sienna container
    onTertiaryContainer = DesignTokens.sienna300,
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

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinAndroid
```

Expected: BUILD FAILS at `Theme.kt:22` with "Unresolved reference: LightColorScheme" / "DarkColorScheme". That's intentional — Task 4 updates Theme.kt to use the new functions. Keep going.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Color.kt
git commit -m "$(cat <<'EOF'
feat(rebrand): rewrite Color.kt as scheme functions

Light + Dark ColorScheme builders converted from val-style to
function-style. Both schemes now map the new indigo/sienna primitives
to Material3's named slots. Sienna → tertiary, indigo → primary.

Build is intentionally broken at this commit — Theme.kt still references
the old LightColorScheme/DarkColorScheme vals. Task 4 fixes that.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create StitchPadColors.kt

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/StitchPadColors.kt`

- [ ] **Step 1: Create the new file**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/StitchPadColors.kt`:

```kotlin
package com.danzucker.stitchpad.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand-specific semantic color slots that Material3's [androidx.compose.material3.ColorScheme]
 * doesn't model.
 *
 * - [heritageAccent] — saffron, used ONLY on rare moments (PRO badges,
 *   star/check marks, Verified Tailor chips, achievement bursts). Same hex
 *   in both modes — saffron doesn't tonally shift.
 * - [brandAccent] — a brighter step of the indigo ramp, for hero
 *   illustration strokes, marketing-hero accents, decorative motifs on
 *   empty states. Distinct from `MaterialTheme.colorScheme.primary` which
 *   is reserved for the deeper brand color used on wordmarks, links, and
 *   CTAs.
 *
 * Two slots only — anything else (elevated surfaces, muted text) is
 * already covered by Material3's surface / onSurfaceVariant slots.
 */
@Immutable
data class StitchPadColors(
    val heritageAccent: Color,
    val brandAccent: Color,
)

val LightStitchPadColors = StitchPadColors(
    heritageAccent = DesignTokens.saffron500,
    brandAccent    = DesignTokens.indigo400,
)

val DarkStitchPadColors = StitchPadColors(
    heritageAccent = DesignTokens.saffron500, // saffron doesn't tonally shift
    brandAccent    = DesignTokens.indigo200,
)

val LocalStitchPadColors = staticCompositionLocalOf<StitchPadColors> {
    error("StitchPadColors not provided — wrap content in StitchPadTheme")
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinAndroid
```

Expected: BUILD still FAILS at `Theme.kt:22` (Task 3's break is still present). The new `StitchPadColors.kt` itself compiles cleanly. Task 5 fixes Theme.kt and the build returns to green.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/StitchPadColors.kt
git commit -m "$(cat <<'EOF'
feat(rebrand): add StitchPadColors brand-extended semantic layer

Two slots Material3 doesn't model: heritageAccent (saffron, rare-use
PRO badges + verified marks) and brandAccent (lighter indigo step for
hero illustrations and decorative motifs). Provided via CompositionLocal
alongside MaterialTheme — Task 5 wires the provider.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Update Theme.kt to wire new schemes + LocalStitchPadColors

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Theme.kt` (entire file)

- [ ] **Step 1: Replace the entire file**

Open `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Theme.kt` and replace the entire contents with:

```kotlin
package com.danzucker.stitchpad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * True when StitchPadTheme is rendering with the dark color scheme. Use this
 * inside child composables instead of `isSystemInDarkTheme()` so the value
 * also reflects the user's Settings → Appearance choice (System / Light /
 * Dark), not just the OS-level setting.
 */
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

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL (with deprecation warnings on the 118 `DesignTokens.primary*` call sites). The build is back to green.

If the build fails, the most likely cause is an import missing — double-check the imports in the snippet above against what was deleted from the old `Theme.kt`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Theme.kt
git commit -m "$(cat <<'EOF'
feat(rebrand): wire new schemes + LocalStitchPadColors in Theme.kt

StitchPadTheme now calls stitchPadLightColorScheme() /
stitchPadDarkColorScheme() and provides LocalStitchPadColors alongside
MaterialTheme. Build returns to green after the intentional Task 3 break.

The 118 deprecated DesignTokens.primary* call sites in feature/** and
ui/components/** are compiling cleanly with deprecation warnings —
PR-B migrates them.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Rewrite Type.kt with Fraunces + Manrope

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Type.kt` (entire file)

- [ ] **Step 1: Replace the entire file**

Open `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Type.kt` and replace the entire contents with:

```kotlin
package com.danzucker.stitchpad.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.fraunces_variable
import stitchpad.composeapp.generated.resources.jetbrains_mono_medium
import stitchpad.composeapp.generated.resources.jetbrains_mono_regular
import stitchpad.composeapp.generated.resources.manrope_bold
import stitchpad.composeapp.generated.resources.manrope_medium
import stitchpad.composeapp.generated.resources.manrope_regular
import stitchpad.composeapp.generated.resources.manrope_semibold

/**
 * Display family — Fraunces, loaded as a single variable font referenced
 * four times at different weights. Compose's Font(resource, weight) maps
 * each entry to the closest weight in the variable file's wght axis.
 *
 * Variable-axis tuning (SOFT, opsz) is V0-out-of-scope per the rebrand
 * spec — uneven platform support across CMP. Revisit as a follow-up.
 */
@Composable
fun FrauncesFamily(): FontFamily = FontFamily(
    Font(Res.font.fraunces_variable, FontWeight.Normal),
    Font(Res.font.fraunces_variable, FontWeight.Medium),
    Font(Res.font.fraunces_variable, FontWeight.SemiBold),
    Font(Res.font.fraunces_variable, FontWeight.Bold),
)

/**
 * Body family — Manrope, four static weight files.
 */
@Composable
fun ManropeFamily(): FontFamily = FontFamily(
    Font(Res.font.manrope_regular,  FontWeight.Normal),
    Font(Res.font.manrope_medium,   FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold,     FontWeight.Bold),
)

/**
 * Mono family — JetBrains Mono, kept verbatim from the saffron era.
 * Used for measurements + due dates at the component level (not in the
 * Material Typography map).
 */
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
        // Display + Headline — Fraunces (was PlusJakartaSans).
        displayLarge = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.displayLg,
            lineHeight = DesignTokens.displayLg * 1.25f,
        ),
        displayMedium = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.displayMd,
            lineHeight = DesignTokens.displayMd * 1.29f,
        ),
        headlineLarge = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingLg,
            lineHeight = DesignTokens.headingLg * 1.33f,
        ),
        headlineMedium = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingMd,
            lineHeight = DesignTokens.headingMd * 1.4f,
        ),
        headlineSmall = TextStyle(
            fontFamily = fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = DesignTokens.headingSm,
            lineHeight = DesignTokens.headingSm * 1.44f,
        ),
        // Body + Label — Manrope (was PlusJakartaSans).
        bodyLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodyLg,
            lineHeight = DesignTokens.bodyLg * 1.5f,
        ),
        bodyMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodyMd,
            lineHeight = DesignTokens.bodyMd * 1.57f,
        ),
        bodySmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Normal,
            fontSize = DesignTokens.bodySm,
            lineHeight = DesignTokens.bodySm * 1.54f,
        ),
        labelLarge = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelLg,
            lineHeight = DesignTokens.labelLg * 1.43f,
        ),
        labelMedium = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelMd,
            lineHeight = DesignTokens.labelMd * 1.38f,
        ),
        labelSmall = TextStyle(
            fontFamily = manrope,
            fontWeight = FontWeight.Medium,
            fontSize = DesignTokens.labelSm,
            lineHeight = DesignTokens.labelSm * 1.45f,
        ),
    )
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL. The new `Res.font.fraunces_variable`, `Res.font.manrope_*` imports resolve because the font files exist (Task 1). The old `Res.font.plus_jakarta_sans_*` imports are gone — but the .ttf files are still on disk, so Compose Resources still generates accessors for them; the accessors just aren't used anymore. They're cleaned up in Task 8.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Type.kt
git commit -m "$(cat <<'EOF'
feat(rebrand): swap Plus Jakarta Sans → Fraunces + Manrope in Type.kt

Display + Headline styles use Fraunces (variable). Body + Label use
Manrope (4 static weights). JetBrains Mono kept unchanged for
measurements + due dates at component level.

Variable-axis tuning (Fraunces SOFT / opsz) is out of scope for V0 per
the spec — uneven CMP platform support. Revisit as a follow-up.

PlusJakartaSans .ttf assets removed in Task 8 once Type.kt no longer
references them.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Create StitchPadThemePreview.kt

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/StitchPadThemePreview.kt`

- [ ] **Step 1: Create the preview file**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/StitchPadThemePreview.kt`:

```kotlin
package com.danzucker.stitchpad.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Theme verification preview — renders a color swatch grid + type
 * specimen using only theme tokens. Run from Android Studio's Compose
 * preview pane in <1s without launching the emulator.
 *
 * Two variants below: light and dark. If either renders broken (wrong
 * colors, missing font, etc.), the theme wiring is wrong.
 */
@Preview
@Composable
private fun StitchPadThemePreview_Light() {
    StitchPadTheme(darkTheme = false) {
        ThemeSpecimenContent()
    }
}

@Preview
@Composable
private fun StitchPadThemePreview_Dark() {
    StitchPadTheme(darkTheme = true) {
        ThemeSpecimenContent()
    }
}

@Composable
private fun ThemeSpecimenContent() {
    val brand = LocalStitchPadColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "StitchPad",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "ADIRE ATELIER · THEME SPECIMEN",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "The smart work pad for tailors.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Body text in Manrope renders here. Measurements use mono.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Bust 38 · Waist 30 · Hips 40",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFamily(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            // Color swatch grid — one row, the key brand slots.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("primary", MaterialTheme.colorScheme.primary)
                Swatch("secondary", MaterialTheme.colorScheme.secondary)
                Swatch("tertiary", MaterialTheme.colorScheme.tertiary)
                Swatch("heritage", brand.heritageAccent)
                Swatch("brandAccent", brand.brandAccent)
            }

            // Surfaces row.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("background", MaterialTheme.colorScheme.background)
                Swatch("surface", MaterialTheme.colorScheme.surface)
                Swatch("surfaceVariant", MaterialTheme.colorScheme.surfaceVariant)
                Swatch("outline", MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun Swatch(label: String, color: Color) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color, RoundedCornerShape(10.dp))
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :composeApp:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL. The preview compiles. In Android Studio, opening this file should render the two `@Preview` previews in the side panel within ~1-2 seconds.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/StitchPadThemePreview.kt
git commit -m "$(cat <<'EOF'
feat(rebrand): add StitchPadThemePreview for fast theme verification

@Preview file rendering color swatches + a type specimen using only
theme tokens. Lets us validate theme wiring (palette + typography +
StitchPadColors CompositionLocal) in Android Studio's preview pane in
<1s, without launching the emulator.

Two previews: light and dark.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Remove old Plus Jakarta Sans font assets

**Files:**
- Delete: `composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_regular.ttf`
- Delete: `composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_medium.ttf`
- Delete: `composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_semibold.ttf`
- Delete: `composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_bold.ttf`

- [ ] **Step 1: Sanity-check no remaining PJS references**

```bash
grep -rn "plus_jakarta_sans\|PlusJakartaSansFamily" composeApp/src/
```

Expected: zero output. If anything matches, do NOT delete the .ttf files yet — go back and fix the reference (this should not happen since Task 6 rewrote the only consumer).

- [ ] **Step 2: Delete the four .ttf files**

```bash
rm composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_regular.ttf \
   composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_medium.ttf \
   composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_semibold.ttf \
   composeApp/src/commonMain/composeResources/font/plus_jakarta_sans_bold.ttf
```

- [ ] **Step 3: Verify build**

```bash
./gradlew :composeApp:compileKotlinAndroid
```

Expected: BUILD SUCCESSFUL. The generated `Res.font.plus_jakarta_sans_*` accessors disappear from the auto-generated Resource class on the next build — and nothing references them anymore.

- [ ] **Step 4: Commit**

```bash
git add -A composeApp/src/commonMain/composeResources/font/
git commit -m "$(cat <<'EOF'
chore(rebrand): remove Plus Jakarta Sans font assets

The four PJS .ttf files are no longer referenced after Type.kt's swap
to Fraunces + Manrope. Compose Resources auto-generated accessors
disappear on the next build.

Net font-asset change for this PR: -4 (PJS) +5 (Fraunces + 4 Manrope)
+0 (JetBrains Mono kept).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Full Android build verification

**Files:** none modified — verification only.

- [ ] **Step 1: Run a full debug build**

```bash
./gradlew :composeApp:assembleDebug
```

Expected: BUILD SUCCESSFUL. Build time depends on machine but should be in the 1-3 minute range for an incremental build, 5-10 minutes cold.

If the build fails with anything other than deprecation warnings, halt and diagnose. Most likely causes:
- A typo in a hex value (`Color(0xFF...)` requires 8 hex chars after `0xFF`)
- A missing import in `Type.kt` for one of the Manrope variants
- A `Res.font.<name>` mismatch — Compose generates accessors using the file basename, lowercased, underscores; verify the .ttf filenames match the imports

- [ ] **Step 2: Run detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. Deprecation warnings on the 118 `DesignTokens.primary*` call sites are expected and NOT detekt errors — detekt's warning rules don't fail the build on them. If detekt fails on any new code (`DesignTokens.kt`, `Color.kt`, `StitchPadColors.kt`, `Theme.kt`, `Type.kt`, `StitchPadThemePreview.kt`), fix the violation before continuing.

- [ ] **Step 3: Run unit tests**

```bash
./gradlew :composeApp:testDebugUnitTest
```

Expected: all existing tests pass. Theme changes don't touch business logic — any failure indicates a regression that needs investigation before proceeding.

- [ ] **Step 4: Commit the Android-verification milestone (no file changes, just a marker)**

This step is intentionally empty if no files changed in Steps 1-3. If any file did need a fix, commit it now with a clear message describing what was fixed.

---

## Task 10: iOS compile verification

**Files:** none modified — verification only.

- [ ] **Step 1: Compile iOS simulator target**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL. This catches iOS-Native-specific issues that the Android build might miss — JVM-only API usage (per memory feedback_kmp_jvm_only_apis), epoch-day Int/Long platform skew (feedback_kotlin_native_epoch_days), etc.

- [ ] **Step 2: Build the iOS framework**

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL. Produces the `.framework` Xcode will consume.

If either step fails:
- **Variable font issues on iOS** (Fraunces single-file): fall back to static weight files per the spec's Risk Register. Download Fraunces static weights from Google Fonts (Static-Regular, Static-Medium, Static-SemiBold, Static-Bold), place at `composeResources/font/fraunces_regular.ttf` etc., update Type.kt's `FrauncesFamily()` to reference them individually instead of the variable file four times. Then rerun this task.
- **Any other iOS-only failure**: halt, diagnose, fix at the source. Do NOT skip iOS verification.

- [ ] **Step 3: Commit any iOS-specific fixes**

If Step 1 or 2 required a fallback to static Fraunces weights, commit that change now:

```bash
git add composeApp/src/commonMain/composeResources/font/fraunces_*.ttf \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/theme/Type.kt
git commit -m "fix(rebrand): fall back to static Fraunces weights for iOS

Variable-font handling on iOS Native was unstable per spec risk register.
Static weights (regular/medium/semibold/bold) work consistently across
both platforms.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

Otherwise skip this step.

---

## Task 11: Android smoke test (manual on emulator)

**Files:** none modified — smoke test only.

- [ ] **Step 1: Boot an Android emulator**

In Android Studio: Device Manager → start an existing Pixel 7 (or Pixel 6) Android 14 emulator. Or via CLI:

```bash
emulator -list-avds
# pick one, then:
emulator -avd <avd-name> &
```

- [ ] **Step 2: Install and launch the debug build**

```bash
./gradlew :composeApp:installDebug
adb shell am start -n com.danzucker.stitchpad/.MainActivity
```

Expected: app launches without crash. Login screen visible.

- [ ] **Step 3: Run the 6 light-mode smoke steps**

Per spec PR-A done criteria, verify these one at a time:

1. App launches without crash → ✅ (verified in Step 2)
2. Login screen renders — wordmark visible, primary CTA visible. (Wordmark may still be in the old saffron-styled `StitchPadLogo` composable since PR-B hasn't migrated screen code yet — that's expected. Verify the primary CTA color is now indigo `#2C3E7C`, not saffron.)
3. Sign up → tap "Create account" → fill form → submit → reach Workshop setup screen → fill (or skip) → reach Home. No crashes during navigation.
4. Dashboard tab opens — Today card renders, status pills visible (Sewing, Ready, etc.), no token-resolution errors in logcat (`adb logcat | grep -i "stitchpad"`).
5. Settings tab opens — looks broadly OK. Cosmetic mismatches expected (e.g., a saffron-colored chip might now be indigo, a hardcoded yellow accent might still be yellow). Catalogue these for PR-B.
6. Settings → Appearance → Dark. Repeat steps 2-5 in dark mode. The wordmark should now read as the lighter indigo `#7388BF`, not the dark indigo from light mode.

- [ ] **Step 4: Record visual mismatches**

For each visual issue noticed during Step 3, write a line in a working file (you'll move these into `docs/rebrand-pr-b-checklist.md` in Task 13). Format:

```
- <screen name> · <element> · <issue> — e.g. "Settings · ProfileHeroCard · still using saffron gradient bg"
```

Aim to catalogue 10-30 items for PR-B. If you find zero, your eyes are too forgiving — re-examine the dashboard and auth screens specifically.

- [ ] **Step 5: This step is verification-only — no commit**

The smoke-test result becomes a checklist file committed in Task 13.

---

## Task 12: iOS smoke test (manual on simulator)

**Files:** none modified — smoke test only.

Per memory `[[reference-test-environment]]`, use iPhone 17 Pro or iPhone 17 simulator (UDIDs in the memory).

- [ ] **Step 1: Open the iOS project in Xcode**

```bash
open iosApp/iosApp.xcodeproj
```

- [ ] **Step 2: Select target + run**

In Xcode: select an iPhone 17 / 17 Pro simulator from the device picker → ⌘R to build and run.

Expected: build succeeds, app launches in the simulator, login screen visible.

If the build fails in Xcode but succeeded in Task 10, the issue is likely SwiftPM (GoogleSignIn dep, etc.) — per memory `[[reference-ios-auth-setup-gaps]]`, those gaps are pre-existing and unrelated to this rebrand. Skip the auth flow during smoke and focus on Dashboard / Settings surfaces.

- [ ] **Step 3: Run the 6 smoke steps (light mode)**

Same 6 steps as Task 11 Step 3, on iOS. Pay specific attention to:
- **Fraunces rendering** — verify the wordmark on the Login screen looks like a serif (Fraunces) and not a fallback sans. If Fraunces is missing, the wordmark falls back to system default — that's the iOS variable-font issue from Task 10's risk and you need to drop back to static weights.
- **Saffron occurrences** — note where saffron still appears (heritage chips OK; anywhere else flag for PR-B).

- [ ] **Step 4: Repeat in dark mode**

iOS Settings → Display & Brightness → Dark. Re-run app, repeat 6 steps.

- [ ] **Step 5: Catalogue iOS-specific mismatches**

iOS visual differences vs Android (font rendering, status bar, navigation chrome) are normal — only catalogue items that are wrong on iOS *specifically*. Add to the running mismatch list for Task 13.

---

## Task 13: Create PR-B preparation checklist

**Files:**
- Create: `docs/rebrand-pr-b-checklist.md`

- [ ] **Step 1: Create the checklist file**

Create `docs/rebrand-pr-b-checklist.md` with this structure:

```markdown
# Rebrand PR-B Preparation Checklist

Cataloged during the PR-A tokens-only smoke test on `feature/rebrand-tokens`. Each entry below is a visual mismatch in feature/** or ui/components/** code that needs fixing in PR-B (the screen migration sweep).

Format: `- <feature> · <file or screen> · <issue>`

## Auth screens

- [ ] _add findings from Task 11 Step 4 and Task 12 Step 5_

## Dashboard

- [ ] _..._

## Customers / Measurements / Orders

- [ ] _..._

## Settings

- [ ] _..._

## Components (ui/components/**)

- [ ] _..._

## iOS-specific

- [ ] _..._

## Notes for PR-B execution

- All 118 `DesignTokens.primary*` call sites still compile (aliased to indigo) but emit deprecation warnings. PR-B migrates each to `MaterialTheme.colorScheme.primary` or another semantic Material slot, then deletes the aliases.
- Saffron literals (`Color(0xFFE8A800)` and `DesignTokens.saffron500`) should be replaced with `LocalStitchPadColors.current.heritageAccent` at usage sites. Verify each saffron usage is genuinely a heritage moment (PRO badge, ★ mark, Verified Tailor chip) — anything else is a mistake to be reframed.
- Font-family references outside `ui/theme/` should be zero. Grep before declaring PR-B done: `grep -rn "FontFamily\|fontFamily =" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/`
```

Replace the `_add findings_` placeholders with the actual mismatches recorded during Tasks 11 and 12. Use the format from Task 11 Step 4. Aim for the checklist to be the actionable to-do list for PR-B.

- [ ] **Step 2: Commit**

```bash
git add docs/rebrand-pr-b-checklist.md
git commit -m "$(cat <<'EOF'
docs(rebrand): PR-B preparation checklist from PR-A smoke test

Lists every visual mismatch noticed while running the 6 smoke steps in
both light and dark mode on Android + iOS. PR-B (the screen migration
sweep) works through this checklist one entry at a time, then deletes
the deprecated DesignTokens.primary* aliases once empty.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Push branch and open the PR

**Files:** none modified.

- [ ] **Step 1: Verify clean working tree**

```bash
git status
```

Expected: "nothing to commit, working tree clean" or only untracked files unrelated to the rebrand. If anything from the rebrand work is uncommitted, commit it now.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feature/rebrand-tokens
```

Expected: branch published to remote, CI pipeline kicks off automatically.

- [ ] **Step 3: Wait for CI to pass**

Watch the CI run. Use a separate terminal:

```bash
gh pr checks --watch
```

Or list runs:

```bash
gh run list --branch feature/rebrand-tokens --limit 5
```

Expected: all checks green (`secrets-scan`, `detekt`, `functions-tests`, `build-android`, `build-ios`, `Unit Tests`). If any fail, halt and fix; do not open the PR with red CI.

- [ ] **Step 4: Open the PR**

```bash
gh pr create --title "feat(rebrand): PR-A — Adire Atelier tokens-only foundation" --body "$(cat <<'EOF'
## Summary
- Rewrite design-token primitives in `DesignTokens.kt`: replace saffron `primary*` ramp with indigo + sienna + saffron-heritage. Keep `primary*` as `@Deprecated` aliases pointing to indigo — keeps the 118 existing call sites compiling and applies the new palette immediately.
- Convert `Color.kt` from val-style schemes to function-style (`stitchPadLightColorScheme()`, `stitchPadDarkColorScheme()`). Wires indigo to Material's `primary`, sienna to `tertiary`, warm paper to `background`.
- Add `StitchPadColors.kt` brand-extended semantic layer (`heritageAccent` + `brandAccent`) provided via `CompositionLocal` alongside MaterialTheme.
- Swap Plus Jakarta Sans → Fraunces (display) + Manrope (body) in `Type.kt`. JetBrains Mono kept.
- Add `StitchPadThemePreview.kt` for fast theme verification in Android Studio's Compose preview pane.
- Catalogue visual mismatches in feature code at `docs/rebrand-pr-b-checklist.md` for the PR-B migration sweep.

## Spec
See `docs/superpowers/specs/2026-05-14-rebrand-design.md` — this PR implements the "Migration plan → PR-A" section.

## What this PR explicitly does NOT do
- No screen-level code is touched. Feature `**` and `ui/components/**` are unchanged. Visual mismatches in those files are expected after this PR and are tracked in `docs/rebrand-pr-b-checklist.md` for PR-B.
- No `strings.xml` changes. Terminology rebrand (Customers / Orders / Workshop) is a separate effort.
- No illustration changes. PR-C handles indigo-tinted illustration refresh.

## Smoke test results

### Android (Pixel 7 emulator, Android 14)
- [x] App launches without crash
- [x] Login screen renders, primary CTA is indigo
- [x] Sign up → Workshop setup → Home navigation works
- [x] Dashboard tab opens, status pills render correctly
- [x] Settings tab opens (with expected cosmetic mismatches catalogued)
- [x] Light/dark toggle flips theme correctly

### iOS (iPhone 17 Pro simulator)
- [x] App launches without crash
- [x] Fraunces renders correctly (or fell back to static weights — see commit log)
- [x] Same 6 steps verified in both modes

## Test plan
- [x] `./gradlew :composeApp:assembleDebug` passes
- [x] `./gradlew detekt` passes
- [x] `./gradlew :composeApp:testDebugUnitTest` passes (no regressions)
- [x] `./gradlew :composeApp:compileKotlinIosSimulatorArm64` passes
- [x] Manual smoke test on Android + iOS, light + dark

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: Report the PR URL**

Print the PR URL so the human reviewer can open it:

```bash
gh pr view --json url --jq '.url'
```

---

## Self-Review

After writing this plan, here's the consistency check against the spec.

### Spec coverage

| Spec section | Covered by |
|---|---|
| DesignTokens.kt rewrite | Task 2 |
| Color.kt rewrite | Task 3 |
| StitchPadColors.kt new | Task 4 |
| Theme.kt update | Task 5 |
| Type.kt rewrite | Task 6 |
| Font asset swap | Task 1 (add) + Task 8 (remove) |
| StitchPadThemePreview.kt new | Task 7 |
| Android smoke test (6 steps) | Task 11 |
| iOS smoke test (6 steps) | Task 12 |
| PR-B preparation checklist | Task 13 |
| Detekt + unit tests + CI | Task 9 + Task 14 Step 3 |
| `@Preview` light+dark dual rendering | Task 7 (both variants explicit) |

Spec coverage: complete.

### Placeholder scan

- No `TBD` / `TODO` / `implement later` in the plan.
- Every code step contains the actual code, not a description of code.
- Every command step has the exact command + expected output.
- The only intentional placeholder is the `_add findings_` block in Task 13 — that's a *content* placeholder the engineer fills in based on their smoke-test observations, not a *planning* placeholder for the author.

### Type / name consistency

- Function name `stitchPadLightColorScheme()` matches across Task 3 (definition), Task 5 (call site), and Task 14 (PR body).
- `StitchPadColors` data class name and `LightStitchPadColors` / `DarkStitchPadColors` instance names match across Tasks 4 and 5.
- Resource accessor names `Res.font.fraunces_variable` / `Res.font.manrope_regular` etc. match the file names from Task 1 (verified manually).
- The 9 `primary*` aliases in Task 2 correspond to the existing tokens in the current `DesignTokens.kt` (verified by reading the file at the start of planning).

No name drift detected.
