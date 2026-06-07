# Android Daily-Summary Push (Notifications Slice 3 V1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send one calm daily-summary push notification (Android/FCM) each morning to tailors with actionable orders, tapping it opens the in-app inbox.

**Architecture:** Extend the existing daily-scan loop (`runDailyDigest`) with an independently-gated push step that reaches a tailor's device tokens via the Admin SDK; the client registers FCM tokens to a `users/{uid}/notificationTokens` subcollection (native token retrieval through a KMP `expect`/`actual` seam, GitLive Firestore for the write), asks for `POST_NOTIFICATIONS` via a contextual dashboard pre-prompt, and routes a notification tap to the inbox.

**Tech Stack:** TypeScript Cloud Functions (firebase-admin messaging), Jest; Kotlin Multiplatform + Compose, native `com.google.firebase:firebase-messaging` (androidMain only — GitLive has no messaging wrapper), Koin, GitLive Firestore.

**Spec:** `docs/superpowers/specs/2026-06-07-notifications-push-android-design.md`

**Branch:** `feature/notifications-push-android` (already created; spec committed).

**Scope note:** Android only. iOS gets an `expect`/`actual` stub returning `null` so the build stays green; the real iOS APNs path is a separate fast-follow.

---

## File Structure

**Functions (`functions/src/`):**
- Create `notifications/pushSummary.ts` — pure `pushSummary(model) → {title, body}`.
- Modify `notifications/types.ts` — extend `DigestIO` + `DigestRecipient` + add `PushSummary`.
- Modify `notifications/runDailyDigest.ts` — insert the gated push block.
- Modify `notifications/dailyDigest.ts` — production IO methods + `listRecipients` reads `dailyPushEnabled` + `debugSendMyDigest` also pushes.
- Modify `cleanup/firestore.ts` — add `'notificationTokens'` to `ALLOWED_SUBCOLLECTIONS`.
- Tests under `functions/src/__tests__/notifications/`.

**Client common (`composeApp/src/commonMain/`):**
- Modify `core/domain/model/User.kt`, `core/data/dto/UserDto.kt`, `core/data/mapper/UserMapper.kt`, `core/domain/repository/UserRepository.kt`, `core/data/repository/FirebaseUserRepository.kt` — add `dailyPushEnabled` + `setDailyPushEnabled`.
- Modify the Settings MVI set (`SettingsState`/`SettingsAction`/`SettingsScreen`/`SettingsViewModel`) — push toggle row.
- Create `feature/notification/push/PushTokenProvider.kt` (`expect`), `PushTokenRepository.kt` + `FirebasePushTokenRepository.kt`, `PushTokenRegistrar.kt`.
- Modify `di/NotificationModule.kt` — register the push data layer.

**Client Android (`composeApp/src/androidMain/`):**
- Create `feature/notification/push/PushTokenProvider.android.kt` (`actual`, native FCM), `StitchPadMessagingService.kt`, `NotificationChannels.kt`, `PushPermissionController.kt`.
- Modify `StitchPadApplication.kt` (channel), `MainActivity.kt` (intent → deep link), `AndroidManifest.xml` (permission + service).

**Client iOS (`composeApp/src/iosMain/`):**
- Create `feature/notification/push/PushTokenProvider.ios.kt` (`actual` stub → `null`).

**Deep-link plumbing (commonMain):**
- Create `navigation/PendingDeepLink.kt`; modify `navigation/NavGraph.kt` to consume it → `NotificationsInboxRoute`.

---

## Task 1: `pushSummary` pure function (functions)

**Files:**
- Create: `functions/src/notifications/pushSummary.ts`
- Test: `functions/src/__tests__/notifications/pushSummary.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
// functions/src/__tests__/notifications/pushSummary.test.ts
import { pushSummary } from '../../notifications/pushSummary';
import { DigestModel } from '../../notifications/types';

const item = (customerName: string, garmentSummary: string, extra: Partial<{ deadline: number; amount: number }> = {}) =>
  ({ orderId: customerName, customerName, garmentSummary, ...extra });

const model = (over: Partial<DigestModel> = {}): DigestModel =>
  ({ overdue: [], dueSoon: [], outstanding: [], ...over });

describe('pushSummary', () => {
  it('uses a fixed title', () => {
    expect(pushSummary(model({ overdue: [item('Folake', 'Asoebi')] })).title).toBe('StitchPad');
  });

  it('leads with the single overdue item, no tail', () => {
    expect(pushSummary(model({ overdue: [item('Folake', 'Asoebi')] })).body)
      .toBe("Folake's Asoebi is overdue");
  });

  it('prioritises overdue over due-soon and outstanding for the lead, and counts the rest', () => {
    const m = model({
      overdue: [item('Folake', 'Asoebi')],
      dueSoon: [item('Aina', 'Buba')],
      outstanding: [item('Ngozi', 'Shirt', { amount: 18000 })],
    });
    expect(pushSummary(m).body).toBe("Folake's Asoebi is overdue + 2 more need attention");
  });

  it('falls back to due-soon when no overdue', () => {
    expect(pushSummary(model({ dueSoon: [item('Aina', 'Buba')] })).body)
      .toBe("Aina's Buba is due soon");
  });

  it('falls back to outstanding (owes, formatted naira) when only outstanding', () => {
    expect(pushSummary(model({ outstanding: [item('Ngozi', 'Shirt', { amount: 18000 })] })).body)
      .toBe('Ngozi owes ₦18,000');
  });
});
```

- [ ] **Step 2: Run it, verify it fails**

Run: `cd functions && npx jest pushSummary`
Expected: FAIL — `Cannot find module '../../notifications/pushSummary'`.

- [ ] **Step 3: Implement**

```typescript
// functions/src/notifications/pushSummary.ts
import { DigestModel } from './types';

export interface PushSummary {
  title: string;
  body: string;
}

/** Thousands-separated naira figure, e.g. 18000 -> "18,000". */
function formatNaira(amount: number): string {
  return Math.round(amount).toLocaleString('en-US');
}

/**
 * Pure one-line summary for the daily push. Caller guarantees the model is
 * non-empty (suppress-when-empty happens in the run loop). Lead item priority:
 * overdue -> due-soon -> outstanding (most urgent first); the remaining count
 * becomes a "+N more" tail.
 */
export function pushSummary(model: DigestModel): PushSummary {
  const total = model.overdue.length + model.dueSoon.length + model.outstanding.length;

  let lead: string;
  if (model.overdue.length > 0) {
    const o = model.overdue[0];
    lead = `${o.customerName}'s ${o.garmentSummary} is overdue`;
  } else if (model.dueSoon.length > 0) {
    const d = model.dueSoon[0];
    lead = `${d.customerName}'s ${d.garmentSummary} is due soon`;
  } else {
    const s = model.outstanding[0];
    lead = `${s.customerName} owes ₦${formatNaira(s.amount ?? 0)}`;
  }

  const moreCount = total - 1;
  const body = moreCount > 0 ? `${lead} + ${moreCount} more need attention` : lead;
  return { title: 'StitchPad', body };
}
```

- [ ] **Step 4: Run tests, verify pass**

Run: `cd functions && npx jest pushSummary`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/pushSummary.ts functions/src/__tests__/notifications/pushSummary.test.ts
git commit -m "feat(notifications): pure pushSummary builder for daily push"
```

---

## Task 2: Extend `DigestIO` + push block in `runDailyDigest` (functions)

**Files:**
- Modify: `functions/src/notifications/types.ts`
- Modify: `functions/src/notifications/runDailyDigest.ts`
- Test: `functions/src/__tests__/notifications/runDailyDigest.test.ts`

- [ ] **Step 1: Extend the types**

In `functions/src/notifications/types.ts`, add `pushEnabled` to `DigestRecipient` and the push methods to `DigestIO`, and import the `PushSummary` type:

```typescript
import { PushSummary } from './pushSummary';

export interface DigestRecipient {
  uid: string;
  email: string;
  name: string;
  digestEnabled: boolean;
  pushEnabled: boolean; // false only when explicitly opted out of push
}

export interface DigestIO {
  listRecipients(): Promise<DigestRecipient[]>;
  loadOrders(uid: string): Promise<OrderScanDoc[]>;
  getLastSentDate(uid: string): Promise<string | null>;
  setLastSentDate(uid: string, dateKey: string): Promise<void>;
  writeNotifications(uid: string, model: DigestModel): Promise<void>;
  sendEmail(p: { to: string; subject: string; html: string; text: string }): Promise<void>;
  isAllowed(uid: string, email: string): boolean;
  // Push (Android slice 3):
  loadPushTokens(uid: string): Promise<string[]>;
  sendPush(tokens: string[], payload: PushSummary): Promise<{ invalidTokens: string[] }>;
  deletePushTokens(uid: string, tokens: string[]): Promise<void>;
  getLastPushDate(uid: string): Promise<string | null>;
  setLastPushDate(uid: string, dateKey: string): Promise<void>;
}
```

- [ ] **Step 2: Write the failing tests**

Add to `functions/src/__tests__/notifications/runDailyDigest.test.ts`. First extend the `fakeIO` helper to capture push activity and accept push config, then add the push test cases. The updated helper:

```typescript
function fakeIO(over: Partial<DigestIO> & {
  recipients: DigestRecipient[];
  ordersByUid: Record<string, OrderScanDoc[]>;
  tokensByUid?: Record<string, string[]>;
  invalidTokens?: string[];
}): {
  io: DigestIO;
  sent: { to: string; subject: string }[];
  pushes: { tokens: string[]; body: string }[];
  pushStamps: Record<string, string>;
  deletedTokens: { uid: string; tokens: string[] }[];
} {
  const sent: { to: string; subject: string }[] = [];
  const stamps: Record<string, string> = {};
  const pushStamps: Record<string, string> = {};
  const pushes: { tokens: string[]; body: string }[] = [];
  const deletedTokens: { uid: string; tokens: string[] }[] = [];
  const io: DigestIO = {
    listRecipients: async () => over.recipients,
    loadOrders: async (uid) => over.ordersByUid[uid] || [],
    getLastSentDate: async (uid) => stamps[uid] ?? null,
    setLastSentDate: async (uid, d) => { stamps[uid] = d; },
    writeNotifications: async () => {},
    sendEmail: async (p) => { sent.push({ to: p.to, subject: p.subject }); },
    isAllowed: over.isAllowed ?? (() => true),
    loadPushTokens: async (uid) => over.tokensByUid?.[uid] ?? [],
    sendPush: async (tokens, payload) => {
      pushes.push({ tokens, body: payload.body });
      return { invalidTokens: over.invalidTokens ?? [] };
    },
    deletePushTokens: async (uid, tokens) => { deletedTokens.push({ uid, tokens }); },
    getLastPushDate: async (uid) => pushStamps[uid] ?? null,
    setLastPushDate: async (uid, d) => { pushStamps[uid] = d; },
  };
  return { io, sent, pushes, pushStamps, deletedTokens };
}
```

Update any existing `recipients:` fixtures in this file to include `pushEnabled: true` (the new required field). Then add:

```typescript
describe('runDailyDigest — push', () => {
  const overdueOrder: OrderScanDoc = {
    id: 'o1', customerName: 'Folake', status: 'IN_PROGRESS',
    deadline: 0, archivedAt: null, totalPrice: 1000, payments: [],
    items: [{ garmentType: 'Asoebi' }],
  };
  const recipient = (over: Partial<DigestRecipient> = {}): DigestRecipient =>
    ({ uid: 'u1', email: 'a@b.com', name: 'Shop', digestEnabled: true, pushEnabled: true, ...over });

  it('sends one push for an enabled, allowed recipient with actionable orders + a token', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1'] } });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(1);
    expect(f.pushes[0].tokens).toEqual(['tok1']);
    expect(f.pushStamps.u1).toBeTruthy();
  });

  it('skips push when pushEnabled is false (but still emails)', async () => {
    const f = fakeIO({ recipients: [recipient({ pushEnabled: false })], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1'] } });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
    expect(f.sent).toHaveLength(1);
  });

  it('skips push when the recipient has no tokens', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: [] } });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
    expect(f.pushStamps.u1).toBeUndefined();
  });

  it('skips push when not allowed by the rollout allowlist', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1'] }, isAllowed: () => false });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
  });

  it('skips push when the model is empty (suppress-when-empty)', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [] }, tokensByUid: { u1: ['tok1'] } });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
  });

  it('skips push when already pushed today', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1'] } });
    const today = lagosDateKey(1_000_000_000_000); // import from ../../notifications/lagosTime
    f.pushStamps.u1 = today;
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.pushes).toHaveLength(0);
  });

  it('prunes invalid tokens reported by sendPush', async () => {
    const f = fakeIO({ recipients: [recipient()], ordersByUid: { u1: [overdueOrder] }, tokensByUid: { u1: ['tok1', 'bad'] }, invalidTokens: ['bad'] });
    await runDailyDigest(f.io, 1_000_000_000_000);
    expect(f.deletedTokens).toEqual([{ uid: 'u1', tokens: ['bad'] }]);
  });
});
```

Add `import { lagosDateKey } from '../../notifications/lagosTime';` to the test file if not present.

- [ ] **Step 3: Run, verify failure**

Run: `cd functions && npx jest runDailyDigest`
Expected: FAIL — push not sent / type errors on the new `DigestIO` members.

- [ ] **Step 4: Implement the loop block**

In `functions/src/notifications/runDailyDigest.ts`, add the import and insert the push block immediately after `await io.writeNotifications(...)` and before `if (!r.digestEnabled)`:

```typescript
import { pushSummary } from './pushSummary';
// ...inside the for-loop, after writeNotifications:
      // PUSH (Android slice 3) — gated independently of email. Suppress-when-empty,
      // rollout allowlist, opt-out flag, and a once-per-day stamp guarding scan retries.
      if (
        r.pushEnabled &&
        io.isAllowed(r.uid, r.email) &&
        !isDigestEmpty(model) &&
        (await io.getLastPushDate(r.uid)) !== todayKey
      ) {
        const tokens = await io.loadPushTokens(r.uid);
        if (tokens.length > 0) {
          const { invalidTokens } = await io.sendPush(tokens, pushSummary(model));
          if (invalidTokens.length > 0) {
            await io.deletePushTokens(r.uid, invalidTokens);
          }
          await io.setLastPushDate(r.uid, todayKey);
        }
      }
```

(Leave the email gates below unchanged. Note push runs inside the same `try`, so a push failure is caught by the existing per-recipient `catch` and increments `failed` — acceptable; the email still won't have sent for that recipient on a thrown push, but push errors here are Admin-SDK multicast which rarely throws wholesale.)

- [ ] **Step 5: Run, verify pass**

Run: `cd functions && npx jest runDailyDigest`
Expected: PASS (existing + 7 new).

- [ ] **Step 6: Commit**

```bash
git add functions/src/notifications/types.ts functions/src/notifications/runDailyDigest.ts functions/src/__tests__/notifications/runDailyDigest.test.ts
git commit -m "feat(notifications): gated daily push step in the scan loop"
```

---

## Task 3: Production push IO + debug callable + token cleanup (functions)

**Files:**
- Modify: `functions/src/notifications/dailyDigest.ts`
- Modify: `functions/src/cleanup/firestore.ts`
- Test: `functions/src/__tests__/cleanup/firestore.test.ts` (if an allowlist test exists)

These are thin Admin-SDK wrappers (integration glue), verified by `tsc` + the existing emulator/unit suites rather than new unit tests.

- [ ] **Step 1: Implement the production IO methods**

In `functions/src/notifications/dailyDigest.ts`:

(a) In `listRecipients`, read the push flag alongside the email flag and add it to the returned `DigestRecipient`:

```typescript
// where the recipient object is built from the user doc:
pushEnabled: data.dailyPushEnabled !== false, // default ON when absent (mirror digestEnabled)
```

(b) Add the push methods to `productionDigestIO`. Add `import { lagosDateKey } from './lagosTime';` if not already imported, and use the existing `db`/`admin` references:

```typescript
loadPushTokens: async (uid: string): Promise<string[]> => {
  const snap = await db.collection('users').doc(uid).collection('notificationTokens').get();
  return snap.docs.map((d) => d.id);
},

sendPush: async (tokens, payload) => {
  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: { title: payload.title, body: payload.body },
    android: { notification: { channelId: 'daily_reminders' } },
    data: { target: 'inbox' },
  });
  const invalidTokens: string[] = [];
  res.responses.forEach((r, i) => {
    // GrpcStatus/named admin constants are type-only at runtime — compare the
    // string error code (see feedback_firebase_admin_grpcstatus_typeonly).
    const code = r.error?.code;
    if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-registration-token') {
      invalidTokens.push(tokens[i]);
    }
  });
  return { invalidTokens };
},

deletePushTokens: async (uid: string, tokens: string[]): Promise<void> => {
  const col = db.collection('users').doc(uid).collection('notificationTokens');
  const batch = db.batch();
  for (const t of tokens) batch.delete(col.doc(t));
  await batch.commit();
},

getLastPushDate: async (uid: string): Promise<string | null> => {
  const snap = await digestStateRef(uid).get();
  return (snap.data()?.lastPushDate as string | undefined) ?? null;
},

setLastPushDate: async (uid: string, dateKey: string): Promise<void> => {
  await digestStateRef(uid).set({ lastPushDate: dateKey }, { merge: true });
},
```

- [ ] **Step 2: Extend `debugSendMyDigest` to also push**

In the `debugSendMyDigest` callable, after it writes notifications/sends the email for the caller, also send a push to the caller's tokens (bypassing the allowlist + stamp, mirroring its existing email behaviour):

```typescript
const tokens = await productionDigestIO.loadPushTokens(uid);
if (tokens.length > 0 && !isDigestEmpty(model)) {
  const { invalidTokens } = await productionDigestIO.sendPush(tokens, pushSummary(model));
  if (invalidTokens.length > 0) await productionDigestIO.deletePushTokens(uid, invalidTokens);
}
```

Add `import { pushSummary } from './pushSummary';` and ensure `isDigestEmpty` is imported.

- [ ] **Step 3: Add `notificationTokens` to the cleanup allowlist**

In `functions/src/cleanup/firestore.ts`, add `'notificationTokens'` to `ALLOWED_SUBCOLLECTIONS` (alphabetical, after `'goals'`):

```typescript
export const ALLOWED_SUBCOLLECTIONS = [
  'customers',
  'goals',
  'notifications',
  'notificationTokens',
  'orders',
  'private',
] as const;
```

If `functions/src/__tests__/cleanup/firestore.test.ts` asserts the list, update its expectation.

- [ ] **Step 4: Typecheck + full functions test suite**

Run: `cd functions && npm run build && npx jest`
Expected: `tsc` clean; all tests pass.

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/dailyDigest.ts functions/src/cleanup/firestore.ts functions/src/__tests__/
git commit -m "feat(notifications): production push IO + debug push + token cleanup"
```

---

## Task 4: `dailyPushEnabled` through the User layer (client)

**Files:**
- Modify: `core/domain/model/User.kt`, `core/data/dto/UserDto.kt`, `core/data/mapper/UserMapper.kt`
- Modify: `core/domain/repository/UserRepository.kt`, `core/data/repository/FirebaseUserRepository.kt`
- Test: `composeApp/src/commonTest/.../core/data/mapper/UserMapperTest.kt` (add a case if the file exists; otherwise add to the nearest user-mapper test)

- [ ] **Step 1: Write the failing mapper test**

In the user-mapper test (mirror existing cases), add:

```kotlin
@Test
fun dailyPushEnabled_defaultsTrue_andRoundTrips() {
    assertTrue(UserDto(id = "u1").toUser().dailyPushEnabled)          // absent -> true
    assertFalse(UserDto(id = "u1", dailyPushEnabled = false).toUser().dailyPushEnabled)
    assertFalse(User(id = "u1", email = "", displayName = "", businessName = null, phoneNumber = null, whatsappNumber = null, avatarColorIndex = 0, dailyPushEnabled = false).toUserDto().dailyPushEnabled)
}
```

- [ ] **Step 2: Run, verify failure**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*UserMapper*'`
Expected: FAIL — `dailyPushEnabled` unresolved.

- [ ] **Step 3: Add the field across the three files**

`User.kt` — add after `dailyDigestEmailEnabled`:
```kotlin
    /** Whether the tailor receives the daily push reminder. Opt-out: true by default. */
    val dailyPushEnabled: Boolean = true,
```
`UserDto.kt` — add after the email flag:
```kotlin
    @SerialName("dailyPushEnabled")
    val dailyPushEnabled: Boolean = true,
```
`UserMapper.kt` — add `dailyPushEnabled = dailyPushEnabled,` to BOTH `toUser()` and `toUserDto()`.

- [ ] **Step 4: Add the repository update method**

`UserRepository.kt` (interface) — add next to `setDailyDigestEmailEnabled`:
```kotlin
    suspend fun setDailyPushEnabled(userId: String, enabled: Boolean): EmptyResult<DataError.Network>
```
`FirebaseUserRepository.kt` — mirror `setDailyDigestEmailEnabled` exactly:
```kotlin
    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun setDailyPushEnabled(userId: String, enabled: Boolean): EmptyResult<DataError.Network> {
        val data = mapOf<String, Any>(
            "dailyPushEnabled" to enabled,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        val accepted = offlineWrites.enqueue("setDailyPushEnabled userId=$userId") {
            firestore.collection(USERS).document(userId).set(data, merge = true)
        }
        return if (accepted) Result.Success(Unit) else Result.Error(DataError.Network.UNKNOWN)
    }
```

- [ ] **Step 5: Run, verify pass + detekt**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*UserMapper*' && ./gradlew detekt`
Expected: PASS, no violations.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/ composeApp/src/commonTest/
git commit -m "feat(notifications): dailyPushEnabled flag through the user layer"
```

---

## Task 5: Settings push toggle (client MVI)

**Files:**
- Modify: `feature/settings/presentation/home/SettingsState.kt`, `SettingsAction.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

No new unit test (mirrors the proven digest-toggle wiring); verified by compile + detekt + the smoke test.

- [ ] **Step 1: State + Action**

`SettingsState.kt` — add after `dailyDigestEmailEnabled`:
```kotlin
    val dailyPushEnabled: Boolean = true,
```
`SettingsAction.kt` — add:
```kotlin
    data class OnDailyPushToggle(val enabled: Boolean) : SettingsAction
```

- [ ] **Step 2: ViewModel — fold the flag into state + dispatch**

In `SettingsViewModel.kt`, wherever the user-doc snapshot maps into `SettingsState` (next to `dailyDigestEmailEnabled = user.dailyDigestEmailEnabled`), add `dailyPushEnabled = user.dailyPushEnabled`. Add the action branch next to `OnDailyDigestToggle`:
```kotlin
            is SettingsAction.OnDailyPushToggle -> setDailyPush(action.enabled)
```
And the handler (mirror `setDailyDigest`):
```kotlin
    private fun setDailyPush(enabled: Boolean) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUser()?.id ?: return@launch
            userRepository.setDailyPushEnabled(userId, enabled)
        }
    }
```

- [ ] **Step 3: String + row**

`strings.xml` — add:
```xml
    <string name="settings_row_daily_push">Daily push reminder</string>
```
`SettingsScreen.kt` — add a `SettingsRow` directly below the daily-digest row:
```kotlin
SettingsRow(
    icon = Icons.Outlined.NotificationsActive,
    label = stringResource(Res.string.settings_row_daily_push),
    onClick = { onAction(SettingsAction.OnDailyPushToggle(!state.dailyPushEnabled)) },
    trailing = {
        Switch(
            checked = state.dailyPushEnabled,
            onCheckedChange = { onAction(SettingsAction.OnDailyPushToggle(it)) },
        )
    },
)
```
Add the import `androidx.compose.material.icons.outlined.NotificationsActive`.

- [ ] **Step 4: Compile + detekt**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid -q && ./gradlew detekt`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/settings/ composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(notifications): settings toggle for the daily push reminder"
```

---

## Task 6: Push-token seam + repository (client KMP)

**Files:**
- Create: `feature/notification/push/PushTokenProvider.kt` (commonMain `expect`)
- Create: `feature/notification/push/PushTokenProvider.android.kt` (androidMain `actual`)
- Create: `feature/notification/push/PushTokenProvider.ios.kt` (iosMain `actual` stub)
- Create: `feature/notification/push/PushTokenRepository.kt` + `FirebasePushTokenRepository.kt` (commonMain)
- Test: `composeApp/src/commonTest/.../feature/notification/FirebasePushTokenRepositoryTest.kt`

- [ ] **Step 1: Declare the `expect` provider**

```kotlin
// commonMain .../feature/notification/push/PushTokenProvider.kt
package com.danzucker.stitchpad.feature.notification.push

/** Platform seam to fetch the current push token. GitLive has no messaging wrapper. */
expect class PushTokenProvider {
    /** The current FCM/APNs registration token, or null if unavailable/not granted. */
    suspend fun currentToken(): String?
}
```

- [ ] **Step 2: Android `actual` (native FCM) + iOS stub**

```kotlin
// androidMain .../feature/notification/push/PushTokenProvider.android.kt
package com.danzucker.stitchpad.feature.notification.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

actual class PushTokenProvider {
    actual suspend fun currentToken(): String? =
        runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
}
```

```kotlin
// iosMain .../feature/notification/push/PushTokenProvider.ios.kt
package com.danzucker.stitchpad.feature.notification.push

// iOS APNs is a fast-follow slice — this stub keeps the KMP build green.
actual class PushTokenProvider {
    actual suspend fun currentToken(): String? = null
}
```

(Add `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` to `androidMain` deps if `kotlinx.coroutines.tasks.await` is not already available — check `libs.versions.toml`; many Firebase-on-Android setups already pull it transitively. If missing, add a `coroutines-play-services` alias and `implementation(...)` in the androidMain block.)

- [ ] **Step 3: Write the failing repository test**

```kotlin
// commonTest .../feature/notification/FirebasePushTokenRepositoryTest.kt
class FirebasePushTokenRepositoryTest {
    @Test
    fun registerToken_writesExpectedShape() = runTest {
        val fake = FakeFirestoreWriter()                 // mirror the project's existing fake-Firestore test util
        val repo = FirebasePushTokenRepository(fake, clock = { 1_000L })
        repo.registerToken("u1", "tok1", platform = "android")
        assertEquals("users/u1/notificationTokens/tok1", fake.lastSetPath)
        assertEquals("android", fake.lastSetData["platform"])
    }
}
```
(If the codebase has no Firestore-write fake, follow the pattern used by the existing repository tests — or, if those are emulator-backed, make this an emulator test instead. Match `FirebaseNotificationRepositoryTest`'s style exactly.)

- [ ] **Step 4: Implement the repository**

```kotlin
// commonMain .../feature/notification/push/PushTokenRepository.kt
package com.danzucker.stitchpad.feature.notification.push

interface PushTokenRepository {
    suspend fun registerToken(userId: String, token: String, platform: String = "android")
    suspend fun unregisterToken(userId: String, token: String)
}
```

```kotlin
// commonMain .../feature/notification/push/FirebasePushTokenRepository.kt
package com.danzucker.stitchpad.feature.notification.push

import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FieldValue

class FirebasePushTokenRepository(
    private val firestore: FirebaseFirestore,
    private val nowMillis: () -> Long,
) : PushTokenRepository {

    private fun tokens(userId: String) =
        firestore.collection("users").document(userId).collection("notificationTokens")

    override suspend fun registerToken(userId: String, token: String, platform: String) {
        // Fire-and-forget shape; GitLive set() awaits the server ACK, so callers must
        // not block UI on this (see feedback_gitlive_firestore_set_awaits_server_ack).
        val data = mapOf(
            "platform" to platform,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        tokens(userId).document(token).set(data, merge = true)
    }

    override suspend fun unregisterToken(userId: String, token: String) {
        tokens(userId).document(token).delete()
    }
}
```
(Match the exact GitLive Firestore API the codebase uses — copy the import + `set(..., merge = true)` idiom from `FirebaseUserRepository`/`FirebaseNotificationRepository`. Adjust `nowMillis`/serverTimestamp to whatever pattern the repo already uses; drop `nowMillis` if `FieldValue.serverTimestamp` is preferred.)

- [ ] **Step 5: Add the Firestore rule for the token subcollection**

Without an explicit rule the client token write is DENIED (the `notifications` block is server-create-only; there is no permissive catch-all). In `firestore.rules`, next to the existing `match /notifications/{notificationId}` block (inside the `users/{uid}` document scope), add:

```
match /notificationTokens/{token} {
  // Owner-managed: the device registers/refreshes/removes its own token.
  allow read, create, update, delete: if isOwner(uid);
}
```

Verify `isOwner(uid)` is the helper used by the neighbouring blocks (it is, per the `notifications` rule). This requires `firebase deploy --only firestore:rules` at deploy time (noted in final verification).

- [ ] **Step 6: Run tests + iOS compile**

Run: `./gradlew :composeApp:testDebugUnitTest --tests '*PushTokenRepository*' && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q`
Expected: PASS + iOS BUILD SUCCESSFUL (the iosMain stub links).

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/push/ composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/notification/push/ composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/feature/notification/push/ composeApp/src/commonTest/ firestore.rules
git commit -m "feat(notifications): KMP push-token provider seam + Firestore token repo + rules"
```

---

## Task 7: Token registration lifecycle + DI (client)

**Files:**
- Create: `feature/notification/push/PushTokenRegistrar.kt` (commonMain)
- Modify: `di/NotificationModule.kt`
- Modify: wherever post-login session setup runs + logout runs (the `AuthViewModel` / session bootstrap — locate the existing "after login" hook)

- [ ] **Step 1: Implement the registrar**

```kotlin
// commonMain .../feature/notification/push/PushTokenRegistrar.kt
package com.danzucker.stitchpad.feature.notification.push

import com.danzucker.stitchpad.core.logging.AppLogger

/** Fetches the device token and registers/unregisters it for the signed-in user. */
class PushTokenRegistrar(
    private val provider: PushTokenProvider,
    private val repository: PushTokenRepository,
) {
    suspend fun registerForUser(userId: String) {
        val token = provider.currentToken() ?: return
        runCatching { repository.registerToken(userId, token) }
            .onFailure { AppLogger.w("push token register failed: ${it.message}") }
    }

    suspend fun unregisterForUser(userId: String) {
        val token = provider.currentToken() ?: return
        runCatching { repository.unregisterToken(userId, token) }
            .onFailure { AppLogger.w("push token unregister failed: ${it.message}") }
    }
}
```
(Match `AppLogger`'s actual API — adjust `w(...)` to the real signature.)

- [ ] **Step 2: Register in Koin**

In `di/NotificationModule.kt` add to `notificationDataModule`:
```kotlin
    single { PushTokenProvider() }
    single<PushTokenRepository> { FirebasePushTokenRepository(get(), nowMillis = { Clock.System.now().toEpochMilliseconds() }) }
    single { PushTokenRegistrar(get(), get()) }
```
(Drop `nowMillis` if you removed it in Task 6. `PushTokenProvider()` no-arg works because the Android `actual` has a no-arg constructor.)

- [ ] **Step 3: Call it on login + logout**

Find the existing post-authentication hook (where the session/user is established after a successful login and on session restore) and call:
```kotlin
    pushTokenRegistrar.registerForUser(userId)   // in a viewModelScope.launch, fire-and-forget
```
On explicit logout (before clearing the session), call `pushTokenRegistrar.unregisterForUser(userId)`. Inject `PushTokenRegistrar` via Koin into that ViewModel. (If unclear where, register on first successful dashboard load instead — but prefer the auth-success hook.)

- [ ] **Step 4: Compile + detekt + iOS**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid -q && ./gradlew detekt && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q`
Expected: all BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/push/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/ composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/auth/
git commit -m "feat(notifications): register/unregister push tokens on login/logout"
```

---

## Task 8: Android FCM dependency, manifest, notification channel

**Files:**
- Modify: `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/notification/push/NotificationChannels.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/StitchPadApplication.kt`

Native wiring — verified by Android build + the device smoke test (no unit test).

- [ ] **Step 1: Add the dependency**

`libs.versions.toml` `[libraries]` (next to `firebase-crashlytics`):
```toml
firebase-messaging = { module = "com.google.firebase:firebase-messaging" }
```
`composeApp/build.gradle.kts` androidMain deps (next to crashlytics):
```kotlin
        implementation(libs.firebase.messaging)
```

- [ ] **Step 2: Manifest — permission + service**

In `composeApp/src/androidMain/AndroidManifest.xml`, add the permission (top-level) and the service (inside `<application>`):
```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
```xml
        <service
            android:name=".feature.notification.push.StitchPadMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
```

- [ ] **Step 3: Channel helper**

```kotlin
// androidMain .../feature/notification/push/NotificationChannels.kt
package com.danzucker.stitchpad.feature.notification.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val DAILY_REMINDERS_CHANNEL_ID = "daily_reminders"

fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(DAILY_REMINDERS_CHANNEL_ID) != null) return
    manager.createNotificationChannel(
        NotificationChannel(
            DAILY_REMINDERS_CHANNEL_ID,
            "Daily reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Your morning summary of deadlines and money owed" },
    )
}
```

- [ ] **Step 4: Create the channel on startup**

In `StitchPadApplication.onCreate()`, after `initKoin { ... }`:
```kotlin
        ensureNotificationChannels(this)
```
Add the import.

- [ ] **Step 5: Build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/androidMain/AndroidManifest.xml composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/notification/push/NotificationChannels.kt composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/StitchPadApplication.kt
git commit -m "feat(notifications): android FCM dependency, manifest, notification channel"
```

---

## Task 9: `StitchPadMessagingService` (androidMain)

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/notification/push/StitchPadMessagingService.kt`

- [ ] **Step 1: Implement the service**

```kotlin
package com.danzucker.stitchpad.feature.notification.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.danzucker.stitchpad.MainActivity
import com.danzucker.stitchpad.R
import com.danzucker.stitchpad.core.domain.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val PUSH_TARGET_EXTRA = "target"
const val PUSH_TARGET_INBOX = "inbox"
private const val DAILY_REMINDER_NOTIFICATION_ID = 2001

class StitchPadMessagingService : FirebaseMessagingService(), KoinComponent {
    private val authRepository: AuthRepository by inject()
    private val registrar: PushTokenRegistrar by inject()

    override fun onNewToken(token: String) {
        val userId = authRepository.currentUserIdOrNull() ?: return   // no-op when logged out
        CoroutineScope(Dispatchers.IO).launch { registrar.register(userId, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Backgrounded/killed: FCM auto-displays the notification payload. This path
        // covers the FOREGROUND case only — post it ourselves so the tailor still sees it.
        val n = message.notification ?: return
        if (NotificationManagerCompat.from(this).areNotificationsEnabled().not()) return
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(PUSH_TARGET_EXTRA, message.data[PUSH_TARGET_EXTRA] ?: PUSH_TARGET_INBOX)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, DAILY_REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)   // see Step 2
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(this).notify(DAILY_REMINDER_NOTIFICATION_ID, notification)
    }
}
```
Add a small helper on `PushTokenRegistrar` (or call the repo directly) — `repositoryRegister` here means "register this token for this user"; simplest is to expose `suspend fun register(userId, token)` on the registrar that calls `repository.registerToken(userId, token)` without re-fetching the token (we already have it from `onNewToken`). Add to `PushTokenRegistrar`:
```kotlin
    suspend fun register(userId: String, token: String) =
        runCatching { repository.registerToken(userId, token) }.getOrNull()
```
(`repository` must be a `val` on the registrar — it already is.) Also confirm `AuthRepository` has a synchronous `currentUserIdOrNull()`; if not, add a thin one that reads the cached current user id, or resolve the uid via the existing GitLive auth `currentUser?.uid`.

- [ ] **Step 2: Provide a monochrome status-bar icon**

Add a white-on-transparent `ic_stat_notification` drawable (Android requires a monochrome small icon). Create `composeApp/src/androidMain/res/drawable/ic_stat_notification.xml` as a simple vector (reuse the app's notebook/bell mark silhouette in a single flat color `#FFFFFFFF`). If a suitable existing drawable exists, reference it instead.

- [ ] **Step 3: Build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/feature/notification/push/StitchPadMessagingService.kt composeApp/src/androidMain/res/drawable/ic_stat_notification.xml composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/push/PushTokenRegistrar.kt
git commit -m "feat(notifications): FCM messaging service (token refresh + foreground display)"
```

---

## Task 10: Permission pre-prompt on the dashboard (androidMain + common)

**Files:**
- Create: `composeApp/src/commonMain/.../feature/notification/push/PushPermissionController.kt` (`expect`)
- Create: `.android.kt` actual + `.ios.kt` stub
- Modify: the dashboard Root + a small pre-prompt bottom sheet + a persisted "asked" flag
- Modify: `strings.xml`

- [ ] **Step 1: `expect` controller**

```kotlin
// commonMain
package com.danzucker.stitchpad.feature.notification.push

expect class PushPermissionController {
    /** True if we can/should show our value pre-prompt (permission undetermined, API 33+). */
    fun shouldRequest(): Boolean
    /** Launch the system POST_NOTIFICATIONS dialog (no-op below API 33 / on iOS stub). */
    fun requestSystemPermission()
}
```
iOS `actual` stub: `shouldRequest()=false`, `requestSystemPermission(){}`.
Android `actual`: `shouldRequest()` returns true only when `Build.VERSION.SDK_INT >= 33` and `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS) != GRANTED`; `requestSystemPermission()` launches the request via the current Activity (use the existing `CurrentActivityHolder` + an `ActivityResultLauncher`, or `ActivityCompat.requestPermissions`).

- [ ] **Step 2: "Already asked" persisted flag**

Reuse the app's existing preferences store (the one behind `onboardingPreferences`/settings prefs). Add `hasAskedPushPermission(): Boolean` + `setAskedPushPermission()`. (Mirror `hasSeenOnboarding`.)

- [ ] **Step 3: Pre-prompt bottom sheet + dashboard hook**

In the dashboard Root: on first composition, if `permissionController.shouldRequest() && !prefs.hasAskedPushPermission()`, show a `ModalBottomSheet` (follows `feedback_notification_patterns`): title "Never miss a deadline", body "Get a quick morning reminder of orders due and money owed", a primary "Turn on reminders" button → `permissionController.requestSystemPermission()` + `prefs.setAskedPushPermission()`, and a "Not now" dismiss → `prefs.setAskedPushPermission()`. Use string resources:
```xml
    <string name="push_prompt_title">Never miss a deadline</string>
    <string name="push_prompt_body">Get a quick morning reminder of orders due and money owed.</string>
    <string name="push_prompt_confirm">Turn on reminders</string>
    <string name="push_prompt_dismiss">Not now</string>
```
Keep all Android permission APIs out of commonMain — the dashboard calls the `expect` controller + prefs only.

- [ ] **Step 4: Build (Android + iOS) + detekt**

Run: `./gradlew :composeApp:assembleDebug && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q && ./gradlew detekt`
Expected: all green.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/ composeApp/src/androidMain/ composeApp/src/iosMain/
git commit -m "feat(notifications): contextual dashboard pre-prompt for push permission"
```

---

## Task 11: Cold-start tap routing → inbox

**Files:**
- Create: `composeApp/src/commonMain/.../navigation/PendingDeepLink.kt`
- Modify: `composeApp/src/androidMain/.../MainActivity.kt`
- Modify: `composeApp/src/commonMain/.../navigation/NavGraph.kt`
- Modify: `di` module to provide the holder

- [ ] **Step 1: Pending deep-link holder**

```kotlin
// commonMain .../navigation/PendingDeepLink.kt
package com.danzucker.stitchpad.navigation

import kotlinx.coroutines.flow.MutableStateFlow

enum class DeepLinkTarget { INBOX }

/** Single-shot holder for an external tap target, consumed once by the NavHost. */
class PendingDeepLinkHolder {
    val target = MutableStateFlow<DeepLinkTarget?>(null)
    fun set(t: DeepLinkTarget) { target.value = t }
    fun consume(): DeepLinkTarget? = target.value?.also { target.value = null }
}
```
Register as a Koin `single { PendingDeepLinkHolder() }` (a module already loaded app-wide, e.g. the navigation/app module).

- [ ] **Step 2: MainActivity reads the intent**

In `MainActivity`, inject the holder and read the extra in `onCreate` and `onNewIntent`:
```kotlin
    private val pendingDeepLink: PendingDeepLinkHolder by inject()
    // in onCreate, after super:
        handlePushIntent(intent)
    // add:
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushIntent(intent)
    }
    private fun handlePushIntent(intent: Intent?) {
        if (intent?.getStringExtra(PUSH_TARGET_EXTRA) == PUSH_TARGET_INBOX) {
            pendingDeepLink.set(DeepLinkTarget.INBOX)
        }
    }
```
Imports: `android.content.Intent`, the holder, `PUSH_TARGET_EXTRA`/`PUSH_TARGET_INBOX`.

- [ ] **Step 3: NavGraph consumes it after auth resolves**

In `NavGraph.kt`, inject the holder, and in a `LaunchedEffect` that runs once the user is authenticated and on `HomeRoute` (i.e. after the Splash decision lands on Home), consume + navigate:
```kotlin
    val pendingDeepLink: PendingDeepLinkHolder = koinInject()
    LaunchedEffect(isOnHome) {
        if (isOnHome && pendingDeepLink.consume() == DeepLinkTarget.INBOX) {
            navController.navigate(NotificationsInboxRoute)
        }
    }
```
Wire `isOnHome` to the nav back-stack/current-destination state (or trigger the effect from the `HomeRoute` composable's `LaunchedEffect(Unit)`). If unauthenticated when the intent arrives, the holder retains the target until the user reaches Home, then it fires (per the spec).

- [ ] **Step 4: Build (Android + iOS) + detekt**

Run: `./gradlew :composeApp:assembleDebug && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q && ./gradlew detekt`
Expected: green.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/ composeApp/src/androidMain/kotlin/com/danzucker/stitchpad/MainActivity.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/
git commit -m "feat(notifications): route push tap to the in-app inbox (cold + warm start)"
```

---

## Task 12: Debug-menu "Send test push to me"

**Files:**
- Modify: the Debug → Notifications menu (debug source set) where "Send daily digest now" lives

- [ ] **Step 1: Add the action**

The existing "Send daily digest now" calls the `debugSendMyDigest` callable, which (after Task 3) also pushes. So either: (a) rename/keep that single button (it now does docs + email + push) and add a note, or (b) add a distinct "Send test push to me" entry that calls the same callable. Prefer (b) for clarity — add a button that invokes `debugSendMyDigest` and shows a snackbar "Test push sent (if you have an actionable order + granted permission)".

- [ ] **Step 2: Build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/debug* composeApp/src/androidDebug* composeApp/src/commonMain/
git commit -m "chore(notifications): debug-menu entry to send a test push"
```

---

## Final verification (before PR)

- [ ] Functions: `cd functions && npm run build && npx jest` — all green.
- [ ] Client: `./gradlew :composeApp:testDebugUnitTest` — all green.
- [ ] `./gradlew detekt` — clean.
- [ ] `./gradlew :composeApp:assembleDebug` — Android builds.
- [ ] `./gradlew :composeApp:compileKotlinIosSimulatorArm64 -q` — iOS still compiles (stub).
- [ ] **Manual smoke (real Android device, debug build):** grant via the pre-prompt → seed an overdue/due-soon/to-collect order → Debug → "Send test push to me" → receive the push with the app in **foreground, background, and killed** states → tap → lands on the inbox. Toggle the Settings push reminder OFF + revoke the OS permission → confirm no push. Confirm the token doc exists at `users/{uid}/notificationTokens/{token}` and is removed on logout.
- [ ] Deploy note for the PR: this slice needs **`firebase deploy --only functions,firestore:rules`** — BOTH the new push functions AND the new `notificationTokens` rule (Task 6 Step 5; without the rule, client token writes are denied). Enable **Cloud Messaging** in the Firebase console if it isn't already.

---

## Notes / known follow-ups
- **iOS APNs** is the next slice: implement `PushTokenProvider.ios.kt` for real (Firebase Messaging via SPM), add the `aps-environment` entitlement + Apple Push key, a `UNUserNotificationCenter` delegate, and iOS tap-routing.
- The `data.target` routing is a fixed `inbox` today; per-order deep-linking is deferred.
- Rollout: push rides the `STAGING` allowlist; flip with email at launch.
