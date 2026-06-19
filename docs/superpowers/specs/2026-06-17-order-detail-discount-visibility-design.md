# Order-Detail Discount Visibility — Design Spec

**Date:** 2026-06-17
**Status:** Approved (pending user review of this doc)
**Author:** Daniel (with Claude)

## Problem

A whole-order discount is captured on the order form and shown in the edit-form
summary and the shared receipt, but it is **invisible everywhere else** in the app.
Worse, two order-detail surfaces compute the order total inconsistently, so they
disagree by exactly the discount amount.

Concrete example (real order from testing): Subtotal ₦380,000, discount ₦30,000,
₦40,000 paid.

- **Edit-form summary** (correct): Subtotal ₦380,000 − Discount (8%) ₦30,000 = **Total ₦350,000**.
- **Hero card** (header): shows **Total ₦380,000** (gross) but **Balance due ₦310,000** (net).
  Internally inconsistent; the discount is invisible.
- **Payment card**: shows **Total ₦380,000** and **Balance ₦340,000** — ignores the
  discount entirely (balance = 380,000 − 40,000).

So the hero says the balance is ₦310,000 and the payment card says ₦340,000 for the
**same order**. The ₦30,000 gap is the unreferenced discount.

## Root cause

The domain model is already correct and is the single source of truth:

```
Order.payableTotal     = (totalPrice - discount).coerceAtLeast(0.0)   // ₦350,000
Order.balanceRemaining = (payableTotal - depositPaid).coerceAtLeast(0.0) // ₦310,000
```

The bugs are purely presentational:

1. **Payment card** (`OrderDetailScreen.kt` ~line 1163): the call site does **not**
   pass `discount` to `OrderPaymentCard`, which defaults it to `0.0`. The card then
   recomputes `payable = totalPrice - 0` and derives balance off the gross subtotal.
   `OrderPaymentCard` *already* contains the full discount-breakdown UI (it renders
   a Subtotal / Discount / Total block when `discount > 0`) — it is simply never fed
   the value.
2. **Hero card** (`OrderHeroCard.kt` ~line 217): renders gross `totalPrice` as the
   "Total" label while `BalanceSection` uses net `balanceRemaining`. No discount shown.

## Goal

Make the discount visible and the totals consistent across every surface that shows
an order's money, by enforcing one rule: **every surface shows `payableTotal` as the
total, and shows the struck-through gross `totalPrice` only when `discount > 0`.**
No surface recomputes its own total.

## Scope

"Everywhere it could appear" resolves to **three surfaces plus one bug fix**, because
the dashboard shows no monetary figures.

| Surface | Today | Change |
|---|---|---|
| **Payment card** | Total ₦380k · Balance ₦340k (discount ignored) | **Bug fix** — pass `discount = order.discount`; existing breakdown renders, Balance becomes ₦310k |
| **Hero card** | Total ₦380k (gross) · Balance ₦310k (net) | Strikethrough: ~~₦380,000~~ **₦350,000**; balance unchanged |
| **Order-list row** | `₦350,000` (net `payableTotal`) + payment status | When `discount > 0`, show a small struck `₦380,000` above the net total |
| **Share-sheet preview** | `totalFormatted` = gross, `balanceFormatted` = net | `totalFormatted` uses `payableTotal` (consistent). Receipt body already itemizes the discount — no change to the receipt itself. |

### Explicitly out of scope

- **Dashboard** — `DashboardOrderRow` carries no price field; pipeline rows show
  customer / label / days-late / created date, never money. Adding amounts purely to
  show a discount would violate the dashboard philosophy (revenue *actions* on the
  dashboard; figures live in Reports). **No change.**
- **Receipt body** — already renders Subtotal / Discount / Total correctly (shipped
  in the discount epic). No change.
- **Reports** — already nets the discount out of revenue. No change.

## Design

### The shared rule (single source of truth)

All surfaces read `order.payableTotal` for the total and `order.discount` to decide
whether to render the struck gross. The `Order` model already exposes both. No new
computation lives in any composable; the "gross vs net" decision is a pure read of
`discount > 0`.

### Reusable component: `StrikethroughPrice`

A small stateless composable so the hero and the list row render identically and we
test the rule once.

```kotlin
// ui/components/ or feature/order/presentation/components — colocate with first use.
@Composable
fun StrikethroughPrice(
    grossPrice: Double,
    netPrice: Double,
    discount: Double,
    modifier: Modifier = Modifier,
    netStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    netColor: Color = MaterialTheme.colorScheme.onSurface,
)
```

Behaviour:
- `discount <= 0.0` → renders **only** the net price with `netStyle`/`netColor`
  (byte-for-byte identical to today's single-`Text` rendering — the dominant case
  must look unchanged).
- `discount > 0.0` → renders the gross price with `TextDecoration.LineThrough` in a
  muted, smaller style, followed by the net price in `netStyle`. Layout direction
  (inline row vs stacked column) is chosen by each call site (hero = inline row,
  list row = stacked, struck above net) — so the component exposes both the gross
  and net slots and the caller arranges them, OR we expose a `layout` enum. **Decision:
  start with an inline `Row` variant for the hero and a stacked usage for the list row
  built from the same two `Text`s; if duplication appears, lift a `layout` param.**

> Keep the component dumb: it knows how to render a gross/net pair, not where the
> numbers come from. Callers pass `grossPrice = totalPrice`, `netPrice = payableTotal`,
> `discount = discount`.

### Surface 1 — Payment card (bug fix)

`OrderDetailScreen.kt`, the `OrderPaymentCard(...)` call (~line 1163): add

```kotlin
discount = order.discount,
```

No change to `OrderPaymentCard` itself — it already renders the breakdown and derives
`payable = (totalPrice - discount)` for both the Total column and the balance.

**Result:** Total ₦350,000, breakdown row "Subtotal ₦380,000 / Discount −₦30,000",
Balance ₦310,000 — now consistent with the hero.

### Surface 2 — Hero card

`OrderHeroCard.kt`:
- Add parameter `discount: Double = 0.0` to both the public composable (~line 93) and
  the inner total/balance composable (~line 158); thread it from the call site in
  `OrderDetailScreen.kt` (~line 1127, `OrderHeroCard(... totalPrice = order.totalPrice,
  balanceRemaining = order.balanceRemaining, discount = order.discount ...)`).
- Replace the single total `Text` (~line 216) with `StrikethroughPrice` in its inline
  Row variant: `grossPrice = totalPrice`, `netPrice = totalPrice - discount`, preserving
  the existing monospace SemiBold `onSurfaceVariant` style for the **net** value, with
  the struck gross in a lighter tint.
- `BalanceSection` is unchanged (already net).

### Surface 3 — Order-list row

`OrderListScreen.kt`, the right-aligned amount `Column` (~line 688). The column already
stacks net total over `PaymentStatusText`. When `order.discount > 0`, insert a small
struck gross line **above** the existing net total (keeps the row narrow; avoids a wide
inline `₦380,000 ₦350,000` on small screens):

```
₦380,000   <- struck, labelSmall, onSurfaceVariant
₦350,000   <- existing net total (unchanged)
Partial · ₦40,000   <- existing PaymentStatusText
```

Net total keeps reading `order.payableTotal` (already does). Only the struck line is new.

### Surface 4 — Share-sheet preview

`OrderDetailScreen.kt` (~line 565): change

```kotlin
totalFormatted = state.order?.let { "₦${formatPrice(it.totalPrice)}" },
```
to
```kotlin
totalFormatted = state.order?.let { "₦${formatPrice(it.payableTotal)}" },
```

`balanceFormatted` already uses `balanceRemaining` (net). This only fixes the preview
chrome; the shared receipt content is produced by `ReceiptFormatter` and already
itemizes the discount.

## Data flow

```
Order (domain)
  ├─ totalPrice      (gross subtotal)
  ├─ discount        (whole-order naira discount)
  ├─ payableTotal    = totalPrice - discount      [existing]
  └─ balanceRemaining= payableTotal - depositPaid  [existing]
        │
        ▼  read-only, no recomputation
  OrderDetailScreen
        ├─ OrderHeroCard(totalPrice, balanceRemaining, discount) ─► StrikethroughPrice
        ├─ OrderPaymentCard(totalPrice, discount, payments)       ─► breakdown (existing)
        └─ ShareReceiptBottomSheet(totalFormatted = payableTotal)
  OrderListScreen row
        └─ StrikethroughPrice(totalPrice, payableTotal, discount)
```

## Edge cases

| Case | Expected |
|---|---|
| `discount == 0` (dominant) | Every surface identical to today — no strikethrough, no breakdown row. |
| `discount == subtotal` (free order) | Total ₦0; struck gross shown; balance ₦0 → "Paid". |
| `discount > subtotal` (shouldn't persist — form blocks it) | `payableTotal` floors at 0; never negative. |
| Very wide figures on a small list row | Struck gross is its own line above the net (stacked), so the row width is governed by the net total alone, as today. |
| Fully paid, discounted | Balance ₦0 → success color (existing logic, now off the correct net). |

## Testing

- **Unit / logic:** a pure helper or the component's branch logic — assert that
  `discount <= 0` yields net-only and `discount > 0` yields gross+net. (If
  `StrikethroughPrice` is purely visual, extract the "show struck?" decision as a tiny
  pure function `shouldShowStruckGross(discount): Boolean` and unit-test it; Compose UI
  isn't unit-tested in this project.)
- **Previews:** add discounted variants to `OrderHeroCard`, `OrderPaymentCard`
  (already has one at line ~454), and the order-list row previews — light + dark.
- **Existing tests:** `OrderPaymentCard` discounted preview already exists; ensure the
  new hero/list previews compile.
- **Gates:** `:composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest`.

## Manual smoke test (Android + iOS)

Using a discounted order (e.g. Subtotal ₦380,000, discount ₦30,000, ₦40,000 paid):

1. **Hero card:** Total shows ~~₦380,000~~ ₦350,000; Balance due ₦310,000.
2. **Payment card:** Total ₦350,000; a Subtotal ₦380,000 / Discount −₦30,000 line;
   Balance ₦310,000. Hero and payment balances now **match**.
3. **Order list:** the order's row shows a small struck ₦380,000 above ₦350,000.
4. **Share sheet:** preview total reads ₦350,000; shared receipt itemizes the discount
   (unchanged).
5. **Non-discounted order:** hero, payment card, and list row look exactly as before
   (no strikethrough, no breakdown).
6. **Dashboard:** unchanged (no amounts shown) — confirm we did not add any.

## Files touched

- `feature/order/presentation/detail/OrderDetailScreen.kt` — payment-card `discount`
  arg, hero `discount` arg, share-sheet `totalFormatted`.
- `feature/order/presentation/detail/components/OrderHeroCard.kt` — `discount` param +
  `StrikethroughPrice` usage.
- `feature/order/presentation/list/OrderListScreen.kt` — struck gross line in the
  amount column.
- New: `StrikethroughPrice` composable (+ optional `shouldShowStruckGross` helper and
  its test).
- No domain/data/DTO/mapper changes — `payableTotal`/`discount` already exist and round-trip.
