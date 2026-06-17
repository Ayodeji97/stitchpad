# Order-Detail Discount Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface the whole-order discount across the order-detail hero card, payment card, order-list row, and share-sheet preview, with one consistent rule so the totals never disagree.

**Architecture:** No domain/data changes — `Order.payableTotal` (= `totalPrice − discount`) and `Order.discount` already exist and round-trip. Add one reusable presentation component `StrikethroughPrice` (+ a pure `shouldShowStruckGross` helper) that renders the net total, plus a struck-through gross only when `discount > 0`. Every surface reads `payableTotal`/`discount` and recomputes nothing.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material3, Koin, kotlin.test. Spec: `docs/superpowers/specs/2026-06-17-order-detail-discount-visibility-design.md`.

**Branch:** `feat/discount-visibility` (already created, spec committed).

**Verify gates (run from repo root):**
```
./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest
```
Note: capture the exit code directly (`echo $?` right after), not through a pipe — piped gradle hides failures.

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `ui/components/StrikethroughPrice.kt` | Reusable net+struck-gross price renderer + pure `shouldShowStruckGross` | **Create** |
| `commonTest/.../ui/components/StrikethroughPriceTest.kt` | Unit-test the pure decision | **Create** |
| `feature/order/presentation/detail/components/OrderHeroCard.kt` | Hero total line | Add `discount` param (×2), use `StrikethroughPrice`, discounted preview |
| `feature/order/presentation/list/OrderListScreen.kt` | List-row amount column | Struck gross above net when discounted, discounted preview |
| `feature/order/presentation/detail/OrderDetailScreen.kt` | Wiring | Pass `discount` to hero + payment card; share-sheet preview total → `payableTotal` |

---

## Task 1: `StrikethroughPrice` component + pure helper

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StrikethroughPrice.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/ui/components/StrikethroughPriceTest.kt`

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/ui/components/StrikethroughPriceTest.kt`:

```kotlin
package com.danzucker.stitchpad.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrikethroughPriceTest {

    @Test
    fun zeroDiscount_hidesStruckGross() {
        // The dominant case: no discount -> only the net total shows, no strikethrough.
        assertFalse(shouldShowStruckGross(0.0))
    }

    @Test
    fun negativeDiscount_hidesStruckGross() {
        // Defensive: a bad/negative value must never trigger a strikethrough.
        assertFalse(shouldShowStruckGross(-5_000.0))
    }

    @Test
    fun positiveDiscount_showsStruckGross() {
        assertTrue(shouldShowStruckGross(30_000.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StrikethroughPriceTest*"`
Expected: FAIL — `shouldShowStruckGross` is unresolved (compilation error).

- [ ] **Step 3: Write the component + helper**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StrikethroughPrice.kt`:

```kotlin
package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.ui.theme.DesignTokens

/** True when a struck-through gross price should be shown next to the net total. */
fun shouldShowStruckGross(discount: Double): Boolean = discount > 0.0

/**
 * Renders [netPrice] as the headline figure. When [discount] > 0 it also shows the
 * struck-through [grossPrice] (in a muted labelSmall) so the discount is visible.
 * [stacked] = true puts the struck gross ABOVE the net (end-aligned column, for tight
 * list rows); false puts them inline in a row (for the hero total line). When there is
 * no discount the output is a single net Text, byte-for-byte like the pre-feature UI.
 */
@Composable
fun StrikethroughPrice(
    grossPrice: Double,
    netPrice: Double,
    discount: Double,
    netStyle: TextStyle,
    netColor: Color,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val net: @Composable () -> Unit = {
        Text(text = "₦${formatPrice(netPrice)}", style = netStyle, color = netColor)
    }
    if (!shouldShowStruckGross(discount)) {
        Row(modifier = modifier) { net() }
        return
    }
    val struck: @Composable () -> Unit = {
        Text(
            text = "₦${formatPrice(grossPrice)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
        )
    }
    if (stacked) {
        Column(modifier = modifier, horizontalAlignment = Alignment.End) {
            struck()
            net()
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            struck()
            net()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*StrikethroughPriceTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/StrikethroughPrice.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/ui/components/StrikethroughPriceTest.kt
git commit -m "feat(ui): StrikethroughPrice component for net + struck gross totals"
```

---

## Task 2: Payment card — feed it the discount (the core bug fix)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt` (the `OrderPaymentCard(...)` call, ~line 1163)

`OrderPaymentCard` already renders the Subtotal/Discount/Total breakdown when `discount > 0`; it is simply never passed the value. This is a one-argument fix.

- [ ] **Step 1: Add the `discount` argument**

In `OrderDetailScreen.kt`, find:

```kotlin
            OrderPaymentCard(
                totalPrice = order.totalPrice,
                payments = order.payments,
                isExpanded = state.isPaymentHistoryExpanded,
                onToggleExpanded = { onAction(OrderDetailAction.OnPaymentHistoryToggle) },
                onRecordPaymentClick = { onAction(OrderDetailAction.OnRecordPaymentClick) },
            )
```

Change to:

```kotlin
            OrderPaymentCard(
                totalPrice = order.totalPrice,
                discount = order.discount,
                payments = order.payments,
                isExpanded = state.isPaymentHistoryExpanded,
                onToggleExpanded = { onAction(OrderDetailAction.OnPaymentHistoryToggle) },
                onRecordPaymentClick = { onAction(OrderDetailAction.OnRecordPaymentClick) },
            )
```

- [ ] **Step 2: Verify it compiles + tests still pass**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. (`OrderPaymentCard` already accepts `discount: Double = 0.0`.)

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "fix(order-detail): pass discount to payment card so balance nets the discount"
```

---

## Task 3: Hero card — struck gross next to the net total

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt` (hero call site, ~line 1117)

- [ ] **Step 1: Add `discount` param to the public composable**

In `OrderHeroCard.kt`, in the public `OrderHeroCard(...)` parameter list, add `discount` right after `balanceRemaining`:

```kotlin
    totalPrice: Double,
    balanceRemaining: Double,
    discount: Double,
    cta: CtaPair,
```

- [ ] **Step 2: Thread `discount` into `HeroDetails`**

Still in `OrderHeroCard.kt`, in the `HeroDetails(...)` call inside `OrderHeroCard`, add `discount = discount,` after `balanceRemaining = balanceRemaining,`:

```kotlin
            HeroDetails(
                garmentTypeIcon = garmentTypeIcon,
                garmentName = garmentName,
                customerName = customerName,
                status = status,
                subStatus = subStatus,
                priority = priority,
                isOverdue = isOverdue,
                dueLabel = dueLabel,
                totalPrice = totalPrice,
                balanceRemaining = balanceRemaining,
                discount = discount,
                onSetDeadlineClick = onSetDeadlineClick,
            )
```

And add the param to the private `HeroDetails(...)` signature, after `balanceRemaining: Double,`:

```kotlin
    totalPrice: Double,
    balanceRemaining: Double,
    discount: Double,
    onSetDeadlineClick: () -> Unit,
```

- [ ] **Step 3: Replace the total price Text with `StrikethroughPrice`**

In `HeroDetails`, the total row currently is:

```kotlin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DesignTokens.space2),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.order_detail_total_price),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = " ₦${formatPrice(totalPrice)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

Replace the second `Text(...)` (the price) with a spacer + `StrikethroughPrice`:

```kotlin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = DesignTokens.space2),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.order_detail_total_price),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(DesignTokens.space1))
            StrikethroughPrice(
                grossPrice = totalPrice,
                netPrice = totalPrice - discount,
                discount = discount,
                netStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                ),
                netColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
```

Add imports at the top of `OrderHeroCard.kt` if not already present:

```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.danzucker.stitchpad.ui.components.StrikethroughPrice
```

(`formatPrice` may now be unused in this file — if the compiler warns/detekt flags `UnusedImport`, remove `import com.danzucker.stitchpad.core.sharing.formatPrice`. Check: it is still used by `BalanceSection`, so keep it.)

- [ ] **Step 4: Thread `discount` from the call site**

In `OrderDetailScreen.kt`, the `OrderHeroCard(...)` call (~line 1117) currently passes `totalPrice`/`balanceRemaining`. Add `discount = order.discount,` after `balanceRemaining = order.balanceRemaining,`:

```kotlin
                totalPrice = order.totalPrice,
                balanceRemaining = order.balanceRemaining,
                discount = order.discount,
                cta = cta,
```

- [ ] **Step 5: Make existing previews compile, then add a discounted one**

`discount` is a new **required** param, so every existing `OrderHeroCard(...)` preview call in this file must get `discount = 0.0,` (inserted after its `balanceRemaining = ...,` line) or the file won't compile. There are several preview functions (`OrderHeroCardInProgressLightPreview`, `OrderHeroCardReadyLightPreview`, dark/overdue variants, etc.) — add `discount = 0.0,` to each.

Then add ONE new discounted preview. Copy the existing `OrderHeroCardInProgressLightPreview` **verbatim** and change only: the function name, `totalPrice`, `balanceRemaining`, the new `discount` line, and the `resolvePrimaryCta(... balanceRemaining = ...)` argument. Result:

```kotlin
@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderHeroCardDiscountedLightPreview() {
    StitchPadTheme {
        OrderHeroCard(
            garmentTypeIcon = Icons.Default.Build,
            garmentName = "Senator",
            customerName = "Oluwaseun Adesina",
            status = OrderStatus.IN_PROGRESS,
            subStatus = OrderSubStatus.SEWING,
            priority = OrderPriority.URGENT,
            isOverdue = false,
            overdueDaysAgo = 0,
            dueLabel = UiText.DynamicString("Due 18 Jun"),
            totalPrice = 380_000.0,
            balanceRemaining = 310_000.0,
            discount = 30_000.0,
            cta = resolvePrimaryCta(
                status = OrderStatus.IN_PROGRESS,
                subStatus = OrderSubStatus.SEWING,
                isOverdue = false,
                balanceRemaining = 310_000.0,
            ),
            onPrimaryCta = {},
            onSecondaryCta = {},
            onSetDeadlineClick = {},
        )
    }
}
```

This is identical to the existing in-progress preview except for the name, the three price-related fields, and the `discount` line — so the constructor/enum/`resolvePrimaryCta` shapes are guaranteed correct.

- [ ] **Step 6: Verify build, detekt, iOS, tests**

Run: `./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest` then `echo $?`
Expected: BUILD SUCCESSFUL, exit 0.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/components/OrderHeroCard.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "feat(order-detail): show struck gross + net total on the hero card when discounted"
```

---

## Task 4: Order-list row — struck gross above the net total

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt` (amount column, ~line 688)

- [ ] **Step 1: Replace the net-total Text with `StrikethroughPrice` (stacked)**

Find the right-aligned amount column (~line 688):

```kotlin
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "₦${formatPrice(order.payableTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            PaymentStatusText(depositPaid = order.depositPaid, amountOwed = order.payableTotal)
        }
```

Replace the first `Text(...)` with `StrikethroughPrice(stacked = true)`:

```kotlin
        Column(horizontalAlignment = Alignment.End) {
            StrikethroughPrice(
                grossPrice = order.totalPrice,
                netPrice = order.payableTotal,
                discount = order.discount,
                netStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                netColor = MaterialTheme.colorScheme.onSurface,
                stacked = true,
            )
            Spacer(Modifier.height(2.dp))
            PaymentStatusText(depositPaid = order.depositPaid, amountOwed = order.payableTotal)
        }
```

Add the import at the top of `OrderListScreen.kt`:

```kotlin
import com.danzucker.stitchpad.ui.components.StrikethroughPrice
```

(Leave the existing `formatPrice` import — it is still used elsewhere in the file. If detekt flags it as unused after this change, remove it.)

- [ ] **Step 2: Add a discounted order to a preview**

In `OrderListScreen.kt`, find an existing list/row `@Preview` that builds an `Order(...)` with `totalPrice = 40_000.0`. Add ONE preview order (or extend an existing preview list) where `totalPrice` and `discount` differ, e.g. a copy with:

```kotlin
                        totalPrice = 380_000.0,
                        discount = 30_000.0,
```

added to the `Order(...)` constructor (keep all other fields the same as the neighbouring preview order). This renders a row with the struck ₦380,000 above ₦350,000.

> Use the exact `Order(...)` field set already present in that preview — copy an existing order literal and change only `totalPrice` and add `discount`.

- [ ] **Step 3: Verify build, detekt, iOS, tests**

Run: `./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest` then `echo $?`
Expected: BUILD SUCCESSFUL, exit 0.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/list/OrderListScreen.kt
git commit -m "feat(order-list): show struck gross above net total on discounted rows"
```

---

## Task 5: Share-sheet preview — total uses payable

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt` (~line 565)

- [ ] **Step 1: Change `totalFormatted` to net**

Find:

```kotlin
            totalFormatted = state.order?.let { "₦${formatPrice(it.totalPrice)}" },
```

Change to:

```kotlin
            totalFormatted = state.order?.let { "₦${formatPrice(it.payableTotal)}" },
```

(`balanceFormatted` already uses `balanceRemaining` — leave it. The shared receipt content is produced by `ReceiptFormatter` and already itemizes the discount — do NOT touch it.)

- [ ] **Step 2: Verify**

Run: `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest` then `echo $?`
Expected: BUILD SUCCESSFUL, exit 0.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailScreen.kt
git commit -m "fix(order-detail): share-sheet preview total reflects the discount"
```

---

## Task 6: Full verification + PR

- [ ] **Step 1: Run the complete gate set**

Run:
```bash
./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest
echo "EXIT=$?"
```
Expected: `BUILD SUCCESSFUL`, `EXIT=0`.

- [ ] **Step 2: Manual smoke test (Android + iOS)**

Using a discounted order (Subtotal ₦380,000, discount ₦30,000, ₦40,000 paid):
1. Hero card: Total shows ~~₦380,000~~ ₦350,000; Balance due ₦310,000.
2. Payment card: Total ₦350,000; Subtotal ₦380,000 / Discount −₦30,000 line; Balance ₦310,000. Hero and payment balances **match**.
3. Order list: that order's row shows a small struck ₦380,000 above ₦350,000.
4. Share sheet: preview total reads ₦350,000; shared receipt itemizes the discount.
5. A **non-discounted** order: hero, payment card, and list row look exactly as before (no strikethrough, no breakdown).
6. Dashboard: unchanged (no amounts shown).

- [ ] **Step 3: Push + open PR**

```bash
git push -u origin feat/discount-visibility
gh pr create --base main --head feat/discount-visibility \
  --title "feat(order): surface the whole-order discount across order detail + list" \
  --body "$(cat <<'BODY'
## What
The whole-order discount was captured on the order form and shown on the edit summary and receipt, but was invisible everywhere else — and two surfaces disagreed on the total. For one order (Subtotal ₦380,000, discount ₦30,000, ₦40,000 paid) the hero card showed Balance ₦310,000 while the payment card showed ₦340,000 — a gap of exactly the discount.

## Root cause
- Payment card call site didn't pass `discount`, so the card defaulted it to 0 and derived balance off the gross subtotal.
- Hero card rendered gross `totalPrice` as "Total" but net `balanceRemaining` as the balance.

## Fix (one rule)
Every surface shows `payableTotal` and a struck-through gross only when `discount > 0`; no surface recomputes its own total.
- New `StrikethroughPrice` component (+ pure `shouldShowStruckGross`, unit-tested).
- Hero card: ~~₦380,000~~ ₦350,000.
- Payment card: pass `discount` → existing Subtotal/Discount/Total breakdown renders; Balance ₦310,000.
- Order-list row: struck gross above the net total when discounted.
- Share-sheet preview total → `payableTotal`.
- Dashboard untouched (shows no money); receipt/Reports unchanged.

## Tests
`StrikethroughPriceTest`; discounted previews for hero/payment/list. Gates green: assembleDebug + detekt + iOS compile + testDebugUnitTest.

## Smoke test
See plan `docs/superpowers/plans/2026-06-17-order-detail-discount-visibility.md` (hero/payment balances now match; non-discounted orders look unchanged).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
BODY
)"
```

---

## Notes for the implementer

- **Dominant case must not regress:** when `discount == 0`, `StrikethroughPrice` renders a single net `Text` — the hero, list row, and payment card must look identical to `main`. Eyeball a non-discounted order in the previews.
- **No new string resources** — prices are formatted via `formatPrice`, not user-facing copy. Do not hardcode any label strings (project rule).
- **No domain/data/DTO/mapper changes** — `payableTotal` and `discount` already exist on `Order` and round-trip through Firestore. If you find yourself editing a mapper, stop; you've gone out of scope.
- **Detekt `TooManyFunctions`:** adding a preview to `OrderHeroCard.kt` or `OrderListScreen.kt` may trip the 20-function/file limit. If so, add `@file:Suppress("TooManyFunctions")` at the top (established convention for cohesive screen/component files) — do not split the file.
- **Piped exit codes:** never judge a gradle run by `... | tail`; capture `echo $?` directly.
