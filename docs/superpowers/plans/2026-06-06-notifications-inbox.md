# Notifications Slice 2 — In-App Inbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a server-written, read/unread in-app notifications inbox (reached from the dashboard bell) that feeds off the existing daily scan — covering the same overdue / due-soon / to-collect events as the email digest — and wire the long-stubbed `BellButton` to a live unread count.

**Architecture:** The existing daily Cloud Function scan is extended to also write deduped notification docs to `users/{uid}/notifications` (deterministic IDs `{orderId}__{type}`, create-if-absent) for *every* tailor, before the email gates. The client adds a `NotificationRepository` (snapshot-listener) + an inbox MVI screen; tapping a notification marks it read and navigates to the order detail. The bell shows a numeric unread count.

**Tech Stack:** TypeScript Cloud Functions (Node 20, firebase-functions v1, firebase-admin), Jest; Kotlin Multiplatform + Compose + Koin + GitLive Firebase SDK; client tests via Turbine on `:composeApp:testDebugUnitTest`.

**Spec:** `docs/superpowers/specs/2026-06-06-notifications-inbox-design.md`
**Branch:** `feature/notifications-inbox` (already checked out, off merged `main`)

> **Test layout note:** functions tests live under `functions/src/__tests__/...` (NOT `functions/__tests__/...`).

---

## File structure

**Backend (`functions/`):**
- Modify `functions/src/notifications/types.ts` — `DigestItem` gains `orderId`; `DigestModel` becomes full sorted lists (drop `*Total`); `NotificationDocSpec` + `writeNotifications` on `DigestIO`.
- Modify `functions/src/notifications/digestDetector.ts` — populate `orderId`, return full lists (no slice/totals).
- Modify `functions/src/notifications/digestEmailTemplate.ts` — cap at render (`.slice(0,5)` + `.length`).
- Create `functions/src/notifications/notificationDocs.ts` — pure `notificationDocsFromModel(model)`.
- Modify `functions/src/notifications/runDailyDigest.ts` — write notifications before email gates.
- Modify `functions/src/notifications/dailyDigest.ts` — `productionDigestIO.writeNotifications` + extend `debugSendMyDigest`.
- Tests under `functions/src/__tests__/notifications/`.

**Rules:** Modify `firestore.rules` — add `notifications` subcollection match.

**Client (`composeApp/`):**
- Create `core/domain/model/Notification.kt` (+ `NotificationType`), `core/data/dto/NotificationDto.kt`, `core/data/mapper/NotificationMapper.kt`.
- Create `core/domain/repository/NotificationRepository.kt`, `feature/notification/data/FirebaseNotificationRepository.kt`, `di/NotificationModule.kt`.
- Create `feature/notification/presentation/inbox/` — `NotificationsInbox{State,Action,Event,ViewModel,Root,Screen}.kt` + `components/NotificationRow.kt`.
- Modify `navigation/Routes.kt`, `feature/main/presentation/MainScreen.kt`, `feature/dashboard/presentation/components/BellButton.kt`, `feature/dashboard/presentation/DashboardScreen.kt`, `DashboardRoot.kt`, `DashboardViewModel.kt`, `DashboardState.kt`, `DashboardAction.kt`, `DashboardEvent.kt`, `StitchPadApp.kt` (register module), `composeResources/values/strings.xml`.
- Tests under `composeApp/src/commonTest/...`.

---

## TASK 1 — Detector: carry `orderId`, return full lists; cap moves to the email

**Files:**
- Modify: `functions/src/notifications/types.ts`
- Modify: `functions/src/notifications/digestDetector.ts`
- Modify: `functions/src/notifications/digestEmailTemplate.ts`
- Test: `functions/src/__tests__/notifications/digestDetector.test.ts`, `.../digestEmailTemplate.test.ts`

- [ ] **Step 1: Update the types**

In `functions/src/notifications/types.ts`, change `DigestItem` and `DigestModel`:

```typescript
export interface DigestItem {
  orderId: string;
  customerName: string;
  garmentSummary: string;
  deadline?: number; // present for dueSoon / overdue
  amount?: number;   // present for outstanding (naira)
}

export interface DigestModel {
  dueSoon: DigestItem[];      // FULL, sorted soonest-first
  overdue: DigestItem[];      // FULL, sorted most-overdue-first
  outstanding: DigestItem[];  // FULL, sorted biggest-owed-first
}
```
(Delete the `dueSoonTotal/overdueTotal/outstandingTotal` fields.)

- [ ] **Step 2: Update the detector tests to the new shape**

In `digestDetector.test.ts`, replace every `m.dueSoonTotal`/`m.overdueTotal`/`m.outstandingTotal` with `m.dueSoon.length`/`m.overdue.length`/`m.outstanding.length`. Update the capping test: the detector no longer caps, so an input of 8 overdue orders now yields `m.overdue.length === 8` (was `overdue.length===5, overdueTotal===8`). Add an assertion that items carry `orderId`:

```typescript
  it('carries orderId on every item', () => {
    const m = digestDetector([order({ id: 'o1', deadline: NOW - DAY })], NOW);
    expect(m.overdue[0].orderId).toBe('o1');
  });
```
(The `order(...)` helper already defaults `id: 'o'` — pass an explicit `id` where asserted. Confirm the helper spreads overrides so `id` passes through.)

- [ ] **Step 3: Run detector tests to verify they fail**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad/functions && npx jest notifications/digestDetector`
Expected: FAIL (compile errors on removed `*Total`; missing `orderId`).

- [ ] **Step 4: Update the detector implementation**

In `digestDetector.ts`: add `orderId: o.id` to each pushed item; return full lists; drop the `.slice(0, CAP)` and the `*Total` fields. Replace the function body's item construction + return:

```typescript
    if (open && o.deadline != null) {
      const day = lagosDayIndex(o.deadline);
      const item: DigestItem = { orderId: o.id, customerName: o.customerName, garmentSummary: summariseGarments(o.items), deadline: o.deadline };
      if (day < today) overdue.push(item);
      else if (day === today || day === today + 1) dueSoon.push(item);
    }

    if ((o.status === 'READY' || o.status === 'DELIVERED') && o.archivedAt == null) {
      const bal = balanceRemaining(o);
      if (bal >= MIN_BALANCE) {
        outstanding.push({ orderId: o.id, customerName: o.customerName, garmentSummary: summariseGarments(o.items), amount: Math.round(bal) });
      }
    }
  }

  overdue.sort((a, b) => (a.deadline! - b.deadline!));
  dueSoon.sort((a, b) => (a.deadline! - b.deadline!));
  outstanding.sort((a, b) => (b.amount! - a.amount!));

  return { dueSoon, overdue, outstanding };
}

export function isDigestEmpty(m: DigestModel): boolean {
  return m.dueSoon.length === 0 && m.overdue.length === 0 && m.outstanding.length === 0;
}
```
Delete the now-unused `CAP` constant.

- [ ] **Step 5: Move capping into the email template**

In `digestEmailTemplate.ts`, the sections currently read `model.overdue` (capped) + `model.overdueTotal`. Update them to slice + use length. Change the `sections` array construction so each entry computes its own cap:

```typescript
  const CAP = 5;
  const sections: { title: string; items: DigestItem[]; total: number; line: (i: DigestItem) => string }[] = [
    { title: 'Overdue', items: model.overdue.slice(0, CAP), total: model.overdue.length, line: (i) => `${i.customerName} · ${i.garmentSummary}` },
    { title: 'Due soon', items: model.dueSoon.slice(0, CAP), total: model.dueSoon.length, line: (i) => `${i.customerName} · ${i.garmentSummary}` },
    { title: 'To collect', items: model.outstanding.slice(0, CAP), total: model.outstanding.length, line: (i) => `${i.customerName} · ${i.garmentSummary} — ${naira(i.amount || 0)}` },
  ];
```
The subject builder uses totals too — change `model.overdueTotal` → `model.overdue.length`, `model.dueSoonTotal` → `model.dueSoon.length`, `model.outstandingTotal` → `model.outstanding.length`.

- [ ] **Step 6: Update the email template tests to the new model shape**

In `digestEmailTemplate.test.ts`, the `model(...)` helper builds `DigestModel`. Update it to the new shape (no `*Total`); where tests passed `overdueTotal: 8` with a 5-item array, now pass an 8-item `overdue` array so the `+3 more` test still exercises capping:

```typescript
function model(p: Partial<DigestModel> = {}): DigestModel {
  return { dueSoon: [], overdue: [], outstanding: [], ...p };
}
```
For the `+N more` test, build `overdue: Array.from({length: 8}, (_, i) => ({ orderId: `o${i}`, customerName: `c${i}`, garmentSummary: 'x', deadline: 0 }))` and assert `html` contains `+3 more`. For the subject-order test, pass arrays of the right lengths (e.g. `overdue` length 1, `dueSoon` length 2, `outstanding` length 3) and keep the expected subject `'StitchPad: 1 overdue, 2 due soon, 3 to collect'`. Add `orderId` to every item literal in the file.

- [ ] **Step 7: Run all functions tests**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad/functions && npx tsc --noEmit && npx jest`
Expected: all PASS (detector + template suites updated; no regressions).

- [ ] **Step 8: Commit**

```bash
git add functions/src/notifications/types.ts functions/src/notifications/digestDetector.ts functions/src/notifications/digestEmailTemplate.ts functions/src/__tests__/notifications/digestDetector.test.ts functions/src/__tests__/notifications/digestEmailTemplate.test.ts
git commit -m "refactor(notifications): detector returns full lists + orderId; email caps at render"
```

---

## TASK 2 — Pure `notificationDocsFromModel`

**Files:**
- Create: `functions/src/notifications/notificationDocs.ts`
- Test: `functions/src/__tests__/notifications/notificationDocs.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// functions/src/__tests__/notifications/notificationDocs.test.ts
import { notificationDocsFromModel } from '../../notifications/notificationDocs';
import { DigestModel } from '../../notifications/types';

function model(p: Partial<DigestModel> = {}): DigestModel {
  return { dueSoon: [], overdue: [], outstanding: [], ...p };
}

describe('notificationDocsFromModel', () => {
  it('produces a deterministic-id doc per item with structured fields', () => {
    const docs = notificationDocsFromModel(model({
      overdue: [{ orderId: 'o1', customerName: 'Ada', garmentSummary: 'Agbada', deadline: 123 }],
      outstanding: [{ orderId: 'o2', customerName: 'Bola', garmentSummary: 'Buba', amount: 6000 }],
    }));
    expect(docs).toHaveLength(2);
    const overdue = docs.find((d) => d.id === 'o1__OVERDUE')!;
    expect(overdue.data).toEqual({
      orderId: 'o1', type: 'OVERDUE', customerName: 'Ada', garmentSummary: 'Agbada', amount: null, deadline: 123,
    });
    const collect = docs.find((d) => d.id === 'o2__TO_COLLECT')!;
    expect(collect.data.amount).toBe(6000);
    expect(collect.data.deadline).toBeNull();
  });

  it('maps due-soon to DUE_SOON and includes deadline', () => {
    const docs = notificationDocsFromModel(model({
      dueSoon: [{ orderId: 'o3', customerName: 'C', garmentSummary: 'G', deadline: 999 }],
    }));
    expect(docs[0].id).toBe('o3__DUE_SOON');
    expect(docs[0].data.type).toBe('DUE_SOON');
    expect(docs[0].data.deadline).toBe(999);
  });

  it('returns empty for an empty model', () => {
    expect(notificationDocsFromModel(model())).toEqual([]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad/functions && npx jest notifications/notificationDocs`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the implementation**

```typescript
// functions/src/notifications/notificationDocs.ts
import { DigestItem, DigestModel } from './types';

export type NotificationType = 'OVERDUE' | 'DUE_SOON' | 'TO_COLLECT';

export interface NotificationDocData {
  orderId: string;
  type: NotificationType;
  customerName: string;
  garmentSummary: string;
  amount: number | null;   // set for TO_COLLECT
  deadline: number | null; // set for OVERDUE / DUE_SOON
}

export interface NotificationDocSpec {
  id: string;              // `${orderId}__${type}` — deterministic for dedup
  data: NotificationDocData;
}

function toSpec(item: DigestItem, type: NotificationType): NotificationDocSpec {
  return {
    id: `${item.orderId}__${type}`,
    data: {
      orderId: item.orderId,
      type,
      customerName: item.customerName,
      garmentSummary: item.garmentSummary,
      amount: item.amount ?? null,
      deadline: item.deadline ?? null,
    },
  };
}

/** Pure: every actionable item across all buckets → a deterministic notification doc spec. */
export function notificationDocsFromModel(model: DigestModel): NotificationDocSpec[] {
  return [
    ...model.overdue.map((i) => toSpec(i, 'OVERDUE')),
    ...model.dueSoon.map((i) => toSpec(i, 'DUE_SOON')),
    ...model.outstanding.map((i) => toSpec(i, 'TO_COLLECT')),
  ];
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad/functions && npx jest notifications/notificationDocs`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/notificationDocs.ts functions/src/__tests__/notifications/notificationDocs.test.ts
git commit -m "feat(notifications): pure model→notification-doc mapper (deterministic ids)"
```

---

## TASK 3 — Run loop: write notifications before the email gates

**Files:**
- Modify: `functions/src/notifications/types.ts` (add `writeNotifications` to `DigestIO`)
- Modify: `functions/src/notifications/runDailyDigest.ts`
- Test: `functions/src/__tests__/notifications/runDailyDigest.test.ts`

- [ ] **Step 1: Add `writeNotifications` to the IO seam**

In `types.ts`, add to `DigestIO` (after `setLastSentDate`):

```typescript
  writeNotifications(uid: string, model: DigestModel): Promise<void>;
```

- [ ] **Step 2: Update the run-loop test (write happens for all recipients)**

In `runDailyDigest.test.ts`, the fake IO must implement `writeNotifications` and record calls. Extend `fakeIO` to capture written models per uid, and add assertions. Add to the fake's returned object a `notified: Record<string, number>` (uid → call count) and the method:

```typescript
  const notified: Record<string, number> = {};
  // inside the io object:
  writeNotifications: async (uid: string) => { notified[uid] = (notified[uid] || 0) + 1; },
  // and return `notified` alongside sent/stamps.
```
Then add tests:

```typescript
  it('writes notifications for a disabled recipient even though no email is sent', async () => {
    const { io, sent, notified } = fakeIO({ recipients: [recip({ digestEnabled: false })], ordersByUid: { u1: [order({ deadline: NOW - DAY })] } });
    await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(notified.u1).toBe(1);
  });

  it('writes notifications even when the digest is empty (no email)', async () => {
    const { io, sent, notified } = fakeIO({ recipients: [recip()], ordersByUid: { u1: [] } });
    await runDailyDigest(io, NOW);
    expect(sent).toHaveLength(0);
    expect(notified.u1).toBe(1);
  });
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad/functions && npx jest notifications/runDailyDigest`
Expected: FAIL — `io.writeNotifications` not called by the loop yet.

- [ ] **Step 4: Restructure the run loop**

In `runDailyDigest.ts`, move detection + notification-writing ahead of the email gates. Replace the `for` body:

```typescript
  for (const r of recipients) {
    try {
      const model = digestDetector(await io.loadOrders(r.uid), now);
      await io.writeNotifications(r.uid, model);   // ALWAYS — in-app inbox is ungated

      if (!r.digestEnabled) { result.skippedDisabled++; continue; }
      if (!io.isAllowed(r.uid, r.email)) { result.skippedNotAllowed++; continue; }
      if ((await io.getLastSentDate(r.uid)) === todayKey) { result.skippedAlreadySent++; continue; }
      if (isDigestEmpty(model)) { result.suppressedEmpty++; continue; }

      const { subject, html, text } = buildDigestEmail(model, r.name);
      // Stamp AFTER a successful send (at-least-once).
      await io.sendEmail({ to: r.email, subject, html, text });
      await io.setLastSentDate(r.uid, todayKey);
      result.sent++;
    } catch (err) {
      result.failed++;
      functions.logger.error('daily digest: recipient failed', {
        uid: r.uid, error: err instanceof Error ? err.message : String(err),
      });
    }
  }
```

- [ ] **Step 5: Run all functions tests**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad/functions && npx tsc --noEmit && npx jest`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add functions/src/notifications/types.ts functions/src/notifications/runDailyDigest.ts functions/src/__tests__/notifications/runDailyDigest.test.ts
git commit -m "feat(notifications): scan writes inbox notifications for every tailor before email gates"
```

---

## TASK 4 — Production wiring: Admin SDK create-if-absent + debug trigger

**Files:**
- Modify: `functions/src/notifications/dailyDigest.ts`

No new unit test (covered by Tasks 2–3; verified by tsc/lint + the manual smoke test).

- [ ] **Step 1: Add the imports + `writeNotifications` to `productionDigestIO`**

In `dailyDigest.ts`, add imports:

```typescript
import { notificationDocsFromModel } from './notificationDocs';
import { DigestIO, DigestModel, DigestRecipient, OrderScanDoc } from './types';
```
Add a helper above `productionDigestIO`:

```typescript
async function writeNotificationsAdmin(db: FirebaseFirestore.Firestore, uid: string, model: DigestModel): Promise<void> {
  const col = db.collection('users').doc(uid).collection('notifications');
  for (const spec of notificationDocsFromModel(model)) {
    try {
      // .create() throws ALREADY_EXISTS if the deterministic-id doc exists →
      // dedup: first time only, and read-state on the existing doc is preserved.
      await col.doc(spec.id).create({ ...spec.data, isRead: false, createdAt: Date.now() });
    } catch (err) {
      const code = (err as { code?: number }).code;
      if (code !== 6 /* ALREADY_EXISTS */) {
        functions.logger.warn('writeNotification failed', { uid, id: spec.id, error: err instanceof Error ? err.message : String(err) });
      }
    }
  }
}
```
(`db` type: use the `admin.firestore.Firestore` type — `db` is already `admin.firestore()`. If `FirebaseFirestore.Firestore` doesn't resolve, type the param as `admin.firestore.Firestore`.)

Add the method to the returned `productionDigestIO` object (after `setLastSentDate`):

```typescript
    writeNotifications(uid, model) {
      return writeNotificationsAdmin(db, uid, model);
    },
```

- [ ] **Step 2: Extend `debugSendMyDigest` to also write notifications**

In `debugSendMyDigest`, after computing `const model = digestDetector(...)` and BEFORE the `isDigestEmpty` early-return, write notifications for the caller:

```typescript
    const model = digestDetector(ordersSnap.docs.map((d) => mapOrder(d.id, d.data())), now);
    await writeNotificationsAdmin(db, uid, model);   // populate the inbox for QA
    if (isDigestEmpty(model)) return { sent: false, reason: 'empty' };
```

- [ ] **Step 3: Typecheck + lint + full test run**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad/functions && npx tsc --noEmit && npm run lint && npx jest`
Expected: clean, all PASS.

- [ ] **Step 4: Commit**

```bash
git add functions/src/notifications/dailyDigest.ts
git commit -m "feat(notifications): production create-if-absent notification writes + debug trigger"
```

---

## TASK 5 — Firestore rules for the notifications subcollection

**Files:**
- Modify: `firestore.rules`

- [ ] **Step 1: Add the match block**

Inside `match /users/{uid} { ... }`, alongside the `orders` block (near line 123), add:

```
      // Notifications inbox: server (Admin SDK) creates; client reads + marks read.
      // No client create (deterministic ids would resurrect a dismissed-but-still-open
      // notification on the next scan) and no client delete in V1.
      match /notifications/{notificationId} {
        allow read: if isOwner(uid);
        allow update: if isOwner(uid) && fieldUnchanged('createdAt');
        allow create, delete: if false;
      }
```

- [ ] **Step 2: Verify rules compile (if firebase CLI available)**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && firebase deploy --only firestore:rules --dry-run 2>/dev/null || echo "skip if no CLI/login; rules are syntactically simple"`
Expected: no syntax error (or skipped).

- [ ] **Step 3: Commit**

```bash
git add firestore.rules
git commit -m "feat(notifications): firestore rules for users/{uid}/notifications (server-create, owner read/update)"
```

> NOTE: rules must be deployed (`firebase deploy --only firestore:rules`) as part of the rollout — flag in the PR smoke-test steps.

---

## TASK 6 — Client: domain model + DTO + mapper

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Notification.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/NotificationDto.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/NotificationMapper.kt`

- [ ] **Step 1: Domain model + type enum**

```kotlin
// core/domain/model/Notification.kt
package com.danzucker.stitchpad.core.domain.model

enum class NotificationType { OVERDUE, DUE_SOON, TO_COLLECT, UNKNOWN }

data class Notification(
    val id: String,
    val orderId: String,
    val type: NotificationType,
    val customerName: String,
    val garmentSummary: String,
    /** Naira owed; meaningful only for [NotificationType.TO_COLLECT]. */
    val amount: Double? = null,
    /** Epoch millis; meaningful only for OVERDUE / DUE_SOON. */
    val deadline: Long? = null,
    val isRead: Boolean = false,
    val createdAt: Long = 0L,
)
```

- [ ] **Step 2: DTO (typed @Serializable — never Map<String,Any?>)**

```kotlin
// core/data/dto/NotificationDto.kt
package com.danzucker.stitchpad.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String = "",
    val orderId: String = "",
    val type: String = "UNKNOWN",
    val customerName: String = "",
    val garmentSummary: String = "",
    val amount: Double? = null,
    val deadline: Long? = null,
    val isRead: Boolean = false,
    val createdAt: Long = 0L,
)
```

- [ ] **Step 3: Mapper**

```kotlin
// core/data/mapper/NotificationMapper.kt
package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.NotificationDto
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType

fun NotificationDto.toNotification(docId: String): Notification = Notification(
    id = docId,
    orderId = orderId,
    type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.UNKNOWN),
    customerName = customerName,
    garmentSummary = garmentSummary,
    amount = amount,
    deadline = deadline,
    isRead = isRead,
    createdAt = createdAt,
)
```

- [ ] **Step 4: Compile**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Notification.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/NotificationDto.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/mapper/NotificationMapper.kt
git commit -m "feat(notifications): client Notification model/dto/mapper"
```

---

## TASK 7 — Client: repository + DI

**Files:**
- Create: `core/domain/repository/NotificationRepository.kt`
- Create: `feature/notification/data/FirebaseNotificationRepository.kt`
- Create: `di/NotificationModule.kt`
- Modify: `StitchPadApp.kt` (register module)

- [ ] **Step 1: Repository interface**

```kotlin
// core/domain/repository/NotificationRepository.kt
package com.danzucker.stitchpad.core.domain.repository

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Notification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    /** Live feed, newest first. */
    fun observeNotifications(userId: String): Flow<Result<List<Notification>, DataError.Network>>

    /** Live unread count (drives the dashboard bell). */
    fun observeUnreadCount(userId: String): Flow<Int>

    suspend fun markAsRead(userId: String, notificationId: String): EmptyResult<DataError.Network>

    suspend fun markAllRead(userId: String): EmptyResult<DataError.Network>
}
```

- [ ] **Step 2: Firebase implementation**

Mirror `FirebaseOrderRepository.observeOrders` (collection `.snapshots()` → DTO → mapper → `.catch`) and the fire-and-forget write pattern (`offlineWrites.enqueue { ... set(merge=true) }`).

```kotlin
// feature/notification/data/FirebaseNotificationRepository.kt
package com.danzucker.stitchpad.feature.notification.data

import com.danzucker.stitchpad.core.data.dto.NotificationDto
import com.danzucker.stitchpad.core.data.mapper.toNotification
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val TAG = "NotificationRepo"

class FirebaseNotificationRepository(
    private val firestore: FirebaseFirestore,
    private val offlineWrites: OfflineWriteDispatcher,
) : NotificationRepository {

    private fun collection(userId: String) =
        firestore.collection("users").document(userId).collection("notifications")

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override fun observeNotifications(userId: String): Flow<Result<List<Notification>, DataError.Network>> =
        collection(userId).snapshots()
            .map { snapshot ->
                val list = snapshot.documents
                    .mapNotNull { doc ->
                        runCatching { doc.data(NotificationDto.serializer()).toNotification(doc.id) }.getOrNull()
                    }
                    .sortedByDescending { it.createdAt }
                Result.Success(list) as Result<List<Notification>, DataError.Network>
            }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeNotifications failed" }
                emit(Result.Error(DataError.Network.UNKNOWN))
            }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override fun observeUnreadCount(userId: String): Flow<Int> =
        collection(userId).where { "isRead" equalTo false }.snapshots()
            .map { it.documents.size }
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) { "observeUnreadCount failed" }
                emit(0)
            }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun markAsRead(userId: String, notificationId: String): EmptyResult<DataError.Network> {
        val accepted = offlineWrites.enqueue("markAsRead userId=$userId id=$notificationId") {
            collection(userId).document(notificationId).set(mapOf("isRead" to true), merge = true)
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun markAllRead(userId: String): EmptyResult<DataError.Network> {
        return try {
            val unread = collection(userId).where { "isRead" equalTo false }.get()
            unread.documents.forEach { doc ->
                offlineWrites.enqueue("markAllRead userId=$userId id=${doc.id}") {
                    collection(userId).document(doc.id).set(mapOf("isRead" to true), merge = true)
                }
            }
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "markAllRead failed userId=$userId" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
```
> NOTE: confirm the GitLive query syntax against `FirebaseOrderRepository`/another repo that already filters — if the installed GitLive version uses `.where("isRead", equalTo = false)` instead of the `{ "isRead" equalTo false }` DSL, match whatever that repo uses. If no repo filters server-side (orders filters client-side), use the same client-side approach: observe all + `count { !it.isRead }` for `observeUnreadCount`, and for `markAllRead` filter the observed list. Prefer the existing project idiom.

- [ ] **Step 3: DI module (data only for now — presentation module added in Task 8)**

```kotlin
// di/NotificationModule.kt
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.feature.notification.data.FirebaseNotificationRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val notificationDataModule = module {
    singleOf(::FirebaseNotificationRepository) bind NotificationRepository::class
}
```
(`FirebaseNotificationRepository` ctor params `FirebaseFirestore` + `OfflineWriteDispatcher` are already provided by `coreModule`/offline DI — confirm `OfflineWriteDispatcher` is a `single` like in `FirebaseUserRepository`'s module; it is.)

- [ ] **Step 4: Register the data module**

In `StitchPadApp.kt`, add the import and add `notificationDataModule,` to the `modules(...)` list (e.g. after `orderDataModule`). The presentation module is added in Task 8 once the ViewModel exists.

- [ ] **Step 5: Compile (this task builds independently)**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/repository/NotificationRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/data/FirebaseNotificationRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/NotificationModule.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt
git commit -m "feat(notifications): NotificationRepository + Firebase impl + DI"
```

---

## TASK 8 — Client: inbox MVI (State/Action/Event/ViewModel) + test

**Files:**
- Create: `feature/notification/presentation/inbox/NotificationsInboxState.kt`, `NotificationsInboxAction.kt`, `NotificationsInboxEvent.kt`, `NotificationsInboxViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/notification/NotificationsInboxViewModelTest.kt`

- [ ] **Step 1: State / Action / Event**

```kotlin
// NotificationsInboxState.kt
package com.danzucker.stitchpad.feature.notification.presentation.inbox

import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.presentation.UiText

data class NotificationsInboxState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
) {
    val unreadCount: Int get() = notifications.count { !it.isRead }
}
```
```kotlin
// NotificationsInboxAction.kt
package com.danzucker.stitchpad.feature.notification.presentation.inbox

import com.danzucker.stitchpad.core.domain.model.Notification

sealed interface NotificationsInboxAction {
    data object OnBackClick : NotificationsInboxAction
    data class OnNotificationClick(val notification: Notification) : NotificationsInboxAction
    data object OnMarkAllReadClick : NotificationsInboxAction
    data object OnErrorDismiss : NotificationsInboxAction
}
```
```kotlin
// NotificationsInboxEvent.kt
package com.danzucker.stitchpad.feature.notification.presentation.inbox

sealed interface NotificationsInboxEvent {
    data object NavigateBack : NotificationsInboxEvent
    data class NavigateToOrderDetail(val orderId: String) : NotificationsInboxEvent
}
```

- [ ] **Step 2: Write the failing ViewModel test**

```kotlin
// composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/notification/NotificationsInboxViewModelTest.kt
package com.danzucker.stitchpad.feature.notification

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Notification
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationsInboxAction
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationsInboxEvent
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationsInboxViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeNotificationRepository : NotificationRepository {
    val flow = MutableStateFlow<Result<List<Notification>, DataError.Network>>(Result.Success(emptyList()))
    var lastMarkedRead: String? = null
    var markAllReadCalled = false
    override fun observeNotifications(userId: String) = flow as Flow<Result<List<Notification>, DataError.Network>>
    override fun observeUnreadCount(userId: String) = MutableStateFlow(0) as Flow<Int>
    override suspend fun markAsRead(userId: String, notificationId: String): EmptyResult<DataError.Network> {
        lastMarkedRead = notificationId; return Result.Success(Unit)
    }
    override suspend fun markAllRead(userId: String): EmptyResult<DataError.Network> {
        markAllReadCalled = true; return Result.Success(Unit)
    }
}

private fun notif(id: String, read: Boolean = false) = Notification(
    id = id, orderId = "ord-$id", type = NotificationType.OVERDUE,
    customerName = "Ada", garmentSummary = "Agbada", isRead = read, createdAt = 1L,
)

class NotificationsInboxViewModelTest {
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun emitsNotificationsFromRepo() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, FakeAuthRepositoryReturning("u1"))
        vm.state.test {
            awaitItem() // initial loading
            repo.flow.value = Result.Success(listOf(notif("a"), notif("b", read = true)))
            val s = awaitItem()
            assertEquals(2, s.notifications.size)
            assertEquals(1, s.unreadCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tapMarksReadAndNavigates() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, FakeAuthRepositoryReturning("u1"))
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() } // trigger onStart
        vm.events.test {
            vm.onAction(NotificationsInboxAction.OnNotificationClick(notif("a")))
            val e = awaitItem()
            assertTrue(e is NotificationsInboxEvent.NavigateToOrderDetail && e.orderId == "ord-a")
            assertEquals("a", repo.lastMarkedRead)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun markAllReadCallsRepo() = runTest {
        val repo = FakeNotificationRepository()
        val vm = NotificationsInboxViewModel(repo, FakeAuthRepositoryReturning("u1"))
        vm.state.test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.onAction(NotificationsInboxAction.OnMarkAllReadClick)
        assertTrue(repo.markAllReadCalled)
    }
}
```
> NOTE: `FakeAuthRepositoryReturning(uid)` — reuse the existing auth fake in `commonTest` if one exists (search `commonTest` for a `FakeAuthRepository`); otherwise add a minimal one whose `getCurrentUser()` returns a `User` with `id = uid`. Match the existing fakes' style (kotlin.test + Turbine, no assertk).

- [ ] **Step 3: Run test to verify it fails**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest --tests '*NotificationsInboxViewModelTest*'`
Expected: FAIL — `NotificationsInboxViewModel` not defined.

- [ ] **Step 4: Implement the ViewModel** (mirrors `OrderListViewModel`'s `onStart`+`stateIn` pattern)

```kotlin
// NotificationsInboxViewModel.kt
package com.danzucker.stitchpad.feature.notification.presentation.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.error.onError
import com.danzucker.stitchpad.core.domain.repository.NotificationRepository
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_unknown

class NotificationsInboxViewModel(
    private val notificationRepository: NotificationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private var hasLoaded = false
    private val _state = MutableStateFlow(NotificationsInboxState())
    private val _events = Channel<NotificationsInboxEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart { if (!hasLoaded) { hasLoaded = true; observe() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), NotificationsInboxState())

    fun onAction(action: NotificationsInboxAction) {
        when (action) {
            NotificationsInboxAction.OnBackClick ->
                viewModelScope.launch { _events.send(NotificationsInboxEvent.NavigateBack) }
            is NotificationsInboxAction.OnNotificationClick -> onNotificationClick(action)
            NotificationsInboxAction.OnMarkAllReadClick -> markAllRead()
            NotificationsInboxAction.OnErrorDismiss -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun observe() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: run {
                _state.update { it.copy(isLoading = false) }; return@launch
            }
            notificationRepository.observeNotifications(uid).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(notifications = result.data, isLoading = false) }
                    is Result.Error -> _state.update {
                        it.copy(isLoading = false, errorMessage = UiText.StringResourceId(Res.string.error_unknown))
                    }
                }
            }
        }
    }

    private fun onNotificationClick(action: NotificationsInboxAction.OnNotificationClick) {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: return@launch
            notificationRepository.markAsRead(uid, action.notification.id)
            _events.send(NotificationsInboxEvent.NavigateToOrderDetail(action.notification.orderId))
        }
    }

    private fun markAllRead() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: return@launch
            notificationRepository.markAllRead(uid)
        }
    }
}
```
> NOTE: confirm `UiText.StringResourceId` is the correct variant name (check `core/presentation/UiText.kt` — it may be `UiText.StringResource`/`DynamicString`). Use whatever the codebase defines. `onError` import can be dropped if unused.

- [ ] **Step 5: Register the presentation module**

In `di/NotificationModule.kt`, add the `viewModelOf` import and the presentation module:
```kotlin
import com.danzucker.stitchpad.feature.notification.presentation.inbox.NotificationsInboxViewModel
import org.koin.core.module.dsl.viewModelOf
// ...
val notificationPresentationModule = module {
    viewModelOf(::NotificationsInboxViewModel)
}
```
In `StitchPadApp.kt`, add the import + `notificationPresentationModule,` to the `modules(...)` list (after `orderPresentationModule`).

- [ ] **Step 6: Run test + compile to verify**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest --tests '*NotificationsInboxViewModelTest*' && ./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: PASS (3 tests) + BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/presentation/inbox/Notifications* composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/NotificationModule.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/notification/NotificationsInboxViewModelTest.kt
git commit -m "feat(notifications): inbox MVI viewmodel + tests + DI"
```

---

## TASK 9 — Client: inbox Screen/Root + row + empty state + strings

**Files:**
- Create: `feature/notification/presentation/inbox/NotificationsInboxScreen.kt`, `NotificationsInboxRoot.kt`, `components/NotificationRow.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: String resources**

Add to `strings.xml` (use `&apos;` for apostrophes, never `\'`):
```xml
<string name="notifications_title">Notifications</string>
<string name="notifications_mark_all_read">Mark all read</string>
<string name="notifications_empty_title">No notifications yet</string>
<string name="notifications_empty_subtitle">Order reminders will show up here.</string>
<string name="notification_overdue">%1$s&apos;s %2$s is overdue</string>
<string name="notification_due_soon">%1$s&apos;s %2$s is due soon</string>
<string name="notification_to_collect">%1$s owes %2$s</string>
```

- [ ] **Step 2: NotificationRow** — reuse the app's list-row + money-formatting idiom. Compose the title from `type` via the strings above; format `amount` with the **existing naira formatter the order rows use** (search `feature/order`/`ui/components` for the helper that renders `₦` amounts, e.g. `formatNaira`/`toNaira`; reuse it — do NOT hand-roll). Render an unread indicator (a leading dot or bold title) when `!notification.isRead`. The whole row is clickable → `onClick(notification)`.

```kotlin
// components/NotificationRow.kt  (sketch — follow AccentedOrderRow's structure for surfaces/spacing)
@Composable
fun NotificationRow(
    notification: Notification,
    onClick: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (notification.type) {
        NotificationType.OVERDUE -> stringResource(Res.string.notification_overdue, notification.customerName, notification.garmentSummary)
        NotificationType.DUE_SOON -> stringResource(Res.string.notification_due_soon, notification.customerName, notification.garmentSummary)
        NotificationType.TO_COLLECT -> stringResource(Res.string.notification_to_collect, notification.customerName, formatNaira(notification.amount ?: 0.0))
        NotificationType.UNKNOWN -> "${notification.customerName} · ${notification.garmentSummary}"
    }
    // Row(...) clickable { onClick(notification) }; emphasize when !isRead (e.g. FontWeight.SemiBold + an unread dot).
}
```
> Implement with the project's real row container + the real `formatNaira` helper name. Keep it focused.

- [ ] **Step 3: Screen + Root** — TopAppBar (title + "Mark all read" action shown when `state.unreadCount > 0`), `LazyColumn` of `NotificationRow`, illustrated empty state (follow `OrderEmptyState`), `LoadingDots` while `isLoading`. Root wires `koinViewModel()` + `ObserveAsEvents` (→ `onNavigateBack` / `onNavigateToOrder`) + the error snackbar pattern (mirror `OrderListRoot`). Every Screen gets `@Preview` (populated + empty).

```kotlin
// NotificationsInboxRoot.kt (shape — mirror OrderListRoot)
@Composable
fun NotificationsInboxRoot(
    onNavigateBack: () -> Unit,
    onNavigateToOrder: (String) -> Unit,
    viewModel: NotificationsInboxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotificationsInboxEvent.NavigateBack -> onNavigateBack()
            is NotificationsInboxEvent.NavigateToOrderDetail -> onNavigateToOrder(event.orderId)
        }
    }
    NotificationsInboxScreen(state = state, onAction = viewModel::onAction)
}
```

- [ ] **Step 4: Compile + verify previews render**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/presentation/inbox/NotificationsInboxScreen.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/presentation/inbox/NotificationsInboxRoot.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/presentation/inbox/components/NotificationRow.kt composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(notifications): inbox screen + row + empty state + strings"
```

---

## TASK 10 — Route + nav graph wiring

**Files:**
- Modify: `navigation/Routes.kt`, `feature/main/presentation/MainScreen.kt`

- [ ] **Step 1: Add the route**

In `Routes.kt`:
```kotlin
@Serializable
data object NotificationsInboxRoute
```

- [ ] **Step 2: Add the composable to the main nav graph**

In `MainScreen.kt`, add a block alongside the other `composable<...>` entries (e.g. after `composable<OrderDetailRoute>`):
```kotlin
        composable<NotificationsInboxRoute> {
            NotificationsInboxRoot(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToOrder = { orderId -> navController.navigate(OrderDetailRoute(orderId = orderId)) },
            )
        }
```
Add imports for `NotificationsInboxRoute` and `NotificationsInboxRoot`.

- [ ] **Step 3: Compile**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/main/presentation/MainScreen.kt
git commit -m "feat(notifications): inbox route + nav graph wiring"
```

---

## TASK 11 — Bell count + dashboard wiring

**Files:**
- Modify: `feature/dashboard/presentation/components/BellButton.kt`, `DashboardState.kt`, `DashboardAction.kt`, `DashboardEvent.kt`, `DashboardViewModel.kt`, `DashboardScreen.kt`, `DashboardRoot.kt`, `feature/main/presentation/MainScreen.kt`

- [ ] **Step 1: BellButton → unread count**

Change the signature `hasUnread: Boolean = false` → `unreadCount: Int = 0`, and render a count bubble instead of the plain dot. Replace the `if (hasUnread) { ... }` block:
```kotlin
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .requiredSize(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
```
Update the four `@Preview`s to pass `unreadCount = 0` / `unreadCount = 3`.

- [ ] **Step 2: Dashboard State/Action/Event**

- `DashboardState.kt`: add `val unreadNotificationCount: Int = 0`.
- `DashboardAction.kt`: add `data object OnNotificationsClick : DashboardAction` (match the file's sealed-interface style).
- `DashboardEvent.kt`: add `data object NavigateToNotifications : DashboardEvent`.

- [ ] **Step 3: DashboardViewModel — observe unread count + route the action**

Add `private val notificationRepository: NotificationRepository,` to the constructor. Add an observer started in `onStart` (mirror `observeSmartQuota`):
```kotlin
    private fun observeUnreadNotifications() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUser()?.id ?: return@launch
            notificationRepository.observeUnreadCount(uid).collect { count ->
                _state.update { it.copy(unreadNotificationCount = count) }
            }
        }
    }
```
Call it from the `onStart { ... }` block (alongside `observeSmartQuota()` etc.). Handle the action in `onAction`:
```kotlin
            DashboardAction.OnNotificationsClick ->
                viewModelScope.launch { _events.send(DashboardEvent.NavigateToNotifications) }
```
Update the `dashboardPresentationModule` DI if it constructs `DashboardViewModel` explicitly; if it uses `viewModelOf(::DashboardViewModel)`, the new `NotificationRepository` param resolves automatically from `notificationDataModule`.

- [ ] **Step 4: DashboardScreen — pass count + click into the header → BellButton**

Thread `unreadCount` + an `onNotificationsClick` callback from `DashboardScreen` (from `state`/`onAction`) down through `DashboardHeader` to `BellButton`. At the call site (currently `BellButton(onClick = { ... }, hasUnread = false)`):
```kotlin
            BellButton(
                onClick = { onAction(DashboardAction.OnNotificationsClick) },
                unreadCount = state.unreadNotificationCount,
            )
```
Add the `unreadCount: Int` + `onNotificationsClick`/`onAction` params to `DashboardHeader`'s signature as needed to reach the call site.

- [ ] **Step 5: DashboardRoot + MainScreen — handle the nav event**

In `DashboardRoot`, add an `onNavigateToNotifications: () -> Unit` param and route `DashboardEvent.NavigateToNotifications -> onNavigateToNotifications()` in its `ObserveAsEvents`. In `MainScreen.kt`'s `composable<DashboardRoute> { DashboardRoot( ... ) }`, pass:
```kotlin
                onNavigateToNotifications = { navController.navigate(NotificationsInboxRoute) },
```

- [ ] **Step 6: Compile + run dashboard tests**

Run: `cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugKotlinAndroid -q && ./gradlew :composeApp:testDebugUnitTest --tests '*Dashboard*'`
Expected: BUILD SUCCESSFUL; existing dashboard tests still pass (update any that construct `DashboardViewModel` directly to pass a fake `NotificationRepository`).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(notifications): wire dashboard bell to live unread count + inbox nav"
```

---

## TASK 12 — Full verification sweep

**Files:** none (verification only).

- [ ] **Step 1: Functions** — `cd functions && npm run lint && npx tsc --noEmit && npx jest` → all green.
- [ ] **Step 2: Client unit tests** — `./gradlew :composeApp:testDebugUnitTest` → BUILD SUCCESSFUL (incl. `NotificationsInboxViewModelTest`, dashboard tests).
- [ ] **Step 3: Detekt** — `./gradlew detekt` → no new violations. (Run the `format` skill on changed Kotlin first if available.)
- [ ] **Step 4: iOS compile (CRITICAL)** — `./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q` → BUILD SUCCESSFUL. (Watch the GitLive `.where{}`/`.snapshots()` query API + `@Serializable` DTO on Native.)
- [ ] **Step 5: Commit any fixups** — `git add -A && git commit -m "chore(notifications): formatting + lint fixups" || echo "nothing to commit"`

---

## TASK 13 — Manual smoke test + PR

**Files:** none (manual QA + PR). Daniel is QA.

- [ ] **Step 1: Deploy** — `cd functions && npm run deploy` (no new function — `dailyDigest`/`debugSendMyDigest` already in the `--only` list); `firebase deploy --only firestore:rules`.
- [ ] **Step 2: Seed + trigger** — signed in as an allowlisted test account with the three order types (due-tomorrow / overdue / delivered-with-balance), tap **Debug → Notifications → "Send daily digest now."** This now also writes notification docs.
- [ ] **Step 3: Verify inbox** — the **dashboard bell shows an unread count**; tap it → inbox lists the notifications (overdue / due-soon / to-collect) with the right copy + amounts; an order appearing in two buckets shows as two rows (intentional). Tap one → opens that order's detail; the unread count **decrements**. **"Mark all read"** → count → 0. Re-fire the debug trigger → no duplicate rows (deterministic-id dedup), and already-read items stay read.
- [ ] **Step 4: Empty state** — a fresh test account with no actionable orders → inbox shows the illustrated empty state; bell shows no badge.
- [ ] **Step 5: Open the PR + review rotation**

```bash
git push -u origin feature/notifications-inbox
gh pr create --base main --title "feat(notifications): in-app inbox (slice 2 of 3)" --body "$(cat <<'EOF'
## What
Slice 2 of notifications: a server-written, read/unread **in-app inbox** (dashboard bell → inbox) covering the same overdue / due-soon / to-collect events as the email digest. The existing daily scan now also writes deduped notification docs (`users/{uid}/notifications`, deterministic ids `{orderId}__{type}`, create-if-absent) for every tailor before the email gates; the client adds a `NotificationRepository` + inbox MVI screen; tapping a notification marks it read and opens the order. The long-stubbed `BellButton` now shows a live unread count.

Spec: `docs/superpowers/specs/2026-06-06-notifications-inbox-design.md`

## Rollout
In-app notifications are intentionally **ungated** (no email opt-out / `STAGING` allowlist — in-app has no reputation risk). Firestore rules updated (server-create-only, owner read/update, no client delete). Remember to `firebase deploy --only firestore:rules` + `functions`.

## Smoke test
Debug → "Send daily digest now" wrote notifications; dashboard bell showed the count; inbox rendered the three event types; tap → order detail + count decremented; "Mark all read" → 0; re-fire produced no duplicates; empty account showed the empty state. Functions tests, client tests, detekt, iOS compile all green.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
- [ ] **Step 6: Address Cursor + codex** — run the rotation; watch for client/server notification field-name drift, the restructured run-loop still gating email correctly, deterministic-id read-state preservation, and plural grammar in the notification copy.

---

## Post-merge follow-ups (not this PR)
- Lifecycle/activity events + triggered/immediate writes (pair with push).
- Retention/cleanup of old read notifications; client delete/dismiss.
- `TO_COLLECT` amount-refresh on partial payment.
- Slice 3 (push) reuses this collection + the deferred external deep-linking.
