# Referral Activity Server-Timestamp (Lane B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make referral active-day counting trust a server-stamped `serverCreatedAt` on customer/order/measurement creates, so forged client `createdAt` days no longer qualify a marketer payout — while leaving offline UX and edit flows untouched.

**Architecture:** Client writes `serverCreatedAt = FieldValue.serverTimestamp` beside the existing client `createdAt` on create. Firestore rules pin `serverCreatedAt` to `request.time` and reject future-dated `createdAt`. The nightly reconcile grader credits a `createdAt` day-key only when its `serverCreatedAt` lands within `ACTIVITY_FRESHNESS_DAYS` of the claimed day; docs with no `serverCreatedAt` (old binaries, all historical docs) fall through to the existing Lane A ratchet unchanged. Purely additive — no backfill, no migration.

**Tech Stack:** Kotlin Multiplatform (commonMain, GitLive firebase-kotlin-sdk), Firebase Cloud Functions (TypeScript, Jest), Firestore Security Rules (`@firebase/rules-unit-testing` emulator).

## Global Constraints

- `ACTIVITY_FRESHNESS_DAYS = 3` — the freshness window, in Lagos-days, verbatim.
- Lane A (`ratchetObservedDayKeys` / `observedDayKeys`) MUST remain deployed and unchanged. Lane B is additive; never gate qualification solely on `serverCreatedAt`.
- `createdAt` (client epoch-millis `Long`) is NEVER constrained to `request.time` — offline writes legitimately backdate it. Only future-dating is rejected, with a 5-minute clock-skew allowance.
- `serverCreatedAt` cannot be a `@Serializable` DTO field (`FieldValue.serverTimestamp` is a write sentinel). Write it via a `Map<String, Any>` merge, exactly like `FirebaseUserRepository.updateProfile` writes `updatedAt` (`FirebaseUserRepository.kt:131,161`).
- Client `commonTest` changes must compile for iOS: gate with `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64` (Kotlin/Native backtick-name + serializer landmines).
- Functions CI runs `npm run lint` before tests — run it for any `functions/` change ([[feedback_functions_ci_eslint]]).
- No direct pushes to main; this work is on branch `feat/referral-server-timestamp` → PR ([[feedback_pr_workflow]]). Review rotation before merge: Cursor Bugbot + `codex review -c model=gpt-5.5` ([[feedback_review_rotation]]).

---

## File Structure

| File | Responsibility |
|---|---|
| `functions/src/referral/referralConstants.ts` | add `ACTIVITY_FRESHNESS_DAYS = 3` |
| `functions/src/referral/reconcileReferrals.ts` | new pure `isServerFresh()`; `gatherSignals` filters each doc's `createdAt` through it |
| `functions/src/__tests__/referral/reconcileReferrals.test.ts` | `isServerFresh` unit cases + handler freshness/legacy/attack cases |
| `firestore.rules` | split customers/orders/measurements `write` into `create`/`update` with `serverCreatedAt`/`createdAt` constraints |
| `functions/src/__tests__/firestore.rules.test.ts` | new emulator suite for the three activity collections |
| `composeApp/.../feature/customer/data/FirebaseCustomerRepository.kt` | write `serverCreatedAt` on `createCustomer` |
| `composeApp/.../feature/order/data/FirebaseOrderRepository.kt` | write `serverCreatedAt` on `createOrder` |
| `composeApp/.../feature/measurement/data/FirebaseMeasurementRepository.kt` | write `serverCreatedAt` on `createMeasurement` |

**Testing note (read before Task 4):** these three GitLive repositories have no unit tests and no fake-Firestore infra — GitLive `DocumentReference`/`FieldValue` types are not fakeable in `commonTest` (same reason the referral debug wrappers are untested — `[[feedback_debug_menu_per_feature]]`). Their correctness is enforced by the **rules** (Task 3: `serverCreatedAt == request.time`) and confirmed in **QA** (verify the field appears with a server value in the Firestore console). Do not invent a fake-Firestore test; the fraud logic that CAN be unit-tested lives in Tasks 1–2.

---

## Task 1: Pure `isServerFresh` + freshness constant

**Files:**
- Modify: `functions/src/referral/referralConstants.ts`
- Modify: `functions/src/referral/reconcileReferrals.ts` (add exported pure function beside `computeActiveDayKeys`, ~line 73)
- Test: `functions/src/__tests__/referral/reconcileReferrals.test.ts`

**Interfaces:**
- Consumes: `DAY_MS` (already exported from `referralConstants.ts`).
- Produces:
  - `export const ACTIVITY_FRESHNESS_DAYS = 3` (in `referralConstants.ts`)
  - `export function isServerFresh(createdAtMs: number, serverCreatedAtMs: number | undefined, freshnessDays: number): boolean` (in `reconcileReferrals.ts`) — returns `false` when `serverCreatedAtMs` is undefined/non-finite (caller decides legacy fallback), else `true` iff `serverCreatedAtMs - createdAtMs` is within `[-DAY_MS, freshnessDays * DAY_MS]`.

- [ ] **Step 1: Add the constant**

In `functions/src/referral/referralConstants.ts`, directly after the `HOLD_WINDOW_DAYS` export (near the `DAY_MS` block):

```typescript
// Lane B: a client `createdAt` day only counts toward qualification when the
// doc's server-stamped `serverCreatedAt` lands within this many Lagos-days of it.
// Larger = kinder to tailors who sync infrequently offline; smaller = stronger
// anti-backdating. Docs with no serverCreatedAt (old binaries) bypass this and
// fall through to the Lane A ratchet. See reconcileReferrals.isServerFresh.
export const ACTIVITY_FRESHNESS_DAYS = 3;
```

- [ ] **Step 2: Write the failing test**

In `functions/src/__tests__/referral/reconcileReferrals.test.ts`, add a `describe` block (place it after the existing `computeActiveDayKeys` describe). Import `isServerFresh` from `../../referral/reconcileReferrals` and `DAY_MS` from `../../referral/referralConstants` (extend the existing import lines):

```typescript
describe('isServerFresh', () => {
  const D0 = 1_700_000_000_000; // arbitrary fixed base ms
  it('credits a same-instant server stamp', () => {
    expect(isServerFresh(D0, D0, 3)).toBe(true);
  });
  it('credits a stamp exactly freshnessDays later (inclusive)', () => {
    expect(isServerFresh(D0, D0 + 3 * DAY_MS, 3)).toBe(true);
  });
  it('rejects a stamp one day past the window', () => {
    expect(isServerFresh(D0, D0 + 4 * DAY_MS, 3)).toBe(false);
  });
  it('tolerates a stamp up to a day before the claimed instant (clock skew)', () => {
    expect(isServerFresh(D0, D0 - DAY_MS, 3)).toBe(true);
    expect(isServerFresh(D0, D0 - DAY_MS - 1, 3)).toBe(false);
  });
  it('treats a missing server stamp as not-fresh (caller handles legacy)', () => {
    expect(isServerFresh(D0, undefined, 3)).toBe(false);
  });
  it('treats a non-finite server stamp as not-fresh', () => {
    expect(isServerFresh(D0, NaN, 3)).toBe(false);
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd functions && npx jest reconcileReferrals -t isServerFresh`
Expected: FAIL — `isServerFresh is not a function` / not exported.

- [ ] **Step 4: Write minimal implementation**

In `functions/src/referral/reconcileReferrals.ts`, add `ACTIVITY_FRESHNESS_DAYS` is NOT needed here (used in Task 2); add the pure function right after `computeActiveDayKeys` (after line 73):

```typescript
// ── Pure: server-freshness of a single activity doc's claimed day ─────────────

/**
 * A client `createdAt` day is trustworthy for payout only if the doc's
 * server-stamped `serverCreatedAt` corroborates it: the server saw the write
 * within `freshnessDays` of the claimed creation day. A burst of week-old
 * backdated `createdAt` values all carry a much-later `serverCreatedAt`, so they
 * fall outside the window and do not count. A small negative tolerance (one day)
 * absorbs the 5-minute future-date rule skew crossing a Lagos midnight.
 *
 * Returns false when `serverCreatedAtMs` is undefined/non-finite — the doc is
 * from a pre-Lane-B binary; the CALLER decides to fall back to the Lane A
 * ratchet for such docs (never treat "not fresh" as "excluded" at the call site
 * without first checking presence).
 */
export function isServerFresh(
  createdAtMs: number,
  serverCreatedAtMs: number | undefined,
  freshnessDays: number,
): boolean {
  if (typeof serverCreatedAtMs !== 'number' || !Number.isFinite(serverCreatedAtMs)) return false;
  const delta = serverCreatedAtMs - createdAtMs;
  return delta >= -DAY_MS && delta <= freshnessDays * DAY_MS;
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd functions && npx jest reconcileReferrals -t isServerFresh`
Expected: PASS (6 tests).

- [ ] **Step 6: Lint + commit**

Run: `cd functions && npm run lint` (expect 0 errors).

```bash
git add functions/src/referral/referralConstants.ts functions/src/referral/reconcileReferrals.ts functions/src/__tests__/referral/reconcileReferrals.test.ts
git commit -m "feat(referral): pure isServerFresh + ACTIVITY_FRESHNESS_DAYS (Lane B)"
```

---

## Task 2: Wire freshness into `gatherSignals`

**Files:**
- Modify: `functions/src/referral/reconcileReferrals.ts` (`gatherSignals`, lines 283–323)
- Test: `functions/src/__tests__/referral/reconcileReferrals.test.ts` (handler-level)

**Interfaces:**
- Consumes: `isServerFresh` (Task 1), `ACTIVITY_FRESHNESS_DAYS` (Task 1), existing `computeActiveDayKeys`, `ratchetObservedDayKeys`.
- Produces: no signature change to `gatherSignals` — it still returns `{ activated, activeDayKeys }`. The change is internal: a doc's `createdAt` enters the day-key computation only when the doc has no `serverCreatedAt` (legacy → Lane A) OR `isServerFresh(createdAt, serverCreatedAt, ACTIVITY_FRESHNESS_DAYS)` is true.

- [ ] **Step 1: Write the failing handler tests**

In `functions/src/__tests__/referral/reconcileReferrals.test.ts`, inside the existing `reconcileReferralsHandler` describe, add. Use the SAME fake-db/seed helpers the surrounding tests already use (match their exact shape — read the top of the file first). Each activity doc now optionally carries `serverCreatedAt` as an `admin.firestore.Timestamp`. Reference test skeleton (adapt to the file's existing `makeDb`/`seedReferral` helpers):

```typescript
it('counts a fresh server-stamped day (serverCreatedAt ≈ createdAt)', async () => {
  // 4 customers on 4 distinct in-window Lagos days, each serverCreatedAt == its createdAt.
  // Expect: qualifies (observed reaches QUALIFY_DISTINCT_DAYS).
});

it('drops a stale-stamped day (createdAt day1, serverCreatedAt day6, freshness 3)', async () => {
  // 4 customers backdated to days 1-4 in ONE session; all serverCreatedAt = day 6.
  // Expect: at most days 3-4 are fresh; with the ratchet, NOT qualified this run.
});

it('falls back to the Lane A ratchet for a doc with no serverCreatedAt', async () => {
  // 4 customers on 4 distinct days, NONE with serverCreatedAt (legacy binary).
  // Expect: identical to pre-Lane-B behavior — graded purely by the ratchet
  // (this is the regression pin; assert the same milestone/observed outcome as
  // the existing "qualifies a set-up user active on 4 distinct days" test).
});

it('counts fresh customer + legacy measurement days together', async () => {
  // Mixed set: some docs fresh-stamped, some legacy (no stamp). Both contribute.
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd functions && npx jest reconcileReferrals -t "server-stamped|Lane A ratchet for a doc|legacy measurement"`
Expected: FAIL (stale-stamped day still counted; qualifies when it should not).

- [ ] **Step 3: Implement the filter in `gatherSignals`**

Replace the activity-collection push loops in `gatherSignals` (lines 300–302 and 316–318) so each doc contributes only when legacy-or-fresh. Add a local helper inside `gatherSignals` and import `ACTIVITY_FRESHNESS_DAYS`:

Extend the top-of-file import from `./referralConstants` to include `ACTIVITY_FRESHNESS_DAYS`.

Replace lines 300–302:

```typescript
  const activityMs: number[] = [];
  const pushFreshOrLegacy = (d: admin.firestore.QueryDocumentSnapshot): void => {
    const createdAt = d.data()?.createdAt as number;
    if (typeof createdAt !== 'number' || !Number.isFinite(createdAt)) return;
    const sca = d.data()?.serverCreatedAt as admin.firestore.Timestamp | undefined;
    // No server stamp → pre-Lane-B doc → let the Lane A ratchet grade it.
    // Stamped → count the day only if the server saw it within the window.
    if (sca === undefined || sca === null) {
      activityMs.push(createdAt);
    } else if (isServerFresh(createdAt, sca.toMillis(), ACTIVITY_FRESHNESS_DAYS)) {
      activityMs.push(createdAt);
    }
  };
  for (const d of customersSnap.docs) pushFreshOrLegacy(d);
  for (const d of ordersSnap.docs) pushFreshOrLegacy(d);
```

Replace the measurement push (line 316–318) with:

```typescript
    for (const mSnap of measurementSnaps) {
      for (const m of mSnap.docs) pushFreshOrLegacy(m);
    }
```

Update the `gatherSignals` doc-comment (lines 275–282) to note the dual-signal filter.

- [ ] **Step 4: Run to verify pass**

Run: `cd functions && npx jest reconcileReferrals`
Expected: PASS — all prior tests still green (the legacy-fallback case proves no regression) plus the 4 new cases.

- [ ] **Step 5: Lint + commit**

Run: `cd functions && npm run lint`

```bash
git add functions/src/referral/reconcileReferrals.ts functions/src/__tests__/referral/reconcileReferrals.test.ts
git commit -m "feat(referral): gate active-day counting on serverCreatedAt freshness"
```

---

## Task 3: Firestore rules — pin `serverCreatedAt`, reject future `createdAt`

**Files:**
- Modify: `firestore.rules` (customers/orders/measurements matches under `/users/{uid}`)
- Test: `functions/src/__tests__/firestore.rules.test.ts`

**Interfaces:**
- Produces: two rule helper functions and split create/update rules on the three activity collections. Update rule keeps `serverCreatedAt` immutable-or-first-stamp so edit flows pass but the field can never be forged.

- [ ] **Step 1: Write the failing emulator tests**

In `functions/src/__tests__/firestore.rules.test.ts`, add a `describe('activity docs — serverCreatedAt / createdAt', ...)`. Reuse the file's existing `db(uid)`, `setDoc`, `doc`, `assertSucceeds`, `assertFails` helpers and the emulator setup already in the file. `serverTimestamp()` and `Timestamp` come from `firebase/firestore` (import alongside the existing imports).

```typescript
describe('activity docs — serverCreatedAt / createdAt', () => {
  const now = Date.now();
  it('allows create with serverCreatedAt == request.time and past createdAt', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c1'), {
        createdAt: now - 60_000,
        serverCreatedAt: serverTimestamp(),
      }),
    );
  });
  it('rejects create with a client-literal serverCreatedAt', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c2'), {
        createdAt: now,
        serverCreatedAt: Timestamp.fromMillis(now),
      }),
    );
  });
  it('rejects create with a future createdAt beyond skew', async () => {
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c3'), {
        createdAt: now + 3_600_000, // 1h ahead > 5m skew
        serverCreatedAt: serverTimestamp(),
      }),
    );
  });
  it('allows a create with no serverCreatedAt (old binary) and past createdAt', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c4'), { createdAt: now - 60_000 }),
    );
  });
  it('allows an edit that leaves serverCreatedAt unchanged', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c5'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/customers/c5'), { name: 'Ada' }, { merge: true }),
    );
  });
  it('rejects an update that rewrites serverCreatedAt to a forged value', async () => {
    await setDoc(doc(db('alice'), 'users/alice/customers/c6'), {
      createdAt: now - 60_000, serverCreatedAt: serverTimestamp(),
    });
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c6'),
        { serverCreatedAt: Timestamp.fromMillis(now - 5 * 86_400_000) }, { merge: true }),
    );
  });
  it('applies the same rules to orders and measurements', async () => {
    await assertSucceeds(
      setDoc(doc(db('alice'), 'users/alice/orders/o1'),
        { createdAt: now - 60_000, serverCreatedAt: serverTimestamp() }),
    );
    await assertFails(
      setDoc(doc(db('alice'), 'users/alice/customers/c1/measurements/m1'),
        { createdAt: now, serverCreatedAt: Timestamp.fromMillis(now) }),
    );
  });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `cd functions && npx jest firestore.rules -t serverCreatedAt`
Expected: FAIL — the blanket `allow read, write: if isOwner(uid)` currently permits every case, so the `assertFails` cases fail.

- [ ] **Step 3: Add rule helpers**

In `firestore.rules`, inside the `match /users/{uid}` block (beside the existing `serverOnlyField` function so it is in scope for the subcollection matches), add:

```
    // ── Lane B: activity docs (customers/orders/measurements) ────────────────
    // serverCreatedAt is the server-stamped anti-backdating anchor. On create it
    // must equal request.time if present (old binaries omit it → allowed, graded
    // by the Lane A ratchet). On update it is immutable, except a create that
    // omitted it may be stamped exactly once (the two-write client path). This
    // lets edit flows through while making the field unforgeable.
    function activityCreateOk() {
      return isOwner(uid)
        && (!('serverCreatedAt' in request.resource.data)
            || request.resource.data.serverCreatedAt == request.time)
        && (!('createdAt' in request.resource.data)
            || request.resource.data.createdAt <= request.time + duration.value(5, 'm'));
    }
    function serverCreatedAtProtectedOnUpdate() {
      return !('serverCreatedAt' in request.resource.data)
        ? true
        : (('serverCreatedAt' in resource.data)
           ? request.resource.data.serverCreatedAt == resource.data.serverCreatedAt
           : request.resource.data.serverCreatedAt == request.time);
    }
```

Note: `createdAt` is `Long` epoch-millis but `request.time` is a Timestamp. `request.time + duration.value(5,'m')` is a Timestamp; comparing a number `<=` a Timestamp fails in rules. Instead compare against the millis: use `request.time.toMillis()`:

```
        && (!('createdAt' in request.resource.data)
            || request.resource.data.createdAt <= request.time.toMillis() + 300000);
```

(`toMillis()` is available on the rules `request.time` Timestamp; `300000` = 5 min.)

- [ ] **Step 4: Split the create/update rules on the three collections**

Replace the customer subcollection rule (`firestore.rules`, the `match /customers/{customerId}` block currently `allow read, create, update, delete: if isOwner(uid);`) with:

```
      match /customers/{customerId} {
        allow read: if isOwner(uid);
        allow create: if activityCreateOk();
        allow update: if isOwner(uid) && serverCreatedAtProtectedOnUpdate();
        allow delete: if isOwner(uid);

        match /measurements/{measurementId} {
          allow read: if isOwner(uid);
          allow create: if activityCreateOk();
          allow update: if isOwner(uid) && serverCreatedAtProtectedOnUpdate();
          allow delete: if isOwner(uid);
        }

        match /styles/{styleId} {
          allow read, write: if isOwner(uid);
        }

        match /styleFolders/{folderId} {
          allow read, write: if isOwner(uid);
          match /styles/{styleId} {
            allow read, write: if isOwner(uid);
          }
        }
      }
```

Replace the orders rule (`match /orders/{orderId} { allow read, write: if isOwner(uid); }`) with:

```
      match /orders/{orderId} {
        allow read: if isOwner(uid);
        allow create: if activityCreateOk();
        allow update: if isOwner(uid) && serverCreatedAtProtectedOnUpdate();
        allow delete: if isOwner(uid);
      }
```

Leave inspiration / styleFolders / all other matches unchanged.

- [ ] **Step 5: Run to verify pass**

Run: `cd functions && npx jest firestore.rules`
Expected: PASS — new suite green AND every pre-existing rules test still green (styles/inspiration untouched, edit flows still allowed).

- [ ] **Step 6: Commit**

```bash
git add firestore.rules functions/src/__tests__/firestore.rules.test.ts
git commit -m "feat(rules): pin serverCreatedAt, reject future createdAt on activity docs"
```

---

## Task 4: Client — write `serverCreatedAt` on create (all three repos)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepository.kt:146`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt:254`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/data/FirebaseMeasurementRepository.kt:62`

**Interfaces:**
- Consumes: GitLive `FieldValue.serverTimestamp` and `DocumentReference.set(map, merge)` — exactly as `FirebaseUserRepository.kt:131,161`.
- Produces: each create enqueues the typed DTO write followed by a `serverCreatedAt` server-timestamp merge, in the same `enqueue` unit so ordering is preserved. No API signature changes.

**Why two writes in one enqueue:** `serverCreatedAt` can't ride the `@Serializable` DTO. Keeping `set(dto)` then a `set(mapOf("serverCreatedAt" to FieldValue.serverTimestamp), merge = true)` in the same `enqueue` lambda preserves the offline ordering and the existing cap checks, and matches the rules (create may omit `serverCreatedAt`; the follow-up merge stamps it). Cost: one extra small write per activity create — acceptable and analogous to the Lane A nightly-write cost.

- [ ] **Step 1: Add the import to each repo**

Ensure each of the three files imports (add if absent):

```kotlin
import dev.gitlive.firebase.firestore.FieldValue
```

- [ ] **Step 2: Customer — stamp on create**

In `FirebaseCustomerRepository.kt`, replace the `createCustomer` enqueue block (currently lines 146–148):

```kotlin
        val accepted = offlineWrites.enqueue("createCustomer customerId=${docRef.id}") {
            docRef.set(dto)
            docRef.set(mapOf("serverCreatedAt" to FieldValue.serverTimestamp), merge = true)
        }
```

- [ ] **Step 3: Order — stamp on create**

In `FirebaseOrderRepository.kt`, replace the `createOrder` enqueue block (currently lines 254–256, `docRef.set(dto)`):

```kotlin
        val accepted = offlineWrites.enqueue("createOrder orderId=${docRef.id}") {
            docRef.set(dto)
            docRef.set(mapOf("serverCreatedAt" to FieldValue.serverTimestamp), merge = true)
        }
```

- [ ] **Step 4: Measurement — stamp on create**

In `FirebaseMeasurementRepository.kt`, replace the `createMeasurement` enqueue block (currently lines 62–64):

```kotlin
        val accepted = offlineWrites.enqueue("createMeasurement measurementId=${docRef.id}") {
            docRef.set(dto)
            docRef.set(mapOf("serverCreatedAt" to FieldValue.serverTimestamp), merge = true)
        }
```

- [ ] **Step 5: Compile both platforms + detekt**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:assembleDebug detekt`
Expected: BUILD SUCCESSFUL. (iOS compile is mandatory — `[[feedback_kmp_jvm_only_apis]]`; `FieldValue`/`set(map,merge)` are commonMain GitLive APIs, already proven on iOS by `FirebaseUserRepository`.)

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/data/FirebaseOrderRepository.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/data/FirebaseMeasurementRepository.kt
git commit -m "feat(referral): stamp serverCreatedAt on customer/order/measurement create"
```

---

## Task 5: Verify, QA, and deploy sequencing

**Files:** none (verification + docs of intent).

- [ ] **Step 1: Full functions suite + lint**

Run: `cd functions && npm run lint && npx jest`
Expected: 0 lint errors; all suites pass (was 539 before Lane B; new cases added).

- [ ] **Step 2: Full client gate**

Run: `./gradlew :composeApp:compileTestKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest detekt`
Expected: all green (client changes add no new commonTest, so existing tests must stay green).

- [ ] **Step 3: Write the QA smoke steps into the PR body** ([[feedback_qa_smoke_tests]])

Manual, on a new-binary build:
1. Online: create a customer → Firestore console shows `serverCreatedAt` ≈ now (server value), `createdAt` = device time. (Rules pass.)
2. Airplane mode: create a customer with an offline device clock → write queues; on reconnect, `serverCreatedAt` stamps at sync time. App UI shows the created customer throughout (offline UX unchanged).
3. Edit that customer's name → save succeeds (update rule allows unchanged `serverCreatedAt`).
4. Seeded-account grader check (after deploy): a genuine 4-distinct-day user still qualifies; a same-session backdated burst does not.

- [ ] **Step 4: Deploy sequencing (record in PR, executed by Daniel — prod deploy is auto-mode gated)**

- Rules may deploy ahead of the binary safely: `serverCreatedAt == request.time` only fires when the field is present (old binaries omit it), and `createdAt <= now+5m` is satisfied by every honest existing client. **Before deploying rules ahead of the binary, run the emulator suite (Task 3) to confirm current-binary writes — which send only `createdAt`, no `serverCreatedAt` — are still allowed.** (Task 3 test "allows a create with no serverCreatedAt" is that gate.)
- Deploy rules: `firebase deploy --only firestore:rules --project stitchpad-30607`.
- No functions redeploy is strictly required for correctness on old data, but redeploy reconcile so the freshness filter is live for new-binary docs: `firebase deploy --only functions:reconcileReferrals,functions:debugReconcileReferrals`.
- Ship the app binary on the next release train. Lane A stays deployed and unchanged.

- [ ] **Step 5: Review rotation + PR**

Run Cursor Bugbot + `codex review -c model=gpt-5.5` ([[feedback_review_rotation]], [[feedback_codex_review_model]]). Open PR from `feat/referral-server-timestamp`; CI must be green (secrets-scan, detekt, crash-check, functions-tests, build-android, build-ios).

- [ ] **Step 6: Update the backlog memory**

Mark `project_referral_activity_timestamp_lane_b` as SHIPPED PENDING MERGE with the PR number; reaffirm "Lane A must remain."

---

## Self-Review

**Spec coverage:**
- Client `serverCreatedAt` on 3 collections → Task 4. ✓
- Rules `serverCreatedAt == request.time` + future-`createdAt` reject + split create/update → Task 3. ✓
- Reconcile freshness gate + legacy fallback → Task 2; pure `isServerFresh` + `FRESHNESS_DAYS=3` → Task 1. ✓
- Serialization wrinkle (option a, two-write merge) → Task 4 rationale + Global Constraints. ✓
- No backfill / additive / Lane A remains → Global Constraints + Task 2 legacy test. ✓
- Test plan items 1–11 → Tasks 1 (pure 1–3,+skew/legacy), 2 (handler 4–6, legacy regression), 3 (rules 7–10), client #11 replaced by compile-gate + QA per the codebase's unfakeable-GitLive reality (documented in File Structure note). ✓
- Release/sequencing (rules-ahead-of-binary safety, verify current-binary writes) → Task 5. ✓

**Placeholder scan:** no TBD/TODO; every code step shows code; the one deliberately-adapted part (Task 2/3 seed helpers) instructs "match the file's existing helpers" because those helpers already exist in the test file and must not be reinvented. ✓

**Type consistency:** `isServerFresh(createdAtMs, serverCreatedAtMs?, freshnessDays)` defined Task 1, consumed Task 2 with the same arg order; `ACTIVITY_FRESHNESS_DAYS` defined Task 1, used Task 2; `activityCreateOk` / `serverCreatedAtProtectedOnUpdate` defined and used within Task 3; `serverCreatedAt` field name identical across rules, reconcile, and client. ✓
