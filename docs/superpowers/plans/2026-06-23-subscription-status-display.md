# Subscription status display — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.
>
> **WORKTREE:** All work happens in `/Users/danzucker/Desktop/Project/StitchPad-subscription-status` (branch `feat/subscription-status`). The main checkout holds the analytics branch — never touch it. `cd` into the worktree at the start of EVERY Bash command (the shell resets cwd between calls).

**Goal:** Show a subscription status line on the Settings plan card — "Expires 23 Sep 2026 · 3 months left" for gifts, "Renews 23 Sep 2026" for auto-renew, nothing for Free — by surfacing the already-existing server fields `subscriptionEndsAt`/`subscriptionRenews` (the client never reads them today).

**Architecture:** Task 1 plumbs the two fields from the user doc through `EntitlementsCalculator` (Lagos math, mirroring `welcomeDaysLeft`) onto `UserEntitlements`. Task 2 adds a pure `resolveSubscriptionStatus` mapper. Task 3 wires it into `SettingsState`/VM and renders the line on `PlanCardPaid`.

**Tech Stack:** KMP, Compose Multiplatform, kotlinx.datetime, JUnit5.

**Spec:** `docs/superpowers/specs/2026-06-23-subscription-status-display-design.md`.

---

## Task 1: Surface `subscriptionEndsAt`/`subscriptionRenews` on entitlements

**Files:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/UserEntitlements.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculator.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/entitlement/UserDocEntitlementsProvider.kt`
- Test: `composeApp/src/commonTest/.../core/domain/entitlement/EntitlementsCalculatorTest.kt` (extend)

READ all three + the existing `welcomeDaysLeft` block in `EntitlementsCalculator` and the `EntitlementsCalculatorTest` (how it injects a fixed `now` + Lagos TZ) first.

- [ ] **Step 1: `UserEntitlements` fields** — add after the welcome fields:
```kotlin
    /** Paid-subscription end date (gift end, or current paid period end). Null for Free / unset. */
    val subscriptionEndsAt: Instant? = null,
    /** True when the subscription auto-renews (Apple/Paystack); false for a gift. */
    val subscriptionRenews: Boolean = false,
    /** Calendar days (Africa/Lagos) until [subscriptionEndsAt], for a NON-renewing paid sub. Null otherwise. */
    val subscriptionDaysLeft: Int? = null,
    /** Whole calendar months (Africa/Lagos) until [subscriptionEndsAt], for a NON-renewing paid sub. Null otherwise. */
    val subscriptionMonthsLeft: Int? = null,
```

- [ ] **Step 2: `EntitlementsCalculator.calculate`** — add params `subscriptionEndsAt: Instant? = null, subscriptionRenews: Boolean = false` (defaults keep existing callers compiling). Compute the counts (mirror `welcomeDaysLeft`):
```kotlin
        val isPaid = tier != SubscriptionTier.FREE
        val nonRenewingPaidEnd = subscriptionEndsAt?.takeIf { isPaid && !subscriptionRenews }
        val subDaysLeft: Int? = nonRenewingPaidEnd?.let { end ->
            now.toLocalDateTime(timeZone).date.daysUntil(end.toLocalDateTime(timeZone).date)
        }
        val subMonthsLeft: Int? = nonRenewingPaidEnd?.let { end ->
            now.toLocalDateTime(timeZone).date.monthsUntil(end.toLocalDateTime(timeZone).date)
        }
```
Add these to the `UserEntitlements(...)` it returns:
```kotlin
            subscriptionEndsAt = subscriptionEndsAt,
            subscriptionRenews = subscriptionRenews,
            subscriptionDaysLeft = subDaysLeft,
            subscriptionMonthsLeft = subMonthsLeft,
```
Import `kotlinx.datetime.monthsUntil` (alongside the existing `daysUntil`).

- [ ] **Step 3: `UserDocEntitlementsProvider`** — `UserEntitlementsDoc` gains:
```kotlin
    val subscriptionEndsAt: Timestamp? = null,
    val subscriptionRenews: Boolean = false,
```
In `computeFromData`, convert + pass through (mirror `welcomeBonusAppliedAt`):
```kotlin
        val subEndsAt = data.subscriptionEndsAt?.let {
            Instant.fromEpochMilliseconds(it.toMilliseconds().toLong())
        }
        return EntitlementsCalculator.calculate(
            tier = SubscriptionTier.fromWire(data.subscriptionTier),
            welcomeBonusAppliedAt = seededAt,
            subscriptionEndsAt = subEndsAt,
            subscriptionRenews = data.subscriptionRenews,
            now = now(),
            timeZone = timeZone,
        )
```
`defaultEntitlements()` is unchanged (defaults → null/false).

- [ ] **Step 4: Extend `EntitlementsCalculatorTest`** — with a fixed `now` + Lagos TZ (match the existing welcome tests):
  - non-renewing paid, `subscriptionEndsAt ≈ now + 90 days` → `subscriptionDaysLeft ≈ 90`, `subscriptionMonthsLeft ≈ 3`, `subscriptionRenews == false`.
  - renewing paid (`subscriptionRenews = true`) → `subscriptionRenews == true`, `subscriptionDaysLeft == null`, `subscriptionMonthsLeft == null`.
  - Free (no subscription args) → all four subscription fields null/false.

- [ ] **Step 5: Verify**
  `cd /Users/danzucker/Desktop/Project/StitchPad-subscription-status` then:
  `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` → clean (iOS TEST compile REQUIRED).
  `./gradlew :composeApp:testDebugUnitTest --tests '*Entitlements*' -q` → pass.

- [ ] **Step 6: Commit**
```bash
cd /Users/danzucker/Desktop/Project/StitchPad-subscription-status
git add -A
git commit -m "feat(billing): surface subscriptionEndsAt/renews + days/months-left on entitlements"
```

---

## Task 2: Pure subscription-status resolver

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/domain/SubscriptionStatusResolver.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/settings/domain/SubscriptionStatusResolverTest.kt`

- [ ] **Step 1: The resolver**
```kotlin
package com.danzucker.stitchpad.feature.settings.domain

import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlin.time.Instant

enum class SubscriptionStatusKind { RENEWS, EXPIRES_MONTHS, EXPIRES_DAYS, EXPIRES_TOMORROW, EXPIRES_TODAY }

data class SubscriptionStatus(
    val kind: SubscriptionStatusKind,
    val endsAt: Instant,
    val count: Int = 0,
)

/** Resolves the Settings plan-card status line from entitlements; null = show nothing (Free / no end date). */
fun resolveSubscriptionStatus(entitlements: UserEntitlements): SubscriptionStatus? {
    val endsAt = entitlements.subscriptionEndsAt ?: return null
    if (entitlements.tier == SubscriptionTier.FREE) return null
    if (entitlements.subscriptionRenews) {
        return SubscriptionStatus(SubscriptionStatusKind.RENEWS, endsAt)
    }
    val days = entitlements.subscriptionDaysLeft ?: return null
    val months = entitlements.subscriptionMonthsLeft ?: 0
    return when {
        days <= 0 -> SubscriptionStatus(SubscriptionStatusKind.EXPIRES_TODAY, endsAt)
        days == 1 -> SubscriptionStatus(SubscriptionStatusKind.EXPIRES_TOMORROW, endsAt)
        months >= 1 -> SubscriptionStatus(SubscriptionStatusKind.EXPIRES_MONTHS, endsAt, months)
        else -> SubscriptionStatus(SubscriptionStatusKind.EXPIRES_DAYS, endsAt, days)
    }
}
```
(Use the SAME `Instant` type `UserEntitlements.subscriptionEndsAt` uses — check whether it's `kotlin.time.Instant` or `kotlinx.datetime.Instant` in `UserEntitlements.kt` and match it. The codebase is mid-migration to `kotlin.time.Instant`.)

- [ ] **Step 2: Tests** — `SubscriptionStatusResolverTest`. Build `UserEntitlements` fixtures (use its constructor with explicit subscription fields + a dummy `endsAt`). Assert:
  - Free (tier FREE) → null. Paid + `subscriptionEndsAt = null` → null.
  - `subscriptionRenews = true` → kind RENEWS.
  - non-renewing: (days 95, months 3) → EXPIRES_MONTHS, count 3; (days 12, months 0) → EXPIRES_DAYS, count 12; (days 1) → EXPIRES_TOMORROW; (days 0) → EXPIRES_TODAY; (days -2) → EXPIRES_TODAY; (days 31, months 1) → EXPIRES_MONTHS, count 1.

- [ ] **Step 3: Verify**
  `cd /Users/danzucker/Desktop/Project/StitchPad-subscription-status` then:
  `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` → clean.
  `./gradlew :composeApp:testDebugUnitTest --tests '*SubscriptionStatusResolver*' -q` → pass.

- [ ] **Step 4: Commit**
```bash
cd /Users/danzucker/Desktop/Project/StitchPad-subscription-status
git add -A
git commit -m "feat(billing): pure resolveSubscriptionStatus (renews/expires kinds)"
```

---

## Task 3: State + ViewModel + plan-card line

**Files:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsState.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/home/SettingsScreen.kt` (the `PlanCard(...)` call site)
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/presentation/components/PlanCard.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: State** — `SettingsState`: add `val subscriptionStatus: SubscriptionStatus? = null` (import the resolver's type).

- [ ] **Step 2: ViewModel** — in `SettingsViewModel`, wherever it maps `EntitlementsProvider`/entitlements → state (it already derives `subscriptionTier` from `entitlements.tier`), also set `subscriptionStatus = resolveSubscriptionStatus(entitlements)`. READ the VM to find the entitlements-collection block and add it to the same `_state.update { it.copy(...) }`.

- [ ] **Step 3: Strings** (`strings.xml`, positional `%1$s`/`%2$d`, `&apos;`):
```xml
    <string name="settings_plan_renews_fmt">Renews %1$s</string>
    <string name="settings_plan_expires_months_one">Expires %1$s · %2$d month left</string>
    <string name="settings_plan_expires_months_other">Expires %1$s · %2$d months left</string>
    <string name="settings_plan_expires_days">Expires %1$s · %2$d days left</string>
    <string name="settings_plan_expires_tomorrow">Expires tomorrow</string>
    <string name="settings_plan_expires_today">Expires today</string>
```

- [ ] **Step 4: PlanCard** — `PlanCard` gains `subscriptionStatus: SubscriptionStatus? = null`, passed into `PlanCardPaid` (which already receives `tier`, `onUpgradeClick`). At the `SettingsScreen` call site, pass `subscriptionStatus = state.subscriptionStatus`.
  In `PlanCardPaid`, inside the weighted `Column`, AFTER the "Unlimited customers" `Text` and BEFORE the existing `if (canUpgrade)` upgrade-hint block, add:
```kotlin
        subscriptionStatus?.let { status ->
            Spacer(Modifier.height(DesignTokens.space1))
            Text(
                text = subscriptionStatusText(status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```
  Add a `@Composable private fun subscriptionStatusText(status: SubscriptionStatus): String` that formats the date + maps kind → string:
```kotlin
@Composable
private fun subscriptionStatusText(status: SubscriptionStatus): String {
    val dateText = formatPlanDate(status.endsAt)
    return when (status.kind) {
        SubscriptionStatusKind.RENEWS -> stringResource(Res.string.settings_plan_renews_fmt, dateText)
        SubscriptionStatusKind.EXPIRES_MONTHS -> stringResource(
            if (status.count == 1) Res.string.settings_plan_expires_months_one
            else Res.string.settings_plan_expires_months_other,
            dateText, status.count,
        )
        SubscriptionStatusKind.EXPIRES_DAYS ->
            stringResource(Res.string.settings_plan_expires_days, dateText, status.count)
        SubscriptionStatusKind.EXPIRES_TOMORROW -> stringResource(Res.string.settings_plan_expires_tomorrow)
        SubscriptionStatusKind.EXPIRES_TODAY -> stringResource(Res.string.settings_plan_expires_today)
    }
}
```
  `formatPlanDate(instant)` → "d MMM yyyy" in the system TZ. Reuse an existing date util if one exists (grep `MONTH_ABBREV` / a shared formatter); else inline a small one (mirror `PipelineOrderRow`'s `MONTH_ABBREV` + `toLocalDateTime(TimeZone.currentSystemDefault()).date`). Match the `Instant` type used by `SubscriptionStatus.endsAt`.

- [ ] **Step 5: Previews** — update `PlanCard` PRO + ATELIER previews to pass a sample `subscriptionStatus` (e.g. `SubscriptionStatus(EXPIRES_MONTHS, <some Instant>, 3)`) so the line renders in preview.

- [ ] **Step 6: Verify**
  `cd /Users/danzucker/Desktop/Project/StitchPad-subscription-status` then:
  `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` → clean (iOS TEST compile REQUIRED; watch ImportOrdering + positional-arg strings).
  `./gradlew :composeApp:testDebugUnitTest --tests '*Settings*' --tests '*PlanCard*' --tests '*Subscription*' --tests '*Entitlements*' -q` → pass.

- [ ] **Step 7: Commit**
```bash
cd /Users/danzucker/Desktop/Project/StitchPad-subscription-status
git add -A
git commit -m "feat(billing): show subscription expiry/renewal on Settings plan card"
```

---

## Manual smoke test (device — Daniel is QA)
1. Gifted Pro (non-renewing, `subscriptionEndsAt` set) → plan card shows "Expires <date> · N months left".
2. `subscriptionEndsAt` within a month → "N days left" / "Expires tomorrow" / "Expires today".
3. Auto-renewing paid → "Renews <date>".
4. Free → no status line; paid with no `subscriptionEndsAt` → no line.

## Self-review notes
- Task 1 plumbs the data (calculator Lagos math + provider read) + calculator test. ✓
- Task 2 pure resolver + tests. ✓
- Task 3 state/VM/UI + strings (positional, singular/plural) + previews; upgrade hint from #215 preserved above it. ✓
- Worktree-isolated; iOS test compile in every gate; resolver + calculator tests in commonTest. ✓
