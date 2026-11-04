# Africa expansion PR 2 — currency-correct money

Status: implementation integrated on `feat/africa-currency`; automated checks pass.
Claude Code review and Daniel's runtime smoke test remain before merge/release.
Base: merged country foundation PR #374 plus its tested local phone/picker refinements.

## Resulting behavior

- Amounts remain major-unit `Double`s. Formatting respects all seven supported currencies,
  including zero-decimal XOF/XAF/GNF, while retaining fractions for NGN/GHS/KES/CDF.
- Every historical order uses its immutable private/money currency, with NGN fallback for
  old documents. Country changes affect newly created orders, preserving existing amounts.
- Receipts, invoices, order list/detail, costs, payment history, reminders and Smart drafts
  carry the stamped order currency. Notification private-money joins, inbox DTOs, email
  and push renderers preserve currency and minor units; mixed digest sections stay separate.
- Monetary dashboard, collection and report totals are grouped by currency. Order/production
  counts stay global. Debtor reminders identify both customer and currency. There is no FX.
- Weekly goals retain their saved currency; legacy/unknown goal codes fall back to NGN.
  New goals use the hydrated account market, and progress counts matching-currency payments.
- `MoneyAmountField` replaces `NairaAmountField` in monetary editors. ViewModels own precision
  validation, major-unit caps and partial edits. Unambiguous decimal commas are supported;
  signs, scientific notation, ambiguous grouping and unsupported fractions are rejected with
  localized recovery. Existing fractional values and payment/cost metadata are preserved.
- Exact minor-unit arithmetic guards subtotal/quantity/discount/deposit comparisons and
  representability. Unsupported aggregate magnitudes are rejected instead of changing cents.
- The membership-checked `getWorkshopMarket` callable returns only country/currency/timeZone
  and workshopUid. Staff cannot read the owner's profile. The provider clears stale session
  state, bounds unresolved writes, polls staff metadata and revalidates before new-order saves.
- Order edits retain the loaded order denomination. A changed workshop/session blocks a
  create with stale prices. Cross-currency duplication is blocked pending Q8; same-currency
  duplication preserves existing behavior.

## Final integrated validation — 2 November 2026

- `./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64 detekt`
  passed: **2,239 tests, zero failures/errors**, Android debug APK built, iOS source compiled,
  detekt clean.
- Functions `npm test -- --runInBand`: **74 suites / 977 tests passed**.
- Functions `npm run build` and `npm run lint` passed; lint reports zero errors and existing warnings.
- Independent agent review covered currency grouping, goal/session behavior, input/caret
  handling and market authorization. It found large-amount and extreme-representability
  defects that were fixed with regression tests before the final run.
- Runtime simulator UI verification remains Daniel's smoke test: computer access to Simulator
  was not approved in this session. No runtime verification or deployment is claimed.

## Review and rollout

1. Claude Code reviews the complete currency diff; Daniel smoke-tests the rebuilt app using
   `docs/qa/2026-11-02-currency-smoke.md`.
2. Deploy the reviewed backend through the existing release workflow before releasing the
   updated client to staff: `getWorkshopMarket` is a runtime dependency and is included in
   the explicit Functions deploy allowlist. No deployment has occurred in this work.
3. Keep full French, Play Billing/bank details, trial/read-only enforcement, new time-zone
   schedules and territory opening in their separately planned stages. English fallback is approved.

## Deliberate limits / decisions still needed

- Cross-currency duplicate: copy details and clear money versus continue to block (Q8).
- Foreign-currency VIP spending thresholds are not invented; the existing NGN threshold
  applies to NGN, with relationship order-count qualification preserved across currencies (Q6).
- Existing goals do not silently change denomination. A new-currency replacement/reset
  control is a separate product decision (Q7).
- Subscription prices and Paystack/referral kobo arithmetic retain their existing Nigerian
  contracts until the billing stage. Compatibility formatting functions remain for old
  tests/callers; production order money uses `formatMoney`.
