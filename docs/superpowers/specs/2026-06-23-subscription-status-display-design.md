# Subscription status display (Settings plan card) — design

**Status:** Design approved (2026-06-23)
**Branch / worktree:** `feat/subscription-status` at `/Users/danzucker/Desktop/Project/StitchPad-subscription-status` (off `main`, isolated from the analytics branch).
**Source:** tester feedback on the gift flow — *"Is there no way to check, on the app, how many months of subscription I have / when it's expiring?"* A gifted (or paid) user has no in-app view of their subscription end date or time remaining.

## Goal
Show, on the Settings plan card, a subscription status line:
- **Gift / non-renewing:** `Expires 23 Sep 2026 · 3 months left` (→ "12 days left" / "Expires tomorrow" / "Expires today" near the end).
- **Auto-renewing paid:** `Renews 23 Sep 2026`.
- **Free / paid-without-an-end-date:** nothing (unchanged).

## Key finding
`subscriptionEndsAt` (Timestamp) and `subscriptionRenews` (Bool) exist on the server user doc (gift billing writes them — `functions/src/billing/giftBilling.ts`), but **the client never reads them**: `UserDocEntitlementsProvider` only reads `subscriptionTier` + `welcomeBonusAppliedAt`, and `UserEntitlements` only exposes welcome-window dates. So this is purely additive: read the two fields → surface on entitlements → derive a display → render.

## Out of scope
A "Manage subscription" screen; cancel/restore flows; the welcome-window banner (separate, unchanged).

---

## 1. Surface the data on `UserEntitlements`

`core/domain/entitlement/UserEntitlements.kt` — add (parallel to the welcome fields):
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

## 2. Compute them in `EntitlementsCalculator`

`core/domain/entitlement/EntitlementsCalculator.kt` — `calculate(...)` already takes `now: Instant` + `timeZone`. Add params `subscriptionEndsAt: Instant? = null`, `subscriptionRenews: Boolean = false`, and compute the relative counts mirroring the existing `welcomeDaysLeft` block:
```kotlin
        val isPaid = tier != SubscriptionTier.FREE
        val subDaysLeft: Int? = subscriptionEndsAt
            ?.takeIf { isPaid && !subscriptionRenews }
            ?.let { end ->
                val nowLocal = now.toLocalDateTime(timeZone).date
                val endLocal = end.toLocalDateTime(timeZone).date
                nowLocal.daysUntil(endLocal)
            }
        val subMonthsLeft: Int? = subscriptionEndsAt
            ?.takeIf { isPaid && !subscriptionRenews }
            ?.let { end ->
                val nowLocal = now.toLocalDateTime(timeZone).date
                val endLocal = end.toLocalDateTime(timeZone).date
                nowLocal.monthsUntil(endLocal)
            }
```
Pass `subscriptionEndsAt = subscriptionEndsAt`, `subscriptionRenews = subscriptionRenews`, `subscriptionDaysLeft = subDaysLeft`, `subscriptionMonthsLeft = subMonthsLeft` into the `UserEntitlements(...)` it returns. (Imports: `kotlinx.datetime.monthsUntil` alongside the existing `daysUntil`.)

## 3. Read the fields in `UserDocEntitlementsProvider`

`core/data/entitlement/UserDocEntitlementsProvider.kt`:
- `UserEntitlementsDoc` (the `@Serializable` internal DTO) — add:
```kotlin
    val subscriptionEndsAt: Timestamp? = null,
    val subscriptionRenews: Boolean = false,
```
- `computeFromData(data)` — convert the timestamp the same way as `welcomeBonusAppliedAt`, and pass through:
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
- `defaultEntitlements()` keeps the defaults (no subscription args → null/false).

## 4. Pure display resolver

`feature/settings/domain/SubscriptionStatusResolver.kt` (clock-free — consumes the already-computed entitlement fields, so the Settings VM needs no clock):
```kotlin
enum class SubscriptionStatusKind { RENEWS, EXPIRES_MONTHS, EXPIRES_DAYS, EXPIRES_TOMORROW, EXPIRES_TODAY }

data class SubscriptionStatus(
    val kind: SubscriptionStatusKind,
    val endsAt: Instant,  // for date formatting in the UI
    val count: Int = 0,   // months (EXPIRES_MONTHS) or days (EXPIRES_DAYS)
)

fun resolveSubscriptionStatus(entitlements: UserEntitlements): SubscriptionStatus? {
    val endsAt = entitlements.subscriptionEndsAt ?: return null
    if (entitlements.tier == SubscriptionTier.FREE) return null
    if (entitlements.subscriptionRenews) return SubscriptionStatus(RENEWS, endsAt)
    val days = entitlements.subscriptionDaysLeft ?: return null
    val months = entitlements.subscriptionMonthsLeft ?: 0
    return when {
        days <= 0 -> SubscriptionStatus(EXPIRES_TODAY, endsAt)
        days == 1 -> SubscriptionStatus(EXPIRES_TOMORROW, endsAt)
        months >= 1 -> SubscriptionStatus(EXPIRES_MONTHS, endsAt, months)
        else -> SubscriptionStatus(EXPIRES_DAYS, endsAt, days)
    }
}
```

## 5. State + ViewModel

`SettingsState` — add `val subscriptionStatus: SubscriptionStatus? = null`.
`SettingsViewModel` — wherever it maps `EntitlementsProvider` → state (already reads `entitlements.tier` for `subscriptionTier`), also set `subscriptionStatus = resolveSubscriptionStatus(entitlements)`.

## 6. Render on the plan card

`feature/settings/presentation/components/PlanCard.kt`:
- `PlanCard` takes a new `subscriptionStatus: SubscriptionStatus? = null` param (sourced from `SettingsState`) and passes it to `PlanCardPaid`.
- `PlanCardPaid` renders a status line inside the weighted `Column`, after "Unlimited customers" and BEFORE the existing Pro upgrade hint:
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
  `subscriptionStatusText(status)` is a `@Composable` helper mapping `kind` → string, formatting `endsAt` as a date:
  - `RENEWS` → `stringResource(settings_plan_renews_fmt, dateText)`
  - `EXPIRES_MONTHS` → singular/plural by `count == 1` (`settings_plan_expires_months_one` / `_other`), args `(dateText, count)`
  - `EXPIRES_DAYS` → `settings_plan_expires_days` (always ≥2), args `(dateText, count)`
  - `EXPIRES_TOMORROW` → `settings_plan_expires_tomorrow`
  - `EXPIRES_TODAY` → `settings_plan_expires_today`
  - `dateText` = `endsAt` formatted "d MMM yyyy" (e.g. "23 Sep 2026") in the user TZ — reuse/inline a month-abbrev formatter (see `PipelineOrderRow.MONTH_ABBREV` pattern; if a shared date util exists, use it).

### Strings (`strings.xml`, positional args `%1$s`/`%2$d`, `&apos;` not `\'`)
```xml
    <string name="settings_plan_renews_fmt">Renews %1$s</string>
    <string name="settings_plan_expires_months_one">Expires %1$s · %2$d month left</string>
    <string name="settings_plan_expires_months_other">Expires %1$s · %2$d months left</string>
    <string name="settings_plan_expires_days">Expires %1$s · %2$d days left</string>
    <string name="settings_plan_expires_tomorrow">Expires tomorrow</string>
    <string name="settings_plan_expires_today">Expires today</string>
```

---

## 7. Testing

- **`SubscriptionStatusResolverTest`** (new, commonTest): build `UserEntitlements` fixtures and assert the kind/count:
  - Free → null; paid + `subscriptionEndsAt == null` → null.
  - renewing → RENEWS.
  - non-renewing: monthsLeft 3 / daysLeft 95 → EXPIRES_MONTHS(3); monthsLeft 0 / daysLeft 12 → EXPIRES_DAYS(12); daysLeft 1 → EXPIRES_TOMORROW; daysLeft 0 → EXPIRES_TODAY; daysLeft -2 (expired but still paid) → EXPIRES_TODAY.
  - monthsLeft 1 → EXPIRES_MONTHS(1) (drives singular string).
- **`EntitlementsCalculatorTest`** (extend): a non-renewing paid entitlement with `subscriptionEndsAt` ~90 days out yields `subscriptionDaysLeft ≈ 90` + `subscriptionMonthsLeft ≈ 3`; a renewing one yields `subscriptionRenews = true`, null day/month counts; Free yields nulls. (Use a fixed `now` + Lagos TZ like the existing welcome tests.)
- The plan-card line is visual → manual smoke; update the `PlanCard` PRO/ATELIER previews to pass a sample `subscriptionStatus` (e.g. `EXPIRES_MONTHS`, count 3).

## 8. Manual smoke test (device — Daniel is QA)
1. A **gifted Pro** account (non-renewing, `subscriptionEndsAt` set) → Settings plan card shows **"Expires <date> · N months left"** under "Unlimited customers".
2. An account with `subscriptionEndsAt` within a month → "N days left" / "Expires tomorrow" / "Expires today".
3. An **auto-renewing** paid account → "Renews <date>".
4. **Free** tier → no status line (unchanged). Paid with no `subscriptionEndsAt` → no line (graceful).

## 9. Self-review checks
- Reads `subscriptionEndsAt`/`subscriptionRenews` (DTO → calculator → entitlements); Lagos math mirrors `welcomeDaysLeft`; no clock injected into the Settings VM. ✓
- Pure resolver → kind/count, unit-tested; UI maps to strings (positional args, singular/plural, `&apos;`). ✓
- Gift = Expires + countdown; auto-renew = Renews; Free / no-end-date = nothing. ✓
- Plan-card upgrade hint/chevron from #215 preserved (status line sits above it). ✓
- Worktree-isolated; iOS test compile in every gate; resolver + calculator tests in commonTest. ✓
