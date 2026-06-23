# Analytics Mid-Funnel Events — Design Spec

**Date:** 2026-06-23
**Branch:** `feat/analytics-mid-funnel`
**Builds on:** PR #214 (Firebase Analytics foundation — `Analytics` interface, `AnalyticsEvent`, `FirebaseAnalyticsTracker`, `FakeAnalytics`, Koin `analyticsModule`).

## Goal

Fill the middle of the user journey. PR #214 captured acquisition + activation
start (`sign_up → workshop_setup_completed → customer_created → order_created →
ai_feature_used → upgrade_completed`). This PR adds 5 events that reveal whether
orders actually progress to completion, whether tailors get paid, and whether
they share value with customers — the "did the product deliver" signals.

## Why these 5

- `order_status_advanced` — the truest value metric: order completion rate / pipeline velocity.
- `payment_recorded` — "tailor got paid"; deposit → balance → fully-paid progression.
- `receipt_sent` — value delivered to the customer; `document_type` reveals invoice vs receipt usage.
- `measurement_added` — core tailor data captured (activation depth).
- `whatsapp_message_sent` — the customer-comms / retention loop.

(Deferred to a later PR: `paywall_viewed` to close the upgrade funnel; the Tier-3/4
events — `search_used`, `customer_deleted`, `order_deleted`, `order_photo_added`.)

## The events

| Event | Params | Fires when | Fire point |
|---|---|---|---|
| `measurement_added` | — | a measurement is **created** (not edited) | `MeasurementFormViewModel.save()` success, create branch only (`!isEditMode`) |
| `order_status_advanced` | `status` (`in_progress`/`ready`/`delivered`/`fitting`…) | order status successfully changes | `OrderDetailViewModel.performStatusUpdate` after `updateOrderStatus` returns success |
| `payment_recorded` | `is_fully_paid` (`"true"`/`"false"`) | a payment is recorded | `OrderDetailViewModel.submitPayment` success branch |
| `receipt_sent` | `document_type` (`invoice`/`deposit_receipt`/`receipt`), `format` (`image`/`pdf`) | a receipt/invoice is successfully shared | `OrderDetailViewModel.shareReceipt` after `share(receiptData)` |
| `whatsapp_message_sent` | `context` (`draft_message`/`order_update`) | a WhatsApp message is dispatched to a customer | **two** points: `DraftMessageViewModel.sendViaWhatsApp` + `OrderDetailViewModel.launchWhatsApp` |

### Param value derivations
- `status` = `newStatus.name.lowercase()` (the `OrderStatus` being set).
- `is_fully_paid` = `(safeAmount >= order.balanceRemaining).toString()` — balance reaches zero. No amount logged.
- `document_type` = `receiptData.documentType.name.lowercase()` (`ReceiptDocumentType` enum: `INVOICE`/`DEPOSIT_RECEIPT`/`RECEIPT`).
- `format` = `"image"` or `"pdf"`, threaded in from the two share call sites (`OnShareAsImageClick`/`OnShareAsPdfClick`).
- `context` = `"draft_message"` (Draft VM) / `"order_update"` (OrderDetail VM).

## Design rules (carried from #214 — non-negotiable)

- **No PII, ever.** Only counts, enums, booleans-as-strings. Never names, phone numbers, amounts, customer/order text, or free text. `is_fully_paid` is a bool, not an amount.
- **Fire-and-forget.** Analytics never throws or blocks a user flow (swallowed at the sink). Log AFTER the operation's success is established; never gate the user action on the log.
- **Create-only where it mirrors existing events.** `measurement_added` fires on create only (`!isEditMode`), exactly as `customer_created`/`order_created` exclude edits.
- **One place per event name.** Each event is one `AnalyticsEvent` subtype; client constants can't drift.
- **Single fire point per logical action**, except `whatsapp_message_sent` which has two genuinely distinct senders (different VMs, different message builders) disambiguated by `context`. NOT a shared-helper extraction — the unrelated 3-handler "own-number confirm" dedup backlog stays separate.

## Architecture

Purely additive: 5 new `AnalyticsEvent` subtypes + injecting the existing
`Analytics` into two more ViewModels (`OrderDetailViewModel`,
`MeasurementFormViewModel`) and firing at the mapped success branches.
`DraftMessageViewModel` already has `Analytics` injected from #214. No new
interfaces, no new modules, no schema. Both target VMs use
`viewModelOf(::…)` with no defaulted params, so adding `analytics: Analytics`
resolves via `get()` automatically (no lambda form needed).

## Out of scope
- `paywall_viewed` and the upgrade-funnel events (next PR — note #215 just shipped the Pro→Atelier path, so the paywall surface moved; re-scope then).
- Tier-3/4 events (search, deletes, photo-added).
- The WhatsApp own-number-confirm controller dedup (unrelated backlog).
- GA4 custom-dimension registration for new params (`status`, `document_type`, `format`, `context`, `is_fully_paid`) — a console step after data flows; note it in the PR.

## Open questions
None blocking. `is_fully_paid` serialized as a string to stay within GA4 param
types; revisit only if a numeric/boolean param is ever preferred.
