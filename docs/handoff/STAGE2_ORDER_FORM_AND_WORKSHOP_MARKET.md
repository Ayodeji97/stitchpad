# Stage 2 — order form and staff workshop market

## Implemented

- `getWorkshopMarket` returns an explicit allowlist of workshop UID, country, derived currency
  and validated time zone. The requested workshop must match the authenticated owner's UID or
  staff token claim. Staff additionally needs an ACTIVE membership document, checked with the
  owner market in one transaction. No owner-profile fields or Firestore rule grants are exposed.
- The standard Functions deploy allowlist includes this new callable. It must be deployed before
  client rollout to staff; no deployment was performed during this implementation.
- `WorkshopCurrentCountryProvider` observes only an owner's own document. Staff uses the market
  callable, refreshes every five minutes while its session scope remains active, and cancels that
  work on workshop switches/sign-out. A session change clears the prior resolution before waiting
  for canceled work. Owner and staff write resolution is bounded; staff writes always recheck the
  callable instead of trusting cached display country. Legacy missing owner profiles stay NG.
- Order creation hydrates a denomination before showing money controls and offers an explicit
  retry when unavailable. The first displayed denomination stays fixed for that form. A fresh
  check before save rejects a changed workshop/currency instead of relabelling entered prices.
  Session identity is checked again before writes after asynchronous photo uploads.
- Editing uses the saved order's immutable currency, preserves fractional price/discount/deposit
  prefills, and retains existing payment IDs/methods/timestamps/notes when the deposit is unchanged.
- Unit price, deposit and discount use the shared `MoneyAmountField` and ViewModel-owned monetary
  parsing. True extra precision is rejected without concatenating decimal digits. Formatting,
  summaries and reconciliation messages use complete currency-formatted amounts; order-form copy
  no longer contains a hardcoded naira denomination.
- Quantity multiplication, subtotal/deposit sums and overpayment comparisons use parsed integer
  minor units. The conversion back to the persisted Double model is checked against the ORIGINAL
  integer sum; unsupported aggregates fail safely instead of becoming a different cent amount.

## Pending product decision

Q8 in `QUESTIONS.md`: duplicate an old order across currencies by copying details and clearing
money with notice, or block the operation. Until Daniel answers, cross-currency duplication is
explicitly blocked with a clear message. Same-currency duplication remains supported. No FX or
implicit conversion was introduced.

## Validation

Backend TypeScript build and ESLint passed. All 22 callable tests passed, including wrong workshop,
missing/pending/revoked membership, legacy defaults, all 15 market currency mappings and exact
response privacy. Focused Kotlin production compilation, detekt and 100 provider/order-form/MoneyInput tests
passed with zero failures/errors. Root owns final integrated full Android/iOS/build checks
and runtime smoke testing after all stage-2 branches merge. No push, merge into main, or deployment
was performed here.
