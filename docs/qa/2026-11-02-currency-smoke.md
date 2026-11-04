# Africa expansion PR 2 — currency smoke test

Use the PR 2 build after automated checks/review. No new country-store launch is implied.
The new `getWorkshopMarket` callable must be deployed before staff create-order runtime QA.
Deploy backend changes through the existing reviewed release workflow; no rollout is done by
this implementation work.

## Existing Nigerian account

- [ ] Existing customers, orders, reports and goals retain their values and NGN denomination.
- [ ] Create an order with unit price 1234.50 and deposit 100.25; payable/balance are correct.
- [ ] Reopen, edit and save without changing amounts. Minor units must not disappear.
- [ ] Record/edit a payment and a cost with decimals; receipts show the saved amount.

## Ghana

- [ ] A Ghana account creates an order denominated GHS, including fields, list/detail,
  receipt/invoice, collection reminder, customer reminder and AI draft preview.
- [ ] Enter 12.34 (and decimal-comma 12,34 where supported by the keyboard); it must never become 1234.
- [ ] Excess precision, minus signs, scientific notation and malformed numeric pastes must not
  silently create a different amount. Clear and mid-string edits keep the caret usable.
- [ ] Change workshop country after creating an NGN order: the old order and its edit forms
  remain NGN; a new order uses the new workshop currency.
- [ ] Mixed NGN/GHS dashboard and report money totals are separate. Order/customer counts remain global.
- [ ] A customer with debts in both currencies gets the amount/currency of the reminder row tapped.

## Zero-decimal currency (e.g. Senegal/XOF)

- [ ] Order, payment and cost fields accept whole units and reject unsupported fractions without
  concatenating digits or truncating a pasted monetary value silently.
- [ ] Display, receipts, reports and notifications show F CFA without fractional digits.

## Goals

- [ ] New Ghana goal is GHS. Reopening/editing preserves decimal values.
- [ ] Existing NGN goal remains NGN after a country change. Only matching-currency payments
  count towards its progress. No exchange-rate conversion is implied.
- [ ] An unreadable goal or unresolved account/workshop market shows a recoverable error and
  cannot silently reset/redenominate the goal.

## Staff and sessions

- [ ] Active Ghana workshop staff create a new order in the workshop currency, even if their
  own profile/device region is Nigeria. No owner profile/financial fields become readable.
- [ ] Revoked membership and unresolved workshop market block new-order save safely.
- [ ] Switching owner/staff/workshops clears old money/market state; no previous workshop values leak.

## Notifications

- [ ] New amount-bearing inbox notifications render the order's stamped currency, including decimals.
- [ ] Legacy notifications without currency keep NGN fallback.
- [ ] Ready/delivered collection push and daily digest use the order currency, not current profile currency.
- [ ] Mixed-currency digest collections are separated; schedule/time-zone behavior is unchanged.

## Duplication

- [ ] Same-currency duplicate preserves intended order details and existing payment-copy semantics.
- [ ] Cross-currency duplicate never relabels old money. Follow the approved behavior once decided.

## Automated baseline

The integrated branch passed 2,239 Android tests, Android debug assembly, iOS simulator
source compilation and detekt. Backend: 977 tests, TypeScript build and lint passed.
These checks do not replace the runtime checks above or Claude Code review.
