# Pro → Atelier upgrade path (Settings + paywall) — design

**Status:** Design approved (2026-06-23)
**Branch / worktree:** `feat/settings-upgrade-tier` at `/Users/danzucker/Desktop/Project/StitchPad-upgrade-tier` (off `main`, isolated from the analytics branch).
**Source:** a **Tailor Pro** user has no way to upgrade to **Tailor Atelier** — the Settings plan card (`PlanCardPaid`) renders a static "You're on Tailor Pro · Unlimited customers" with **no upgrade affordance**, so there's no tap target to reach the paywall.

## Key finding (why this is small)
The upgrade machinery already exists:
- The **paywall already sells Atelier** — `UpgradeScreen` has a full tier picker (Pro + Atelier `TierCard`s, `SelectTier(...)`).
- The **Upgrade VM is current-tier-aware** — `UpgradeViewModel.initialState` sets `selectedTier = if (currentTier == FREE) PRO else ATELIER`, so a Pro user lands with **Atelier pre-selected**.
- **Settings `OnUpgradeClick → NavigateToUpgrade → UpgradeRoute`** is already wired.

The only gap is the missing CTA on the paid plan card. Plus one polish: the paywall lets a Pro user re-select their current Pro tier.

## Goals
1. Give the **Pro** plan card a tappable "upgrade to Atelier" affordance (whole card + chevron → paywall). Atelier (top tier) stays static.
2. On the paywall, mark the user's **current tier** card as **"Current plan"** and make it non-selectable.

## Out of scope
Atelier→Pro downgrade flows; pricing/benefit copy; the Free-tier PlanCard states (unchanged).

---

## 1. Settings Pro plan card — `PlanCardPaid`

`feature/settings/presentation/components/PlanCard.kt`.

`PlanCard` already has `onUpgradeClick`; pass it into `PlanCardPaid`:
```kotlin
    if (tier != SubscriptionTier.FREE) {
        PlanCardPaid(tier = tier, onUpgradeClick = onUpgradeClick, modifier = modifier)
        return
    }
```

Rework `PlanCardPaid(tier, onUpgradeClick, modifier)`:
- `val canUpgrade = tier == SubscriptionTier.PRO` (Atelier is the top tier → no upgrade).
- When `canUpgrade`, make the `Surface` clickable → `onUpgradeClick` (`role = Role.Button`), add a trailing **chevron** (`Icons.AutoMirrored.Filled.KeyboardArrowRight`, tint `onSurfaceVariant`) in the row, and a subtle **hint line** under "Unlimited customers":
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
  Place the chevron as a trailing element in the existing `Row` (after the weighted `Column`), shown only when `canUpgrade`.
- When `!canUpgrade` (Atelier): no clickable, no chevron, no hint — the current static card.
- Keep the existing `PlanPill(tierLabel)` + "Unlimited customers" line for both.

String:
```xml
    <string name="plan_card_pro_upgrade_hint">Tap to explore Tailor Atelier</string>
```

---

## 2. Paywall "Current plan" marking — `TierCard` + `UpgradeScreen`

`feature/freemium/presentation/upgrade/UpgradeScreen.kt`.

Add `isCurrent: Boolean = false` to the private `TierCard` composable. When `isCurrent`:
- Render a small **"Current plan"** label/badge (e.g. above or beside the tier name) using `Res.string.upgrade_current_plan`.
- **Not selectable:** drop the `clickable`/selection handling (no `onClick`), no selected-state ring, and dim the card (e.g. `Modifier.alpha(0.6f)` or muted container) so it reads as informational, not actionable.

In the `UpgradeScreen` tier picker, pass `isCurrent` per card:
```kotlin
                TierCard(
                    name = stringResource(Res.string.upgrade_pro_name),
                    // ...existing price/hint args...
                    isSelected = state.selectedTier == SubscriptionTier.PRO,
                    isCurrent = state.currentTier == SubscriptionTier.PRO,
                    onClick = { onAction(UpgradeAction.SelectTier(SubscriptionTier.PRO)) },
                )
                TierCard(
                    name = stringResource(Res.string.upgrade_atelier_name),
                    // ...existing price/hint args...
                    isSelected = state.selectedTier == SubscriptionTier.ATELIER,
                    isCurrent = state.currentTier == SubscriptionTier.ATELIER,
                    onClick = { onAction(UpgradeAction.SelectTier(SubscriptionTier.ATELIER)) },
                )
```
(When `isCurrent`, `TierCard` itself ignores `onClick`/`isSelected` — so the Pro card for a Pro user shows "Current plan", disabled, while Atelier stays the pre-selected upgrade. For a Free user neither is current — unchanged.)

String:
```xml
    <string name="upgrade_current_plan">Current plan</string>
```

### Defensive VM guard — `UpgradeViewModel`
In the `SelectTier` handler, ignore a selection equal to the current tier (belt-and-suspenders with the disabled card):
```kotlin
            is UpgradeAction.SelectTier ->
                if (!_state.value.isStartingCheckout && action.tier != _state.value.currentTier) {
                    _state.update { it.copy(selectedTier = action.tier) }
                }
```

---

## 3. Testing

`UpgradeViewModelTest` (extend):
- `selectTier_ignoresCurrentTier`: with `currentTier = PRO`, `SelectTier(PRO)` leaves `selectedTier` unchanged (still ATELIER, the pre-selected upgrade).
- `selectTier_allowsUpgradeTier`: with `currentTier = PRO`, `SelectTier(ATELIER)` sets `selectedTier = ATELIER` (sanity; already the default).
- Confirm the existing pre-selection tests still hold (Free → PRO default; Pro → ATELIER default).

`PlanCard` previews: update/confirm the `SubscriptionTier.PRO` preview renders the tappable hint + chevron, and the `ATELIER` preview stays static. (Card click + paywall disabled-state are visual → manual smoke.)

## 4. Manual smoke test (device — Daniel is QA)
1. As a **Pro** user → Settings → the "You're on Tailor Pro · Unlimited customers" card now shows a chevron + "Tap to explore Tailor Atelier" and is tappable → opens the paywall with **Atelier pre-selected** and **Pro marked "Current plan" (disabled)**.
2. Tapping the disabled Pro card does nothing; Atelier checkout proceeds normally.
3. As an **Atelier** user → the plan card is static (no chevron, no tap) — already top tier.
4. As a **Free** user → unchanged (existing upgrade states).

## 5. Self-review checks
- Bug fixed: Pro card is now a tap target → paywall (Atelier pre-selected, already wired). ✓
- Atelier = top tier, no CTA. ✓
- Paywall current tier = "Current plan", non-selectable; VM guard ignores re-selecting current. ✓
- Strings resourced; no hardcoded text; chevron reuses the existing icon. ✓
- Isolated worktree off main; iOS test compile in every gate; VM tests in commonTest. ✓
