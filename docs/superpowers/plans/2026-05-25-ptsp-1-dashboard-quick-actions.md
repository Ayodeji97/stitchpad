# PTSP-1 — Dashboard Quick Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface both *New Customer* and *New Order* on the StitchPad
dashboard via two redundant entry points: an inline *Quick actions* row
and a Material 3-style speed-dial FAB.

**Architecture:** The redundancy is intentional. The inline row gives
discoverability above the fold; the speed-dial FAB gives thumb-reach
when the user has scrolled. Both reuse the existing
`DashboardAction.OnNewCustomerClick` and `OnNewOrderClick` actions
already wired in `DashboardViewModel` — no new actions, no nav rewire.
The speed-dial component is built without M3's `FloatingActionButtonMenu`
(not available in `material3` 1.11.0-alpha07) using a primary FAB +
animated mini-FABs + a Compose-managed scrim, all owned by
`DashboardScreen` (Compose-internal UI state).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3
1.11.0-alpha07, MVI.

**Spec:** `docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md` §4.
(Spec doc lives on `feature/ptsp-2-remove-delivery-filter` until PR #74
merges — view it there or on PR #74 in the meantime.)

**Branch:** `feature/ptsp-1-dashboard-quick-actions` (already checked out).
Off latest `main` (after the 11e4b8f CI/version-bump batch).

---

## Shipping deviation (recorded 2026-05-25, post-smoke)

During manual smoke testing, Daniel determined that the inline
`QuickActionsRow` was redundant with the speed-dial FAB — the dashboard
already exposes both "Add customer" and "Add order" via the speed-dial,
and the inline row was eating vertical space above the pipeline without
adding value. The brainstorm spec's "intentional redundancy (discovery +
thumb-reach)" framing didn't hold up once both surfaces were visible in
the running app.

**Reverted:**
- **Task 2** (Create `QuickActionsRow`) — file `QuickActionsRow.kt` was
  built then deleted before commit. Never landed.
- **Task 5** (Wire `QuickActionsRow` into `DashboardContent`) — call
  site was inserted then removed before commit. Never landed.
- The `dashboard_quick_actions_title` string was added then removed.
  The `dashboard_quick_action_customer` / `dashboard_quick_action_order`
  strings stayed (still used by the speed-dial mini-FAB labels).

**Shipped:** Only the speed-dial FAB (Task 1) + its wiring (Task 4) +
the 5 still-used strings (Task 3 minus one). Tasks 6 and 7 still apply.

**Why this matters for future readers:** the spec at
`docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md` §4.2 still
describes the inline row. If you're updating that spec post-merge, drop
§4.2 and tighten §4.1 to "one entry point: speed-dial FAB" — that's the
canonical shipped state.

---

## File Map

| File                                                                                                       | Change                                                                                          |
|------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadSpeedDialFab.kt`          | **New.** Reusable speed-dial component. Primary FAB + 2 mini-FABs + rotation + scrim hook.       |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/QuickActionsRow.kt` | **New.** Inline section: title + two chip-style action surfaces.                                 |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` | Replace single `StitchPadFab` with the speed-dial cluster (FAB + scrim + mini-FABs overlay). Insert `QuickActionsRow` in `DashboardContent` right after `IllustratedFocusCard`. |
| `composeApp/src/commonMain/composeResources/values/strings.xml`                                            | Add 6 string keys (see Task 5).                                                                 |

**No ViewModel changes. No new actions/events. No tests to write** — both new composables are Compose-internal UI state, and the verification path is `@Preview` + manual smoke. The existing `DashboardViewModelTest` already covers `OnNewCustomerClick` and `OnNewOrderClick`.

---

### Task 1: Create `StitchPadSpeedDialFab`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadSpeedDialFab.kt`

**Design notes:**
- The component owns the FAB stack. **It does not own the scrim** — the scrim is rendered by `DashboardScreen` as a sibling overlay so it covers the entire screen (the Scaffold's `floatingActionButton` slot only renders a small bottom-end area).
- Expansion state is **lifted to the caller** so `DashboardScreen` can render the scrim conditional on the same flag.
- Mini-FABs use `RoundedCornerShape(16.dp)` to match `StitchPadFab` (per saved memory `feedback_fab_shape`).
- Mini-FAB labels render in a `Surface` pill to the LEFT of each mini-FAB for parity with Material's speed-dial pattern.
- Animation: `AnimatedVisibility` with `slideInVertically(initialOffsetY = { it / 2 }) + fadeIn` for entry; reverse for exit. Stagger by 30ms so the top mini-FAB lands second (subtle but feels intentional).
- Main FAB icon rotates 45° to morph `Add` (`+`) into a close-style `×`. Use `animateFloatAsState` for the rotation.

- [ ] **Step 1: Create the file with the data class and skeleton**

Write `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadSpeedDialFab.kt`:

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val FAB_SHADOW_ELEVATION = 12.dp
private val FAB_CORNER_RADIUS = 16.dp
private val MINI_FAB_SIZE = 48.dp
private const val ROTATION_DURATION_MS = 200
private const val MINI_FAB_STAGGER_MS = 30

/**
 * One action surfaced by [StitchPadSpeedDialFab]. Order in the
 * `actions` list = bottom-up on screen (first action sits closest to
 * the main FAB).
 */
data class SpeedDialAction(
    val label: String,
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Speed-dial floating action button. The main FAB toggles expansion;
 * each mini-FAB renders with a pill-label to its left.
 *
 * Expansion state is **lifted** so the caller can render a backdrop
 * scrim covering the screen — the Scaffold's `floatingActionButton`
 * slot only covers the FAB's own bounds.
 *
 * Mini-FAB taps invoke their `onClick` and DO NOT auto-collapse —
 * collapse is the caller's responsibility (typically by setting
 * `isExpanded = false` inside the click lambda passed in).
 */
@Composable
fun StitchPadSpeedDialFab(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    actions: List<SpeedDialAction>,
    closeContentDescription: String,
    addContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(FAB_CORNER_RADIUS)
    val targetRotation = if (isExpanded) 45f else 0f
    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = ROTATION_DURATION_MS),
        label = "speed_dial_rotation",
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = modifier,
    ) {
        actions.forEachIndexed { index, action ->
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = ROTATION_DURATION_MS,
                        delayMillis = index * MINI_FAB_STAGGER_MS,
                    ),
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = ROTATION_DURATION_MS,
                        delayMillis = index * MINI_FAB_STAGGER_MS,
                    ),
                    initialOffsetY = { it / 2 },
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = ROTATION_DURATION_MS)) +
                    slideOutVertically(
                        animationSpec = tween(durationMillis = ROTATION_DURATION_MS),
                        targetOffsetY = { it / 2 },
                    ),
            ) {
                MiniFabRow(action = action, shape = shape)
            }
        }

        FloatingActionButton(
            onClick = onToggle,
            shape = shape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.shadow(
                elevation = FAB_SHADOW_ELEVATION,
                shape = shape,
                spotColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isExpanded) closeContentDescription else addContentDescription,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun MiniFabRow(
    action: SpeedDialAction,
    shape: RoundedCornerShape,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(DesignTokens.radiusSm),
            shadowElevation = 2.dp,
        ) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space3,
                    vertical = DesignTokens.space2,
                ),
            )
        }
        FloatingActionButton(
            onClick = action.onClick,
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(MINI_FAB_SIZE)
                .shadow(elevation = 6.dp, shape = shape),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.contentDescription,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadSpeedDialFabCollapsedPreview() {
    StitchPadTheme {
        StitchPadSpeedDialFab(
            isExpanded = false,
            onToggle = {},
            actions = emptyList(),
            closeContentDescription = "Close",
            addContentDescription = "Add",
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StitchPadSpeedDialFabExpandedPreview() {
    StitchPadTheme {
        StitchPadSpeedDialFab(
            isExpanded = true,
            onToggle = {},
            actions = listOf(
                SpeedDialAction(
                    label = "Add customer",
                    icon = androidx.compose.material.icons.Icons.Default.Person,
                    contentDescription = "Add a new customer",
                    onClick = {},
                ),
                SpeedDialAction(
                    label = "Add order",
                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = "Add a new order",
                    onClick = {},
                ),
            ),
            closeContentDescription = "Close",
            addContentDescription = "Add",
        )
    }
}
```

**Note on icons in the preview**: `Icons.Default.Person` and `Icons.AutoMirrored.Filled.Assignment` are M3 stock icons. If either isn't on the Material Icons set bundled with this project's `compose-multiplatform`, fall back to `Icons.Default.Add` for both — the preview only needs to render.

- [ ] **Step 2: Add the missing `androidx.compose.foundation.layout.size` import**

The `MiniFabRow` uses `Modifier.size(MINI_FAB_SIZE)`. The import is:

```kotlin
import androidx.compose.foundation.layout.size
```

Add it to the imports block alphabetically (after `padding`).

- [ ] **Step 3: Compile Android**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. If anything fails, the most likely cause is an icon import — see the icon fallback note above.

- [ ] **Step 4: Open the file's previews in Android Studio**

Open `StitchPadSpeedDialFab.kt` and split-pane the preview gutter. Both `StitchPadSpeedDialFabCollapsedPreview` and `StitchPadSpeedDialFabExpandedPreview` should render. Collapsed shows the main FAB only. Expanded shows two pill-labelled mini-FABs above the main FAB, with the FAB's `+` icon rotated 45°.

If the previews don't render in your IDE: that's OK as long as `compileDebugKotlinAndroid` passed. Composable correctness will be verified by manual smoke later.

- [ ] **Step 5: Do not commit yet**

---

### Task 2: Create `QuickActionsRow`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/QuickActionsRow.kt`

**Design notes:**
- Section title in `labelSmall` uppercase to match adjacent dashboard sections (e.g. the pipeline section title).
- Two equal-width tappable `Surface`s arranged in a `Row` with `Arrangement.spacedBy(DesignTokens.space3)`.
- Each surface: leading icon (28dp) + label (`bodyLarge`, SemiBold).
- Use `MaterialTheme.colorScheme.surfaceVariant` for the surface background and `primary` for the icon tint to match the rest of the dashboard's chip language.

- [ ] **Step 1: Write the file**

```kotlin
package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.dashboard_quick_action_customer
import stitchpad.composeapp.generated.resources.dashboard_quick_action_order
import stitchpad.composeapp.generated.resources.dashboard_quick_actions_title

@Composable
fun QuickActionsRow(
    onAddCustomerClick: () -> Unit,
    onAddOrderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.dashboard_quick_actions_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            QuickActionChip(
                icon = Icons.Default.Person,
                label = stringResource(Res.string.dashboard_quick_action_customer),
                onClick = onAddCustomerClick,
                modifier = Modifier.weight(1f),
            )
            QuickActionChip(
                icon = Icons.Default.Add,
                label = stringResource(Res.string.dashboard_quick_action_order),
                onClick = onAddOrderClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            modifier = Modifier.padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space3,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun QuickActionsRowPreview() {
    StitchPadTheme {
        QuickActionsRow(onAddCustomerClick = {}, onAddOrderClick = {})
    }
}
```

If `Icons.Default.Person` isn't bundled, fall back to `Icons.Default.PersonAdd`; if that also isn't available, fall back to `Icons.Default.Add` for both icons and adjust the visual later. The component contract doesn't care about the specific icon.

- [ ] **Step 2: This file references string resources we haven't added yet.**

The compile will FAIL right now with `unresolved reference: dashboard_quick_actions_title`. Don't compile — proceed to Task 5 first to add the strings, then circle back.

(We add the composables before the strings so the IDE can autocomplete keys, but the build won't go green until strings exist.)

- [ ] **Step 3: Do not commit yet**

---

### Task 3: Add the string resources

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Locate the dashboard string block**

Open `strings.xml`. Find the existing dashboard-related strings (search for `dashboard_fab_cd` — it's around line 280 in the post-PR-#74 state, slightly different here because PR #74 is not yet merged on `main`). Add the new strings right after the existing dashboard block.

- [ ] **Step 2: Insert the 6 new keys**

Add the following entries:

```xml
    <!-- Dashboard quick actions (PTSP-1) -->
    <string name="dashboard_quick_actions_title">Quick actions</string>
    <string name="dashboard_quick_action_customer">Add customer</string>
    <string name="dashboard_quick_action_order">Add order</string>
    <string name="dashboard_fab_new_customer_cd">Add a new customer</string>
    <string name="dashboard_fab_new_order_cd">Add a new order</string>
    <string name="dashboard_fab_close_cd">Close quick actions</string>
```

Place under the section comment if there is one, or as its own block with the header comment shown above.

- [ ] **Step 3: Compile to regenerate resources**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. The Compose Multiplatform resource generator regenerates `Res.string.dashboard_quick_actions_title` (etc.) and the previously-failing `QuickActionsRow` reference now resolves.

If `QuickActionsRow.kt` still has unresolved references, the most likely cause is the resource generator not picking up the new keys — clean and re-run:

```bash
./gradlew clean :composeApp:compileDebugKotlinAndroid
```

- [ ] **Step 4: Do not commit yet**

---

### Task 4: Wire the speed-dial + scrim into `DashboardScreen`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`

**Design notes:**
- Lift the `isExpanded` state to `DashboardScreen` using `rememberSaveable` so process death doesn't reopen the speed-dial in the expanded state (a stale snapshot of an action sheet is jarring).
- Wrap the Scaffold in a `Box` so we can layer the scrim + speed-dial cluster above it.
- The Scaffold's `floatingActionButton` slot becomes empty when `showFab` is false (BrandNew / Loading) — same gate as today.
- Add `Icons.Default.Person` import for the customer mini-FAB icon (or whatever icon Task 1 settled on for the customer preview).

- [ ] **Step 1: Add the new imports**

Open `DashboardScreen.kt` and add these imports (alphabetical placement):

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.danzucker.stitchpad.ui.components.SpeedDialAction
import com.danzucker.stitchpad.ui.components.StitchPadSpeedDialFab
import stitchpad.composeapp.generated.resources.dashboard_fab_close_cd
import stitchpad.composeapp.generated.resources.dashboard_fab_new_customer_cd
import stitchpad.composeapp.generated.resources.dashboard_fab_new_order_cd
```

(Some of these may already exist. Skip duplicates. `Icons` and `Color` are likely already imported — verify with the IDE auto-import.)

- [ ] **Step 2: Restructure the `DashboardScreen` body**

Find the existing `DashboardScreen` composable (around line 390). Replace its body:

OLD:
```kotlin
@Composable
fun DashboardScreen(
    state: DashboardState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (DashboardAction) -> Unit
) {
    // FAB is hidden during the brand-new and loading states. Brand-new has no orders and the
    // Order form requires an existing customer; surfacing the FAB there would route the user
    // into a dead end. Loading is suppressed too so the FAB doesn't briefly flash before the
    // first state emission resolves.
    val showFab = state.uiState != DashboardUiState.BrandNew &&
        state.uiState != DashboardUiState.Loading
    Scaffold(
        floatingActionButton = {
            if (showFab) {
                StitchPadFab(
                    onClick = { onAction(DashboardAction.OnNewOrderClick) },
                    contentDescription = stringResource(Res.string.dashboard_fab_cd)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // ... content lambda unchanged ...
    }
}
```

NEW:
```kotlin
@Composable
fun DashboardScreen(
    state: DashboardState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (DashboardAction) -> Unit
) {
    // FAB is hidden during the brand-new and loading states. Brand-new has no orders and the
    // Order form requires an existing customer; surfacing the FAB there would route the user
    // into a dead end. Loading is suppressed too so the FAB doesn't briefly flash before the
    // first state emission resolves.
    val showFab = state.uiState != DashboardUiState.BrandNew &&
        state.uiState != DashboardUiState.Loading
    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    val collapseFab: () -> Unit = { isFabExpanded = false }

    val speedDialActions = listOf(
        SpeedDialAction(
            label = stringResource(Res.string.dashboard_quick_action_customer),
            icon = Icons.Default.Person,
            contentDescription = stringResource(Res.string.dashboard_fab_new_customer_cd),
            onClick = {
                collapseFab()
                onAction(DashboardAction.OnNewCustomerClick)
            },
        ),
        SpeedDialAction(
            label = stringResource(Res.string.dashboard_quick_action_order),
            icon = Icons.Default.Add,
            contentDescription = stringResource(Res.string.dashboard_fab_new_order_cd),
            onClick = {
                collapseFab()
                onAction(DashboardAction.OnNewOrderClick)
            },
        ),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            floatingActionButton = {
                if (showFab) {
                    StitchPadSpeedDialFab(
                        isExpanded = isFabExpanded,
                        onToggle = { isFabExpanded = !isFabExpanded },
                        actions = speedDialActions,
                        closeContentDescription = stringResource(Res.string.dashboard_fab_close_cd),
                        addContentDescription = stringResource(Res.string.dashboard_fab_cd),
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            when (state.uiState) {
                DashboardUiState.Loading -> LoadingState(modifier = contentModifier)
                DashboardUiState.BrandNew,
                DashboardUiState.FirstCustomer,
                DashboardUiState.QuietDay,
                DashboardUiState.PipelineSteady,
                DashboardUiState.NbaActive,
                DashboardUiState.BusyDay,
                DashboardUiState.ReadyForPickup -> DashboardContent(
                    state = state,
                    onAction = onAction,
                    modifier = contentModifier,
                    bottomPadding = if (showFab) FAB_BOTTOM_PADDING else NO_FAB_BOTTOM_PADDING,
                )
            }
        }

        // Backdrop scrim — covers the entire screen UNDER the speed-dial cluster
        // when expanded. Tap dismisses. Renders only when expanded and only when
        // the FAB is shown (defensive: should be unreachable otherwise).
        if (isFabExpanded && showFab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = collapseFab),
            )
        }
    }
}
```

Three structural changes vs. OLD:
1. `Scaffold` is wrapped in a `Box(fillMaxSize)`.
2. The `StitchPadFab` call inside `floatingActionButton` is replaced with `StitchPadSpeedDialFab` carrying the new state + actions.
3. A scrim `Box` is rendered as a sibling of the `Scaffold` inside the outer `Box`, gated on `isFabExpanded && showFab`.

The scrim renders **below** the Scaffold's FAB in z-order because the Scaffold is drawn last. But the FAB is small and bottom-aligned, so the scrim covers everything else. The FAB stays interactive on top of the scrim. The user can tap the FAB (rotated `×` icon) to close, OR tap the scrim, OR tap a mini-FAB.

**Important:** The scrim is `Color.Black.copy(alpha = 0.4f)`. If the dark theme renders this too aggressively, switch to `MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)`. Both behaviors are acceptable; the M3-native one is technically more correct.

- [ ] **Step 3: Compile Android**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Do not commit yet**

---

### Task 5: Wire `QuickActionsRow` into `DashboardContent`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`

**Design notes:**
- Render the row IMMEDIATELY AFTER `IllustratedFocusCard` (or after the `DashboardHeader` if no focus card).
- Render in EVERY state where the speed-dial FAB is shown — i.e. NOT `BrandNew` (covered by `OnboardingStepsCard`) and NOT `Loading`.
- Use the same `state.uiState` gate as the FAB. The existing `showFab` is a `DashboardScreen`-local variable; we need to compute the same gate in `DashboardContent`.

- [ ] **Step 1: Add the import**

In `DashboardScreen.kt`:

```kotlin
import com.danzucker.stitchpad.feature.dashboard.presentation.components.QuickActionsRow
```

- [ ] **Step 2: Insert the call inside `DashboardContent`**

Find the section block right after `IllustratedFocusCard` (around lines 487–498 of the current file). The current structure:

```kotlin
        // 2. Illustrated focus card (null headline means no card to show)
        val focusTitle = state.focusHeadline?.asString()
        if (focusTitle != null) {
            IllustratedFocusCard(
                variant = state.focusVariant,
                title = focusTitle,
                supporting = state.focusSupporting?.asString(),
                ctaLabel = state.focusCtaLabel?.asString(),
                ctaSubtitle = state.focusCtaSubtitle?.asString(),
                sectionLabel = state.focusSectionLabel?.asString(),
                onClick = { onAction(DashboardAction.OnFocusCtaClick) },
            )
        }

        // BrandNew adds a 3-step onboarding tile grid above the standard
        // sections. ...
        if (state.uiState == DashboardUiState.BrandNew) {
            OnboardingStepsCard(...)
        }
```

Insert the Quick actions row between these two blocks:

```kotlin
        // 2. Illustrated focus card (null headline means no card to show)
        val focusTitle = state.focusHeadline?.asString()
        if (focusTitle != null) {
            IllustratedFocusCard(...)
        }

        // 2b. Quick actions — inline twin of the speed-dial FAB. Always-visible
        //     discovery for "New customer" / "New order". Hidden on BrandNew
        //     (OnboardingStepsCard covers that journey) and Loading.
        if (state.uiState != DashboardUiState.BrandNew &&
            state.uiState != DashboardUiState.Loading
        ) {
            QuickActionsRow(
                onAddCustomerClick = { onAction(DashboardAction.OnNewCustomerClick) },
                onAddOrderClick = { onAction(DashboardAction.OnNewOrderClick) },
            )
        }

        // BrandNew adds a 3-step onboarding tile grid above the standard
        // sections. ...
        if (state.uiState == DashboardUiState.BrandNew) {
            OnboardingStepsCard(...)
        }
```

Don't expand the `...` — leave the existing call literals untouched. The diff only adds the `// 2b. Quick actions` comment block and the `if (...) { QuickActionsRow(...) }` block.

- [ ] **Step 3: Compile Android**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Do not commit yet**

---

### Task 6: Full verification

**Files:**
- Verify only (no edits).

- [ ] **Step 1: Android compile**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: iOS compile (per `feedback_kmp_jvm_only_apis`)**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: detekt**

```bash
./gradlew detekt
```

Expected: BUILD SUCCESSFUL. If a `LongMethod` finding fires on the new `DashboardScreen` body, factor the `SpeedDialAction` list construction out into a private `@Composable` helper called `rememberDashboardSpeedDialActions(...)` and call it once. (Don't pre-emptively refactor; only do this if detekt complains.)

- [ ] **Step 4: Tests**

```bash
./gradlew :composeApp:allTests
```

Expected: existing tests pass. The iOS `linkDebugTestIosSimulatorArm64` task may fail with `ld: framework 'FirebaseCore' not found` — this is a **pre-existing** issue documented in PR #74. It is not caused by this PR. Confirm by checking the failure message matches the FirebaseCore symptom; if a different failure surfaces, investigate.

---

### Task 7: Manual smoke test (Daniel)

**Pre-req:** Android emulator or device running the debug build of this branch.

- [ ] **Step 1: Install**

```bash
./gradlew :composeApp:installDebug
```

- [ ] **Step 2: Sign in with Fola (per `reference_test_environment`)**

Expected `uiState` will be `PipelineSteady` or similar (Fola has customers + orders).

- [ ] **Step 3: Verify Quick actions row**

Above the pipeline section, below the focus card (or below the header if no focus card), find a "QUICK ACTIONS" label with two chips:
- "Add customer" with a person icon
- "Add order" with a plus icon

Both chips are tappable; tapping either routes to the existing form (`CustomerFormScreen` or `OrderFormScreen` respectively).

- [ ] **Step 4: Verify speed-dial FAB**

Scroll down to confirm the FAB is bottom-right with the StitchPad orange `+` icon.

Tap the FAB:
- Icon rotates 45° (becomes `×`-style)
- A scrim dims the rest of the screen
- Two pill-labelled mini-FABs fan out above the main FAB
- Mini-FABs are in order (bottom-up): "Add customer", then "Add order"

Tap the "Add customer" mini-FAB:
- Speed-dial collapses
- Customer form opens

Repeat for "Add order" → order form opens.

Tap the main FAB while expanded:
- Speed-dial collapses (icon rotates back to `+`)

Tap on the scrim (anywhere not on the FAB or mini-FABs):
- Speed-dial collapses

- [ ] **Step 5: BrandNew state check**

Wipe app data on the device, sign up a fresh account, complete workshop setup. Dashboard should render `BrandNew` with the `OnboardingStepsCard` visible.

Expected:
- **No** Quick actions row visible (BrandNew is gated out)
- **No** FAB visible (existing behavior — BrandNew has no FAB)

- [ ] **Step 6: Loading state check**

Hard-quit the app and reopen. While the dashboard's initial flow emission is pending:

Expected:
- LoadingDots visible
- **No** FAB visible
- **No** Quick actions row visible (Loading is gated out)

- [ ] **Step 7: Locked-customer parity (per spec §4.6)**

Sign in as an account whose customer count exceeds the freemium slot cap (so it has locked customers). Open the dashboard:

Expected:
- Quick actions row renders unchanged
- Speed-dial FAB opens / collapses / routes exactly as for Fola
- Tapping "Add customer" from the locked-customer account → the form opens (whether the *save* succeeds is the existing freemium guardrail's problem, not this PR's; just confirm the form opens without crash)

- [ ] **Step 8: Layout shift sanity check**

Compare the position of the pipeline section before/after this PR. The Quick actions row adds ~80dp of vertical space above the pipeline. The dashboard should still scroll cleanly; the focus card should still be the visual hero above the Quick actions row.

---

### Task 8: Commit and open PR

- [ ] **Step 1: Stage**

```bash
git add \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StitchPadSpeedDialFab.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/QuickActionsRow.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt \
  composeApp/src/commonMain/composeResources/values/strings.xml \
  docs/superpowers/plans/2026-05-25-ptsp-1-dashboard-quick-actions.md
```

- [ ] **Step 2: Verify staged diff**

```bash
git diff --cached --stat
git diff --cached
```

Expected: 5 files changed. Two new files (`StitchPadSpeedDialFab.kt`, `QuickActionsRow.kt`, plan doc) + two modified (`DashboardScreen.kt`, `strings.xml`). No surprise touches.

- [ ] **Step 3: Commit**

Two commits — first the plan doc, then the feature, matching the pattern used on PR #74.

```bash
git reset HEAD -- docs/superpowers/plans/2026-05-25-ptsp-1-dashboard-quick-actions.md
git add docs/superpowers/plans/2026-05-25-ptsp-1-dashboard-quick-actions.md
git commit -m "$(cat <<'EOF'
docs(plans): PTSP-1 implementation plan (dashboard quick actions)

Task-by-task plan for the second QA-batch PR. Ships alongside the
implementation in this branch.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Then the feature commit:

```bash
git commit -m "$(cat <<'EOF'
feat(dashboard): quick actions row + speed-dial FAB (PTSP-1)

Adds two redundant entry points for "New Customer" and "New Order"
on the dashboard:
- Inline Quick actions row immediately below the focus card,
  always visible for discoverability
- Speed-dial FAB that expands to two mini-FABs (Customer, Order)
  with a scrim backdrop for thumb-reach when scrolled

Reuses existing OnNewCustomerClick / OnNewOrderClick actions; no
ViewModel changes. Hidden on Loading and BrandNew (the latter
already shows OnboardingStepsCard with these actions).

Spec: docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md §4.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 4: Push**

```bash
git push -u origin feature/ptsp-1-dashboard-quick-actions
```

(Pre-push hook will run `codex review` automatically — same as PR #74.)

- [ ] **Step 5: Open PR**

Pause here. Ask Daniel before running `gh pr create` (PR creation is a user-visible action). When approved, run:

```bash
gh pr create --title "feat(dashboard): quick actions row + speed-dial FAB (PTSP-1)" --body "$(cat <<'EOF'
## Summary

- Adds an inline Quick actions row to the dashboard with two tappable chips: Add customer / Add order.
- Replaces the single FAB with a Material 3-style speed-dial that expands to two labelled mini-FABs over a scrim backdrop.
- Both surfaces reuse the existing `OnNewCustomerClick` / `OnNewOrderClick` actions — no ViewModel changes.
- Hidden on `Loading` and `BrandNew` (the latter still shows `OnboardingStepsCard` which covers these journeys).

Spec: `docs/superpowers/specs/2026-05-25-qa-cleanup-batch-design.md` §4.

This is the second of three QA-batch PRs. PTSP-2 (delivery filter removal) is #74; PTSP-15 (customer row actions sheet) follows on its own branch.

## Test plan

- [x] `./gradlew :composeApp:compileDebugKotlinAndroid` ✅
- [x] `./gradlew :composeApp:compileKotlinIosSimulatorArm64` ✅
- [x] `./gradlew detekt` ✅
- [x] `./gradlew :composeApp:allTests` — Android passes; iOS link fails on missing FirebaseCore (pre-existing, same as PR #74).
- [x] Manual smoke (Android, Fola): Quick actions row visible above pipeline; speed-dial expands, both mini-FABs route correctly; scrim dismisses; main FAB collapses.
- [x] Manual smoke (Android, fresh account): BrandNew dashboard has OnboardingStepsCard, no Quick actions row, no FAB.
- [x] Manual smoke (Android, app cold start): Loading state has no FAB and no Quick actions row.
- [ ] Pre-push `codex review` (automatic on push)
- [ ] Cursor review

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of scope (already noted in spec)

- New analytics events for the Quick actions taps.
- Changes to `OnNewCustomerClick` / `OnNewOrderClick` routing.
- Locked-customer behavior (speed-dial behaves identically on locked accounts).
- Tablet / landscape-specific layouts.
- Adding a third speed-dial action (the `SpeedDialAction` list is data-driven and ready, but nothing surfaces a third action today).
