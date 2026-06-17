# Order-form summary reflects the discount — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The order form's live Order summary card shows Subtotal → Discount (X%) → Total when a discount is entered, and is unchanged (just Total) when there's no discount.

**Architecture:** A pure `discountBreakdown(subtotal, discountInput)` helper in `feature/order/domain/` (unit-tested) returns `(amount, percent, payable)`; the `OrderTotalSummary` composable computes `subtotal` from its priced lines (as today), feeds it the entered `discount`, and renders the breakdown. The discount is shown raw (not clamped) with the Total floored at ₦0, consistent with #178's reject-over-subtotal save behaviour.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-06-17-order-summary-discount-design.md`
**Branch:** `feat/order-summary-discount` (off `feat/receipt-discount` / PR #178)

**Conventions:** no hardcoded user-facing strings (compose.resources); `%%` for a literal percent next to a `%1$d` arg; the discount minus sign is U+2212 `−` (matches the receipt). iOS compile before declaring done.

---

## File Structure

**Create:**
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/DiscountBreakdown.kt` — pure helper + data class.
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/DiscountBreakdownTest.kt` — unit tests.

**Modify:**
- `feature/order/presentation/form/OrderFormScreen.kt` — `OrderTotalSummary` signature + breakdown rows + call site + import + preview.
- `composeApp/src/commonMain/composeResources/values/strings.xml` — 3 new strings.

---

## Task 1: `discountBreakdown` pure helper (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/DiscountBreakdown.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/DiscountBreakdownTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.feature.order.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DiscountBreakdownTest {

    @Test
    fun noDiscountString() {
        val b = discountBreakdown(subtotal = 108_000.0, discountInput = "")
        assertEquals(0.0, b.amount)
        assertEquals(0, b.percent)
        assertEquals(108_000.0, b.payable)
    }

    @Test
    fun zeroDiscount() {
        val b = discountBreakdown(subtotal = 108_000.0, discountInput = "0")
        assertEquals(0.0, b.amount)
        assertEquals(108_000.0, b.payable)
    }

    @Test
    fun normalDiscountRoundsPercent() {
        val b = discountBreakdown(subtotal = 108_000.0, discountInput = "20000")
        assertEquals(20_000.0, b.amount)
        assertEquals(19, b.percent) // 18.5% rounds to 19
        assertEquals(88_000.0, b.payable)
    }

    @Test
    fun discountEqualsSubtotalIsFreeOrder() {
        val b = discountBreakdown(subtotal = 30_000.0, discountInput = "30000")
        assertEquals(30_000.0, b.amount)
        assertEquals(100, b.percent)
        assertEquals(0.0, b.payable)
    }

    @Test
    fun discountOverSubtotalKeepsRawAmountAndFloorsPayable() {
        val b = discountBreakdown(subtotal = 30_000.0, discountInput = "50000")
        assertEquals(50_000.0, b.amount) // raw, not clamped
        assertEquals(0.0, b.payable)     // floored at 0
        assertEquals(167, b.percent)     // 166.67 rounds to 167 → card uses the plain "Discount" label
    }

    @Test
    fun zeroSubtotalNoDivideByZero() {
        val b = discountBreakdown(subtotal = 0.0, discountInput = "5000")
        assertEquals(0, b.percent)
        assertEquals(0.0, b.payable)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DiscountBreakdownTest*"`
Expected: FAIL — `discountBreakdown` / `DiscountBreakdown` unresolved.

- [ ] **Step 3: Implement the helper**

Create `DiscountBreakdown.kt`:

```kotlin
package com.danzucker.stitchpad.feature.order.domain

import kotlin.math.roundToInt

/**
 * Breakdown of a whole-order discount for the order-form summary. [amount] is the raw typed
 * discount floored at 0 — NOT clamped to the subtotal, so an over-subtotal entry stays visible
 * (the save path rejects it rather than clamping). [payable] floors at 0 so the displayed Total
 * never goes negative. [percent] is `round(amount / subtotal * 100)`, 0 when subtotal is 0.
 */
data class DiscountBreakdown(
    val amount: Double,
    val percent: Int,
    val payable: Double,
)

fun discountBreakdown(subtotal: Double, discountInput: String): DiscountBreakdown {
    val amount = (discountInput.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val percent = if (subtotal > 0.0) (amount / subtotal * 100).roundToInt() else 0
    val payable = (subtotal - amount).coerceAtLeast(0.0)
    return DiscountBreakdown(amount = amount, percent = percent, payable = payable)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DiscountBreakdownTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/DiscountBreakdown.kt \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/order/domain/DiscountBreakdownTest.kt
git commit -m "feat(order): discountBreakdown helper for the order-form summary"
```

---

## Task 2: Render the breakdown in `OrderTotalSummary`

**Files:**
- Modify: `feature/order/presentation/form/OrderFormScreen.kt` (composable ~L1180, call site ~L1115)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add the 3 strings**

In `strings.xml`, next to the existing `order_form_summary_total`:

```xml
    <string name="order_form_summary_subtotal">Subtotal</string>
    <string name="order_form_summary_discount">Discount (%1$d%%)</string>
    <string name="order_form_summary_discount_plain">Discount</string>
```

- [ ] **Step 2: Add the import + generated-resource imports**

In `OrderFormScreen.kt` add:

```kotlin
import com.danzucker.stitchpad.feature.order.domain.discountBreakdown
import stitchpad.composeapp.generated.resources.order_form_summary_subtotal
import stitchpad.composeapp.generated.resources.order_form_summary_discount
import stitchpad.composeapp.generated.resources.order_form_summary_discount_plain
```

Place each alphabetically within its existing import group (the `stitchpad.composeapp.generated.resources.order_form_summary_*` imports are already grouped near `order_form_summary_item_qty`/`order_form_summary_title`/`order_form_summary_total`; `discount`/`discount_plain` sort before `item_qty`, `subtotal` sorts after `item_qty`). Fix ordering with detekt in Step 7.

- [ ] **Step 3: Change the composable signature + call site**

Signature (~L1180):

```kotlin
private fun OrderTotalSummary(items: List<OrderItemFormState>, discount: String) {
```

Call site (~L1115):

```kotlin
        OrderTotalSummary(items = state.items, discount = state.discount)
```

- [ ] **Step 4: Compute subtotal + breakdown**

Replace the line `val grandTotal = lines.sumOf { it.unit * it.qty }` (~L1204) with:

```kotlin
    val subtotal = lines.sumOf { it.unit * it.qty }
    val breakdown = discountBreakdown(subtotal, discount)
```

- [ ] **Step 5: Insert the Subtotal + Discount rows before the Total row**

The current code (after the items loop) is:

```kotlin
            Spacer(Modifier.height(DesignTokens.space2))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(DesignTokens.space2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.order_form_summary_total),
                    ...
                )
                Text(
                    text = "₦${formatPrice(grandTotal)}",
                    ...
                )
            }
```

Insert this block immediately AFTER the second `Spacer(Modifier.height(DesignTokens.space2))` (the one right after `HorizontalDivider`) and BEFORE the `Row` that renders the Total:

```kotlin
            if (breakdown.amount > 0.0) {
                val discountLabel = if (breakdown.percent in 1..100) {
                    stringResource(Res.string.order_form_summary_discount, breakdown.percent)
                } else {
                    stringResource(Res.string.order_form_summary_discount_plain)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.order_form_summary_subtotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "₦${formatPrice(subtotal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = discountLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "−₦${formatPrice(breakdown.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = DesignTokens.success500,
                    )
                }
                Spacer(Modifier.height(DesignTokens.space2))
            }
```

IMPORTANT: the `−` before `₦` in `"−₦${formatPrice(breakdown.amount)}"` is U+2212 MINUS SIGN (not a hyphen), matching the receipt/payment card. Copy it exactly.

- [ ] **Step 6: Point the Total amount at the payable**

In the Total `Row`, change `"₦${formatPrice(grandTotal)}"` to:

```kotlin
                    text = "₦${formatPrice(breakdown.payable)}",
```

(When `breakdown.amount == 0`, `payable == subtotal`, so the no-discount case is byte-identical to today; the Subtotal/Discount rows only render when `amount > 0`.)

- [ ] **Step 7: Add a preview**

Find the existing previews at the bottom of `OrderFormScreen.kt` (they use `StitchPadTheme { ... }`). Add one rendering the discounted summary (mirror the existing previews' imports — `StitchPadTheme`, `@Preview`, `GarmentType`, and `OrderItemFormState` are same-package/already imported):

```kotlin
@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderTotalSummaryWithDiscountPreview() {
    StitchPadTheme {
        OrderTotalSummary(
            items = listOf(
                OrderItemFormState(
                    garmentType = GarmentType.AGBADA,
                    price = "18000",
                    quantity = "6",
                ),
            ),
            discount = "20000",
        )
    }
}
```

If `OrderItemFormState`'s constructor requires params beyond these, fill them from its defaults by reading the data class (it has an all-default constructor — `OrderItemFormState()` is used in the add-item path). If `StitchPadTheme`/`@Preview` are not yet imported in this file, add them (other previews here will already import them).

- [ ] **Step 8: Build + iOS compile + detekt**

Run: `./gradlew :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64 detekt; echo "exit=$?"`
Expected: BUILD SUCCESSFUL, `exit=0`. Fix any import-ordering detekt issue introduced in Step 2.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/form/OrderFormScreen.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(order-form): show subtotal/discount/total in the live order summary"
```

---

## Task 3: Verification + PR

- [ ] **Step 1: Full test suite + gates**

Run: `./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileKotlinIosSimulatorArm64; echo "exit=$?"`
Expected: all green, `exit=0`.

- [ ] **Step 2: Manual smoke (Daniel is QA) — Android + iOS**
  1. Edit an order (e.g. Asoebi 6 × ₦18,000) → Details step → the summary shows just **Total ₦108,000**.
  2. Type a **Discount** of 20,000 → the summary updates live to **Subtotal ₦108,000 / Discount (19%) −₦20,000 / Total ₦88,000**.
  3. Clear the discount → reverts to a single **Total ₦108,000**.
  4. Type a discount **larger than** the subtotal (e.g. 200,000) → summary shows the raw `−₦200,000` and **Total ₦0** (label is plain "Discount", not a >100% percent); attempting to **Save** is blocked with the existing over-total error.
  5. Enter a discount **equal** to the subtotal → **Discount (100%) / Total ₦0** (free order, allowed).

- [ ] **Step 3: Push + open PR (base = feat/receipt-discount)**

```bash
git push -u origin feat/order-summary-discount
gh pr create --base feat/receipt-discount --title "feat(order-form): live order summary reflects the discount" \
  --body "Implements docs/superpowers/specs/2026-06-17-order-summary-discount-design.md. The Details-step Order summary now shows Subtotal -> Discount (X%) -> Total when a discount is entered (unchanged otherwise), via a tested pure discountBreakdown helper. Shows the raw typed discount with Total floored at 0 to stay consistent with #178's reject-over-subtotal save. Stacked on #178; base is feat/receipt-discount. Merge #178 first, then rebase this to main. Includes the manual smoke test above."
```

Then run the required reviews: Cursor Bugbot (auto) + `codex review`.

---

## Self-Review notes
- **Spec coverage:** `discountBreakdown` helper + tests (Task 1); signature/call-site/subtotal/breakdown/rows/Total/strings/preview (Task 2); manual smoke incl. the over-subtotal + free-order cases (Task 3). All spec sections map to a task.
- **Type consistency:** `discountBreakdown(subtotal, discountInput): DiscountBreakdown(amount, percent, payable)` defined in Task 1 is used with those exact names in Task 2; the `percent in 1..100` gate matches the spec's label rule; Total uses `breakdown.payable`.
- **Out of scope:** discount entry fields + save validation (#178), receipt, order-detail payment card, the discount reason.
