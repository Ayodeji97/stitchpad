# Dashboard V2 — Illustrated Stack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current 9-section vertical-stack dashboard with a 7-section illustrated stack that gives every state a clear primary action, drops the redundant KPI tile grid, and preserves the revenue goal / Today's Work / NBA / Pipeline / Reconnect features the user explicitly asked to keep.

**Architecture:** Presentation-layer only. Six new dashboard composables + two small header composables (bell, avatar) + one drawable resolver. The existing five domain calculators (`BucketCalculator`, `NbaCalculator`, `ReconnectCalculator`, `FocusResolver`, `WeeklyGoalCalculator`) are untouched — every value the new sections render already flows from them. Old composables (`FocusTodayCard`, `Tile`, `TileGrid`, `QuickStartTiles`, `ReconnectStrip`, `WelcomeHero`) are deleted in the final task once nothing references them.

**Tech Stack:** Kotlin Multiplatform · Compose Multiplatform · Material3 · Koin · kotlin.test · Compose resources

**Spec:** [2026-04-30-dashboard-v2-illustrated-stack-design.md](../specs/2026-04-30-dashboard-v2-illustrated-stack-design.md)

**Branch:** `feature/dashboard-illustrated-stack` (already created, spec already committed at `26aa93d`)

---

## Pre-conditions verified before starting

- `FocusVariant.Pickup` **already exists** in `feature/dashboard/presentation/model/FocusVariant.kt` (spec said to add it — turns out it was added in the calculator-refactor PR that just merged). Skip Tasks 1–2 of an earlier draft; jump straight to UI work.
- `FocusResolver` **already maps** `DashboardUiState.ReadyForPickup → FocusVariant.Pickup` (verified at `FocusResolver.kt:125`). No domain change needed.
- All five calculators are extracted and on `main`.
- Branch `feature/dashboard-illustrated-stack` is cut from `main`; spec + previews already committed.

---

## File Structure

### New files

| File | Responsibility |
|------|----------------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/BellButton.kt` | 36dp icon button with optional unread dot |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/UserAvatar.kt` | 36dp gradient circle with first letter of name |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardIllustration.kt` | `dashboardIllustrationFor(variant)` resolver — maps `FocusVariant` to a `DrawableResource` (placeholder until PNGs ship); renders an illustration slot composable |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/IllustratedFocusCard.kt` | Replaces `FocusTodayCard`. Variant-driven hero with text on left, illustration on right |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/TodayWorkCard.kt` | Wraps up to 5 `AccentedOrderRow`s in a card with a "View all" header |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineDualCard.kt` | One card with two columns inside (In progress / Not started). Falls back to stacked rows under 360dp |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/ReconnectChipStrip.kt` | Horizontally scrollable pill row of reconnect candidates |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/EmptyIllustrationCard.kt` | Generic illustrated empty state — used by Pipeline and NBA sections |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardIllustrationTest.kt` | Unit tests for the variant→drawable resolver |

### Modified files

| File | Change |
|------|--------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` | Replace private `DashboardHeader`, `WelcomeHero`, `TileGrid` and the `DashboardContent` section list with calls to the new composables. Keep `DashboardRoot`, `DashboardLoadingScreen` etc. unchanged. |
| `composeApp/src/commonMain/composeResources/drawable/` | (Final task) Drop in the 10 generated PNGs from the user. |

### Files to delete (Task 12)

| File | Reason |
|------|--------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FocusTodayCard.kt` | Replaced by `IllustratedFocusCard` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/Tile.kt` | KPI tiles removed (redundant with Reports) |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/QuickStartTiles.kt` | Folded into `IllustratedFocusCard` BrandNew variant |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/ReconnectStrip.kt` | Replaced by `ReconnectChipStrip` |

`WelcomeHero`, `TileGrid`, `FocusTodayCardSection` and the old `DashboardHeader` are private composables inside `DashboardScreen.kt` — they're removed as part of the rewire in Task 11, no separate file deletion.

---

## Task 1: BellButton composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/BellButton.kt`

A small icon button. Notifications feature does not exist yet, so for V1 the button is hidden behind a feature flag (`enabled = false`). Building it now keeps the header layout final.

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@Composable
fun BellButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasUnread: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = "Notifications",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasUnread) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .clip(CircleShape),
            )
        }
    }
}

@Preview
@Composable
private fun BellButtonNoBadgePreview() {
    StitchPadTheme {
        BellButton(onClick = {}, hasUnread = false)
    }
}

@Preview
@Composable
private fun BellButtonWithBadgePreview() {
    StitchPadTheme {
        BellButton(onClick = {}, hasUnread = true)
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/BellButton.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): add BellButton composable for V2 header

Notifications screen ships later — for V1 the button is rendered with
hasUnread=false and a no-op click handler. Building it now keeps the
header layout final.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: UserAvatar composable

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/UserAvatar.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@Composable
fun UserAvatar(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = Brush.linearGradient(
        colors = listOf(
            DesignTokens.primary700,
            DesignTokens.primary900,
        ),
    )
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(gradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = DesignTokens.primary100,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Preview
@Composable
private fun UserAvatarPreview() {
    StitchPadTheme {
        UserAvatar(name = "Olawale", onClick = {})
    }
}

@Preview
@Composable
private fun UserAvatarLowercasePreview() {
    StitchPadTheme {
        UserAvatar(name = "daniel", onClick = {})
    }
}

@Preview
@Composable
private fun UserAvatarEmptyNamePreview() {
    StitchPadTheme {
        UserAvatar(name = "", onClick = {})
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. If `DesignTokens.primary700/primary900/primary100` aren't named those exact things, open `ui/theme/DesignTokens.kt` and substitute the actual property names (the saffron palette tokens).

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/UserAvatar.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): add UserAvatar composable for V2 header

Saffron-gradient circle with the first letter of firstName. Tap routes
to settings (wired up in Task 11).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: DashboardIllustration resolver — pure function + tests

The resolver is a pure mapping from `FocusVariant` (and an "empty state" tag) to a `DrawableResource`. Treating it as a pure function keeps the placeholder swap (Task 13) trivial — change the `when` body, leave the call sites alone.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardIllustration.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardIllustrationTest.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import kotlin.test.Test
import kotlin.test.assertNotNull

class DashboardIllustrationTest {

    @Test
    fun heroIllustrationDefinedForEveryFocusVariant() {
        FocusVariant.entries.forEach { variant ->
            assertNotNull(
                heroIllustrationFor(variant),
                "FocusVariant.$variant must have a hero illustration",
            )
        }
    }

    @Test
    fun emptyIllustrationDefinedForEverySlot() {
        EmptyIllustrationSlot.entries.forEach { slot ->
            assertNotNull(
                emptyIllustrationFor(slot),
                "EmptyIllustrationSlot.$slot must have an empty illustration",
            )
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.dashboard.presentation.components.DashboardIllustrationTest"`
Expected: FAIL with "Unresolved reference: heroIllustrationFor" / "EmptyIllustrationSlot".

- [ ] **Step 3: Implement the resolver**

Create `DashboardIllustration.kt`:

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.onboarding_measurements

/**
 * Slots for empty-state illustrations (one drawable per slot).
 * Names mirror the spec's illustration slugs.
 */
enum class EmptyIllustrationSlot {
    Pipeline,
    Nba,
    Customers,
}

/**
 * Maps a FocusVariant to its hero illustration drawable.
 *
 * Currently returns the same placeholder PNG for every variant — this is the
 * placeholder strategy from the spec (HTML preview ships final art; Compose
 * previews ship a recognisable but generic placeholder until real PNGs arrive).
 *
 * When the user generates the V2 illustrations, replace each branch with the
 * variant-specific drawable (e.g. Res.drawable.dashboard_hero_busy).
 */
fun heroIllustrationFor(variant: FocusVariant): DrawableResource = when (variant) {
    FocusVariant.FirstOrder -> Res.drawable.onboarding_measurements
    FocusVariant.Quiet -> Res.drawable.onboarding_measurements
    FocusVariant.Steady -> Res.drawable.onboarding_measurements
    FocusVariant.Earn -> Res.drawable.onboarding_measurements
    FocusVariant.Focus -> Res.drawable.onboarding_measurements
    FocusVariant.Pickup -> Res.drawable.onboarding_measurements
}

/**
 * Maps an empty-state slot to its illustration drawable. Same placeholder
 * strategy as [heroIllustrationFor].
 */
fun emptyIllustrationFor(slot: EmptyIllustrationSlot): DrawableResource = when (slot) {
    EmptyIllustrationSlot.Pipeline -> Res.drawable.onboarding_measurements
    EmptyIllustrationSlot.Nba -> Res.drawable.onboarding_measurements
    EmptyIllustrationSlot.Customers -> Res.drawable.onboarding_measurements
}

@Composable
fun DashboardIllustration(
    drawable: DrawableResource,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}
```

(The unused `androidx.compose.foundation.layout.size` and `dp` imports will be added in IDE on save; Step 4 catches it via the build.)

- [ ] **Step 4: Add the missing imports**

Add to the top of `DashboardIllustration.kt`:

```kotlin
import androidx.compose.ui.unit.dp
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.dashboard.presentation.components.DashboardIllustrationTest"`
Expected: PASS.

- [ ] **Step 6: Run the existing dashboard tests to confirm no regression**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.feature.dashboard.*"`
Expected: PASS for all tests (FocusResolver, BucketCalculator, NbaCalculator, ReconnectCalculator, WeeklyGoalCalculator, plus the new test).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardIllustration.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/DashboardIllustrationTest.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): add DashboardIllustration resolver + composable

Pure function maps FocusVariant and EmptyIllustrationSlot to a
DrawableResource. Currently every branch returns the existing
onboarding placeholder so layouts can be built and previewed before
the V2 illustrations are generated. Swap is a one-line change per
branch in Task 13.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: IllustratedFocusCard composable

Replaces `FocusTodayCard`. Same variant-driven styling but with an 88dp illustration on the right.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/IllustratedFocusCard.kt`

- [ ] **Step 1: Open the existing FocusTodayCard for reference**

Read `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FocusTodayCard.kt`. The variant accent colors, icon, and copy structure are reused — only the layout changes (icon → illustration on the right, text on the left).

- [ ] **Step 2: Create the file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private data class FocusCardPalette(
    val border: Color,
    val accent: Color,
    val ctaTint: Color,
    val backgroundGradient: Brush?,
)

@Composable
private fun paletteFor(variant: FocusVariant): FocusCardPalette {
    val scheme = MaterialTheme.colorScheme
    return when (variant) {
        FocusVariant.FirstOrder -> FocusCardPalette(
            border = scheme.tertiary.copy(alpha = 0.25f),
            accent = scheme.tertiary,
            ctaTint = scheme.tertiary,
            backgroundGradient = null,
        )
        FocusVariant.Quiet -> FocusCardPalette(
            border = DesignTokens.successColor.copy(alpha = 0.25f),
            accent = DesignTokens.successColor,
            ctaTint = DesignTokens.successColor,
            backgroundGradient = null,
        )
        FocusVariant.Steady -> FocusCardPalette(
            border = scheme.tertiary.copy(alpha = 0.25f),
            accent = scheme.tertiary,
            ctaTint = scheme.tertiary,
            backgroundGradient = null,
        )
        FocusVariant.Earn -> FocusCardPalette(
            border = scheme.primary.copy(alpha = 0.4f),
            accent = scheme.primary,
            ctaTint = scheme.primary,
            backgroundGradient = Brush.linearGradient(
                listOf(
                    scheme.primary.copy(alpha = 0.12f),
                    scheme.surface,
                ),
            ),
        )
        FocusVariant.Focus -> FocusCardPalette(
            border = scheme.error.copy(alpha = 0.3f),
            accent = scheme.error,
            ctaTint = scheme.error,
            backgroundGradient = Brush.linearGradient(
                listOf(
                    scheme.error.copy(alpha = 0.08f),
                    scheme.surface,
                ),
            ),
        )
        FocusVariant.Pickup -> FocusCardPalette(
            border = DesignTokens.successColor.copy(alpha = 0.3f),
            accent = DesignTokens.successColor,
            ctaTint = DesignTokens.successColor,
            backgroundGradient = Brush.linearGradient(
                listOf(
                    DesignTokens.successColor.copy(alpha = 0.10f),
                    scheme.surface,
                ),
            ),
        )
    }
}

@Composable
fun IllustratedFocusCard(
    variant: FocusVariant,
    title: String,
    supporting: String?,
    ctaLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = paletteFor(variant)
    val drawable = heroIllustrationFor(variant)
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusLg))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (palette.backgroundGradient != null)
                        Modifier.background(palette.backgroundGradient)
                    else Modifier,
                )
                .padding(DesignTokens.spacing4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (supporting != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ctaLabel != null) {
                    Spacer(Modifier.height(DesignTokens.spacing2))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = ctaLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.ctaTint,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = palette.ctaTint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            DashboardIllustration(drawable = drawable, size = 88.dp)
        }
    }
}

@Preview
@Composable
private fun IllustratedFocusCardFocusPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Focus,
            title = "2 orders need attention today",
            supporting = "1 overdue fitting · 1 dress due today · ₦120,000 to collect",
            ctaLabel = "View priorities",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun IllustratedFocusCardPickupPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Pickup,
            title = "1 order is ready for pickup",
            supporting = "Kunle Adeyemi's senator wear is ready. Message customer or mark delivered.",
            ctaLabel = "Open order",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun IllustratedFocusCardEarnPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Earn,
            title = "₦185,000 is ready to collect",
            supporting = "2 deposits and 1 final balance can be collected today.",
            ctaLabel = "Collect payments",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun IllustratedFocusCardSteadyPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Steady,
            title = "Workshop is steady",
            supporting = "5 orders are moving smoothly. Nothing is overdue today.",
            ctaLabel = "Open pipeline",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun IllustratedFocusCardQuietPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Quiet,
            title = "Quiet day — bring in new work",
            supporting = "No orders are due today. Reconnect with past customers and follow up on quotes.",
            ctaLabel = "Reconnect now",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun IllustratedFocusCardFirstOrderPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.FirstOrder,
            title = "Turn your customer into your first order",
            supporting = "Add a customer, save measurements, and create your first custom outfit.",
            ctaLabel = "Create order for Ola Kunle",
            onClick = {},
        )
    }
}
```

- [ ] **Step 3: Verify the file compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. If `DesignTokens.radiusLg` / `spacing3` / `spacing4` / `successColor` aren't named those exact things, open `ui/theme/DesignTokens.kt` and substitute the actual property names.

- [ ] **Step 4: Open Android Studio and visually verify each preview**

Open `IllustratedFocusCard.kt` in Android Studio split-view. All six previews should render. Light mode only is fine for now — dark mode is verified at the Task 14 smoke test.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/IllustratedFocusCard.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): add IllustratedFocusCard for V2 hero

Six variant-driven palettes (FirstOrder/Quiet/Steady/Earn/Focus/Pickup)
matching the existing FocusTodayCard accents. 88dp illustration on the
right slot driven by DashboardIllustration. FocusTodayCard is removed
in Task 12 once nothing references it.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: TodayWorkCard composable

Wraps existing `AccentedOrderRow`s in a contained card with header strip ("Today's Work · View all"). Capped at 5 rows with overflow chevron.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/TodayWorkCard.kt`

- [ ] **Step 1: Verify the existing `AccentedOrderRow` signature**

Read `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/AccentedOrderRow.kt`. The signature is:

```kotlin
fun AccentedOrderRow(
    customerName: String,
    primaryLabel: String,
    accentColor: Color,
    chipText: String,
    chipTextColor: Color,
    chipBackground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

We use it unchanged.

- [ ] **Step 2: Create the file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.TodayWorkRowUi
import com.danzucker.stitchpad.ui.components.AccentedOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private const val MAX_VISIBLE_ROWS = 5

@Composable
fun TodayWorkCard(
    rows: List<TodayWorkRowUi>,
    onRowClick: (orderId: String) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val visible = rows.take(MAX_VISIBLE_ROWS)
    val hasMore = rows.size > MAX_VISIBLE_ROWS
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.spacing3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's Work",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (hasMore) {
                    Text(
                        text = "View all (${rows.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onViewAllClick),
                    )
                }
            }
            Spacer(Modifier.height(DesignTokens.spacing2))
            visible.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(DesignTokens.spacing2))
                AccentedOrderRow(
                    customerName = row.customerName,
                    primaryLabel = row.primaryLabel,
                    accentColor = row.accentColor,
                    chipText = row.chipText,
                    chipTextColor = row.chipTextColor,
                    chipBackground = row.chipBackground,
                    onClick = { onRowClick(row.orderId) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun TodayWorkCardPreview() {
    StitchPadTheme {
        TodayWorkCard(
            rows = listOf(
                TodayWorkRowUi.preview(
                    orderId = "1",
                    customerName = "Adaeze Okoro",
                    primaryLabel = "Buba & Skirt · Due 4 PM",
                    accent = DesignTokens.errorColor,
                    chip = "Due today",
                ),
                TodayWorkRowUi.preview(
                    orderId = "2",
                    customerName = "Kunle Adeyemi",
                    primaryLabel = "Senator Wear · Ready",
                    accent = DesignTokens.successColor,
                    chip = "Ready pickup",
                ),
                TodayWorkRowUi.preview(
                    orderId = "3",
                    customerName = "Ifeoma Balogun",
                    primaryLabel = "Bridesmaid Dress · Fitting 2 PM",
                    accent = DesignTokens.warningColor,
                    chip = "Fitting today",
                ),
            ),
            onRowClick = {},
            onViewAllClick = {},
        )
    }
}
```

- [ ] **Step 3: Add the `TodayWorkRowUi` model**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/TodayWorkRowUi.kt`:

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.model

import androidx.compose.ui.graphics.Color
import com.danzucker.stitchpad.ui.theme.DesignTokens

/**
 * UI model for a single Today's Work row. Built in DashboardViewModel from
 * BucketCalculator output.
 */
data class TodayWorkRowUi(
    val orderId: String,
    val customerName: String,
    val primaryLabel: String,
    val accentColor: Color,
    val chipText: String,
    val chipTextColor: Color,
    val chipBackground: Color,
) {
    companion object {
        /** Convenience factory used only in @Preview composables. */
        fun preview(
            orderId: String,
            customerName: String,
            primaryLabel: String,
            accent: Color,
            chip: String,
        ) = TodayWorkRowUi(
            orderId = orderId,
            customerName = customerName,
            primaryLabel = primaryLabel,
            accentColor = accent,
            chipText = chip,
            chipTextColor = accent,
            chipBackground = accent.copy(alpha = 0.12f),
        )
    }
}
```

- [ ] **Step 4: Verify the file compiles and preview renders**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

Open `TodayWorkCard.kt` in Android Studio. Preview should render three rows (red overdue, green ready, amber fitting).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/TodayWorkCard.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/TodayWorkRowUi.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): add TodayWorkCard wrapping AccentedOrderRow

Caps at 5 rows with View all overflow. New TodayWorkRowUi presentation
model holds the styled row data the ViewModel will assemble from
BucketCalculator output in Task 11.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: PipelineDualCard composable

One card with two columns inside (In progress / Not started). Each column shows up to 2 rows + a "View more" footer link. Falls back to single-column stack on phones narrower than 360dp.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineDualCard.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private const val MAX_ROWS_PER_COLUMN = 2
private val NARROW_BREAKPOINT = 360.dp

data class PipelineColumnData(
    val totalCount: Int,
    val visibleRows: List<DashboardOrderRow>,
)

@Composable
fun PipelineDualCard(
    inProgress: PipelineColumnData,
    notStarted: PipelineColumnData,
    onRowClick: (orderId: String) -> Unit,
    onInProgressMoreClick: () -> Unit,
    onNotStartedMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(modifier = Modifier.padding(DesignTokens.spacing3)) {
            val isNarrow = maxWidth < NARROW_BREAKPOINT
            if (isNarrow) {
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.spacing3)) {
                    PipelineColumn(
                        title = "In progress",
                        icon = Icons.Filled.LocalFireDepartment,
                        accent = DesignTokens.successColor,
                        data = inProgress,
                        onRowClick = onRowClick,
                        onMoreClick = onInProgressMoreClick,
                    )
                    PipelineColumn(
                        title = "Not started",
                        icon = Icons.Filled.Close,
                        accent = DesignTokens.warningColor,
                        data = notStarted,
                        onRowClick = onRowClick,
                        onMoreClick = onNotStartedMoreClick,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing3)) {
                    PipelineColumn(
                        title = "In progress",
                        icon = Icons.Filled.LocalFireDepartment,
                        accent = DesignTokens.successColor,
                        data = inProgress,
                        onRowClick = onRowClick,
                        onMoreClick = onInProgressMoreClick,
                        modifier = Modifier.weight(1f),
                    )
                    PipelineColumn(
                        title = "Not started",
                        icon = Icons.Filled.Close,
                        accent = DesignTokens.warningColor,
                        data = notStarted,
                        onRowClick = onRowClick,
                        onMoreClick = onNotStartedMoreClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineColumn(
    title: String,
    icon: ImageVector,
    accent: Color,
    data: PipelineColumnData,
    onRowClick: (orderId: String) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .height(14.dp),
            )
            Text(
                text = "$title · ${data.totalCount}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.height(DesignTokens.spacing2))
        data.visibleRows.take(MAX_ROWS_PER_COLUMN).forEach { row ->
            Text(
                text = row.customerName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRowClick(row.orderId) }
                    .padding(vertical = 2.dp),
            )
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (data.totalCount > MAX_ROWS_PER_COLUMN) {
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable(onClick = onMoreClick),
            ) {
                Text(
                    text = "View all",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(12.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun PipelineDualCardPreview() {
    StitchPadTheme {
        PipelineDualCard(
            inProgress = PipelineColumnData(
                totalCount = 4,
                visibleRows = listOf(
                    DashboardOrderRow(
                        orderId = "1",
                        customerName = "Adeyinka Paul",
                        subtitle = "Vintage Buba",
                        deadlineMillis = null,
                        amountMinor = null,
                    ),
                    DashboardOrderRow(
                        orderId = "2",
                        customerName = "Blessing T.",
                        subtitle = "Trouser",
                        deadlineMillis = null,
                        amountMinor = null,
                    ),
                ),
            ),
            notStarted = PipelineColumnData(
                totalCount = 6,
                visibleRows = listOf(
                    DashboardOrderRow(
                        orderId = "3",
                        customerName = "Pooja Paul",
                        subtitle = "Ankara Dress",
                        deadlineMillis = null,
                        amountMinor = null,
                    ),
                    DashboardOrderRow(
                        orderId = "4",
                        customerName = "Tolu Ojo",
                        subtitle = "Jacket",
                        deadlineMillis = null,
                        amountMinor = null,
                    ),
                ),
            ),
            onRowClick = {},
            onInProgressMoreClick = {},
            onNotStartedMoreClick = {},
        )
    }
}
```

> **Note:** the preview references `DashboardOrderRow`. Open `feature/dashboard/domain/model/DashboardOrderRow.kt` and confirm the constructor argument names match (`orderId`, `customerName`, `subtitle`, etc.). If they differ, update the preview to match the real fields — do not invent new fields.

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Visually verify the preview renders**

Open Android Studio. Preview should show two columns with green/amber headers, two rows each, "View all →" footers.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineDualCard.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): add PipelineDualCard with narrow-screen fallback

Single card with two internal columns (In progress / Not started).
Falls back to single-column stack under 360dp width via
BoxWithConstraints. Replaces the two separate Pipeline sections in the
current dashboard.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: ReconnectChipStrip composable

Horizontally scrollable pill row of reconnect candidates. Hides itself when the list is empty.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/ReconnectChipStrip.kt`

- [ ] **Step 1: Inspect ReconnectCandidate**

Read `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/ReconnectCandidate.kt`. Note the field names; the chip uses `customerName` and a relative-time label.

- [ ] **Step 2: Create the file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@Composable
fun ReconnectChipStrip(
    candidates: List<ReconnectCandidate>,
    onCandidateClick: (customerId: String) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusFull),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DesignTokens.spacing3, end = DesignTokens.spacing2, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Reconnect",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing2),
            ) {
                items(items = candidates, key = { it.customerId }) { candidate ->
                    ReconnectChip(candidate = candidate, onClick = { onCandidateClick(candidate.customerId) })
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoreClick),
            )
        }
    }
}

@Composable
private fun ReconnectChip(candidate: ReconnectCandidate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusFull))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = candidate.customerName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "· ${candidate.relativeLastSeenLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun ReconnectChipStripPreview() {
    StitchPadTheme {
        ReconnectChipStrip(
            candidates = listOf(
                ReconnectCandidate.preview(id = "1", name = "Tolu Adebayo", relative = "45d"),
                ReconnectCandidate.preview(id = "2", name = "Funmi Akinola", relative = "2mo"),
            ),
            onCandidateClick = {},
            onMoreClick = {},
        )
    }
}

@Preview
@Composable
private fun ReconnectChipStripEmptyPreview() {
    StitchPadTheme {
        ReconnectChipStrip(
            candidates = emptyList(),
            onCandidateClick = {},
            onMoreClick = {},
        )
    }
}
```

- [ ] **Step 3: Add the preview helper to `ReconnectCandidate`**

Open `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/ReconnectCandidate.kt`. Add a `companion object` with a `preview` factory used only for previews:

```kotlin
companion object {
    fun preview(id: String, name: String, relative: String) = ReconnectCandidate(
        customerId = id,
        customerName = name,
        relativeLastSeenLabel = relative,
        // Fill remaining fields with sensible defaults — adapt to the actual constructor.
    )
}
```

> **Note:** the actual `ReconnectCandidate` may have more fields (last-order timestamp, phone number, etc.). Adapt the preview helper to match — fill non-essential fields with empty strings or 0L.

- [ ] **Step 4: Verify compile + preview render**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. Open Android Studio; the preview should render two pills + a chevron, and the empty preview should render nothing (the composable returns early).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/ReconnectChipStrip.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/ReconnectCandidate.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): add ReconnectChipStrip pill-row composable

Horizontally scrollable LazyRow of reconnect chips with a leading
"Reconnect" label and trailing chevron. Hides itself when the
candidate list is empty. Preview helper added to ReconnectCandidate.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: EmptyIllustrationCard composable

Generic illustrated empty state. Used by the Pipeline section when no orders exist and (optionally) the NBA section.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/EmptyIllustrationCard.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

@Composable
fun EmptyIllustrationCard(
    slot: EmptyIllustrationSlot,
    title: String,
    supporting: String,
    ctaLabel: String? = null,
    onCtaClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val drawable = emptyIllustrationFor(slot)
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.spacing4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing3),
        ) {
            DashboardIllustration(drawable = drawable, size = 72.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ctaLabel != null) {
                    Spacer(Modifier.height(DesignTokens.spacing2))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable(onClick = onCtaClick),
                    ) {
                        Text(
                            text = ctaLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun EmptyIllustrationCardPipelinePreview() {
    StitchPadTheme {
        EmptyIllustrationCard(
            slot = EmptyIllustrationSlot.Pipeline,
            title = "No work in flight yet",
            supporting = "When you create orders, they'll appear here.",
            ctaLabel = "Create order",
            onCtaClick = {},
        )
    }
}

@Preview
@Composable
private fun EmptyIllustrationCardNbaPreview() {
    StitchPadTheme {
        EmptyIllustrationCard(
            slot = EmptyIllustrationSlot.Nba,
            title = "No suggested moves",
            supporting = "Show up when balances or deadlines need follow-up.",
        )
    }
}
```

- [ ] **Step 2: Verify compile + previews**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. Both previews render in Android Studio.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/EmptyIllustrationCard.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): add EmptyIllustrationCard for V2 empty states

Generic illustrated empty card used by the Pipeline and NBA sections.
72dp illustration on the left, title + supporting + optional CTA on
the right. Drawable resolved through emptyIllustrationFor(slot).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Modify DashboardHeader (private composable in DashboardScreen.kt)

The current `DashboardHeader` (line ~983 of `DashboardScreen.kt`) shows `firstName + greeting + businessName + date + settings icon`. Replace it with `firstName + greeting + date + BellButton + UserAvatar`.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`

- [ ] **Step 1: Find the existing DashboardHeader**

Search the file for `private fun DashboardHeader(`. Read the full function body (~30 lines). Note its parameters and call sites.

- [ ] **Step 2: Replace the function body**

Replace the entire `DashboardHeader` function with:

```kotlin
@Composable
private fun DashboardHeader(
    firstName: String,
    greeting: String,
    todayDate: String,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$greeting, $firstName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = todayDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BellButton(
                onClick = { /* notifications screen ships later */ },
                hasUnread = false,
            )
            UserAvatar(
                name = firstName,
                onClick = onAvatarClick,
            )
        }
    }
}
```

- [ ] **Step 3: Update the call site**

In `DashboardContent`, find the existing `DashboardHeader(...)` call. Update the arguments to drop `businessName` and `onSettingsClick`, and add `onAvatarClick`. The settings nav callback gets routed through `onAvatarClick` instead.

Before:
```kotlin
DashboardHeader(
    firstName = state.firstName,
    businessName = state.businessName,
    greeting = state.greeting.toUiText().asString(),
    todayDate = state.todayDate,
    onSettingsClick = { onAction(DashboardAction.OnSettingsClick) },
)
```

After:
```kotlin
DashboardHeader(
    firstName = state.firstName,
    greeting = state.greeting.toUiText().asString(),
    todayDate = state.todayDate,
    onAvatarClick = { onAction(DashboardAction.OnSettingsClick) },
)
```

> The `businessName` field stays in `DashboardState` for use elsewhere (e.g. settings screen). It's just no longer rendered in the header.

- [ ] **Step 4: Add missing imports if needed**

The new header references `BellButton` and `UserAvatar`. They live in the same package (`feature.dashboard.presentation.components` — check the actual `DashboardScreen.kt` package), so explicit imports may be needed at the top of the file:

```kotlin
import com.danzucker.stitchpad.feature.dashboard.presentation.components.BellButton
import com.danzucker.stitchpad.feature.dashboard.presentation.components.UserAvatar
```

- [ ] **Step 5: Verify the file compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the app on Android, navigate to Dashboard, check the header**

Run: `./gradlew :composeApp:installDebug`
Open the app on emulator. Header should show "Good afternoon, Olawale / Thursday, 30 Apr" on the left, bell + saffron-gradient avatar circle on the right. Tap the avatar — should navigate to Settings (existing behavior preserved).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): rewire DashboardHeader for V2 with bell + avatar

Replaces the businessName + settings-icon header. Settings now lives
behind the avatar tap. Bell is rendered with hasUnread=false until the
notifications feature ships.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Rewire DashboardContent — assemble the new sections

This is the largest task. The existing `DashboardContent` composable (around line 294) renders 9 vertically-stacked sections. Replace it with the 7-section V2 layout.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt` (only if the ViewModel needs to assemble new UI models — `TodayWorkRowUi`, `PipelineColumnData`)

- [ ] **Step 1: Read the existing DashboardContent + DashboardState**

Open both files. Map out which state fields are read by each existing section so you know what data to pass to the new composables.

- [ ] **Step 2: Build the UI-model assemblers in DashboardViewModel**

The ViewModel needs to map raw calculator output to the new UI models. Add two private extension functions inside `DashboardViewModel.kt`:

```kotlin
private fun List<DashboardOrderRow>.toTodayWorkRows(): List<TodayWorkRowUi> = map { row ->
    val (chip, accent) = when (row.bucket) {
        WorkBucket.Overdue -> "Overdue" to DesignTokens.errorColor
        WorkBucket.DueToday -> "Due today" to DesignTokens.warningColor
        WorkBucket.Ready -> "Ready" to DesignTokens.successColor
    }
    TodayWorkRowUi(
        orderId = row.orderId,
        customerName = row.customerName,
        primaryLabel = row.subtitle,
        accentColor = accent,
        chipText = chip,
        chipTextColor = accent,
        chipBackground = accent.copy(alpha = 0.12f),
    )
}

private fun pipelineColumnFrom(rows: List<DashboardOrderRow>, total: Int) =
    PipelineColumnData(totalCount = total, visibleRows = rows.take(2))
```

> **Note:** field names like `row.bucket` / `WorkBucket.Overdue` may differ in the real `DashboardOrderRow` / `Buckets` model. Open `feature/dashboard/domain/model/Buckets.kt` and `DashboardOrderRow.kt` and substitute the actual types and enum cases.

- [ ] **Step 3: Replace the DashboardContent body**

Open `DashboardScreen.kt`, find `private fun DashboardContent(...)`. Replace its `Column` body with:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = DesignTokens.spacing4)
        .padding(bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(DesignTokens.spacing4),
) {
    Spacer(Modifier.height(DesignTokens.spacing4))

    // 1. Header
    DashboardHeader(
        firstName = state.firstName,
        greeting = state.greeting.toUiText().asString(),
        todayDate = state.todayDate,
        onAvatarClick = { onAction(DashboardAction.OnSettingsClick) },
    )

    // 2. Illustrated focus card — drives every state
    IllustratedFocusCard(
        variant = state.focusVariant ?: FocusVariant.Steady,
        title = state.focusHeadline.toUiText().asString(),
        supporting = state.focusSupporting?.toUiText()?.asString(),
        ctaLabel = state.focusCtaLabel?.toUiText()?.asString(),
        onClick = { onAction(DashboardAction.OnFocusCtaClick) },
    )

    // 3. Weekly goal card (kept as-is)
    if (state.weeklyGoal != null) {
        WeeklyGoalsCard(
            goal = state.weeklyGoal,
            onClick = { onAction(DashboardAction.OnWeeklyGoalClick) },
        )
    }

    // 4. Today's work
    val todayWorkRows = remember(state.overdue, state.dueToday, state.ready) {
        (state.overdue + state.dueToday + state.ready).toTodayWorkRows()
    }
    TodayWorkCard(
        rows = todayWorkRows,
        onRowClick = { onAction(DashboardAction.OnOrderClick(it)) },
        onViewAllClick = { onAction(DashboardAction.OnViewAllOrdersClick) },
    )

    // 5. Next best actions — 2-up grid
    if (state.nextBestActions.isNotEmpty()) {
        NextBestActionsGrid(
            actions = state.nextBestActions,
            onActionClick = { onAction(DashboardAction.OnNbaClick(it)) },
        )
    } else {
        EmptyIllustrationCard(
            slot = EmptyIllustrationSlot.Nba,
            title = "No suggested moves",
            supporting = "Show up when balances or deadlines need follow-up.",
        )
    }

    // 6. Pipeline
    val pipelineEmpty = state.pipelineInProgress.isEmpty() && state.pipelinePending.isEmpty()
    if (pipelineEmpty) {
        EmptyIllustrationCard(
            slot = EmptyIllustrationSlot.Pipeline,
            title = "No work in flight yet",
            supporting = "When you create orders, they'll appear here.",
            ctaLabel = "Create order",
            onCtaClick = { onAction(DashboardAction.OnCreateOrderClick) },
        )
    } else {
        PipelineDualCard(
            inProgress = pipelineColumnFrom(state.pipelineInProgress, state.pipelineInProgressTotal),
            notStarted = pipelineColumnFrom(state.pipelinePending, state.pipelinePendingTotal),
            onRowClick = { onAction(DashboardAction.OnOrderClick(it)) },
            onInProgressMoreClick = { onAction(DashboardAction.OnViewPipelineInProgressClick) },
            onNotStartedMoreClick = { onAction(DashboardAction.OnViewPipelinePendingClick) },
        )
    }

    // 7. Reconnect
    ReconnectChipStrip(
        candidates = state.reconnectCandidates,
        onCandidateClick = { onAction(DashboardAction.OnReconnectClick(it)) },
        onMoreClick = { onAction(DashboardAction.OnViewReconnectClick) },
    )
}
```

> **NextBestActionsGrid** is the existing `NextBestActionsSection` composable, restructured to render its NBAs in a 2-up grid instead of a horizontal carousel. If a 2-up grid is too involved for this task, leave the existing horizontal NBA carousel in place and ship the grid as a follow-up. The spec calls for 2-up but the layout decision is independent of the rest of the rewire — preserving correctness here is more important than visual purity.

- [ ] **Step 4: Add missing imports**

```kotlin
import com.danzucker.stitchpad.feature.dashboard.presentation.components.IllustratedFocusCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.TodayWorkCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.PipelineDualCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.PipelineColumnData
import com.danzucker.stitchpad.feature.dashboard.presentation.components.ReconnectChipStrip
import com.danzucker.stitchpad.feature.dashboard.presentation.components.EmptyIllustrationCard
import com.danzucker.stitchpad.feature.dashboard.presentation.components.EmptyIllustrationSlot
```

- [ ] **Step 5: Add any missing `DashboardAction` cases**

The new layout references actions that may not exist:
- `OnViewAllOrdersClick`
- `OnViewPipelineInProgressClick`
- `OnViewPipelinePendingClick`
- `OnViewReconnectClick`
- `OnCreateOrderClick`

Open `DashboardAction.kt` and add the missing cases (modeled on existing ones). Then handle each in the ViewModel's `onAction` dispatcher with the appropriate `DashboardEvent` (or reuse an existing one — `OnViewAllOrdersClick` likely already maps to a `NavigateToOrders` event).

- [ ] **Step 6: Verify the app compiles and runs**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :composeApp:installDebug` and open the app. Sign in with a test tailor that has data. The dashboard should render the new 7-section layout.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardAction.kt
git commit -m "$(cat <<'EOF'
feat(dashboard): rewire DashboardContent to V2 illustrated stack

Replaces the 9-section vertical scroll with 7 sections: header,
illustrated focus card, weekly goal, today's work, NBA, pipeline,
reconnect chip-row. Empty pipeline / NBA states render
EmptyIllustrationCard. New DashboardAction cases for the additional
navigation targets.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Delete the unreferenced old composables

Run a final clean-up to remove dead code now that nothing references the old V1 composables. **Do not skip this task** — leaving deleted-but-still-present composables creates confusion for the next person reading the code.

**Files to delete:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FocusTodayCard.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/Tile.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/QuickStartTiles.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/ReconnectStrip.kt`

**Private composables to remove from `DashboardScreen.kt`:**
- `private fun WelcomeHero(...)` (line ~1037)
- `private fun TileGrid(...)` (line ~738)
- `private fun FocusTodayCardSection(...)` (line ~434)
- Anything that referenced these (`QuickStartTiles` call sites, `Tile` imports)

- [ ] **Step 1: Verify nothing references the targets**

```bash
grep -rn "FocusTodayCard\|QuickStartTiles\b\|ui.components.Tile\b\|ReconnectStrip\b\|WelcomeHero\b\|TileGrid\b" composeApp/src/commonMain/kotlin
```

The only matches should be the definition sites themselves (the files we're about to delete). If any production code still references them, you missed a call site in Task 10 — fix that first.

- [ ] **Step 2: Delete the files**

```bash
rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/FocusTodayCard.kt
rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/Tile.kt
rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/QuickStartTiles.kt
rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/ReconnectStrip.kt
```

- [ ] **Step 3: Remove the private composables from DashboardScreen.kt**

Open `DashboardScreen.kt`. Delete:
- `private fun WelcomeHero(...)` and any related private helpers it used
- `private fun TileGrid(...)`
- `private fun FocusTodayCardSection(...)` (this was the wrapper that called the now-deleted `ui.components.FocusTodayCard`)
- Any private icon helpers or palette resolvers that only existed for the deleted composables

- [ ] **Step 4: Remove now-unused imports from DashboardScreen.kt**

Run the IDE's "Optimize Imports" action OR manually remove:
```kotlin
import com.danzucker.stitchpad.ui.components.FocusTodayCard
import com.danzucker.stitchpad.ui.components.Tile
import com.danzucker.stitchpad.ui.components.QuickStartTiles
```
and any other imports that the IDE flags as unused.

- [ ] **Step 5: Verify the build is clean**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Run detekt to confirm no new style issues**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -u && git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt
git commit -m "$(cat <<'EOF'
refactor(dashboard): delete V1 composables superseded by illustrated stack

Removes FocusTodayCard, Tile, QuickStartTiles, ReconnectStrip plus the
WelcomeHero/TileGrid/FocusTodayCardSection privates inside
DashboardScreen.kt. All four feature concepts are now covered by the
new V2 components.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: End-to-end smoke test + screenshots

Per the `feedback_qa_smoke_tests` memory, every PR must include manual smoke steps. This task IS the smoke test for the V2 dashboard.

- [ ] **Step 1: Boot a clean emulator with no Firestore data**

Sign up as a fresh user. Verify:
- Header shows greeting + bell + avatar
- IllustratedFocusCard renders with `BrandNew`/`Quiet` variant
- Pipeline section shows the illustrated empty state ("No work in flight yet")
- NBA section shows the illustrated empty state ("No suggested moves")
- Reconnect strip is hidden (no candidates yet)

Capture screenshot: `dashboard-brand-new-light.png`. Toggle dark mode. Capture: `dashboard-brand-new-dark.png`.

- [ ] **Step 2: Add one customer**

Verify the focus card updates to `FirstOrder` variant ("Turn your customer into your first order").

Screenshot: `dashboard-first-customer-light.png` + dark.

- [ ] **Step 3: Create one order due today**

Verify the focus card updates to `Focus` variant. Today's Work card shows the order.

Screenshot: `dashboard-busy-day-light.png` + dark.

- [ ] **Step 4: Mark the order as Ready**

Verify the focus card updates to `Pickup` variant. Today's Work shows "Ready pickup" chip.

Screenshot: `dashboard-ready-pickup-light.png` + dark.

- [ ] **Step 5: Verify scroll behavior**

Scroll the dashboard. Confirm FAB no longer overlaps content (the spec's primary fix). The Reconnect chip-row stays accessible at the bottom.

- [ ] **Step 6: Verify dark mode parity**

Toggle dark mode at every step. Confirm:
- Saffron CTAs stay readable
- Status chips (red overdue, amber due, green ready) remain legible
- Illustration backgrounds blend with dark surface (acceptable that placeholder PNG looks generic — gets replaced in next task)

- [ ] **Step 7: Capture all screenshots into `docs/superpowers/screenshots/dashboard-v2/`**

Create the folder and drop in all the PNGs from the steps above.

- [ ] **Step 8: Commit the screenshots**

```bash
git add docs/superpowers/screenshots/dashboard-v2/
git commit -m "$(cat <<'EOF'
test(dashboard): add V2 illustrated-stack smoke screenshots

Captures BrandNew, FirstCustomer, BusyDay, ReadyForPickup states in
both light and dark modes. Used in PR description to demonstrate the
state matrix.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Open the PR and (separately) generate the real illustrations

The redesign is mergeable as-is — every state renders, every test passes, the layout shrinks the dashboard within the viewport. The remaining work (real illustrations) is **non-blocking** and can ship in a follow-up PR.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feature/dashboard-illustrated-stack
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "feat(dashboard): V2 illustrated stack" --body "$(cat <<'EOF'
## Summary
- Replaces 9-section dashboard with 7-section illustrated stack
- Drops the redundant KPI tile grid (overlap with Reports production status)
- New composables: IllustratedFocusCard, TodayWorkCard, PipelineDualCard, ReconnectChipStrip, EmptyIllustrationCard, BellButton, UserAvatar
- Removes FocusTodayCard, Tile, QuickStartTiles, ReconnectStrip, WelcomeHero, TileGrid

## Spec
- Design: docs/superpowers/specs/2026-04-30-dashboard-v2-illustrated-stack-design.md
- Plan: docs/superpowers/plans/2026-04-30-dashboard-v2-illustrated-stack.md
- Preview: preview/dashboard-v2-direction-options.html (Option A approved)

## Illustration placeholders
Hero illustrations currently fall back to `Res.drawable.onboarding_measurements`
across all variants. Real PNGs are generated in a follow-up issue using the
locked DALL·E prompt in the spec — swapping them in is a one-line change per
branch in `DashboardIllustration.kt`.

## Test plan
- [ ] Build clean emulator: header shows bell + avatar, focus card matches variant, empty states are illustrated
- [ ] Add one customer: focus card switches to FirstOrder
- [ ] Create one order due today: focus card switches to Focus, Today's Work populates
- [ ] Mark order Ready: focus card switches to Pickup
- [ ] Scroll: FAB no longer overlaps content
- [ ] Dark mode parity: every state, every section
- [ ] Run tests: `./gradlew :composeApp:testDebugUnitTest`
- [ ] Run detekt: `./gradlew detekt`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Generate the V2 illustrations (parallel track — does not block this PR)**

Open ChatGPT (or DALL·E directly). For each of the 10 illustration slugs in the spec, paste the locked style prompt and generate the image. Save into `composeApp/src/commonMain/composeResources/drawable/` as `dashboard_hero_busy.png`, `dashboard_hero_pickup.png`, etc. (snake_case to match the existing onboarding pattern).

When at least the seven hero PNGs are ready, open `DashboardIllustration.kt` and swap each `Res.drawable.onboarding_measurements` for the real drawable. Commit on a follow-up branch — `feature/dashboard-v2-illustrations` — and PR.

---

## Self-Review

I checked the plan against the spec section by section.

**Spec coverage:**
- Header (greeting + bell + avatar) — Tasks 1, 2, 9 ✓
- Illustrated focus card with 7 variants — Tasks 3, 4 ✓
- Revenue goal kept — Task 10 (no change to WeeklyGoalsCard, used as-is) ✓
- Today's Work card — Task 5 ✓
- NBA grid — Task 10 (NextBestActionsGrid follow-up if 2-up grid restructure is too big) ✓
- Pipeline dual card — Task 6 ✓
- Reconnect chip strip — Task 7 ✓
- Empty illustration card — Task 8 ✓
- State matrix (FocusVariant Pickup) — already exists in the codebase (verified pre-conditions) ✓
- Illustrations + locked DALL·E prompt — referenced in Task 3 + Task 13 ✓
- Migration & branch strategy — already executed (Task 0 / branch creation) ✓
- Smoke test — Task 12 ✓
- Definition of Done — covered across Tasks 11 (deletion) and 12 (smoke) ✓

**Placeholder scan:** No "TBD", no "implement appropriate handling", no "similar to Task N" without code. The few `> Note:` callouts are guidance about real-codebase verification (substitute exact field names), not stand-ins for actual content.

**Type consistency:** `TodayWorkRowUi`, `PipelineColumnData`, `EmptyIllustrationSlot` are all defined in their introducing tasks before being used in later tasks. `DashboardOrderRow` is referenced from the existing domain model — Task 6 includes a verification step.

**Spec gap noted:** The spec calls for `Pickup` to be added to `FocusVariant` — turns out it was already added in the calculator-refactor PR. The plan flags this in "Pre-conditions verified" and skips the corresponding task.

---
