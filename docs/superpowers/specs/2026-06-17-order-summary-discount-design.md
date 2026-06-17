# Order-form summary reflects the discount

**Date:** 2026-06-17
**Status:** Approved design, pending implementation plan
**Branch:** `feat/order-summary-discount` (off `feat/receipt-discount` / PR #178)

## Problem

The order form's live **Order summary** card (bottom of the Details step) lists items and a
**Total**, but ignores the discount the tailor enters in the Discount field. Editing an order to
add a ₦20,000 discount leaves the summary showing the full ₦108,000 — the tailor can't see the
discount reflected until they open the saved order / receipt.

## Goal

When a discount is entered, the Order summary shows the breakdown:

```
Order summary
Asoebi          6 × ₦18,000   ₦108,000
──────────────────────────────────────
Subtotal                      ₦108,000
Discount (19%)                −₦20,000
Total                          ₦88,000
```

With no discount, the card is unchanged — just the existing **Total** row.

## Pure helper (the testable core)

A small pure function in `feature/order/domain/`:

```kotlin
data class DiscountBreakdown(
    val amount: Double,    // discount value (floored at 0; NOT clamped to subtotal — see below)
    val percent: Int,      // round(amount / subtotal * 100), 0 when subtotal == 0
    val payable: Double,   // (subtotal - amount).coerceAtLeast(0)
)

fun discountBreakdown(subtotal: Double, discountInput: String): DiscountBreakdown {
    val amount = (discountInput.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val percent = if (subtotal > 0.0) (amount / subtotal * 100).roundToInt() else 0
    val payable = (subtotal - amount).coerceAtLeast(0.0)
    return DiscountBreakdown(amount, percent, payable)
}
```

Home: `feature/order/domain/DiscountBreakdown.kt` (matches the "aggregation math lives in
`feature/x/domain` pure-function objects" pattern). `roundToInt` from `kotlin.math`.

### Why display the raw amount (not clamped to subtotal)

PR #178 was updated (commit `1b9ed762`) to **reject** a discount larger than the subtotal at save
(snackbar `error_order_discount_exceeds_total`, save blocked) rather than silently clamp it. To
stay consistent, the summary must NOT clamp the displayed discount either: it shows the **raw
typed** amount, and the Total floors at ₦0. So a mid-typing over-discount reads e.g. `Subtotal
₦30,000 / Discount −₦50,000 / Total ₦0` — visibly wrong, nudging the tailor to fix it (which the
save block then enforces). Clamping the display would hide the problem and contradict the save
behaviour. `payable` flooring at 0 keeps the Total from going negative.

## The card (`OrderTotalSummary`, `OrderFormScreen.kt` ~L1154)

- Change the signature from `OrderTotalSummary(items: List<OrderItemFormState>)` to
  `OrderTotalSummary(items: List<OrderItemFormState>, discount: String)`. Update the call site
  (~L1115) to pass `discount = state.discount`.
- `subtotal` = the existing `grandTotal` (`lines.sumOf { it.unit * it.qty }`) — rename the local
  to `subtotal` for clarity.
- `val breakdown = discountBreakdown(subtotal, discount)`.
- After the divider:
  - **When `breakdown.amount > 0`:** render a **Subtotal** row (`₦${formatPrice(subtotal)}`), a
    **Discount** row (`−₦${formatPrice(breakdown.amount)}` right-aligned in the
    `DesignTokens.success500` green, matching the receipt/payment-card discount treatment), then
    the existing **Total** row using `breakdown.payable`.
  - **When `breakdown.amount == 0`:** render only the existing **Total** row using `subtotal`
    (byte-identical to today).
- **Discount label / percent:** use `order_form_summary_discount` = `"Discount (%1$d%%)"` ONLY
  when `breakdown.percent in 1..100`; otherwise (a sub-1% discount, or an over-subtotal >100%
  case) use a plain `order_form_summary_discount_plain` = `"Discount"`. This avoids "Discount
  (0%)" and "Discount (167%)".

## New strings

```xml
<string name="order_form_summary_subtotal">Subtotal</string>
<string name="order_form_summary_discount">Discount (%1$d%%)</string>
<string name="order_form_summary_discount_plain">Discount</string>
```
(`%%` is the escaped literal percent sign alongside the `%1$d` arg. No apostrophes.)

## Scope

In scope: the in-form `OrderTotalSummary` card + the `discountBreakdown` helper + 3 strings.

Out of scope (unchanged):
- The discount **entry** fields and the over-subtotal save validation (already on #178).
- The shared receipt and the order-detail payment card (already show Subtotal/Discount/Total).
- The discount **reason** — not shown in the live summary (kept lean; it's on the receipt).

## Testing

- **Unit tests** (`feature/order/domain/DiscountBreakdownTest.kt`, kotlin.test):
  - No discount (`""` / `"0"`) → amount 0, percent 0, payable == subtotal.
  - `discountBreakdown(108_000.0, "20000")` → amount 20_000, percent 19 (rounding), payable 88_000.
  - Discount == subtotal (free order) → amount == subtotal, percent 100, payable 0.
  - Discount > subtotal → amount == raw typed, payable 0, percent > 100.
  - subtotal 0 → percent 0 (no divide-by-zero).
- **Composable:** verified by `OrderTotalSummary`'s `@Preview` (add one preview with a discount)
  + build gates + a quick manual check: open an order, type a discount → summary updates live to
  Subtotal/Discount/Total; clear it → reverts to a single Total.

## Branching

Based on `feat/receipt-discount` (it has `state.discount`); shipped as a stacked PR with base =
`feat/receipt-discount`. Merge after #178; rebase onto main and retarget once #178 lands.
