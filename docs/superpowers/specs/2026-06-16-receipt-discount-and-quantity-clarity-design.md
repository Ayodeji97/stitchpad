# Receipt: quantity-row clarity + whole-order discount

**Date:** 2026-06-16
**Branch:** `feat/receipt-discount`
**Status:** Design approved (brainstorm), pending spec review → implementation plan

## Context

A tailor tester (Middies Outfit) shared an invoice receipt and flagged two problems, and Daniel asked for a third capability:

1. **Multi-quantity rows are confusing.** When an item has quantity > 1, the receipt prints two stacked naira figures — `Unit price ₦4,000` and `Price for 2 ₦8,000` — while every single-quantity row shows just one figure. The customer can't tell at a glance which number "counts." The tester literally circled ₦4,000 and ticked ₦8,000 asking which to pay.

2. **No way to record a discount.** Tailors routinely knock something off a bill ("I'll do ₦30k for you"). There is currently no discount concept anywhere in the order/pricing model, so they can't represent it on an invoice.

**Intended outcome:** A receipt where the right-hand column always means "the amount that counts," and an optional whole-order discount that reads cleanly as Subtotal → Discount → Total. Both changes are backward-compatible: existing orders (no discount, single-quantity items) render exactly as today.

## Decisions (locked during brainstorm)

- **Quantity row → "Option B":** name carries the quantity (`Blouse ×2`), a small grey caption reads `₦4,000 each`, and the right column shows the bold line total (`₦8,000`) — identical treatment to every single-quantity row.
- **Discount scope:** whole order only (not per-item).
- **Discount type:** fixed naira amount (not percentage).
- **Discount reason:** optional free-text, shown as small grey text under the Discount line on the receipt.
- **Tier gating:** free for everyone — no entitlement gate.
- **No debug-menu entry needed** (no time/entitlement/network/state variance worth a debug toggle).

## Change 1 — Quantity-row clarity (receipt renderers only)

No data-model change. `ReceiptItem` already carries `quantity`, `formattedUnitPrice`, and `formattedPrice` — enough to render Option B.

Update the `quantity > 1` branch in **both** platform renderers:
- `composeApp/src/iosMain/.../core/sharing/OrderReceiptSharer.ios.kt`
- `composeApp/src/androidMain/.../core/sharing/OrderReceiptSharer.android.kt`

Replace the current three-line block (name / `Unit price` row / `Price for N` row) with:
- **Line 1 (left):** `"$garmentName ×$quantity"` bold; **(right)** `formattedPrice` (line total) bold.
- **Line 2 (left, caption):** `"$formattedUnitPrice each"` in the muted/grey color, smaller.

Single-quantity rows are unchanged. The `×` glyph and `each` wording must come from string resources (no hardcoded strings; use `&apos;`-style rules if any apostrophes appear). Keep the two renderers visually in lock-step.

## Change 2 — Whole-order discount

### Data model
- **`core/domain/model/Order.kt`** — add `val discount: Double = 0.0` and `val discountReason: String? = null`. Add computed `val payableTotal: Double get() = (totalPrice - discount).coerceAtLeast(0.0)`. `totalPrice` keeps its current meaning (sum of `price × qty` = subtotal). Redefine `balanceRemaining` as `(payableTotal - depositPaid).coerceAtLeast(0.0)`.
- **`core/data/dto/OrderDto.kt`** — add `val discount: Double = 0.0` and `val discountReason: String? = null` (defaults keep old documents valid).
- **`core/data/mapper/OrderMapper.kt`** — map both fields in `toOrder()` and `toOrderDto()`.

### Order form (entry)
Mirror the existing `notes`/deposit pattern exactly:
- **`OrderFormState.kt`** — add `val discount: String = ""` and `val discountReason: String = ""`.
- **`OrderFormAction.kt`** — add `OnDiscountChange(discount: String)` and `OnDiscountReasonChange(reason: String)`.
- **`OrderFormViewModel.kt`** — handle both actions (digit-filter the discount like the price field); in `executeSave()` parse `val discount = s.discount.toDoubleOrNull() ?: 0.0` and pass `discount` + `discountReason = s.discountReason.trim().ifBlank { null }` into the `Order(...)` constructor.
- **`OrderFormScreen.kt`** — in the Details step (Step 3), after the Deposit field: a `Discount (₦)` number field (`ThousandsSeparatorTransformation`, numeric keyboard) and an optional `Discount reason` text field. Both use string resources.
- **Validation:** clamp discount so it cannot exceed the subtotal (a discount larger than `totalPrice` floors `payableTotal` at 0 via `coerceAtLeast`, but the form should also guard/explain rather than silently swallow). Deposit-vs-balance validation must compare against `payableTotal`, not `totalPrice`.

### Receipt (display)
- **`ReceiptData.kt`** — add `val subtotalFormatted: String`, `val discountFormatted: String?` (null when discount == 0), and `val discountReason: String?`.
- **`ReceiptFormatter.kt`** — populate: `subtotalFormatted` = `totalPrice`; `discountFormatted` = `discount` formatted with a leading `−` when `discount > 0`, else `null`; `totalFormatted` switches to `order.payableTotal`; `balanceFormatted` stays `order.balanceRemaining` (now discount-aware). Pass through `discountReason`.
- **Renderers (iOS + Android):** when `discountFormatted != null`, render `Subtotal` then `Discount` (with optional grey reason caption) above the existing `Total` line. When it's null, render exactly today's single `Total` line.

### In-app detail screen
- **`OrderPaymentCard.kt`** — accept the discount (and/or `payableTotal`) so the displayed Total reflects the discounted amount and balance comes off `payableTotal`. Show a Discount line when `discount > 0`. Update the internal balance calc from `totalPrice - paid` to `payableTotal - paid`.
- **`OrderDetailScreen.kt`** (lines ~532, ~1025, ~1056) and **`OrderHeroCard.kt`** — the "amount owed" figures should read `payableTotal`, not raw `totalPrice`.

### Revenue/accounting ripple (must-fix — silent-corruption risk)

Several calculators currently derive "collected" as `totalPrice − balanceRemaining`. That identity equals `depositPaid` **only while `balanceRemaining = totalPrice − depositPaid`**. Once `balanceRemaining` becomes discount-aware, `totalPrice − balanceRemaining` wrongly evaluates to `discount + depositPaid`. Fix by switching these to the unambiguous `order.depositPaid`, and make "revenue earned" net-of-discount (`payableTotal`):

- `feature/reports/domain/KpiCalculator.kt` — revenue `Σ totalPrice` → `Σ payableTotal`; collected → `Σ depositPaid`. Update the header doc comment.
- `feature/reports/domain/CustomerInsightsCalculator.kt` — collected → `Σ depositPaid`.
- `feature/dashboard/domain/WeeklyGoalCalculator.kt` — collected → `Σ depositPaid` (replace `totalPrice − balanceRemaining`).
- `feature/dashboard/domain/NbaCalculator.kt` — `totalPrice > 0` / `balance = totalPrice` → use `payableTotal` / `balanceRemaining`.
- `feature/order/presentation/list/PaymentDisplay.kt` + `PaymentStatusText.kt` — `formatPaymentStatus` should compare deposit against `payableTotal` so a fully-paid discounted order reads "Paid."
- `feature/order/presentation/list/OrderListScreen.kt` (~690) and `feature/dashboard/presentation/DashboardViewModel.kt` (~464) — list/summary "amount" should show `payableTotal`.

Implementation should grep `totalPrice` and `balanceRemaining` across `commonMain` and audit every call site against the rule: **"amount owed" = `payableTotal`; "cash collected" = `depositPaid`; raw `totalPrice` only where "subtotal before discount" is genuinely meant.**

## Testing / verification

- **Unit tests** (`:composeApp:testDebugUnitTest`, kotlin.test + Turbine):
  - `Order` computed props: `payableTotal` and `balanceRemaining` with discount = 0, discount < subtotal, discount > subtotal (floors at 0), plus deposit interplay.
  - `KpiCalculator` / `WeeklyGoalCalculator` / `CustomerInsightsCalculator`: revenue is net-of-discount and collected equals deposits for a discounted, partially-paid order.
  - `OrderMapper` round-trip preserves `discount` + `discountReason`; an old DTO without the fields maps to 0/null.
  - `ReceiptFormatter`: discount > 0 yields subtotal+discount+net-total lines; discount == 0 yields `discountFormatted == null`.
- **iOS compile** before declaring done (KMP JVM/Native skew rule).
- **Manual smoke (Daniel is QA):**
  1. Create an order with a qty-2 item → receipt shows `Blouse ×2` + `₦4,000 each` + `₦8,000`, no "Unit price/Price for 2" stack.
  2. Add a ₦2,500 discount + reason → detail screen Total = subtotal − 2,500, balance off the discounted total.
  3. Share as image and PDF on Android and iOS → Subtotal/Discount/Total render and align on both.
  4. Edit the order, clear the discount → receipt reverts to a single Total line, identical to pre-feature output.
  5. Pay the full discounted balance → order reads "Paid"; Reports revenue reflects the net amount.

## Out of scope (YAGNI)

Per-item discounts, percentage discounts, multiple stacked discounts, discount on deposit/balance independently, discount analytics/reporting as its own metric.
