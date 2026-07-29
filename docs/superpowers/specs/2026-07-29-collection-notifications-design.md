# Collection Notifications — design

**Date:** 2026-07-29
**Status:** Approved design, ready for implementation plan
**Branch:** `feat/collection-notifications` (stacked on `feat/to-collect`)
**Depends on:** the "To collect" feature (`feature/collection/*`, `ToCollectRoute`, `CollectionCalculator`) — PR #312.

## Goal

Make collection notifications actually useful and land in the right place. Three
parts, one workstream:

- **A. Deep-link plumbing** (client) — targets for "open this order" and "open the
  To-collect list", used by B and C.
- **B. Instant "Ready/Delivered but unpaid" notification** (server, new) — the
  moment an order becomes collectible, notify the tailor to collect/record it,
  rather than waiting for the 07:00 daily digest.
- **C. Digest ↔ To-collect consistency** (server) — make the daily digest agree
  with `CollectionCalculator` (balance floor + 7-day overdue), and send the daily
  summary push to the To-collect list.

## Background (current state, from investigation)

- Orders: `users/{uid}/orders/{orderId}`. **No existing Firestore trigger.**
- In-app inbox is **live for everyone**; email + push are **staged** to an
  allowlist (`functions/src/notifications/rollout.ts` `STAGING = true`).
- Daily digest (`dailyDigest.ts`, `0 7 * * *` Africa/Lagos) produces `TO_COLLECT`
  notification docs, one per outstanding order, deterministic id
  `${orderId}__TO_COLLECT` (`notificationDocs.ts`), deduped via `.create()`.
- `NotificationType` (client `core/domain/model/Notification.kt`): `OVERDUE,
  DUE_SOON, TO_COLLECT, GIFT_RECEIVED, UNKNOWN`. Unknown wire types decode to
  `UNKNOWN` safely.
- `DeepLinkTarget` (`navigation/PendingDeepLink.kt`): `{ INBOX, UPGRADE,
  CLAIM_GIFT }`. Push data currently sets `target: 'inbox'` (`dailyDigest.ts`).
- Server helpers to reuse: `writeNotificationsAdmin(db, uid, model)` and
  `admin.messaging().sendEachForMulticast(...)` (`dailyDigest.ts`). Push respects
  `pushEnabled` (explicit `dailyPushEnabled` → inherits `dailyDigestEmailEnabled`
  → default ON) and the rollout gate.
- `balanceRemaining` server-side (`digestDetector.ts`): `payable = max(0,
  totalPrice - discount)`; paid = sum(payments) else legacy `depositPaid`.

## Locked decisions (from brainstorming)

1. **Unpaid trigger** = any `balanceRemaining > 0` (matches the To-collect list;
   a partial deposit still triggers).
2. **Channels** = in-app inbox **and** immediate push.
3. **Tap target** of the instant per-order notification = **that order** (detail,
   where Record payment lives).
4. Build **all three together**, **B first**.

---

## A. Deep-link plumbing (client)

**Files:** `navigation/PendingDeepLink.kt`, the push/notification tap handler, and
the nav host wiring.

- Extend `DeepLinkTarget` with:
  - `ORDER` carrying an `orderId` (open that order's detail).
  - `TO_COLLECT` (open the To-collect list — `ToCollectRoute`).
- Parse these from the push `data` payload (`target` = `"order"` + `orderId`, or
  `target` = `"to_collect"`), alongside the existing `inbox`/`upgrade`/`claim_gift`.
- Nav handling: `ORDER` → `OrderDetailRoute(orderId)`; `TO_COLLECT` → `ToCollectRoute`.
- In-app inbox tap for a per-order `TO_COLLECT` notification already navigates to
  the order (`NotificationsInboxViewModel` → `NavigateToOrderDetail`) — **no
  change needed** there; it already matches decision 3.

## B. Instant "Ready/Delivered but unpaid" notification (server — NEW)

**Files:** new `functions/src/notifications/orderStatusNotify.ts` (pure detector +
doc/push builders) + a trigger registration in the functions entrypoint; add the
new function name to the `deploy --only` allowlist in `functions/package.json`.

**Trigger:** Firestore `onUpdate` (or `onWrite`) of `users/{uid}/orders/{orderId}`
(match the project's existing function generation — mirror `dailyDigest.ts`'s
Firebase Functions style/region `europe-west1`).

**Fire condition (pure, unit-tested):** given `before` and `after` order docs:
- `after.status ∈ {READY, DELIVERED}` AND `before.status ∉ {READY, DELIVERED}`
  (first entry into a collectible state — so READY→DELIVERED does not re-fire), AND
- `balanceRemaining(after) > 0`.

**On fire:**
- **In-app:** create a `TO_COLLECT` notification doc with id
  `${orderId}__TO_COLLECT` (same id the daily digest uses → the later digest
  `.create()` is a no-op, so no duplicate). Fields: `orderId`, `type=TO_COLLECT`,
  `customerName`, `garmentSummary`, `amount = round(balanceRemaining)`,
  `deadline = null`, `isRead = false`, `createdAt`. Reuse the digest's
  doc-writing path where practical.
- **Push:** immediate push to the owner's tokens via `sendEachForMulticast`,
  respecting `pushEnabled` + the rollout gate (testers now, everyone when
  `STAGING` flips). Prune invalid tokens (same as digest). Copy:
  `"{firstName}'s {garmentLabel} is ready — ₦{amount} to collect"`
  (use "ready" for READY, "delivered" for DELIVERED). `data.target = "order"`,
  `data.orderId = orderId` (→ deep-link part A).

**Idempotency / no double-push:** the fire condition is first-entry-only, so a
normal READY→DELIVERED progression pushes once. If both `before`/`after` are
already collectible, skip. The in-app doc is id-deduped regardless.

**Staleness (noted, NOT V1):** if payment later clears the balance, the inbox doc
persists as a historical item; the To-collect list is the live truth. Auto-clear
on payment is a follow-up.

## C. Digest ↔ To-collect consistency (server)

**Files:** `functions/src/notifications/digestDetector.ts`,
`notificationDocs.ts`, `pushSummary.ts`, `digestEmailTemplate.ts`, `dailyDigest.ts`.

- **Balance floor:** change the outstanding inclusion from `bal >= MIN_BALANCE`
  (`MIN_BALANCE = 1`) to `bal > 0` to match `CollectionCalculator.kt`
  (`balanceRemaining > 0.0`). (Removes the ₦0.01–₦0.99 divergence.)
- **Overdue (7-day):** compute `owedSince` server-side as the earliest
  `statusHistory.changedAt` whose status is `READY`/`DELIVERED` (fallback
  `updatedAt` → `createdAt`), then `daysOwed` in Africa/Lagos days, and
  `isOverdue = daysOwed >= 7` — mirroring `CollectionCalculator`. Stamp
  `isOverdue` (and `daysOwed`) onto each outstanding item / `TO_COLLECT` doc.
- **Use the overdue signal:** in `pushSummary.ts` bump overdue-to-collect items
  in the ordering (currently outstanding always ranks last regardless of age);
  in `digestEmailTemplate.ts` emphasise overdue "to collect" rows.
- **Summary push target:** the daily digest summary push `data.target` →
  `"to_collect"` (→ opens the To-collect list via part A), instead of `"inbox"`.

## Client model touch (small)

`Notification` / `NotificationDto` may gain an optional `isOverdue: Boolean`
(default false) so the inbox can badge an overdue "to collect" row consistently
with the list. If added, decode defensively (default false when absent) — old
docs and clients must keep working (see the WhatsApp-confirm serverOnlyField
lesson: additive, non-breaking only).

## Out of scope (follow-ups)

- Auto-clear / update the `TO_COLLECT` notification when payment is recorded.
- Flipping `rollout.ts` `STAGING = false` for broad email/push rollout (a separate
  go-live decision; also address the `listRecipients` N+1 before scaling).
- Inbox resilience polish (retry affordance, explicit signed-out state, logging
  silently-dropped malformed docs).

## Testing

- **B detector** (`orderStatusNotify` pure fn): fires on
  PENDING→READY-unpaid, IN_PROGRESS→DELIVERED-unpaid; does NOT fire on
  READY→DELIVERED (already collectible), on transitions to READY/DELIVERED with
  zero balance, or on non-status edits; amount/copy correct for READY vs DELIVERED.
- **C detector**: outstanding inclusion at `bal > 0` (₦0.5 now included);
  `owedSince`/`daysOwed`/`isOverdue` boundary at exactly 7 Lagos-days (6 not
  overdue, 7 overdue); fallback when `statusHistory` empty.
- **Client**: `DeepLinkTarget.ORDER`/`TO_COLLECT` parse + nav mapping unit tests
  (parse from data map; route resolution). Existing notification/nav tests stay green.
- Functions tests run `npm run lint` first (per project rule) — run lint for
  changed TS.

## Success criteria

- Marking an unpaid order Ready/Delivered produces an **immediate** in-app
  notification + push; tapping it opens **that order** to record payment.
- The daily digest, its push, and the in-app To-collect list agree on which orders
  are outstanding and which are overdue (≥ 7 days).
- The daily summary push opens the **To-collect list**.
- No duplicate inbox docs between the instant notification and the daily digest.
- No new Firestore data required beyond an optional additive `isOverdue` flag;
  old clients/docs keep working.

## QA smoke test

1. Mark an unpaid order **In-progress → Ready**. Within seconds an inbox
   notification appears ("… is ready — ₦X to collect") and, as a push tester,
   a push arrives. Tapping the notification opens **that order**.
2. Record a partial payment on an order, then set it **Ready**. The
   notification still fires (balance remaining > 0). A **fully-paid** order
   moved to Ready produces **no** notification.
3. On the same order, move **Ready → Delivered**. **No** second
   notification/push fires (first-entry only); the inbox shows a single item
   for that order (deduped id).
4. Run `debugSendMyDigest` (tester). The summary push opens the **To-collect
   list**. An order unpaid for **≥ 7 days** is shown as overdue and leads the
   summary.
5. Regression: existing OVERDUE / DUE_SOON / gift notifications still render
   and tap through correctly; old notification docs without `isOverdue` still
   load without error.
