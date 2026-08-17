# Testing Round Aug-16 Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the seven fixes/improvements Daniel collected during the 2026-08-16 manual testing round: finish the staff-role-change redirect (routing demoted staff into Workshop Setup so the launch Atelier grant fires), trailing invite-code dash, Settings "Join a workshop" entry, 3-tile staff dashboard, reversible stage advance (undo snackbar + stage sheet), stage dots on Orders rows, and "Hide amounts".

**Architecture:** Two PR trains. Tasks 1–2 finish the in-flight `feat/staff-role-change-redirect` branch (its uncommitted WIP is in THIS checkout — do NOT use a worktree for them). Tasks 3–10 build a UI fix-round branch stacked on it. Every task follows MVI (State/Action/Event + ViewModel), Compose Multiplatform, TDD with commonTest.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin, GitLive Firebase SDK, compose.resources strings, detekt.

## Global Constraints

- Never hardcode user-facing strings — every new string goes in `composeApp/src/commonMain/composeResources/values/strings.xml`.
- All state lives in ViewModels; no business logic in composables.
- Tests: `./gradlew :composeApp:testDebugUnitTest` (NOT `allTests` — the iOS simulator test link is a known pre-existing local failure: FirebaseCore framework absent on this Mac, CI covers iOS). Detekt: `./gradlew detekt`. Both must pass before every commit.
- Saffron (`DesignTokens.saffron500`) marks ONLY the current stage — never text, never other accents.
- Tasks 1–2 run in the existing checkout on branch `feat/staff-role-change-redirect` (uncommitted WIP lives there). Task 3 creates `feat/testing-round-aug16` stacked on it; Tasks 4–10 run there.
- Do NOT commit these untracked files (other sessions' work): `.maestro/android/`, `docs/staff/founding-tailors-emulator-smoke-runbook.md`, `docs/superpowers/plans/2026-08-04-founding-tailors-tiered-points.md`, `docs/superpowers/plans/pr-360-review-comment.md`, `functions/scripts/foundingTailorsReconcileChainSmoke.js`, `preview/staff-dashboard-topcards-redesign.html`.

---

### Task 1: Commit the in-flight staff-role-change-redirect work

The working tree already contains a complete, device-tested implementation (session resolver demotes REVOKED staff, `StaffRoleChangeRedirectEffect` redirects Home, tests in `StaffRoleChangeRedirectTest`). It just needs verifying and committing.

**Files:**
- Commit (already modified, do not edit): `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/session/FirebaseActiveWorkshopProvider.kt`, `core/domain/session/WorkshopSessionResolver.kt`, `di/CoreModule.kt`, `feature/staff/presentation/pending/StaffPendingViewModel.kt`, `feature/staff/presentation/redeem/RedeemInviteViewModel.kt`, `navigation/NavGraph.kt`, plus the three modified test files and new `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/navigation/StaffRoleChangeRedirectTest.kt`

**Interfaces:**
- Produces: `shouldRedirectHomeForStaffSessionChange(previous: WorkshopSession, current: WorkshopSession): Boolean` (internal, `NavGraph.kt`) and `StaffRoleChangeRedirectEffect` — Task 2 modifies both.

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS (the WIP ships with green tests). If anything fails, STOP and report — do not fix forward.

- [ ] **Step 2: Run detekt**

Run: `./gradlew detekt`
Expected: PASS

- [ ] **Step 3: Commit exactly the WIP files**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/session/FirebaseActiveWorkshopProvider.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/session/WorkshopSessionResolver.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/staff/presentation/pending/StaffPendingViewModel.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/staff/presentation/redeem/RedeemInviteViewModel.kt \
  composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/data/session/FirebaseActiveWorkshopProviderTest.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/session/WorkshopSessionResolverTest.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/staff/presentation/redeem/RedeemInviteViewModelTest.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/navigation/StaffRoleChangeRedirectTest.kt
git commit -m "feat(staff): demote revoked staff mid-session and redirect home"
```

---

### Task 2: Route demoted staff into Workshop Setup when they have no profile (Atelier fix)

Root cause (research 2026-08-16): a staff-only user has no `users/{uid}` doc; the server launch grant (`functions/src/freemium/onUserCreated.ts` → `grantLaunchFreeOnSignup`) fires on doc **create**, and only `WorkshopSetupViewModel` (line 391 `createUserProfile`) seeds the doc. `StaffRoleChangeRedirectEffect` currently hard-navigates `HomeRoute`, so a revoked staffer lands on Home with no doc → `UserDocEntitlementsProvider` FREE/15 defaults. Fix: after a demotion, resolve the destination the same way cold start does — `WorkshopSetupRoute` when `needsWorkshopSetupForCurrentUser(...)` is true, else `HomeRoute`. Workshop Setup then seeds the doc as `free` and the server grant upgrades to `atelier` (while `config/app.launchFreeGrantEnabled` is on).

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt` (StaffRoleChangeRedirectEffect, currently ~line 238-259; add helper near `shouldRedirectHomeForStaffSessionChange`)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/navigation/StaffRoleChangeRedirectTest.kt`

**Interfaces:**
- Consumes: `shouldRedirectHomeForStaffSessionChange(...)` from Task 1; existing `needsWorkshopSetupForCurrentUser(authRepository, resolveNeedsWorkshopSetup)` (private suspend fn already in NavGraph.kt, used at ~line 127); existing routes `HomeRoute`, `WorkshopSetupRoute`.
- Produces: `internal fun staffDemotionDestination(needsWorkshopSetup: Boolean): Any` in NavGraph.kt.

- [ ] **Step 1: Write the failing tests** — append to `StaffRoleChangeRedirectTest.kt`:

```kotlin
    @Test
    fun demotionWithoutWorkshopProfileGoesToWorkshopSetup() {
        assertEquals(WorkshopSetupRoute, staffDemotionDestination(needsWorkshopSetup = true))
    }

    @Test
    fun demotionWithExistingProfileGoesHome() {
        assertEquals(HomeRoute, staffDemotionDestination(needsWorkshopSetup = false))
    }
```

Add imports: `kotlin.test.assertEquals`, `com.danzucker.stitchpad.navigation.WorkshopSetupRoute` / `HomeRoute` (same package — only `assertEquals` import needed if routes are in `com.danzucker.stitchpad.navigation`).

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :composeApp:testDebugUnitTest --tests` equivalent — just run `./gradlew :composeApp:testDebugUnitTest`
Expected: FAIL — `staffDemotionDestination` unresolved.

- [ ] **Step 3: Implement** — in `NavGraph.kt`, below `shouldRedirectHomeForStaffSessionChange`:

```kotlin
/**
 * Where a just-demoted (revoked) staffer lands. A staff-only user has no
 * users/{uid} doc — Workshop Setup is the only path that seeds it, and the
 * server-side launch Atelier grant fires on that doc's creation. Sending a
 * profile-less demoted staffer straight Home would strand them on FREE/15
 * entitlement defaults until their next cold start.
 */
internal fun staffDemotionDestination(needsWorkshopSetup: Boolean): Any =
    if (needsWorkshopSetup) WorkshopSetupRoute else HomeRoute
```

Then in `StaffRoleChangeRedirectEffect`, inject the two extra dependencies next to the existing `koinInject()` for `ActiveWorkshopProvider`:

```kotlin
    val authRepository: AuthRepository = koinInject()
    val resolveNeedsWorkshopSetup: ResolveNeedsWorkshopSetup = koinInject()
```

and replace the redirect body:

```kotlin
            if (prior != null && shouldRedirectHomeForStaffSessionChange(prior, current)) {
                val destination = staffDemotionDestination(
                    needsWorkshopSetup =
                        needsWorkshopSetupForCurrentUser(authRepository, resolveNeedsWorkshopSetup),
                )
                navController.navigate(destination) {
                    popUpTo(navController.graph.id) { inclusive = false }
                    launchSingleTop = true
                }
            }
```

(`ResolveNeedsWorkshopSetup` import: `com.danzucker.stitchpad.feature.onboarding.domain.ResolveNeedsWorkshopSetup` — already imported at the top of NavGraph.kt for `resolvePostAuthDestination`; verify, add if missing.)

- [ ] **Step 4: Run tests + detekt**

Run: `./gradlew :composeApp:testDebugUnitTest detekt`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/navigation/StaffRoleChangeRedirectTest.kt
git commit -m "fix(staff): route profile-less demoted staff into Workshop Setup so the launch Atelier grant fires"
```

**Ops note (not code):** verify `config/app.launchFreeGrantEnabled == true` in the `stitchpad-30607` Firestore before the November review — runbook `docs/superpowers/plans/2026-07-06-launch-free-grant.md:507`.

---

### Task 3: Create the fix-round branch

- [ ] **Step 1:**

```bash
git checkout -b feat/testing-round-aug16
```

(All remaining tasks commit here; it stacks on `feat/staff-role-change-redirect`.)

---

### Task 4: Trailing invite-code dash at 4 characters

Daniel: the dash must appear the moment the 4th character is typed ("SNNY-"), caret sitting after it. Today `InviteCodeVisualTransformation` only groups when length > 4.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/staff/presentation/redeem/InviteCodeVisualTransformation.kt`
- Test (create): `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/staff/presentation/redeem/InviteCodeVisualTransformationTest.kt`

**Interfaces:**
- Consumes: nothing new. `RedeemInviteScreen.kt:184` already applies the transformation; ViewModel normalization (`RedeemInviteViewModel.kt:102`) is untouched, so paste with/without a dash keeps working.

- [ ] **Step 1: Write the failing test** (new file):

```kotlin
package com.danzucker.stitchpad.feature.staff.presentation.redeem

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class InviteCodeVisualTransformationTest {

    private fun transform(raw: String) = InviteCodeVisualTransformation.filter(AnnotatedString(raw))

    @Test
    fun underFourCharactersRendersUnchanged() {
        assertEquals("SNN", transform("SNN").text.text)
    }

    @Test
    fun exactlyFourCharactersRendersTrailingHyphen() {
        assertEquals("SNNY-", transform("SNNY").text.text)
    }

    @Test
    fun fullCodeRendersGrouped() {
        assertEquals("SNNY-1234", transform("SNNY1234").text.text)
    }

    @Test
    fun caretAtBoundarySitsAfterTheHyphen() {
        // Raw caret 4 (end of "SNNY") must display after the hyphen (transformed 5)
        assertEquals(5, transform("SNNY").offsetMapping.originalToTransformed(4))
        assertEquals(3, transform("SNNY").offsetMapping.originalToTransformed(3))
    }

    @Test
    fun transformedOffsetsOnEitherSideOfHyphenCollapseToBoundary() {
        val mapping = transform("SNNY1234").offsetMapping
        assertEquals(4, mapping.transformedToOriginal(4))
        assertEquals(4, mapping.transformedToOriginal(5))
        assertEquals(8, mapping.transformedToOriginal(9))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: FAIL — `exactlyFourCharactersRendersTrailingHyphen` gets "SNNY", `caretAtBoundarySitsAfterTheHyphen` gets 4.

- [ ] **Step 3: Implement** — in `InviteCodeVisualTransformation.kt`, change `filter` and `originalToTransformed` (leave `transformedToOriginal` as-is — it already collapses both hyphen-adjacent offsets to the boundary):

```kotlin
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length < GROUP_SIZE) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val grouped = "${raw.take(GROUP_SIZE)}-${raw.drop(GROUP_SIZE)}"
        return TransformedText(AnnotatedString(grouped), HyphenAfterFirstGroup)
    }
```

```kotlin
        override fun originalToTransformed(offset: Int): Int =
            if (offset < GROUP_SIZE) offset else offset + 1
```

Update the class KDoc's first line to mention the trailing hyphen appears at exactly [GROUP_SIZE] characters so the user never reaches for the dash key.

- [ ] **Step 4: Run tests + detekt** — `./gradlew :composeApp:testDebugUnitTest detekt` — Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/staff/presentation/redeem/InviteCodeVisualTransformation.kt \
  composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/staff/presentation/redeem/InviteCodeVisualTransformationTest.kt
git commit -m "feat(staff): show the invite-code hyphen the moment four characters are typed"
```

---

### Task 5: Settings "Join a workshop" row

A revoked/standalone user who only got the raw code (no deep link) needs an in-app path to the redeem screen. New Manage-section row, `!state.isActiveStaff`-gated, navigating to the root-level `RedeemInviteRoute()`.

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (2 new strings)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsAction.kt`, `SettingsEvent.kt`, `SettingsViewModel.kt`, `SettingsEventEffect.kt`, `SettingsRoot.kt`, `SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` (`MainRoot` + `MainNavGraph` + the `composable<SettingsRoute>` block ~line 688)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt` (`composable<HomeRoute>` block ~line 504)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModelTest.kt` (exists — follow its fake/setup pattern)

**Interfaces:**
- Produces: `SettingsAction.OnJoinWorkshopClick`, `SettingsEvent.NavigateToJoinWorkshop`, `MainRoot(onNavigateToJoinWorkshop: () -> Unit, ...)`.
- Consumes: `RedeemInviteRoute` (`navigation/Routes.kt:42`, code-less by design — the screen shows an empty field).

- [ ] **Step 1: Add strings** to `strings.xml` next to `settings_row_team` (~line 1407):

```xml
    <string name="settings_row_join_workshop">Join a workshop</string>
    <string name="settings_row_join_workshop_subtitle">Enter an invite code from a workshop owner</string>
```

- [ ] **Step 2: Write the failing ViewModel test** — in `SettingsViewModelTest.kt`, following the file's existing event-assertion pattern (Turbine on `viewModel.events`):

```kotlin
    @Test
    fun joinWorkshopClickEmitsNavigateToJoinWorkshop() = runTest {
        val viewModel = createViewModel() // use the file's existing factory/fakes
        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnJoinWorkshopClick)
            assertEquals(SettingsEvent.NavigateToJoinWorkshop, awaitItem())
        }
    }
```

- [ ] **Step 3: Run to verify failure** — `./gradlew :composeApp:testDebugUnitTest` — Expected: FAIL, unresolved `OnJoinWorkshopClick`.

- [ ] **Step 4: Implement the MVI chain**

`SettingsAction.kt` (after `OnTeamClick`):
```kotlin
    /** Standalone/demoted user opens the invite-code redeem screen (code shared as text, no deep link). */
    data object OnJoinWorkshopClick : SettingsAction
```

`SettingsEvent.kt` (after `NavigateToTeam`):
```kotlin
    /** Navigate to the root-level invite-code redeem screen. */
    data object NavigateToJoinWorkshop : SettingsEvent
```

`SettingsViewModel.kt` — in `onAction`, next to the `OnTeamClick` branch (~line 190), same emit pattern:
```kotlin
            SettingsAction.OnJoinWorkshopClick -> emit(SettingsEvent.NavigateToJoinWorkshop)
```

`SettingsEventEffect.kt` — add parameter `onNavigateToJoinWorkshop: () -> Unit = {},` and branch:
```kotlin
            SettingsEvent.NavigateToJoinWorkshop -> onNavigateToJoinWorkshop()
```

`SettingsRoot.kt` — add parameter `onNavigateToJoinWorkshop: () -> Unit,` and pass it to `SettingsEventEffect`.

`SettingsScreen.kt` — in `SettingsLandingHub`'s Manage card (directly after the owner-only Team row block ending ~line 658):
```kotlin
        // A revoked/standalone user whose owner shared the code as plain text
        // (no deep link) needs an in-app path to the redeem screen.
        if (!state.isActiveStaff) {
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Outlined.GroupAdd,
                label = stringResource(Res.string.settings_row_join_workshop),
                subtitle = stringResource(Res.string.settings_row_join_workshop_subtitle),
                onClick = { onAction(SettingsAction.OnJoinWorkshopClick) },
                trailing = { SettingsRowChevron() },
            )
        }
```
Also add the same row to `SettingsLandingLegacy`'s ACCOUNT card (find the card containing the sign-out/account rows, add before its last row, same `if (!state.isActiveStaff)` gate). Import `androidx.compose.material.icons.outlined.GroupAdd` and the two new string resources.

`MainScreen.kt` — add `onNavigateToJoinWorkshop: () -> Unit,` to `MainRoot` (line ~119) and `MainNavGraph` (line ~268), thread it through the `MainNavGraph(...)` call (~line 257), and in `composable<SettingsRoute>` pass `onNavigateToJoinWorkshop = onNavigateToJoinWorkshop,` to `SettingsRoot`.

`NavGraph.kt` — in `composable<HomeRoute>` (~line 504) add to `MainRoot(...)`:
```kotlin
                onNavigateToJoinWorkshop = { navController.navigate(RedeemInviteRoute()) },
```

- [ ] **Step 5: Run tests + detekt** — `./gradlew :composeApp:testDebugUnitTest detekt` — Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add -A composeApp/src/commonMain composeApp/src/commonTest
git commit -m "feat(settings): add Join a workshop row so a plain-text invite code is redeemable without a deep link"
```

---

### Task 6: Staff dashboard tiles — drop "Mine", add "My work · N" header link (Decision 1A)

Zero-softening already exists in `StaffCountTile` (`isZero` handling, line ~305). Remaining work: remove the 4th tile and re-home the count.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/StaffDashboardContent.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Consumes: existing `DashboardAction.OnViewMyWorkClick` (`DashboardAction.kt:47` → `DashboardViewModel.kt:256` → Orders filtered `MY_WORK`). `state.staffMineCount` (existing).
- Produces: `StaffCountTiles(overdue, dueToday, inProgress, onOverdueClick, onDueTodayClick, onInProgressClick)` — 3-tile signature.

- [ ] **Step 1: Add string / remove string** in `strings.xml`: add next to the tile strings (~line 907-911):

```xml
    <string name="staff_my_work_link">My work · %1$d</string>
```

Delete `dashboard_staff_tile_mine` only in Step 2 after its last usage is gone.

- [ ] **Step 2: Implement.** In `StaffDashboardContent.kt`:

(a) `StaffCountTiles` (line ~238): delete the `mine`/`onMineClick` parameters and the 4th `StaffCountTile` block (lines ~279-286). Update its call site (~line 160-169) accordingly — it no longer passes `mine = state.staffMineCount` / `onMineClick`.

(b) `StaffFocusQueueSection` (line ~459): add parameters `mineCount: Int` and keep using `onAction`. Replace the hero-branch header line (~470):
```kotlin
            FocusSectionHeader(stringResource(Res.string.staff_up_next_header))
```
with a header row carrying the link:
```kotlin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusSectionHeader(stringResource(Res.string.staff_up_next_header))
                if (mineCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onAction(DashboardAction.OnViewMyWorkClick) },
                    ) {
                        Text(
                            text = stringResource(Res.string.staff_my_work_link, mineCount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
```
Call site of `StaffFocusQueueSection` passes `mineCount = state.staffMineCount`.
(Known edge, accepted: the link renders only in the hero branch; with all assigned orders READY the hero is null and the link is absent — the pipeline bar still shows Ready counts.)

(c) Delete `dashboard_staff_tile_mine` from `strings.xml`.

(d) Update any `@Preview` composables in the file that pass a `mine` argument.

- [ ] **Step 3: Run tests + detekt** — `./gradlew :composeApp:testDebugUnitTest detekt` — Expected: PASS (compile-level; tiles have no VM logic change).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/StaffDashboardContent.kt \
  composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(staff): three-tile dashboard top row; Mine count moves to the Up-next header link"
```

---

### Task 7: `PipelineStage.previous()` + generalized `OnSetStage` with undo event (Decision 2A groundwork)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/domain/model/PipelineStage.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardAction.kt`, `DashboardEvent.kt`, `DashboardViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModelTest.kt` (exists — reuse its fakes), `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/domain/PipelineStageTest.kt` (create if absent; check for an existing stage test file first and extend it instead)

**Interfaces:**
- Produces: `PipelineStage.previous(): PipelineStage?`; `DashboardAction.OnSetStage(orderId: String, fromStage: PipelineStage, toStage: PipelineStage)`; `DashboardEvent.StageAdvanced(orderId: String, fromStage: PipelineStage, toStage: PipelineStage)`. Task 8 (sheet) and Task 9's screen wiring consume all three.
- Consumes: existing `handleAdvanceStage` machinery — `advancingOrders` re-entrancy map, live-stage stale guard, `orderRepository.updateOrderStatus` + `updateSubStatus`, `toOrderStatusAndSubStatus()`, `staff_advance_stage_error` state snackbar.

- [ ] **Step 1: Failing domain test** — `previous()`:

```kotlin
    @Test
    fun previousStepsBackwardAndStopsAtPending() {
        assertEquals(PipelineStage.FITTING, PipelineStage.READY.previous())
        assertNull(PipelineStage.PENDING.previous())
    }
```

- [ ] **Step 2: Failing ViewModel tests** — in `DashboardViewModelTest.kt`, using the file's existing fake-repository + staff-queue setup helpers (mirror the existing `OnAdvanceStage` tests' arrangement):

```kotlin
    @Test
    fun setStageMovesBackwardViaRepository() = runTest { /* arrange staff queue with order at FITTING */
        viewModel.onAction(DashboardAction.OnSetStage("order-1", fromStage = PipelineStage.FITTING, toStage = PipelineStage.SEWING))
        // assert fake repo received IN_PROGRESS + SEWING (per SEWING.toOrderStatusAndSubStatus())
    }

    @Test
    fun setStageWithStaleFromStageNoOps() = runTest { /* live stage FITTING, action says SEWING */
        viewModel.onAction(DashboardAction.OnSetStage("order-1", fromStage = PipelineStage.SEWING, toStage = PipelineStage.CUTTING))
        // assert fake repo received nothing
    }

    @Test
    fun advanceEmitsStageAdvancedEventForUndo() = runTest {
        viewModel.events.test {
            viewModel.onAction(DashboardAction.OnAdvanceStage("order-1", PipelineStage.FITTING))
            assertEquals(
                DashboardEvent.StageAdvanced("order-1", fromStage = PipelineStage.FITTING, toStage = PipelineStage.READY),
                awaitItem(),
            )
        }
    }

    @Test
    fun setStageDoesNotEmitStageAdvanced() = runTest { /* undo/sheet moves must not re-offer undo */ }
```

Flesh the arrange/assert bodies out against the file's real helpers — the intent above is binding, the helper names come from the file.

- [ ] **Step 3: Run to verify failure** — `./gradlew :composeApp:testDebugUnitTest` — Expected: FAIL on unresolved symbols.

- [ ] **Step 4: Implement**

`PipelineStage.kt` (next to `next()`, line ~28):
```kotlin
    /** Step back one stage; null at PENDING. Powers undo and the stage sheet. */
    fun previous(): PipelineStage? = entries.getOrNull(ordinal - 1)
```

`DashboardAction.kt` (next to `OnAdvanceStage`, ~line 114):
```kotlin
    /**
     * Move an order to an arbitrary stage (undo snackbar / stage sheet).
     * [fromStage] is the stage the UI believed current at tap time — the
     * handler no-ops when it no longer matches, same stale guard as
     * [OnAdvanceStage].
     */
    data class OnSetStage(
        val orderId: String,
        val fromStage: PipelineStage,
        val toStage: PipelineStage,
    ) : DashboardAction
```

`DashboardEvent.kt`:
```kotlin
    /** A one-tap hero advance landed — offer undo back to [fromStage]. */
    data class StageAdvanced(
        val orderId: String,
        val fromStage: PipelineStage,
        val toStage: PipelineStage,
    ) : DashboardEvent
```

`DashboardViewModel.kt` — refactor `handleAdvanceStage` (line ~337) into a generalized worker, keeping every existing guard in the existing order:
```kotlin
        is DashboardAction.OnAdvanceStage -> {
            val next = action.fromStage.next()
            if (next != null) {
                handleSetStage(action.orderId, action.fromStage, next, announceAdvance = true)
            }
        }
        is DashboardAction.OnSetStage ->
            handleSetStage(action.orderId, action.fromStage, action.toStage, announceAdvance = false)
```
`handleSetStage(orderId, fromStage, toStage, announceAdvance)`: identical body to today's `handleAdvanceStage` except (a) `toStage` replaces the internally computed `nextStage`, (b) guard `if (toStage == fromStage) return`, (c) after BOTH repository calls succeed and `announceAdvance` is true, `emitEvent(DashboardEvent.StageAdvanced(orderId, fromStage, toStage))`. Keep the analytics call for forward moves only (`toStage.ordinal > fromStage.ordinal`).

- [ ] **Step 5: Run tests + detekt** — Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add -A composeApp/src/commonMain composeApp/src/commonTest
git commit -m "feat(staff): generalize stage moves — OnSetStage action, previous(), StageAdvanced undo event"
```

---

### Task 8: Undo snackbar on the dashboard (Decision 2A)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` (event handling ~lines 315-353 / `handleDashboardEvent` ~473)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

**Interfaces:**
- Consumes: `DashboardEvent.StageAdvanced` and `DashboardAction.OnSetStage` from Task 7; existing `stageLabel(stage)` (still in `StaffDashboardContent.kt` until Task 10 moves it — for the snackbar resolve the label via the stage string resources directly, see below); existing `snackbarHostState` on the screen.

- [ ] **Step 1: Add strings**:

```xml
    <string name="staff_stage_moved_snackbar">Moved to %1$s</string>
    <string name="staff_stage_undo">Undo</string>
```

- [ ] **Step 2: Implement.** In `DashboardScreen.kt`, `StageAdvanced` needs string resolution inside a coroutine — follow the existing pattern where events are handled with access to `scope` + `snackbarHostState`. Add to the event `when`:

```kotlin
        is DashboardEvent.StageAdvanced -> scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = getString(Res.string.staff_stage_moved_snackbar, getString(stageLabelRes(event.toStage))),
                actionLabel = getString(Res.string.staff_stage_undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onAction(
                    DashboardAction.OnSetStage(
                        orderId = event.orderId,
                        fromStage = event.toStage,
                        toStage = event.fromStage,
                    )
                )
            }
        }
```

Add a small shared helper (place it in `PipelineStage.kt`'s file as a top-level fun in the presentation layer is NOT allowed — put it in `StaffDashboardContent.kt` for now; Task 10 moves it to the shared `StageDots.kt` file):

```kotlin
internal fun stageLabelRes(stage: PipelineStage): StringResource = when (stage) {
    PipelineStage.PENDING -> Res.string.order_stage_pending
    PipelineStage.CUTTING -> Res.string.order_stage_cutting
    PipelineStage.SEWING -> Res.string.order_stage_sewing
    PipelineStage.FITTING -> Res.string.order_stage_fitting
    PipelineStage.READY -> Res.string.order_stage_ready
}
```
(Existing composable `stageLabel(stage)` becomes `stringResource(stageLabelRes(stage))` — refactor it to delegate so there is one mapping.)

Note the undo dispatch goes through the same `OnSetStage` stale guard — if the order moved again before the tap, the undo silently no-ops (correct).

- [ ] **Step 3: Run tests + detekt** — Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add -A composeApp/src/commonMain
git commit -m "feat(staff): undo snackbar after a hero stage advance"
```

---

### Task 9: Stage sheet from the tappable stepper (Decision 2B)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/components/StageSheet.kt`
- Modify: `DashboardState.kt`, `DashboardAction.kt`, `DashboardViewModel.kt`, `components/StaffDashboardContent.kt`
- Test: `DashboardViewModelTest.kt`

**Interfaces:**
- Produces: `StageSheet(currentStage: PipelineStage, customerName: String, orderCode: String, onSelect: (PipelineStage) -> Unit, onDismiss: () -> Unit)`; `DashboardState.stageSheetOrderId: String?`; `DashboardAction.OnStageStepperClick(orderId)`, `DashboardAction.OnDismissStageSheet`.
- Consumes: `OnSetStage` (Task 7), `orderCodeFor(orderId)` (existing in StaffDashboardContent.kt), `stageLabelRes` (Task 8).

- [ ] **Step 1: Failing ViewModel tests**:

```kotlin
    @Test
    fun stepperClickOpensStageSheetForThatOrder() = runTest {
        viewModel.onAction(DashboardAction.OnStageStepperClick("order-1"))
        assertEquals("order-1", viewModel.state.value.stageSheetOrderId)
    }

    @Test
    fun dismissAndStageSelectionCloseTheSheet() = runTest {
        viewModel.onAction(DashboardAction.OnStageStepperClick("order-1"))
        viewModel.onAction(DashboardAction.OnDismissStageSheet)
        assertNull(viewModel.state.value.stageSheetOrderId)
        viewModel.onAction(DashboardAction.OnStageStepperClick("order-1"))
        viewModel.onAction(DashboardAction.OnSetStage("order-1", PipelineStage.FITTING, PipelineStage.SEWING))
        assertNull(viewModel.state.value.stageSheetOrderId)
    }
```

- [ ] **Step 2: Run to verify failure**, then **Step 3: Implement**

`DashboardState.kt`: `val stageSheetOrderId: String? = null,`

`DashboardAction.kt`:
```kotlin
    /** Staff tapped the hero's stage stepper — open the stage sheet for that order. */
    data class OnStageStepperClick(val orderId: String) : DashboardAction
    data object OnDismissStageSheet : DashboardAction
```

`DashboardViewModel.kt`:
```kotlin
        is DashboardAction.OnStageStepperClick ->
            _state.update { it.copy(stageSheetOrderId = action.orderId) }
        DashboardAction.OnDismissStageSheet ->
            _state.update { it.copy(stageSheetOrderId = null) }
```
and at the top of `handleSetStage`: `_state.update { it.copy(stageSheetOrderId = null) }` (selection always closes the sheet, even when guards then no-op).

`StageSheet.kt` — M3 `ModalBottomSheet`; one row per `PipelineStage.entries`; done rows show a primary-filled dot + "✓"-style done marker, current row highlighted `primaryContainer`-style with saffron dot (reuse the exact color rules from `StageDots`); each row `clickable { onSelect(stage) }`; title = customer name, subtitle = order code. All text via string resources — add:

```xml
    <string name="staff_stage_sheet_title">Set stage</string>
```
Rows use `stringResource(stageLabelRes(stage))`.

`StaffDashboardContent.kt`:
- `UpNextHero`: wrap `HeroStageStepper(stage = stage)` in `Box(Modifier.clickable(onClickLabel = ...) { onStepperClick() })` — add `onStepperClick: () -> Unit` param, dispatched as `DashboardAction.OnStageStepperClick(hero.orderId)` from `StaffFocusQueueSection`.
- In `StaffDashboardContent`, after the queue section: when `state.stageSheetOrderId != null`, resolve `val sheetRow = (queue rows).firstOrNull { it.orderId == state.stageSheetOrderId }` and mount:
```kotlin
        sheetRow?.stage?.let { current ->
            StageSheet(
                currentStage = current,
                customerName = sheetRow.customerName,
                orderCode = orderCodeFor(sheetRow.orderId),
                onSelect = { picked ->
                    onAction(DashboardAction.OnSetStage(sheetRow.orderId, fromStage = current, toStage = picked))
                },
                onDismiss = { onAction(DashboardAction.OnDismissStageSheet) },
            )
        }
```

- [ ] **Step 4: Run tests + detekt** — Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A composeApp/src/commonMain composeApp/src/commonTest
git commit -m "feat(staff): tappable hero stepper opens a stage sheet — move any direction"
```

---

### Task 10: Shared StageDots + stage line on Orders rows (Decision 3A)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StageDots.kt`
- Modify: `feature/dashboard/presentation/components/StaffDashboardContent.kt` (delete private copies, import shared)
- Modify: `feature/order/presentation/list/OrderListScreen.kt` (`OrderListItem`)

**Interfaces:**
- Produces (in `ui/components/StageDots.kt`, all public): `StageDots(stage: PipelineStage, modifier: Modifier = Modifier)`, `stageLabelRes(stage: PipelineStage): StringResource`, `@Composable stageLabel(stage: PipelineStage): String`, `@Composable stageProgressDescription(stage: PipelineStage): String`.
- Consumes: `stageOf(order.status, order.subStatus)` (`PipelineStage.kt:38`).

- [ ] **Step 1: Move.** Create `StageDots.kt` containing the four members above — bodies copied verbatim from `StaffDashboardContent.kt` lines ~707-712 (`stageProgressDescription`), ~809-830 (`StageDots`), the `stageLabel`/`stageLabelRes` pair from Task 8. Package `com.danzucker.stitchpad.ui.components`; KDoc on `StageDots` keeps the `●●●○○` color-rule comment (done = primary, current = saffron, upcoming = outlineVariant). Delete the originals from `StaffDashboardContent.kt` and fix its imports (`TicketRow` and the sheet keep compiling).

- [ ] **Step 2: Orders row.** In `OrderListItem` (`OrderListScreen.kt` — insert between `DeadlineLine` at line ~830 and the badges row at ~832):

```kotlin
            val stage = stageOf(order.status, order.subStatus)
            if (stage != null && stage != PipelineStage.READY) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                    modifier = Modifier.padding(top = DesignTokens.space1),
                ) {
                    StageDots(stage)
                    Text(
                        text = stageLabel(stage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
```
READY is skipped by design — the row's `DeadlineLine` already prints "Pickup ready". Imports: `com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage`, `...model.stageOf`, `com.danzucker.stitchpad.ui.components.StageDots`, `...stageLabel`. (Cross-feature import precedent: `DashboardViewModel` already imports `OrderListFilter` from the order feature.)

- [ ] **Step 3: Run tests + detekt** — `./gradlew :composeApp:testDebugUnitTest detekt` — Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add -A composeApp/src/commonMain
git commit -m "feat(orders): stage dots on order rows — shared StageDots component with the staff tickets"
```

---

### Task 11: "Hide amounts" (was "Hide profit")

The toggle becomes a true privacy switch: hidden = no naira anywhere on the list. Visible (default) = price + payment + profit-when-costed. **Deliberate behavior change to flag in the PR:** profit was previously opt-in per session; it now shows by default whenever costs exist, because the eye toggle's job is now "hide money from bystanders", not "reveal margins".

**Files:**
- Modify: `feature/order/presentation/list/OrderListState.kt`, `OrderListAction.kt`, `OrderListViewModel.kt`, `OrderListScreen.kt`, `PaymentStatusText.kt`
- Modify: `strings.xml`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListStaffTest.kt` (has existing profit-gating assertions at ~107/116 — update), plus the OrderListViewModel test file if one exists (check `composeApp/src/commonTest/.../order/presentation/list/`).

**Interfaces:**
- Produces: `OrderListState.hideAmounts: Boolean = false`; `OrderListAction.OnToggleHideAmounts`; `PaymentStatusText(depositPaid, amountOwed, showAmounts: Boolean = true, modifier)`.
- Removes: `OrderListState.showProfit`, `OrderListAction.OnToggleShowProfit`, strings `order_show_profit`/`order_hide_profit`.

- [ ] **Step 1: Strings** — replace in `strings.xml` (~line 1882):

```xml
    <string name="order_show_amounts">Show amounts</string>
    <string name="order_hide_amounts">Hide amounts</string>
    <string name="payment_partial_masked">Part-paid</string>
```
(Delete `order_show_profit` / `order_hide_profit` once usages are gone.)

- [ ] **Step 2: Failing test** — in the VM test (or `OrderListStaffTest` pattern):

```kotlin
    @Test
    fun toggleHideAmountsFlips() = runTest {
        viewModel.onAction(OrderListAction.OnToggleHideAmounts)
        assertTrue(viewModel.state.value.hideAmounts)
    }

    @Test
    fun staffCannotToggleAmounts() = runTest { /* isActiveStaff -> action ignored, mirrors old OnToggleShowProfit guard */ }
```

- [ ] **Step 3: Implement**

`OrderListState.kt:10`: `val showProfit: Boolean = false` → `val hideAmounts: Boolean = false`.
`OrderListAction.kt`: `OnToggleShowProfit` → `OnToggleHideAmounts`.
`OrderListViewModel.kt:141-147`:
```kotlin
            OrderListAction.OnToggleHideAmounts -> {
                if (_state.value.isActiveStaff) return
                _state.update { it.copy(hideAmounts = !it.hideAmounts) }
            }
```
`OrderListScreen.kt`:
- `ProfitToggleAction` (line ~693) → rename `AmountsToggleAction(hideAmounts: Boolean, onToggle)`; label `if (hideAmounts) Res.string.order_show_amounts else Res.string.order_hide_amounts`; icon `if (hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility` (eye open = amounts visible).
- Thread `hideAmounts` instead of `showProfit` through `SwipeableOrderItem` / `OrderListItem` / `ArchivedOrderItem` (matching params).
- In `OrderListItem`'s money column (lines ~850-866):
```kotlin
        if (!isActiveStaff) {
            Column(horizontalAlignment = Alignment.End) {
                if (!hideAmounts) {
                    StrikethroughPrice(
                        grossPrice = order.totalPrice,
                        netPrice = order.payableTotal,
                        discount = order.discount,
                        netStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        netColor = MaterialTheme.colorScheme.onSurface,
                        stacked = true,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                PaymentStatusText(
                    depositPaid = order.depositPaid,
                    amountOwed = order.payableTotal,
                    showAmounts = !hideAmounts,
                )
                if (!hideAmounts && order.hasCosts) {
                    Spacer(Modifier.height(2.dp))
                    OrderRowProfit(profit = order.profit)
                }
            }
        }
```
`PaymentStatusText.kt` — add `showAmounts: Boolean = true`; in the `Partial` branch:
```kotlin
        is PaymentDisplay.Partial -> {
            val label = if (showAmounts) {
                stringResource(Res.string.payment_partial, display.formatAbbreviated())
            } else {
                stringResource(Res.string.payment_partial_masked)
            }
            label to DesignTokens.warning500
        }
```
(Paid/Unpaid carry no figures — unchanged. The qualitative word stays visible when amounts are hidden: that's operational info, per the locked decision.)
- Fix every other `PaymentStatusText` call site (`grep -rn "PaymentStatusText(" composeApp/src` — default `showAmounts = true` keeps them compiling; only the list row passes the flag).
- Update `OrderListStaffTest` assertions from `showProfit` semantics to `hideAmounts`.

- [ ] **Step 4: Run tests + detekt** — Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A composeApp/src/commonMain composeApp/src/commonTest
git commit -m "feat(orders): Hide amounts — the eye toggle now masks every naira on the list"
```

---

### Task 12: Final verification + PRs

- [ ] **Step 1:** `./gradlew :composeApp:testDebugUnitTest detekt` — full green.
- [ ] **Step 2:** `./gradlew :composeApp:assembleDebug` — compiles.
- [ ] **Step 3:** Push both branches; open PR-1 (`feat/staff-role-change-redirect` → main: demotion redirect + Atelier fix) and PR-2 (`feat/testing-round-aug16` → `feat/staff-role-change-redirect`, marked stacked). PR bodies list the Notion tracker items each closes.
- [ ] **Step 4:** Do NOT merge — Daniel re-tests on simulators first (his stated workflow: fix everything, then test).

## Self-Review Notes

- Spec coverage: tracker items → Task 2 (Atelier), Task 4 (dash), Task 5 (join row), Task 6 (1A), Tasks 7-8 (2A), Task 9 (2B), Task 10 (3A), Task 11 (hide amounts). Item "stale build" already closed, no task.
- Deviation from locked package, deliberate: the stage sheet is NOT yet mounted in Order Detail (it has its own stage timeline editor); noted as follow-up.
- Type consistency: `OnSetStage(orderId, fromStage, toStage)` used identically in Tasks 7, 8, 9; `stageLabelRes` introduced Task 8, moved Task 10; `hideAmounts` naming consistent in Task 11.
