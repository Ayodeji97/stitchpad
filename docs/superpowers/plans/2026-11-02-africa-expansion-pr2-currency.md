# Africa expansion PR 2 — currency migration

Status: two implementation slices only; do not describe this branch as complete market support.
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
4. **Reports, collections, dashboard and goals.** Daniel approved separate totals per currency on 2 November 2026. Current aggregate DTOs drop currency and sum across orders. A display-only
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

- Approved 2 November 2026: separate totals per immutable order currency, no filter requirement
  and no FX conversion. Every summary pipeline must partition before adding monetary values.
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


## Second slice implemented — immutable-order UI

- Order list gross/net prices, partial-payment labels and profit use each order's currency.
- Detail hero, paid/balance/total, discount, cost/profit and payment history cards propagate
  the same immutable currency through their child composables.
- Receipt-share summaries, delete-payment warnings and unpaid-balance status warnings receive
  complete formatted amounts; corresponding resources no longer hardcode naira.
- Existing payment and cost editor prefixes and paid-in-full text use the order currency.
  Their numeric editing semantics remain unchanged (integer-only); decimal-safe ViewModel
  input/capping and the actual MoneyAmountField migration are a separate remaining slice.
- `StrikethroughPrice`, `PaymentStatusText` and the detail cards keep NGN defaults for previews
  and source compatibility. Every production caller of StrikethroughPrice and PaymentStatusText
  now explicitly passes `order.currency`; no active production price caller relies on that default.
- Remaining scope includes order form monetary state/inputs, currency-safe aggregated reports and
  goals, notification payloads, and active-workshop market metadata for staff.

Second-slice check: Android source compilation, detekt, and targeted formatter/receipt/input/partial-payment/strikethrough tests passed (78 tests, zero failures/errors). No runtime screenshot verification or iOS build was performed for this second slice.

## Third slice implemented — grouped monetary reports

Approved decision (Daniel, 2 November 2026): show separate totals per currency; no FX conversion
and no required currency filter. `CurrencyReportsCalculator` partitions immutable order currency
before KPI windows/sparklines, customer rankings and debt calculations. Multiple-currency reports
show a section per currency while single-currency reports keep the existing simple layout. Order
counts and production counts remain global. Customer/reminder identity includes currency so a
customer owing both GHS and NGN receives the amount from the tapped section.

Customer relationship order-count badges continue to count all lifetime orders across currencies.
The existing NGN spend-based VIP threshold is never compared against foreign-currency amounts;
foreign monetary thresholds remain an explicit pending question in `docs/handoff/QUESTIONS.md`.

Validation: Android compilation, detekt and 58 targeted report/domain/ViewModel/money tests passed,
including mixed current/historical currency windows, sparklines, the same customer's separate debts,
global counts, currency-specific reminder lookup, single-currency compatibility and empty data.
No runtime visual QA or iOS compilation was performed for this slice.

## Fourth slice implemented — dashboard, collections and goal denomination

- Shared `CurrencyAmount` / `sumByCurrency` groups money before summing.
- Dashboard and To Collect display separate owed totals per immutable order currency, with global
  order/overdue counts. Ready/Delivered collectibility semantics remain unchanged. Rows and NBA
  reminders carry their order currency. Face-value sorting of unlike balances is avoided by a stable
  currency grouping before balance comparison.
- Legacy single-amount summary fields remain for source compatibility only: on mixed-currency data
  they are zero, never a cross-currency sum. Production views render the explicit currency groups.
- Bucket/owner state includes grouped outstanding values; switching to staff clears them along with
  the existing money state. Staff views retain their money-free behavior.
- Weekly goals now persist their currency. Old/unknown codes map to NGN, new goals use the hydrated
  account market, and existing goals retain their denomination across account-country changes.
  Progress counts only matching-currency order payments within the existing cycle window.
- An unreadable existing goal or an unresolved market prevents saving, with a recoverable load
  error, instead of guessing a denomination. Goal numeric editing semantics remain integer-only;
  decimal-safe ViewModel input remains a separate slice.
- An explicit reset/replace-goal-in-new-currency control is still a pending product question; no
  automatic conversion or silent target reset was added.

Validation: complete Android unit suite **2,201 tests passed**, zero failures/errors; detekt and
`compileKotlinIosSimulatorArm64` passed. Regression coverage includes mixed collectible totals,
row denominations, global counts, owner-to-staff amount clearing, goal-only matching-currency
progress, legacy goal mapper fallback, new Ghana goal stamping and unresolved-market save guards.
No runtime screenshot verification or app install was performed in this worktree.
