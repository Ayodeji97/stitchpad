# Africa expansion PR 2 — currency migration

Status: first implementation slice only; do not describe this branch as complete market support.
Base: country foundation + shared phone UI checkpoint, merged with PR #374 head fixes.

## Money contract

- Order item prices, totals, discounts, costs and payments are major-unit `Double`s.
  `500.50` means 500 major units and 50 minor units; do not divide these by 100.
- Subscription/Paystack kobo values are a different contract and are outside this slice.
- Existing `Order.currency` (mapped from private/money, NGN fallback) is authoritative for
  every historical order amount, including new payments against that order.
- A country change affects newly created orders only. It must never relabel or convert old orders.
- No exchange-rate conversion exists in this plan.

## First slice implemented

- `formatMoney(amount, currency, includeSymbol)` is the common major-unit formatter.
  It respects all seven currency precisions, groups thousands and places a negative sign
  before the currency. Alphabetic symbols use a separating space.
- `formatPrice` delegates to that formatter in number-only NGN compatibility mode.
  It remains temporarily because unmigrated call sites supply their own naira symbol.
- Receipt totals, discounts, line quantities/unit prices and payment history use `order.currency`,
  regardless of the owner's current country.
- Collection WhatsApp messages receive a complete formatted amount; their two shared resources
  no longer hardcode a naira prefix.
- New reusable `MoneyAmountField` provides currency prefix, decimal precision and grouping
  that preserves fractional digits/caret offsets. It is not yet wired into every screen.

## Next implementation slices

1. **Workshop market source, including staff.** The current country provider observes the
   signed-in user's document. Staff must instead receive the active owner's market, but
   `firestore.rules` permits reading the owner user document only to that owner. Do not fix
   this by exposing the owner's private profile. Extend a safe workshop-session callable or
   add a minimal market projection (country/currency/time zone) with membership-checked reads;
   then bind its lifecycle to `ActiveWorkshopProvider`, resetting on sign-out/workshop switch.
   Cover hydration delays, offline errors, membership removal, and old Nigerian records.
2. **Order form and detail.** Carry a currency in form state: the saved order's currency in
   edit mode and hydrated active-workshop currency in create mode. Replace hardcoded amount
   prefixes in item editors, totals, deposit rebase prompts, payments and cost editors.
   Do not let a stale/default country stamp new orders; retain the existing creation guard.
3. **Order list/customer detail.** Thread each order's currency into summary rows, profit,
   payment progress and shared `StrikethroughPrice`. Ensure foreign-currency order histories
   remain correctly labelled after a country change.
4. **Reports, collections, dashboard and goals.** Await Daniel's mixed-currency-summary
   decision. Current aggregate DTOs drop currency and sum across orders. A display-only
   symbol replacement would mislabel amounts; propagate grouping/filter selection through
   calculations and presentation first. Existing weekly goals lack a currency stamp and need
   an explicit old-goal fallback/migration strategy before market changes can be safe.
5. **Remaining shared copy and notifications.** Inventory `₦` resources and `formatPrice`
   callers. Where a notification represents an order, retain that order's immutable currency
   in the notification payload. Server templates/localization are a later coordinated stage.
6. **Retire compatibility.** Only after all production callers have explicit currency,
   remove `formatPrice`, `DashboardFormat.formatNaira`, and the old `NairaAmountField`.
   Keep Nigeria-only subscription/referral prices separate from workshop order amounts.

## Decisions and dependencies

- Root has raised the summary question with Daniel: separate totals per currency versus a
  selected-currency filter. Do not aggregate unlike currencies while waiting for that answer.
- User profile fields are intentionally owner-only. The staff-market fix needs an explicit
  minimal shared data contract and emulator tests rather than broader profile read access.
- XOF/XAF/GNF input must not accept fractional minor units. Read-time formatting rounds
  legacy fractional data; it must not silently persist a rounded rewrite to old records.

## Validation

Targeted tests cover all currency symbols/precision, Nigerian compatibility, rounding/negative
fractions, pasted amount precision/caret mapping, and receipts retaining historical currency
when the owner moves to a different country. Broader PR2 validation still needs all affected
order form/payment/cost, report aggregation and workshop-session lifecycle tests, plus Ghana,
Senegal and existing-Nigeria smoke tests on both platforms.

### Completed first-slice checks

- `./gradlew detektFormat` — passed, no unrelated source churn.
- `ANDROID_HOME=/Users/danzucker/Library/Android/sdk ./gradlew :composeApp:testDebugUnitTest --tests '*MoneyFormatterTest*' --tests '*MoneyAmountInputTest*' --tests '*PriceFormatterTest*' --tests '*ReceiptFormatterTest*' -x :composeApp:processDebugGoogleServices detekt` — passed, 64 tests, zero failures/errors.
- Android production sources compiled as part of that run. Firebase config is deliberately absent
  in this worktree; the Google Services processing task was excluded for unit-test validation.
- No runtime UI inspection or cross-platform build was performed for this isolated slice.
