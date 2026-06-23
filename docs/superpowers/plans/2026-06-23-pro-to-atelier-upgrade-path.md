# Pro → Atelier upgrade path — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.
>
> **WORKTREE:** All work happens in `/Users/danzucker/Desktop/Project/StitchPad-upgrade-tier` (branch `feat/settings-upgrade-tier`). The main checkout holds the analytics branch — never touch it. `cd` into the worktree for every command (the shell resets cwd between calls).

**Goal:** Give a Tailor Pro user a way to upgrade to Tailor Atelier — a tappable Settings plan card → paywall (Atelier already pre-selected), and mark the user's current tier as "Current plan" on the paywall.

**Architecture:** The paywall already sells Atelier and the Upgrade VM already pre-selects Atelier for a Pro user; only the Settings `PlanCardPaid` lacks a CTA. Task 1 = the Settings card CTA. Task 2 = the paywall "Current plan" marking + a defensive VM guard. Two tasks so they can ship/review independently.

**Tech Stack:** KMP, Compose Multiplatform, JUnit5.

**Spec:** `docs/superpowers/specs/2026-06-23-pro-to-atelier-upgrade-path-design.md`.

---

## Task 1: Settings Pro plan card → tappable upgrade affordance

**Files:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/components/PlanCard.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

READ `PlanCard.kt` first — the `PlanCard` short-circuit + the `PlanCardPaid` composable + its previews.

- [ ] **Step 1: String** — add to `strings.xml`:
```xml
    <string name="plan_card_pro_upgrade_hint">Tap to explore Tailor Atelier</string>
```

- [ ] **Step 2: Pass `onUpgradeClick` into `PlanCardPaid`** — in `PlanCard`, the paid short-circuit:
```kotlin
    if (tier != SubscriptionTier.FREE) {
        PlanCardPaid(tier = tier, onUpgradeClick = onUpgradeClick, modifier = modifier)
        return
    }
```

- [ ] **Step 3: Rework `PlanCardPaid`** to be tappable for Pro. New signature `PlanCardPaid(tier: SubscriptionTier, onUpgradeClick: () -> Unit, modifier: Modifier = Modifier)`:
  - `val canUpgrade = tier == SubscriptionTier.PRO` (Atelier = top tier, stays static).
  - When `canUpgrade`, the `Surface` gets `Modifier.clickable(onClick = onUpgradeClick, role = Role.Button)` (add to the existing `modifier.fillMaxWidth()`).
  - In the existing `Row`, after the weighted `Column`, add a trailing chevron only when `canUpgrade`:
```kotlin
            if (canUpgrade) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
```
  - Inside the weighted `Column`, after the "Unlimited customers" `Text`, add the hint only when `canUpgrade`:
```kotlin
                if (canUpgrade) {
                    Spacer(Modifier.height(DesignTokens.space1))
                    Text(
                        text = stringResource(Res.string.plan_card_pro_upgrade_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
```
  - Imports: `androidx.compose.foundation.clickable`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight`, `androidx.compose.material3.Icon`, `androidx.compose.ui.semantics.Role`, `androidx.compose.foundation.layout.size`, the new string — add only those not already present (grep).

- [ ] **Step 4: Previews** — ensure a `PlanCardPaid`/`PlanCard` preview exists (or the existing `SubscriptionTier.PRO` preview at the SettingsScreen previews) renders the new Pro card. If `PlanCard.kt` has its own previews, confirm the PRO one shows the chevron + hint and the ATELIER one stays static. Add a `PlanCardPaid` PRO preview if none exists.

- [ ] **Step 5: Verify**
  `cd /Users/danzucker/Desktop/Project/StitchPad-upgrade-tier` then:
  `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` → clean (iOS TEST compile REQUIRED).
  `./gradlew :composeApp:testDebugUnitTest --tests '*PlanCard*' --tests '*Settings*' -q` → pass.

- [ ] **Step 6: Commit**
```bash
git add -A
git commit -m "feat(billing): Pro plan card is tappable -> Atelier upgrade (Settings)"
```

---

## Task 2: Paywall "Current plan" marking + VM guard

**Files:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/upgrade/UpgradeScreen.kt` (the private `TierCard` + the two call sites)
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/upgrade/UpgradeViewModel.kt` (the `SelectTier` handler)
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/freemium/presentation/upgrade/UpgradeViewModelTest.kt`

READ `UpgradeScreen.kt` (the `TierCard` composable + its two invocations ~lines 126-144) and `UpgradeViewModel.kt` (`SelectTier` ~line 105 + `initialState` ~line 75-89) first.

- [ ] **Step 1: String** — add to `strings.xml`:
```xml
    <string name="upgrade_current_plan">Current plan</string>
```

- [ ] **Step 2: `TierCard` gets `isCurrent`** — add `isCurrent: Boolean = false` to the private `TierCard` signature. When `isCurrent`:
  - Show a small **"Current plan"** label (`stringResource(Res.string.upgrade_current_plan)`, e.g. a muted `labelSmall` near the tier name or as a small pill).
  - Make it NON-selectable: when `isCurrent`, do NOT apply the clickable/selection handling (skip `onClick`), do NOT render the selected ring, and dim the card (e.g. wrap the card `Modifier` with `.alpha(0.6f)` when `isCurrent`). Read how the card currently applies `isSelected` + `onClick` and gate them on `!isCurrent`.

- [ ] **Step 3: Pass `isCurrent` at the call sites** — in the tier picker:
```kotlin
                TierCard(
                    name = stringResource(Res.string.upgrade_pro_name),
                    monthlyPrice = tierPriceText(isApple, state, SubscriptionTier.PRO, Res.string.upgrade_pro_price),
                    annualHint = tierHintText(isApple, state.billingCadence, Res.string.upgrade_pro_annual),
                    isSelected = state.selectedTier == SubscriptionTier.PRO,
                    isCurrent = state.currentTier == SubscriptionTier.PRO,
                    onClick = { onAction(UpgradeAction.SelectTier(SubscriptionTier.PRO)) },
                )
                TierCard(
                    name = stringResource(Res.string.upgrade_atelier_name),
                    monthlyPrice = tierPriceText(isApple, state, SubscriptionTier.ATELIER, Res.string.upgrade_atelier_price),
                    annualHint = tierHintText(isApple, state.billingCadence, Res.string.upgrade_atelier_annual),
                    isSelected = state.selectedTier == SubscriptionTier.ATELIER,
                    isCurrent = state.currentTier == SubscriptionTier.ATELIER,
                    onClick = { onAction(UpgradeAction.SelectTier(SubscriptionTier.ATELIER)) },
                )
```
  (Match the EXACT existing arg names/order — adapt the price/hint args to what the current `TierCard` call passes; only ADD `isCurrent`.)

- [ ] **Step 4: VM guard** — in `UpgradeViewModel.onAction`, the `SelectTier` handler ignores the current tier:
```kotlin
            is UpgradeAction.SelectTier ->
                if (!_state.value.isStartingCheckout && action.tier != _state.value.currentTier) {
                    _state.update { it.copy(selectedTier = action.tier) }
                }
```

- [ ] **Step 5: Tests** — in `UpgradeViewModelTest.kt` (READ it to learn how it builds the VM — the `EntitlementsProvider` fake + how `currentTier` is seeded):
  - `selectTier_ignoresCurrentTier`: seed `currentTier = PRO` (selectedTier defaults to ATELIER) → `SelectTier(PRO)` → `selectedTier` stays `ATELIER`.
  - `selectTier_allowsUpgradeTier`: seed `currentTier = PRO` → `SelectTier(ATELIER)` → `selectedTier == ATELIER`.
  - Confirm existing pre-selection tests still pass (Free → selectedTier PRO; Pro → selectedTier ATELIER).
  Run FIRST (the ignore test fails pre-guard), then Step 4 makes it pass.

- [ ] **Step 6: Verify**
  `cd /Users/danzucker/Desktop/Project/StitchPad-upgrade-tier` then:
  `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` → clean (iOS TEST compile REQUIRED).
  `./gradlew :composeApp:testDebugUnitTest --tests '*Upgrade*' -q` → pass.

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "feat(billing): paywall marks current tier as Current plan (non-selectable) + VM guard"
```

---

## Manual smoke test (device — Daniel is QA)
1. **Pro** user → Settings → "You're on Tailor Pro · Unlimited customers" card shows a chevron + "Tap to explore Tailor Atelier", is tappable → opens the paywall with **Atelier pre-selected** and **Pro shown as "Current plan" (disabled)**. Tapping the disabled Pro card does nothing; Atelier checkout proceeds.
2. **Atelier** user → plan card is static (no chevron/tap).
3. **Free** user → unchanged.

## Self-review notes
- Task 1 = the bug fix (Pro card CTA); Task 2 = paywall current-plan + VM guard. ✓
- Reuses the existing nav + Atelier pre-selection; Atelier stays top-tier (no CTA). ✓
- Strings resourced; chevron reuses existing icon. ✓
- Worktree-isolated; iOS test compile in every gate; VM tests in commonTest. ✓
