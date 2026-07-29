# Collection Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An instant push + in-app notification the moment an order becomes Ready/Delivered while still unpaid (tap → that order to record payment), deep-link plumbing for "open this order" / "open the To-collect list", and a daily digest that agrees with `CollectionCalculator` on outstanding + overdue.

**Architecture:** New Firebase Functions **v1** Firestore `onUpdate` trigger writes a deduped `TO_COLLECT` inbox doc + immediate push; a pure decision helper keeps the trigger a thin shell. Client gains `DeepLinkTarget.ORDER(orderId)` + `TO_COLLECT` consumed by `MainRoot`. The daily digest detector is aligned to the client's `> 0` balance floor + 7-day (`owedSince`) overdue.

**Tech Stack:** Firebase Cloud Functions v1 (TypeScript, Node 22, jest/ts-jest), Kotlin Multiplatform + Compose (kotlin.test).

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-07-29-collection-notifications-design.md` — source of truth; every task implicitly includes it.
- **Firebase Functions v1 only** — `import * as functions from 'firebase-functions/v1'`; use `.region('europe-west1').firestore.document('users/{uid}/orders/{orderId}').onUpdate((change, context) => …)`. NEVER v2 (`onDocumentUpdated`) — it'd be the only v2 fn and break the runtime setup. Mirror `functions/src/freemium/onUserCreated.ts:59` and `functions/src/notifications/pruneTokenOwnership.ts:14`.
- **A new function needs THREE edits:** the new file (`export const …`), an `export { … } from './…'` line in `functions/src/index.ts` (~L53-91), and `,functions:<name>` appended to the `deploy` allowlist in `functions/package.json:14`.
- **Deterministic notification id `${orderId}__TO_COLLECT`** shared with the daily digest → dedup via `.create()` (swallow gRPC code `6` ALREADY_EXISTS). Collection path `users/{uid}/notifications`.
- **Instant fire condition:** `after.status ∈ {READY,DELIVERED}` AND `before.status ∉ {READY,DELIVERED}` AND `balanceRemaining(after) > 0`.
- **Server `balanceRemaining`** = payments-sum (else legacy `depositPaid`); `payable = max(0, totalPrice − (discount ?? 0))`; `max(0, payable − paid)` (matches `digestDetector.ts:6-15` and `Order.kt:116-123`). Legacy persisted `balanceRemaining`/`depositPaid` on the doc are STALE — recompute; don't trust them.
- **Overdue = 7 days** since Ready/Delivered: `owedSince` = earliest READY/DELIVERED `statusHistory.changedAt` (fallback `updatedAt` → `createdAt`), day math via `lagosDayIndex` (`lagosTime.ts`). Mirror `CollectionCalculator.kt:14,42-49`.
- **Push** respects `pushEnabled` (`dailyPushEnabled` else `dailyDigestEmailEnabled` else ON, `dailyDigest.ts:87-89`) + rollout gate `isDigestAllowed(uid,email)` (`rollout.ts`, `STAGING=true`); prune `messaging/registration-token-not-registered` + `messaging/invalid-registration-token`. **In-app doc write is ungated; only push is gated** (mirror `runDailyDigest.ts:20`).
- **Order doc** at `users/{uid}/orders/{orderId}` has NO `userId` field — uid is `context.params.uid`. `status` is a string enum (`PENDING/IN_PROGRESS/READY/DELIVERED`). Garment label = `customGarmentName?.trim() || garmentType?.trim() || description?.trim()` (server `summariseGarments`).
- **Additive-only** notification field `isOverdue: Boolean = false` on model/DTO/mapper — old docs/clients must decode fine (DTO fields already all default).
- **No hardcoded user-facing strings** on the client (compose.resources). Functions copy is server TS.
- **Client tests:** `:composeApp:testDebugUnitTest`; camelCase names; KMP-safe. **Functions tests:** `npx jest <file>` from `functions/`; CI runs `npm run lint` first — lint changed TS. Functions test style: pure detector + `order(partial)` factory (`__tests__/notifications/digestDetector.test.ts`).

---

### Task 1: Instant-notification decision helper (pure, server)

The pure core of feature B, testable with no Firestore. The trigger (Task 2) is a thin shell around it.

**Files:**
- Create: `functions/src/notifications/orderCollectNotify.ts`
- Test: `functions/src/__tests__/notifications/orderCollectNotify.test.ts`
- Modify: `functions/src/notifications/digestDetector.ts` (export `summariseGarments` + `balanceRemaining` for reuse — confirm current visibility; if private, add `export`)

**Interfaces:**
- Produces (used by Task 2): `collectibleTransition(before, after): CollectNotification | null`, `collectPushCopy(n): { title, body }`, type `CollectNotification`.

- [ ] **Step 1: Ensure the shared helpers are exported** in `digestDetector.ts`

`balanceRemaining` (L6-15) and `summariseGarments` are currently module-private. Add `export` to both so Task 1 reuses them (single source of truth for balance + garment label). Verify no other breakage (they're pure).

- [ ] **Step 2: Write the failing test** `orderCollectNotify.test.ts`

```ts
import { collectibleTransition, collectPushCopy } from '../../notifications/orderCollectNotify';

function order(p: Partial<any> = {}): any {
  return { status: 'IN_PROGRESS', totalPrice: 0, discount: 0, payments: [], depositPaid: 0,
    customerName: 'Ada Obi', items: [{ garmentType: 'Agbada' }], ...p };
}

describe('collectibleTransition', () => {
  it('fires on IN_PROGRESS → READY with a balance', () => {
    const n = collectibleTransition(order({ status: 'IN_PROGRESS' }),
      order({ status: 'READY', totalPrice: 10000, payments: [{ amount: 1500 }] }));
    expect(n).not.toBeNull();
    expect(n!.amount).toBe(8500);
    expect(n!.status).toBe('READY');
  });
  it('fires on PENDING → DELIVERED with a balance', () => {
    const n = collectibleTransition(order({ status: 'PENDING' }),
      order({ status: 'DELIVERED', totalPrice: 5000, payments: [] }));
    expect(n?.amount).toBe(5000);
  });
  it('does NOT fire on READY → DELIVERED (already collectible)', () => {
    expect(collectibleTransition(order({ status: 'READY', totalPrice: 5000 }),
      order({ status: 'DELIVERED', totalPrice: 5000 }))).toBeNull();
  });
  it('does NOT fire when the balance is zero', () => {
    expect(collectibleTransition(order({ status: 'IN_PROGRESS' }),
      order({ status: 'READY', totalPrice: 5000, payments: [{ amount: 5000 }] }))).toBeNull();
  });
  it('does NOT fire on a non-status edit that stays non-collectible', () => {
    expect(collectibleTransition(order({ status: 'IN_PROGRESS' }),
      order({ status: 'IN_PROGRESS', totalPrice: 9000 }))).toBeNull();
  });
  it('push copy names the garment, state, and amount', () => {
    const copy = collectPushCopy({ customerName: 'Ada Obi', garmentSummary: 'Agbada', amount: 8500, status: 'READY' });
    expect(copy.body).toContain('Agbada');
    expect(copy.body).toContain('ready');
    expect(copy.body).toContain('8,500');
  });
});
```

- [ ] **Step 3: Run test → fail**

Run (from `functions/`): `npx jest src/__tests__/notifications/orderCollectNotify.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 4: Implement** `orderCollectNotify.ts`

```ts
import { balanceRemaining, summariseGarments } from './digestDetector';

const COLLECTIBLE = new Set(['READY', 'DELIVERED']);

export interface CollectNotification {
  customerName: string;
  garmentSummary: string;
  amount: number;               // rounded naira
  status: 'READY' | 'DELIVERED';
}

/** First entry into a collectible (READY/DELIVERED) state with a balance owing → notify. */
export function collectibleTransition(before: unknown, after: any): CollectNotification | null {
  const beforeStatus = (before as { status?: string })?.status ?? '';
  if (COLLECTIBLE.has(beforeStatus)) return null;      // already collectible → not a first entry
  if (!COLLECTIBLE.has(after?.status)) return null;
  const bal = balanceRemaining(after);
  if (bal <= 0) return null;
  return {
    customerName: after.customerName ?? '',
    garmentSummary: summariseGarments(after.items ?? []),
    amount: Math.round(bal),
    status: after.status,
  };
}

export function collectPushCopy(n: CollectNotification): { title: string; body: string } {
  const state = n.status === 'READY' ? 'ready' : 'delivered';
  const firstName = (n.customerName.trim().split(/\s+/)[0]) || n.customerName;
  return {
    title: 'StitchPad',
    body: `${firstName}'s ${n.garmentSummary} is ${state} — ₦${n.amount.toLocaleString('en-NG')} to collect`,
  };
}
```
> Verify `balanceRemaining`/`summariseGarments` accept the raw `after` doc shape (fields `status,totalPrice,discount,payments,depositPaid,items`). `balanceRemaining` types its arg as `OrderScanDoc`; pass the `after` data cast to that or loosen the helper's param to a structural subset.

- [ ] **Step 5: Run test → pass**

Run: `npx jest src/__tests__/notifications/orderCollectNotify.test.ts` → PASS. Then `npm run lint` (eslint changed TS).

- [ ] **Step 6: Commit**

```bash
git add functions/src/notifications/orderCollectNotify.ts functions/src/notifications/digestDetector.ts \
        functions/src/__tests__/notifications/orderCollectNotify.test.ts
git commit -m "feat(functions): pure collectible-transition detector for instant notify"
```

---

### Task 2: Instant-notification Firestore trigger (server)

Wire the pure helper into a v1 `onUpdate` trigger that writes the deduped inbox doc + sends the immediate push.

**Files:**
- Modify: `functions/src/notifications/orderCollectNotify.ts` (append the trigger `export const onOrderCollectible`)
- Modify: `functions/src/index.ts` (add `export { onOrderCollectible } from './notifications/orderCollectNotify';`)
- Modify: `functions/package.json` (append `,functions:onOrderCollectible` to the `deploy` allowlist)

**Interfaces:**
- Consumes: `collectibleTransition`, `collectPushCopy` (Task 1); push-token load + `pushEnabled` + `isDigestAllowed` patterns from `dailyDigest.ts`.
- Produces: push `data = { target: 'order', orderId }` (consumed by Task 3's client deep-link).

- [ ] **Step 1: Append the trigger** to `orderCollectNotify.ts`

```ts
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { isDigestAllowed } from './rollout';

const REGION = 'europe-west1';

export const onOrderCollectible = functions
  .region(REGION)
  .firestore.document('users/{uid}/orders/{orderId}')
  .onUpdate(async (change, context) => {
    const uid = context.params.uid as string;
    const orderId = context.params.orderId as string;
    const n = collectibleTransition(change.before.data(), change.after.data());
    if (!n) return;

    const db = admin.firestore();

    // 1) In-app inbox doc — ungated, deduped against the daily digest via the same id.
    try {
      await db.collection('users').doc(uid).collection('notifications')
        .doc(`${orderId}__TO_COLLECT`)
        .create({
          orderId, type: 'TO_COLLECT', customerName: n.customerName,
          garmentSummary: n.garmentSummary, amount: n.amount,
          deadline: null, isOverdue: false, isRead: false, createdAt: Date.now(),
        });
    } catch (err) {
      if ((err as { code?: number }).code !== 6) {
        functions.logger.warn('onOrderCollectible: notification write failed', { uid, orderId });
      }
    }

    // 2) Immediate push — gated by pushEnabled + rollout, mirroring dailyDigest.
    try {
      const userSnap = await db.collection('users').doc(uid).get();
      const u = userSnap.data() ?? {};
      const email = (u.email as string | undefined) ?? '';
      const pushEnabled = u.dailyPushEnabled !== undefined
        ? u.dailyPushEnabled !== false
        : u.dailyDigestEmailEnabled !== false;
      if (!pushEnabled || !isDigestAllowed(uid, email)) return;

      const tokensSnap = await db.collection('users').doc(uid).collection('notificationTokens').get();
      const tokens = tokensSnap.docs.map((d) => d.id);
      if (tokens.length === 0) return;

      const { title, body } = collectPushCopy(n);
      const res = await admin.messaging().sendEachForMulticast({
        tokens,
        notification: { title, body },
        android: { notification: { channelId: 'daily_reminders' } },
        data: { target: 'order', orderId },
      });
      const invalid: string[] = [];
      res.responses.forEach((r, i) => {
        if (!r.success && (r.error?.code === 'messaging/registration-token-not-registered'
          || r.error?.code === 'messaging/invalid-registration-token')) invalid.push(tokens[i]);
      });
      await Promise.all(invalid.map((t) =>
        db.collection('users').doc(uid).collection('notificationTokens').doc(t).delete().catch(() => undefined)));
    } catch (err) {
      functions.logger.error('onOrderCollectible: push failed', { uid, orderId, error: err instanceof Error ? err.message : String(err) });
    }
  });
```
> Cold-start note: this runs on every order update. The early `if (!n) return` after the pure check keeps non-qualifying updates cheap (no Firestore reads). Keep the two Firestore reads (user + tokens) strictly inside the qualifying branch, as above. Tokens are ≤ a handful per user, so no 500-batch needed here (unlike the digest).

- [ ] **Step 2: Register** in `index.ts` — add near the other notification exports (~L58):
```ts
export { onOrderCollectible } from './notifications/orderCollectNotify';
```

- [ ] **Step 3: Add to deploy allowlist** — append `,functions:onOrderCollectible` to the `deploy` script string in `functions/package.json:14`.

- [ ] **Step 4: Build + lint**

Run (from `functions/`): `npm run build && npm run lint`
Expected: no TS/eslint errors. (Trigger logic is integration-tested manually via the emulator/smoke; the pure decision is unit-tested in Task 1 — do NOT add a Firestore-fake test here.)

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/orderCollectNotify.ts functions/src/index.ts functions/package.json
git commit -m "feat(functions): onOrderCollectible trigger — instant Ready/Delivered-unpaid notify + push"
```

---

### Task 3: Client deep-link targets — `ORDER(orderId)` + `TO_COLLECT`

So the instant push (Task 2, `target: order`) opens the order, and the daily summary push (Task 5, `target: to_collect`) opens the list. The in-app inbox tap already routes a per-order notification to its order (`NotificationsInboxViewModel.onNotificationClick` → `NavigateToOrderDetail`) — **no inbox change needed**.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/PendingDeepLink.kt` (enum L6 `{INBOX,UPGRADE,CLAIM_GIFT}`; `PendingDeepLinkHolder`)
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/PushTargetParser.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/navigation/PushTargetParserTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt` (`MainRoot` `when (deepLinkTarget)` ~L126-153; `OrderDetailRoute`/`ToCollectRoute` already imported + NavGraph entries exist)
- Modify (Android): `.../androidMain/.../notification/push/StitchPadMessagingService.kt` (constants L16-17, `onMessageReceived` L38-41) + `.../androidMain/.../MainActivity.kt` (`handlePushIntent` L47-53)
- Modify (iOS): `.../iosMain/.../notification/push/IosPushBridge.kt` (mirror `iosOnPushInboxTap` L34-38)

**Interfaces:**
- Consumes: push `data` contract from Task 2/5 (`target` + `orderId`).

- [ ] **Step 1: Enum + holder payload** — `PendingDeepLink.kt`

Add `ORDER, TO_COLLECT` to `DeepLinkTarget`. In `PendingDeepLinkHolder` add (mirror `setClaimGift`/`consumeClaimGiftCode`):
```kotlin
private var pendingOrderId: String? = null
fun setOrder(orderId: String) { pendingOrderId = orderId; target.value = DeepLinkTarget.ORDER }
fun consumeOrderId(): String? = pendingOrderId.also { pendingOrderId = null }
```
`TO_COLLECT` uses the existing `set(DeepLinkTarget.TO_COLLECT)`.

- [ ] **Step 2: Write the failing parser test** `PushTargetParserTest.kt` (kotlin.test, camelCase — mirror `DeepLinkParserTest.kt`)

```kotlin
package com.danzucker.stitchpad.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushTargetParserTest {
    @Test fun parsesInbox() =
        assertEquals(PushTargetParser.Parsed(DeepLinkTarget.INBOX), PushTargetParser.parse(mapOf("target" to "inbox")))
    @Test fun parsesToCollect() =
        assertEquals(PushTargetParser.Parsed(DeepLinkTarget.TO_COLLECT), PushTargetParser.parse(mapOf("target" to "to_collect")))
    @Test fun parsesOrderWithId() =
        assertEquals(PushTargetParser.Parsed(DeepLinkTarget.ORDER, "o1"), PushTargetParser.parse(mapOf("target" to "order", "orderId" to "o1")))
    @Test fun orderWithoutIdIsNull() = assertNull(PushTargetParser.parse(mapOf("target" to "order")))
    @Test fun unknownTargetIsNull() = assertNull(PushTargetParser.parse(mapOf("target" to "wat")))
}
```

- [ ] **Step 3: Run → fail; implement** `PushTargetParser.kt`

```kotlin
package com.danzucker.stitchpad.navigation

object PushTargetParser {
    const val TARGET_KEY = "target"
    const val ORDER_ID_KEY = "orderId"
    const val TARGET_INBOX = "inbox"
    const val TARGET_ORDER = "order"
    const val TARGET_TO_COLLECT = "to_collect"

    data class Parsed(val target: DeepLinkTarget, val orderId: String? = null)

    fun parse(data: Map<String, String>): Parsed? = when (data[TARGET_KEY]) {
        TARGET_INBOX -> Parsed(DeepLinkTarget.INBOX)
        TARGET_TO_COLLECT -> Parsed(DeepLinkTarget.TO_COLLECT)
        TARGET_ORDER -> data[ORDER_ID_KEY]?.takeIf { it.isNotBlank() }?.let { Parsed(DeepLinkTarget.ORDER, it) }
        else -> null
    }
}
```
Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.navigation.PushTargetParserTest"` → PASS.

- [ ] **Step 4: Route new targets** in `MainScreen.kt` `MainRoot` `when` (before `null ->`):
```kotlin
DeepLinkTarget.ORDER -> {
    val orderId = pendingDeepLink.consumeOrderId()
    pendingDeepLink.clear()
    if (orderId != null) innerNavController.navigate(OrderDetailRoute(orderId = orderId)) { launchSingleTop = true }
}
DeepLinkTarget.TO_COLLECT -> {
    pendingDeepLink.clear()
    innerNavController.navigate(ToCollectRoute) { launchSingleTop = true }
}
```

- [ ] **Step 5: Android forwarding** — `StitchPadMessagingService.kt`: add `PUSH_TARGET_ORDER="order"`, `PUSH_TARGET_TO_COLLECT="to_collect"`, `PUSH_ORDER_ID_EXTRA="orderId"`; forward `message.data["orderId"]` as an extra on the tap intent. `MainActivity.handlePushIntent` (L47-53): replace the single `== PUSH_TARGET_INBOX` check with `PushTargetParser.parse(intent extras → map)` and dispatch (`setOrder` / `set(TO_COLLECT)` / `set(INBOX)`), then remove consumed extras + `setIntent(intent)` as today.

- [ ] **Step 6: iOS bridges** — `IosPushBridge.kt`: add `iosOnPushOrderTap(orderId: String)` → `holder.setOrder(orderId)` and `iosOnPushToCollectTap()` → `holder.set(DeepLinkTarget.TO_COLLECT)`, mirroring `iosOnPushInboxTap`.
> The Swift `UNUserNotificationCenter` delegate (in `iosApp/`, Xcode) must read `userInfo["target"]`/`userInfo["orderId"]` and call these. If the Swift target isn't editable in this worktree, ship the Kotlin bridges and flag the Swift wiring as a native follow-up in the task report.

- [ ] **Step 7: Build + test**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 detekt :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.navigation.*"`
Expected: BUILD SUCCESSFUL, tests green (the exhaustive `MainRoot` `when` forces handling the new enum values).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/ \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt \
        composeApp/src/androidMain composeApp/src/iosMain \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/navigation/PushTargetParserTest.kt
git commit -m "feat(notifications): ORDER + TO_COLLECT deep-link targets"
```

---

### Task 4: Align the daily digest detector with `CollectionCalculator` (server)

Make the digest's outstanding bucket use the same `> 0` floor and compute the 7-day collection-overdue flag.

**Files:**
- Modify: `functions/src/notifications/types.ts` (`OrderScanDoc` + `DigestItem`)
- Modify: `functions/src/notifications/digestDetector.ts` (balance floor + owedSince/overdue)
- Modify: `functions/src/notifications/dailyDigest.ts` (`mapOrder` — map `statusHistory`, `updatedAt`, `createdAt`)
- Test: `functions/src/__tests__/notifications/digestDetector.test.ts` (extend)

- [ ] **Step 1: Extend types** in `types.ts`
```ts
// OrderScanDoc: add
statusHistory?: { status: string; changedAt: number }[];
updatedAt?: number;
createdAt?: number;
// DigestItem: add
isOverdue?: boolean;   // TO_COLLECT: money owed ≥ 7 days since Ready/Delivered
```

- [ ] **Step 2: Add failing tests** (extend `digestDetector.test.ts`)
```ts
it('includes a sub-₦1 balance (matches client > 0 floor)', () => {
  const m = digestDetector([order({ status: 'READY', totalPrice: 1000, payments: [{ amount: 999.5 }] })], NOW);
  expect(m.outstanding.length).toBe(1);
});
it('flags outstanding overdue at 7 Lagos-days since ready, not at 6', () => {
  const READY = (daysAgo: number) => order({
    status: 'READY', totalPrice: 5000, payments: [],
    statusHistory: [{ status: 'READY', changedAt: NOW - daysAgo * DAY }],
  });
  const m = digestDetector([READY(6), READY(7)], NOW);
  const byOverdue = m.outstanding.map((o) => o.isOverdue);
  expect(byOverdue).toContain(true);   // the 7-day one
  expect(byOverdue).toContain(false);  // the 6-day one
});
```

- [ ] **Step 3: Implement** in `digestDetector.ts`
- Change outstanding inclusion `if (bal >= MIN_BALANCE)` → `if (bal > 0)`.
- Add an `owedSince(o)` + overdue computation and stamp `isOverdue` on outstanding items:
```ts
const OVERDUE_THRESHOLD_DAYS = 7;
function owedSince(o: OrderScanDoc): number {
  const changes = (o.statusHistory ?? [])
    .filter((c) => c.status === 'READY' || c.status === 'DELIVERED')
    .map((c) => c.changedAt);
  if (changes.length > 0) return Math.min(...changes);
  return o.updatedAt ?? o.createdAt ?? 0;
}
// inside the outstanding branch, when building the item:
const daysOwed = lagosDayIndex(now) - lagosDayIndex(owedSince(o));
outstanding.push({ orderId: o.id, customerName: o.customerName,
  garmentSummary: summariseGarments(o.items), amount: Math.round(bal),
  isOverdue: daysOwed >= OVERDUE_THRESHOLD_DAYS });
```
(Keep `MIN_BALANCE` if referenced elsewhere; the outstanding gate no longer uses it.)

- [ ] **Step 4: Map the new fields** in `mapOrder` (`dailyDigest.ts:22-37`) — add `statusHistory: (data.statusHistory ?? []).map(...)`, `updatedAt: data.updatedAt`, `createdAt: data.createdAt` to the `OrderScanDoc` it builds. Match the doc field shapes (`statusHistory` = `{status,changedAt}[]`).

- [ ] **Step 5: Run tests + lint**

Run (from `functions/`): `npx jest src/__tests__/notifications/digestDetector.test.ts` → PASS; `npm run lint`.

- [ ] **Step 6: Commit**
```bash
git add functions/src/notifications/types.ts functions/src/notifications/digestDetector.ts \
        functions/src/notifications/dailyDigest.ts functions/src/__tests__/notifications/digestDetector.test.ts
git commit -m "feat(functions): digest outstanding matches CollectionCalculator (>0 floor + 7-day overdue)"
```

---

### Task 5: Propagate overdue + point the summary push at the list (server + client)

Carry `isOverdue` onto the TO_COLLECT doc, use it for push/email urgency, send the daily summary push to the To-collect list, and add the additive client `isOverdue` field.

**Files:**
- Modify: `functions/src/notifications/notificationDocs.ts` (`NotificationDocData` + `toSpec`)
- Modify: `functions/src/notifications/pushSummary.ts` (rank overdue outstanding higher)
- Modify: `functions/src/notifications/dailyDigest.ts` (`sendPush` `data.target` → `'to_collect'`)
- Modify: `functions/src/notifications/digestEmailTemplate.ts` (emphasise overdue "to collect" rows) — light touch
- Modify (client): `core/domain/model/Notification.kt`, `core/data/dto/NotificationDto.kt`, `core/data/mapper/NotificationMapper.kt`
- Test: `functions/src/__tests__/notifications/notificationDocs.test.ts` (extend)

- [ ] **Step 1: TO_COLLECT doc carries `isOverdue`** — `notificationDocs.ts`
- `NotificationDocData`: add `isOverdue: boolean`.
- `toSpec`: set `isOverdue: item.isOverdue ?? false` (false for OVERDUE/DUE_SOON items, which have no `isOverdue`).
- `writeNotificationsAdmin` already spreads `spec.data`, so the field is written automatically.

- [ ] **Step 2: Push urgency** — `pushSummary.ts`: when composing the lead line/order, rank an overdue outstanding item above non-overdue outstanding (keep overdue-deadline + dueSoon ahead as today). Add a test asserting an overdue outstanding leads over a fresh one.

- [ ] **Step 3: Summary push → list** — in `dailyDigest.ts` `sendPush`, change `data: { target: 'inbox' }` to `data: { target: 'to_collect' }` (the digest push is a batch summary → the To-collect list via Task 3).

- [ ] **Step 4: Client additive field** — add `val isOverdue: Boolean = false` to `Notification` (`Notification.kt` after `createdAt`), `NotificationDto` (`NotificationDto.kt`, defaulted so old docs decode), and set `isOverdue = isOverdue` in `NotificationMapper.kt`. (Rendering an overdue badge in the inbox row is optional polish — not required here; the field just needs to round-trip.)

- [ ] **Step 5: notificationDocs test** — extend `notificationDocs.test.ts` to assert a TO_COLLECT spec from an `isOverdue: true` outstanding item has `data.isOverdue === true`, and OVERDUE/DUE_SOON specs have `false`.

- [ ] **Step 6: Verify**

Run (from `functions/`): `npx jest src/__tests__/notifications/` ; `npm run lint`.
Run (client): `./gradlew :composeApp:compileKotlinIosSimulatorArm64 detekt :composeApp:testDebugUnitTest --tests "*Notification*"`
Expected: all green (existing notification/mapper tests still pass with the additive field).

- [ ] **Step 7: Commit**
```bash
git add functions/src/notifications/ functions/src/__tests__/notifications/notificationDocs.test.ts \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/
git commit -m "feat(notifications): overdue on TO_COLLECT + summary push → To-collect list"
```

---

### Task 6: Full verification + smoke doc

**Files:**
- Modify: `docs/superpowers/specs/2026-07-29-collection-notifications-design.md` (append QA smoke section)

- [ ] **Step 1: Full verification**

Run (from `functions/`): `npm run lint && npm test`
Run (client, repo root/worktree): `./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileKotlinIosSimulatorArm64`
Expected: all green. If any fails, report BLOCKED with the exact error (do not silently patch feature code).

- [ ] **Step 2: Append QA smoke steps** to the spec:
  1. Mark an unpaid order **In progress → Ready** → within seconds an inbox notification appears ("… is ready — ₦X to collect") and (as a tester) a push; tapping opens **that order**.
  2. Record a partial payment, then Ready → still fires (balance > 0). Fully-paid order → **no** notification.
  3. Ready → Delivered on the same order → **no** second notification/push (first-entry only); the inbox shows one item (deduped id).
  4. Trigger `debugSendMyDigest` (tester) → the summary **push opens the To-collect list**; an order unpaid ≥ 7 days shows as overdue and leads the summary.
  5. Regression: existing OVERDUE/DUE_SOON/gift notifications still render and tap through; old notification docs (without `isOverdue`) still load.

- [ ] **Step 3: Commit + open PR** (base `feat/to-collect`; note deploy: `onOrderCollectible` added to the allowlist — deploy `--only functions:onOrderCollectible,functions:dailyDigest` after merge). Per PR workflow: Cursor + `codex review` before merge.
```bash
git add docs/superpowers/specs/2026-07-29-collection-notifications-design.md
git commit -m "docs(notifications): QA smoke test steps"
```

---

## Self-Review notes (for the executor)

- **Spec coverage:** instant notification detector + trigger (T1-T2), deep-link ORDER/TO_COLLECT (T3), digest `>0` floor + 7-day overdue (T4), overdue propagation + summary-push target + client field (T5), verification (T6). Out-of-scope (auto-clear on payment, `STAGING` flip, inbox retry/signed-out) intentionally absent.
- **Type consistency:** `CollectNotification` (T1) consumed by the trigger (T2); `data:{target:'order',orderId}` (T2) parsed by `PushTargetParser`/`MainRoot` (T3); `data:{target:'to_collect'}` (T5) same; `DigestItem.isOverdue` (T4) → `NotificationDocData.isOverdue` (T5) → client `Notification.isOverdue` (T5).
- **Dedup:** the instant trigger and the daily digest both write id `${orderId}__TO_COLLECT` via `.create()` (gRPC 6 swallowed) — no duplicate inbox doc.
- **Flagged verifications:** `balanceRemaining`/`summariseGarments` export + arg shape (T1); `mapOrder` field names for `statusHistory`/`updatedAt`/`createdAt` (T4); the iOS Swift-delegate wiring is a native follow-up if not editable here (T3).
