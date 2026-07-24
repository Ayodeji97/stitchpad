# Dashboard Simplification "Day at a Glance" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce dashboard noise by removing the duplicate Quick-access section, slimming the Smart card to its one working action, and collapsing the Work Pipeline to a one-line summary — with no action or feature losing its only entry point.

**Architecture:** UI-only changes in the `feature/dashboard` presentation layer. No calculator, ViewModel, DTO, or Firestore behaviour changes. `BucketCalculator`, `NbaCalculator`, `ReconnectCalculator`, and all `DashboardState` fields stay as-is; we only change what the Compose tree renders. One new pure helper (`pipelineSummarySegments`) is the sole unit-tested piece; everything else is verified by Detekt + iOS compile + Compose previews + a manual per-state smoke test (repo convention: Daniel is QA, screens have no Compose UI tests).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material3, `compose.resources` string resources, Koin, Detekt, JUnit-style `kotlin.test` in `commonTest`.

## Global Constraints

- **No hardcoded user-facing strings** — every label uses `compose.resources` (`Res.string.*`). (CLAUDE.md)
- **`compose.resources` positional args only** — templates substitute `%1$d`, `%2$d`, … positionally. (memory: compose.resources positional args)
- **No backslash escapes in `strings.xml`** — use `&apos;` / `’`, never `\'`. (memory: No backslash in strings.xml)
- **iOS compile is a required gate** — an Android-only build passing is not "done"; run the iOS compile before finishing each code task. (memory: KMP JVM-only APIs break iOS)
- **Detekt must pass** — `./gradlew detekt`; it fails on unused imports/properties, so dead imports must be removed. (repo convention)
- **Capture Gradle exit codes correctly** — never trust a piped `$?`; use `PIPESTATUS`/direct exit code. (memory: Gradle piped exit codes)
- **Every Screen/component composable keeps a `@Preview`.** (CLAUDE.md)
- **Feature branch already created:** `feature/dashboard-simplify-day-at-a-glance` (off `main`). Commit per task; do not push to `main`. (memory: PR Workflow)

---

## File-by-file map

**Task 1 — Remove Quick access (Change A)**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` — delete the `QuickAccessSection` call site (§8) and the `QuickAccessSection` + `QuickAccessRow` composables; remove imports Detekt flags.

**Task 2 — Slim Smart card (Change B)**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/SmartSectionCard.kt` — replace the 3-tile `LazyRow` with a single full-width Draft Message action row; drop the two disabled tiles; remove imports Detekt flags.
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` — the two "coming soon" tile strings (`smart_intent_price_this_title`, `smart_intent_reply_helper_title`, `smart_intent_coming_soon_label`) become unused; leave them (harmless) OR delete — see Task 2 Step 5.

**Task 3 — Collapse Pipeline to a summary (Change C)**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineSummaryRow.kt` — pure `pipelineSummarySegments()` helper + `PipelineSummaryRow` composable.
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineSummaryRowTest.kt` — helper unit tests.
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` — add 3 workshop-summary strings + 1 content-description string.
- Modify: `DashboardScreen.kt` — replace the `PipelineSection(...)` block (§6) with `PipelineSummaryRow`; delete the `PIPELINE_EMPTY_HIDDEN_STATES` constant + its KDoc; swap the `PipelineSection` import for `PipelineSummaryRow`.
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineSection.kt` (now uncalled — the two remaining references are KDoc comments).
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineEmptyHeroCard.kt` (only used by `PipelineSection`).
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/DashboardUiState.kt` — update KDoc mentions of "PipelineSection" to "pipeline summary row" (lines ~49/57/66/75).

**Keep (do NOT touch):** `PipelineOrderRow.kt` (shared with `TodayWorkRichRow`), `PipelinePaymentStatus.kt`, `DashboardOrderRow.kt`, `BucketCalculator.kt`, all `DashboardState` pipeline fields (`pipelineInProgress/Pending` lists stay set by the VM; only their dashboard rendering is dropped).

---

## Task 1: Remove the Quick access section

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new. Inspiration remains reachable via the header `IconButton` (`DashboardHeader`, ~line 1539) and the FAB `SpeedDialAction` (~line 627); Measurements remains reachable via the FAB `SpeedDialAction` (~line 638). No signature changes.

- [ ] **Step 1: Delete the Quick access call site**

In `DashboardContent`, remove the entire §8 block (currently ~lines 1010–1018):

```kotlin
        // 8. Quick access — Inspiration + Measurements shortcut rows. Visible
        //    in all populated states (app-bar icon guarantees Inspiration
        //    access in Loading/BrandNew too).
        if (state.uiState != DashboardUiState.Loading) {
            QuickAccessSection(
                onInspirationClick = { onAction(DashboardAction.OnInspirationClick) },
                onMeasurementsClick = { onAction(DashboardAction.OnMeasurementsShortcutClick) },
            )
        }
```

Delete all of it (including the comment). `Reconnect` (§7) becomes the last section in the scroll column.

- [ ] **Step 2: Delete the `QuickAccessSection` and `QuickAccessRow` composables**

Remove both composables in full (currently ~lines 1022–1118): the `QuickAccessSection` function (with its KDoc) and the `QuickAccessRow` function. Nothing else references them.

- [ ] **Step 3: Remove now-unused imports**

Run Detekt to surface unused imports:

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew detekt 2>&1 | tail -40; echo "exit=${PIPESTATUS[0]}"
```

Expected: `UnusedImports` findings for symbols that were only used by the deleted code. Remove each flagged import from `DashboardScreen.kt`. Likely set (verify against Detekt output, do not remove blindly): `androidx.compose.foundation.BorderStroke`, `androidx.compose.ui.graphics.vector.ImageVector`, `androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight`, and the string imports `dashboard_inspiration_card_title`, `dashboard_inspiration_card_subtitle`, `dashboard_measurements_card_subtitle`. Do NOT remove `dashboard_measurements_card_title` (still used for the FAB `measurementsLabel`), `Surface`/`CollectionsBookmark`/`Straighten` if Detekt shows them still used elsewhere.

- [ ] **Step 4: Re-run Detekt to confirm clean**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew detekt 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`, no `UnusedImports` in `DashboardScreen.kt`.

- [ ] **Step 5: Android compile**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`.

- [ ] **Step 6: iOS compile (required gate)**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`.

- [ ] **Step 7: Commit**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt
git commit -m "refactor(dashboard): remove duplicate Quick-access section

Inspiration stays in the header icon + FAB; Measurements stays in the
FAB. No entry point lost.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

**Manual smoke test (record in PR):** open the dashboard in a populated state → the bottom "Quick access" card is gone; the Inspiration header icon opens Inspiration; the FAB speed-dial still lists New customer / New order / Inspiration / Measurements and each opens its screen.

---

## Task 2: Slim the Smart card to a single Draft Message row

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/SmartSectionCard.kt`

**Interfaces:**
- Consumes: existing `IntentTile` is NOT reused here (it is fixed-width 160dp, tile-shaped). This task builds a full-width row inline. `FreeTierCounterChip(remaining: Int)` is unchanged and kept.
- Produces: `SmartSectionCard(remainingFreeQuota: Int?, onDraftMessageClick: () -> Unit, modifier: Modifier)` — signature UNCHANGED (the dashboard call site at `DashboardScreen.kt:963` stays as-is).

- [ ] **Step 1: Replace the card body**

Rewrite `SmartSectionCard.kt` to render the header (title/subtitle + quota chip) followed by a single full-width Draft Message action row, dropping the `LazyRow` and the two disabled tiles. Replace the whole file with:

```kotlin
package com.danzucker.stitchpad.feature.smart.presentation

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.danzucker.stitchpad.feature.smart.presentation.components.FreeTierCounterChip
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.smart_intent_draft_message_subtitle
import stitchpad.composeapp.generated.resources.smart_intent_draft_message_title
import stitchpad.composeapp.generated.resources.smart_section_subtitle
import stitchpad.composeapp.generated.resources.smart_section_title

/**
 * Always-on Dashboard section card. Hidden by the caller when the customer
 * list is empty (no dead-end taps).
 *
 * V2: a single Draft Message action row. The two "Coming soon" placeholder
 * tiles were removed so the card stops reading as a multi-feature hub when
 * it is really one action. Draft Message is the ONLY UI entry point to the
 * Smart draft feature, so this card must not be removed outright.
 */
@Composable
fun SmartSectionCard(
    remainingFreeQuota: Int?,
    onDraftMessageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.elevation1,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.smart_section_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.smart_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (remainingFreeQuota != null) {
                    FreeTierCounterChip(remaining = remainingFreeQuota)
                }
            }
            Spacer(Modifier.height(DesignTokens.space3))
            DraftMessageRow(onClick = onDraftMessageClick)
        }
    }
}

@Composable
private fun DraftMessageRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.smart_intent_draft_message_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(Res.string.smart_intent_draft_message_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun SmartSectionCardWithQuotaPreview() {
    StitchPadTheme {
        SmartSectionCard(remainingFreeQuota = 5, onDraftMessageClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun SmartSectionCardPremiumPreview() {
    StitchPadTheme {
        SmartSectionCard(remainingFreeQuota = null, onDraftMessageClick = {})
    }
}
```

- [ ] **Step 2: Android compile**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`.

- [ ] **Step 3: iOS compile (required gate)**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`.

- [ ] **Step 4: Detekt**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew detekt 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`. (The rewrite already omits the unused `LazyRow`/`PaddingValues`/`LocalOffer`/`Reply`/`IntentTile` and coming-soon string imports, so there should be no `UnusedImports` finding.)

- [ ] **Step 5: (Optional) prune the orphaned "coming soon" strings**

The rewrite stops referencing `smart_intent_price_this_title`, `smart_intent_reply_helper_title`, and `smart_intent_coming_soon_label`. Unused string resources do not fail the build or Detekt, but for tidiness delete those three `<string>` lines from `composeApp/src/commonMain/composeResources/values/strings.xml`. Before deleting, confirm they are unreferenced:

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
grep -rn "smart_intent_price_this_title\|smart_intent_reply_helper_title\|smart_intent_coming_soon_label" composeApp/src --include=*.kt | grep -v build
```

Expected: no matches → safe to delete the three lines. If anything matches, leave them.

- [ ] **Step 6: Commit**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/SmartSectionCard.kt composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "refactor(smart): slim Smart card to a single Draft Message row

Drop the two disabled 'Coming soon' tiles; keep the one working action.
The dashboard Smart card is the only UI entry to Draft Message, so the
card itself stays.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

**Manual smoke test:** dashboard in any populated state → Smart card shows one Draft Message row + the quota chip (when on free tier); tapping it opens the Draft Message screen; no "Coming soon" tiles remain.

---

## Task 3: Collapse Work Pipeline to a one-line summary

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineSummaryRow.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineSummaryRowTest.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/DashboardUiState.kt`
- Delete: `.../presentation/components/PipelineSection.kt`, `.../presentation/components/PipelineEmptyHeroCard.kt`

**Interfaces:**
- Produces:
  - `enum class PipelineSummarySegment { InProgress, NotStarted }`
  - `internal fun pipelineSummarySegments(inProgressTotal: Int, notStartedTotal: Int): List<PipelineSummarySegment>`
  - `@Composable fun PipelineSummaryRow(inProgressTotal: Int, notStartedTotal: Int, onClick: () -> Unit, modifier: Modifier = Modifier)`
- Consumes: `DashboardAction.OnViewAllOrdersClick` (existing; routes to the Orders tab via `DashboardEvent.NavigateToOrders`). `state.pipelineInProgressTotal`, `state.pipelinePendingTotal` (existing Int fields).

- [ ] **Step 1: Write the failing helper test**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineSummaryRowTest.kt`:

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineSummaryRowTest {

    @Test
    fun `both totals positive shows both segments in order`() {
        assertEquals(
            listOf(PipelineSummarySegment.InProgress, PipelineSummarySegment.NotStarted),
            pipelineSummarySegments(inProgressTotal = 3, notStartedTotal = 2),
        )
    }

    @Test
    fun `only in-progress shows only in-progress segment`() {
        assertEquals(
            listOf(PipelineSummarySegment.InProgress),
            pipelineSummarySegments(inProgressTotal = 4, notStartedTotal = 0),
        )
    }

    @Test
    fun `only not-started shows only not-started segment`() {
        assertEquals(
            listOf(PipelineSummarySegment.NotStarted),
            pipelineSummarySegments(inProgressTotal = 0, notStartedTotal = 5),
        )
    }

    @Test
    fun `both zero shows no segments`() {
        assertEquals(
            emptyList(),
            pipelineSummarySegments(inProgressTotal = 0, notStartedTotal = 0),
        )
    }
}
```

- [ ] **Step 2: Run the test — verify it fails to compile (helper not defined)**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest --tests "*PipelineSummaryRowTest*" 2>&1 | tail -25; echo "exit=${PIPESTATUS[0]}"
```

Expected: FAIL — unresolved reference `pipelineSummarySegments` / `PipelineSummarySegment`.

- [ ] **Step 3: Create the helper + composable**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineSummaryRow.kt`:

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_workshop_summary_cd
import stitchpad.composeapp.generated.resources.dashboard_workshop_summary_in_progress
import stitchpad.composeapp.generated.resources.dashboard_workshop_summary_not_started
import stitchpad.composeapp.generated.resources.dashboard_workshop_summary_title

/** Which count segments the workshop summary row shows. */
enum class PipelineSummarySegment { InProgress, NotStarted }

/**
 * Pure decision for the summary subtitle: include a segment only when its
 * count is non-zero, in-progress before not-started. The caller only renders
 * the row when the combined total is &gt; 0, so this returns a non-empty list
 * in practice.
 */
internal fun pipelineSummarySegments(
    inProgressTotal: Int,
    notStartedTotal: Int,
): List<PipelineSummarySegment> = buildList {
    if (inProgressTotal > 0) add(PipelineSummarySegment.InProgress)
    if (notStartedTotal > 0) add(PipelineSummarySegment.NotStarted)
}

/**
 * One-line replacement for the old two-bucket Work Pipeline section. Shows
 * the workshop counts and opens the Orders tab (the full, grouped order
 * book) on tap. Counts only — no order rows — so it can never double-render
 * an order already shown in an NBA card.
 */
@Composable
fun PipelineSummaryRow(
    inProgressTotal: Int,
    notStartedTotal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = pipelineSummarySegments(inProgressTotal, notStartedTotal)
        .joinToString(" · ") { segment ->
            when (segment) {
                PipelineSummarySegment.InProgress ->
                    stringResource(Res.string.dashboard_workshop_summary_in_progress, inProgressTotal)
                PipelineSummarySegment.NotStarted ->
                    stringResource(Res.string.dashboard_workshop_summary_not_started, notStartedTotal)
            }
        }
    val cd = stringResource(
        Res.string.dashboard_workshop_summary_cd,
        inProgressTotal,
        notStartedTotal,
    )
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd },
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.dashboard_workshop_summary_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineSummaryRowBothPreview() {
    StitchPadTheme {
        PipelineSummaryRow(inProgressTotal = 3, notStartedTotal = 2, onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PipelineSummaryRowInProgressOnlyPreview() {
    StitchPadTheme {
        PipelineSummaryRow(inProgressTotal = 4, notStartedTotal = 0, onClick = {})
    }
}
```

- [ ] **Step 4: Add the string resources**

In `composeApp/src/commonMain/composeResources/values/strings.xml`, add these four strings next to the existing `dashboard_pipeline_*` entries (around line 888). Note positional `%1$d` / `%2$d` only:

```xml
    <string name="dashboard_workshop_summary_title">In the workshop</string>
    <string name="dashboard_workshop_summary_in_progress">%1$d in progress</string>
    <string name="dashboard_workshop_summary_not_started">%1$d not started</string>
    <string name="dashboard_workshop_summary_cd">In the workshop: %1$d in progress, %2$d not started. Opens all orders.</string>
```

- [ ] **Step 5: Run the helper test — verify it passes**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest --tests "*PipelineSummaryRowTest*" 2>&1 | tail -25; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`, 4 tests pass.

- [ ] **Step 6: Rewire the dashboard to use the summary row**

In `DashboardScreen.kt`:

(a) Replace the `PipelineSection` import with the summary row + segment enum:

```kotlin
import com.danzucker.stitchpad.feature.dashboard.presentation.components.PipelineSummaryRow
```

(remove `import ...components.PipelineSection`).

(b) Delete the `PIPELINE_EMPTY_HIDDEN_STATES` constant and its KDoc block (currently ~lines 241–254).

(c) Replace the §6 Work-pipeline block (currently ~lines 972–999, from the `// 6. Work pipeline` comment through the closing `}` of the `if (firstOrderSetup == null && !hidePipelineWhenEmpty) { PipelineSection(...) }`) with:

```kotlin
        // 6. Workshop summary — one-line count of active in-progress /
        //    not-started orders, opening the Orders tab (the full, grouped
        //    order book). Counts only, so it never double-renders an order
        //    already shown in an NBA card. Hidden during first-order
        //    onboarding (the setup card covers that single order) and when
        //    there is no active workshop work.
        val workshopTotal = state.pipelineInProgressTotal + state.pipelinePendingTotal
        if (firstOrderSetup == null && workshopTotal > 0) {
            PipelineSummaryRow(
                inProgressTotal = state.pipelineInProgressTotal,
                notStartedTotal = state.pipelinePendingTotal,
                onClick = { onAction(DashboardAction.OnViewAllOrdersClick) },
            )
        }
```

- [ ] **Step 7: Delete the dead Pipeline composables**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
git rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineSection.kt \
       composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/PipelineEmptyHeroCard.kt
```

- [ ] **Step 8: Update stale KDoc references**

In `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/model/DashboardUiState.kt`, replace each doc-comment mention of `PipelineSection` (lines ~49, ~57, ~66, ~75) with `pipeline summary row`. These are comments only — no code behaviour changes.

- [ ] **Step 9: Detekt (catches any leftover unused import, e.g. `EmptyIllustrationCard`/`EmptyIllustrationSlot`/`EmptyCardCtaStyle` if the empty-NBA branch still uses them — it does, so keep those)**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew detekt 2>&1 | tail -30; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`. If Detekt flags `dashboard_pipeline_in_progress_v2` / `dashboard_pipeline_pending_v2` / `dashboard_pipeline_empty_*` / `dashboard_section_pipeline` string imports as unused *in `DashboardScreen.kt`*, remove them (they were only referenced by the deleted `PipelineSection`). Do not delete the `<string>` entries themselves in this task.

- [ ] **Step 10: Android compile**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`.

- [ ] **Step 11: iOS compile + iOS test compile (required gate)**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 2>&1 | tail -20; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0` (the new test uses backtick names with letters/spaces/hyphens only — compiles on iOS).

- [ ] **Step 12: Full dashboard test suite (no regressions in calculators/VM)**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest --tests "*dashboard*" 2>&1 | tail -25; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`, all green (`BucketCalculatorTest`, `NbaCalculatorTest`, `DashboardViewModelTest`, etc. unchanged).

- [ ] **Step 13: Commit**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
git add -A
git commit -m "refactor(dashboard): collapse Work Pipeline to a workshop summary row

Replace the two-bucket pipeline section with a single count row that
opens the Orders tab. Kills the NBA/Pipeline double-render and reclaims
the tallest low-signal block. BucketCalculator + state fields unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

**Manual smoke test (per state):**
- `PipelineSteady`: summary row shows "In the workshop · N in progress · M not started →"; tap opens the Orders tab.
- `QuietDay` (no active work): summary row absent (total 0).
- `BusyDay` / `NbaActive` with active workshop work: summary row present, and no order appears both in an NBA card and as a pipeline row (rows are gone).
- First-order onboarding (`FirstCustomer` with an order): summary row absent; the setup card still drives that order.
- Brand-new: no pipeline empty hero; onboarding steps card still offers order creation.

---

## Final verification (after all three tasks)

- [ ] **Full build + detekt + tests green**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 detekt :composeApp:testDebugUnitTest 2>&1 | tail -30; echo "exit=${PIPESTATUS[0]}"
```

Expected: `exit=0`.

- [ ] **Success criteria (from the spec) confirmed by smoke test**
  - Populated-day dashboard drops from ~8 stacked blocks to ~5.
  - Pipeline is now one line.
  - No action lost its entry point (Inspiration: header + FAB; Measurements: FAB; Draft Message: Smart row).
  - No order renders twice on the same screen.

- [ ] **Open PR** (per repo workflow: feature branch + PR + CI; Cursor + `codex review` on non-trivial diffs). Include the per-state smoke-test checklist above in the PR description.

---

## Spec coverage check

| Spec change | Task |
|-------------|------|
| A — Remove Quick access section | Task 1 |
| B — Slim Smart card to Draft Message row (keep only entry point) | Task 2 |
| C — Collapse Pipeline to two-count summary opening Orders tab | Task 3 |
| D — Keep Today's Work unchanged | (no task — untouched by design) |
| Leave `BucketCalculator` + state fields in place | Task 3 keeps them; only rendering changes |
| Out of scope: banners, header-icon trim, `PipelineSteady`/`QuietDay` merge, NBA ordering | Not touched |
