# Founding Tailors Leaderboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the ~108-member WhatsApp designer community into referrers via a monthly points leaderboard (top 3 win a branded shirt; points bank toward future Pro months), reusing the existing referral backend's server-verified `qualified` milestone as a "point."

**Architecture:** Three dependency-ordered layers. (1) Firebase Functions: stamp `qualifiedAt` on qualify, a self-serve link-minting callable, a daily aggregator that writes public leaderboard docs, and a public read callable. (2) The Astro/Vercel marketing site gets a `/founding-tailors` page consuming the read callable via the existing `httpsCallable` pattern. (3) The KMP app gains a `referralCode` on `User`, a repository method to fetch/create the link, and a Founding Tailors surface (dashboard card + Settings entry) that shares the link and opens the leaderboard.

**Tech Stack:** TypeScript + firebase-functions v1 + firebase-admin (Jest tests); Astro 5 static + Firebase JS callable SDK + Tailwind v4; Kotlin Multiplatform + Compose + Koin + GitLive Firebase SDK (JUnit5/Turbine/AssertK tests).

## Global Constraints

- **Firebase region:** `europe-west1` — every function uses the `REGION` constant from `functions/src/referral/referralConstants.ts`.
- **New functions deploy gate:** every new function MUST be added to `functions/src/index.ts` exports AND the `package.json` `deploy` allow-list before deploy. `firebase.json` `predeploy` runs `npm run lint` then `npm run build` — run `npm run lint` locally first (CI `functions-tests` lints before testing).
- **"Point" definition (do not reinvent):** a referral counts iff `milestone === 'qualified'` AND it carries no BLOCKING flag (`self_referral`, `device_reuse`, `velocity`). Use `hasBlockingFlag(flags)` from `referralConstants.ts`.
- **Day/month boundary timezone:** `Africa/Lagos` (`ACTIVE_DAY_TIMEZONE`). `monthId` format is `YYYY-MM` computed in Africa/Lagos.
- **Program referrers are payout-disabled:** minted with `payoutRatePerUser: 0` and `program: 'founding_tailors'`, so a qualifying referral counts for the leaderboard but never enters the cash payout pipeline (`gradeReferral` only opens a payout when `payoutRatePerUser > 0`).
- **Community-facing copy:** no em dashes (WhatsApp/marketing strings). Applies to the web page and any shared message text.
- **App conventions:** no hardcoded user-facing strings (use `compose.resources`); `Result<T, E>` for expected failures (never throw); MVI (State/Action/Event + ViewModel); all state in the ViewModel; every `Screen` composable has a `@Preview`.
- **GitLive writes:** `set()/update()/delete()` suspend until the server ACKs — safe to read back after they return.
- **Kotlin backtick test names:** letters, digits, spaces, and hyphens ONLY (no punctuation/underscores inside backticks).

---

## File Structure

**Functions (`functions/`):**
- Modify `src/referral/reconcileReferrals.ts` — stamp `qualifiedAt` on first qualify.
- Create `src/referral/getOrCreateMyReferralLink.ts` — self-serve link mint.
- Create `src/referral/foundingTailorsLeaderboard.ts` — aggregator + public read callable + shared types/helpers.
- Modify `src/index.ts` — export the three new functions.
- Modify `package.json` — add the three to the `deploy` allow-list.
- Create tests under `src/__tests__/referral/`.

**Web (`~/Desktop/Business/StitchPad/StitchPad-IT/stitchpad-web`):**
- Create `src/lib/foundingTailors.ts` — typed `httpsCallable` wrapper.
- Create `src/pages/founding-tailors.astro` — the page + client script.

**App (`composeApp/src/commonMain/.../`):**
- Modify `core/domain/model/User.kt` + `core/data/dto/UserDto.kt` + the User mapper — add `referralCode`.
- Modify `feature/referral/domain/ReferralRepository.kt` + `feature/referral/data/CloudFunctionsReferralRepository.kt` — add `getOrCreateMyReferralLink()`.
- Create `feature/foundingtailors/presentation/` — `FoundingTailorsViewModel`, State/Action/Event, Root + Screen composables.
- Modify the dashboard + settings entry points to surface it, and register DI + navigation.

---

## Phase 1a — Backend + Web

### Task 1: Stamp `qualifiedAt` when a referral first qualifies

**Files:**
- Modify: `functions/src/referral/reconcileReferrals.ts` (the transaction write block, around lines 471-489)
- Test: `functions/src/__tests__/referral/reconcileReferrals.test.ts` (add cases to the existing suite)

**Interfaces:**
- Consumes: existing `gradeReferral` result `{ milestone, qualifiedDelta, ... }`; existing `nowTs` (`admin.firestore.Timestamp`).
- Produces: `referrals/{uid}.qualifiedAt: Timestamp` — set exactly once, on the run where `qualifiedDelta === 1`. Consumed by Task 3's month bucketing.

- [ ] **Step 1: Write the failing test**

Add to `reconcileReferrals.test.ts` (mirror the existing fake-Firestore setup in that file):

```ts
test('stamps qualifiedAt once on the run that first qualifies a referral', async () => {
  // Arrange a referral that will cross the qualification bar this run
  // (4 distinct active Lagos days within the window), milestone currently 'activated'.
  const { db, referralRef } = seedQualifyingReferral(); // existing test helper pattern

  await reconcileReferralsHandler({ db, now: () => new Date('2026-08-10T04:00:00Z') });

  const after = (await referralRef.get()).data();
  expect(after.milestone).toBe('qualified');
  expect(after.qualifiedAt).toBeDefined();
  expect(after.qualifiedAt.toMillis()).toBe(new Date('2026-08-10T04:00:00Z').getTime());
});

test('does not overwrite qualifiedAt on a later run of an already-qualified referral', async () => {
  const { db, referralRef } = seedAlreadyQualifiedReferral({ qualifiedAtMs: 1_000 });

  await reconcileReferralsHandler({ db, now: () => new Date('2026-08-11T04:00:00Z') });

  const after = (await referralRef.get()).data();
  expect(after.qualifiedAt.toMillis()).toBe(1_000); // unchanged
});
```

If the file lacks `seedQualifyingReferral`/`seedAlreadyQualifiedReferral`, build the docs inline the way the existing tests in this file construct `referrals/{uid}` fixtures (copy an existing arrange block and set `milestone`/`activeDayKeys` accordingly).

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd functions && npx jest src/__tests__/referral/reconcileReferrals.test.ts -t qualifiedAt`
Expected: FAIL — `after.qualifiedAt` is `undefined`.

- [ ] **Step 3: Implement the stamp**

In `reconcileReferrals.ts`, inside the `if (grade) { ... }` block that already sets `update.milestone`, add the one-time stamp:

```ts
      if (grade) {
        update.milestone = grade.milestone;
        update.payoutState = grade.payoutState;
        if (grade.qualifiedDelta === 1) {
          // First time this referral reaches `qualified`. Stamp the server instant
          // so the Founding Tailors aggregator can bucket the point by month.
          // qualifiedDelta is only ever 1 on the transition, so this never overwrites.
          update.qualifiedAt = nowTs;
        }
        if (grade.payoutState === 'pending' && f.payoutState === 'none') {
          update.payoutAmount = grade.payoutAmount;
          update.holdEndsAt = admin.firestore.Timestamp.fromMillis(grade.holdEndsAtMs as number);
        }
      }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd functions && npx jest src/__tests__/referral/reconcileReferrals.test.ts`
Expected: PASS (new cases + all existing cases still green).

- [ ] **Step 5: Lint + commit**

```bash
cd functions && npm run lint
git add src/referral/reconcileReferrals.ts src/__tests__/referral/reconcileReferrals.test.ts
git commit -m "feat(referral): stamp qualifiedAt on first qualify for leaderboard bucketing"
```

---

### Task 2: `getOrCreateMyReferralLink` callable (self-serve link mint)

**Files:**
- Create: `functions/src/referral/getOrCreateMyReferralLink.ts`
- Modify: `functions/src/index.ts` (export), `functions/package.json` (deploy allow-list)
- Test: `functions/src/__tests__/referral/getOrCreateMyReferralLink.test.ts`

**Interfaces:**
- Consumes: `generateCode` (from `../billing/giftBilling`), `REGION, MARKETERS, REFERRAL_CODES, REFERRAL_CODE_LENGTH, REFERRAL_LINK_BASE, PLAY_PACKAGE` (from `./referralConstants`).
- Produces: callable `getOrCreateMyReferralLink` returning `{ code: string; url: string; playUrl: string }`; writes `marketers/{id}` (with `type:'user'`, `program:'founding_tailors'`, `payoutRatePerUser:0`, `referrerUid:<uid>`) + `referralCodes/{code}` + `users/{uid}.referralCode`. Consumed by App Task 8 and the web `you` lookup.

- [ ] **Step 1: Write the failing test**

```ts
import { getOrCreateMyReferralLinkHandler } from '../../referral/getOrCreateMyReferralLink';
import { makeFakeDb } from '../helpers/fakeFirestore'; // reuse the same fake used by other referral tests

const ctx = (uid?: string) => ({ auth: uid ? { uid, token: {} } : undefined }) as any;

test('mints a payout-disabled user-referrer and stores the code on the user doc', async () => {
  const db = makeFakeDb({ 'users/u1': { displayName: 'Ada', businessName: 'Ada Styles', email: 'ada@x.com' } });
  let n = 0;
  const deps = { db, now: () => new Date('2026-08-03T00:00:00Z'), randomCode: () => `CODE${n++}`, randomId: () => 'rid' };

  const res = await getOrCreateMyReferralLinkHandler({}, ctx('u1'), deps);

  expect(res.code).toBe('CODE0');
  expect(res.url).toBe('https://link.getstitchpad.com/r/CODE0');
  const marketer = await db.doc(`marketers/${(await db.doc('referralCodes/CODE0').get()).data().marketerId}`).get();
  expect(marketer.data()).toMatchObject({ type: 'user', program: 'founding_tailors', payoutRatePerUser: 0, referrerUid: 'u1', name: 'Ada Styles' });
  expect((await db.doc('users/u1').get()).data().referralCode).toBe('CODE0');
});

test('is idempotent: a second call returns the same code and does not mint again', async () => {
  const db = makeFakeDb({ 'users/u1': { displayName: 'Ada', email: 'ada@x.com', referralCode: 'CODE0' } });
  const deps = { db, now: () => new Date(), randomCode: () => 'SHOULD_NOT_BE_USED', randomId: () => 'rid' };

  const res = await getOrCreateMyReferralLinkHandler({}, ctx('u1'), deps);

  expect(res.code).toBe('CODE0');
});

test('rejects an unauthenticated caller', async () => {
  const db = makeFakeDb({});
  const deps = { db, now: () => new Date(), randomCode: () => 'X', randomId: () => 'rid' };
  await expect(getOrCreateMyReferralLinkHandler({}, ctx(undefined), deps)).rejects.toThrow('unauthenticated');
});
```

Use whatever fake-Firestore helper the existing referral tests import (e.g. `recordAttribution.test.ts`). If it is inline rather than a shared helper, copy that inline fake into this test file.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd functions && npx jest src/__tests__/referral/getOrCreateMyReferralLink.test.ts`
Expected: FAIL — module not found / handler undefined.

- [ ] **Step 3: Implement the callable**

Create `functions/src/referral/getOrCreateMyReferralLink.ts` (mirrors `createMarketerHandler`'s mint transaction but self-serve, payout-disabled, and idempotent on `users/{uid}.referralCode`):

```ts
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import * as crypto from 'crypto';
import { generateCode } from '../billing/giftBilling';
import {
  REGION, MARKETERS, REFERRAL_CODES, REFERRAL_CODE_LENGTH, REFERRAL_LINK_BASE, PLAY_PACKAGE,
} from './referralConstants';
import type { ReferrerType, PayoutKind, MarketerStatus } from './referralConstants';

export const FOUNDING_TAILORS_PROGRAM = 'founding_tailors';
const MAX_CODE_ATTEMPTS = 5;

export interface MyReferralLinkResponse { code: string; url: string; playUrl: string }
export interface MyReferralLinkDeps {
  db: admin.firestore.Firestore;
  now: () => Date;
  randomCode: () => string;
  randomId: () => string;
}

export const getOrCreateMyReferralLink = functions
  .region(REGION)
  .https.onCall(async (_data, context): Promise<MyReferralLinkResponse> =>
    getOrCreateMyReferralLinkHandler(_data, context, {
      db: admin.firestore(),
      now: () => new Date(),
      randomCode: () => generateCode(REFERRAL_CODE_LENGTH),
      randomId: () => crypto.randomBytes(6).toString('hex'),
    }));

export async function getOrCreateMyReferralLinkHandler(
  _data: unknown,
  context: functions.https.CallableContext,
  deps: MyReferralLinkDeps,
): Promise<MyReferralLinkResponse> {
  const uid = context.auth?.uid;
  if (!uid) throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');

  const userRef = deps.db.doc(`users/${uid}`);
  const user = (await userRef.get()).data() as
    | { referralCode?: string; displayName?: string; businessName?: string; email?: string }
    | undefined;

  // Idempotent: a user already has exactly one outbound code.
  if (user?.referralCode) return linkFor(user.referralCode);

  const name = (user?.businessName?.trim() || user?.displayName?.trim() || 'Tailor'); // D3: business name, fallback first name
  const email = (user?.email ?? '').toLowerCase();
  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  const marketerId = `mkt_${deps.now().getTime()}_${deps.randomId()}`;

  let code = '';
  for (let attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt += 1) {
    const candidate = deps.randomCode();
    const claimed = await deps.db.runTransaction(async (tx) => {
      const codeRef = deps.db.doc(`${REFERRAL_CODES}/${candidate}`);
      const freshUser = await tx.get(userRef);
      if ((freshUser.data() as { referralCode?: string } | undefined)?.referralCode) return false; // race: already minted
      if ((await tx.get(codeRef)).exists) return false;
      tx.set(deps.db.doc(`${MARKETERS}/${marketerId}`), {
        name, email, phone: null,
        type: 'user' as ReferrerType,
        program: FOUNDING_TAILORS_PROGRAM,
        referrerUid: uid,
        code: candidate,
        payoutRatePerUser: 0,           // payout-disabled: leaderboard only, never queues cash
        payoutKind: 'credit' as PayoutKind,
        bankName: null, bankAccountName: null, bankAccountNumber: null,
        status: 'active' as MarketerStatus,
        installs: 0, activated: 0, qualified: 0,
        pendingAmount: 0, confirmedAmount: 0, paidAmount: 0,
        createdAt: nowTs, updatedAt: nowTs,
      });
      tx.set(codeRef, { marketerId, createdAt: nowTs });
      tx.set(userRef, { referralCode: candidate, updatedAt: nowTs }, { merge: true });
      return true;
    });
    if (claimed) { code = candidate; break; }
    // If the race path returned false because another mint won, re-read and return it.
    const reUser = (await userRef.get()).data() as { referralCode?: string } | undefined;
    if (reUser?.referralCode) return linkFor(reUser.referralCode);
  }
  if (!code) throw new functions.https.HttpsError('internal', 'code_generation_failed');
  return linkFor(code);
}

function linkFor(code: string): MyReferralLinkResponse {
  return {
    code,
    url: `${REFERRAL_LINK_BASE}/${code}`,
    playUrl: `https://play.google.com/store/apps/details?id=${PLAY_PACKAGE}&referrer=${encodeURIComponent(`ref=${code}`)}`,
  };
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd functions && npx jest src/__tests__/referral/getOrCreateMyReferralLink.test.ts`
Expected: PASS.

- [ ] **Step 5: Wire exports + deploy allow-list**

In `functions/src/index.ts` add:
```ts
export { getOrCreateMyReferralLink } from './referral/getOrCreateMyReferralLink';
```
In `functions/package.json`, add `getOrCreateMyReferralLink` to the `deploy` allow-list array (same place the other referral functions are listed).

- [ ] **Step 6: Lint + commit**

```bash
cd functions && npm run lint && npx tsc --noEmit
git add src/referral/getOrCreateMyReferralLink.ts src/index.ts package.json src/__tests__/referral/getOrCreateMyReferralLink.test.ts
git commit -m "feat(referral): self-serve getOrCreateMyReferralLink for Founding Tailors"
```

---

### Task 3: `aggregateFoundingTailorsLeaderboard` (daily scheduled aggregator)

**Files:**
- Create: `functions/src/referral/foundingTailorsLeaderboard.ts` (aggregator + shared types; the read callable is added in Task 4 to the same file)
- Modify: `functions/src/index.ts`, `functions/package.json`
- Test: `functions/src/__tests__/referral/foundingTailorsLeaderboard.test.ts`

**Interfaces:**
- Consumes: `referrals/{uid}` docs (`marketerId`, `milestone`, `flags`, `qualifiedAt`), `marketers/{id}` docs (`program`, `name`), `hasBlockingFlag`, `ACTIVE_DAY_TIMEZONE`, `REGION`, `MARKETERS`, `REFERRALS`.
- Produces: public docs `leaderboards/{monthId}` = `{ monthId, updatedAt, entries: LeaderEntry[] }`, `leaderboards/current` = `{ monthId }`, `leaderboards/alltime` = `{ updatedAt, entries }`, where `LeaderEntry = { marketerId: string; name: string; points: number }` sorted points-desc. `monthKeyLagos(ms)` helper exported for Task 4.

- [ ] **Step 1: Write the failing test**

```ts
import { aggregateFoundingTailorsLeaderboardHandler, monthKeyLagos } from '../../referral/foundingTailorsLeaderboard';
import { makeFakeDb } from '../helpers/fakeFirestore';

const ts = (iso: string) => ({ toMillis: () => new Date(iso).getTime() });

test('counts only qualified, non-blocked referrals of program user-referrers, bucketed by qualifiedAt month', async () => {
  const db = makeFakeDb({
    'marketers/mA': { program: 'founding_tailors', name: 'Ada Styles', type: 'user' },
    'marketers/mB': { program: 'founding_tailors', name: 'Bola Wears', type: 'user' },
    'marketers/mAff': { type: 'affiliate', name: 'Paid Marketer' }, // must be excluded
    'referrals/r1': { marketerId: 'mA', milestone: 'qualified', qualifiedAt: ts('2026-08-05T10:00:00Z'), flags: [] },
    'referrals/r2': { marketerId: 'mA', milestone: 'qualified', qualifiedAt: ts('2026-08-20T10:00:00Z'), flags: [] },
    'referrals/r3': { marketerId: 'mA', milestone: 'qualified', qualifiedAt: ts('2026-08-21T10:00:00Z'), flags: ['self_referral'] }, // blocked
    'referrals/r4': { marketerId: 'mB', milestone: 'qualified', qualifiedAt: ts('2026-08-06T10:00:00Z'), flags: [] },
    'referrals/r5': { marketerId: 'mB', milestone: 'activated', flags: [] }, // not qualified
    'referrals/r6': { marketerId: 'mAff', milestone: 'qualified', qualifiedAt: ts('2026-08-06T10:00:00Z'), flags: [] },
  });

  await aggregateFoundingTailorsLeaderboardHandler({ db, now: () => new Date('2026-08-25T00:00:00Z') });

  const aug = (await db.doc('leaderboards/2026-08').get()).data();
  expect(aug.entries).toEqual([
    { marketerId: 'mA', name: 'Ada Styles', points: 2 },
    { marketerId: 'mB', name: 'Bola Wears', points: 1 },
  ]);
  expect((await db.doc('leaderboards/current').get()).data().monthId).toBe('2026-08');
});

test('monthKeyLagos buckets a UTC-evening instant into the correct Lagos month', () => {
  // 2026-07-31T23:30Z is 2026-08-01 00:30 in Lagos (UTC+1)
  expect(monthKeyLagos(new Date('2026-07-31T23:30:00Z').getTime())).toBe('2026-08');
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd functions && npx jest src/__tests__/referral/foundingTailorsLeaderboard.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the aggregator**

Create `functions/src/referral/foundingTailorsLeaderboard.ts`:

```ts
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { REGION, MARKETERS, REFERRALS, ACTIVE_DAY_TIMEZONE, hasBlockingFlag } from './referralConstants';
import type { ReferralFlag, ReferralMilestone } from './referralConstants';
import { FOUNDING_TAILORS_PROGRAM } from './getOrCreateMyReferralLink';

export interface LeaderEntry { marketerId: string; name: string; points: number }
export interface AggregatorDeps { db: admin.firestore.Firestore; now: () => Date }

/** YYYY-MM in Africa/Lagos for the given epoch-ms. */
export function monthKeyLagos(ms: number): string {
  // en-CA formats as YYYY-MM-DD; slice to YYYY-MM. timeZone shifts the day/month boundary.
  const ymd = new Intl.DateTimeFormat('en-CA', {
    timeZone: ACTIVE_DAY_TIMEZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(ms));
  return ymd.slice(0, 7);
}

function sortEntries(map: Map<string, LeaderEntry>): LeaderEntry[] {
  return [...map.values()].sort((a, b) => b.points - a.points || a.name.localeCompare(b.name));
}

export async function aggregateFoundingTailorsLeaderboardHandler(deps: AggregatorDeps): Promise<void> {
  // 1. Program user-referrers → id -> display name.
  const marketersSnap = await deps.db.collection(MARKETERS).where('program', '==', FOUNDING_TAILORS_PROGRAM).get();
  const names = new Map<string, string>();
  marketersSnap.forEach((d) => names.set(d.id, (d.data().name as string) ?? 'Tailor'));
  if (names.size === 0) return;

  // 2. Scan qualified referrals; keep only program referrers with no blocking flag.
  const qualifiedSnap = await deps.db.collection(REFERRALS).where('milestone', '==', 'qualified').get();
  const byMonth = new Map<string, Map<string, LeaderEntry>>();
  const allTime = new Map<string, LeaderEntry>();
  const bump = (map: Map<string, LeaderEntry>, id: string) => {
    const e = map.get(id) ?? { marketerId: id, name: names.get(id) as string, points: 0 };
    e.points += 1; map.set(id, e);
  };

  qualifiedSnap.forEach((d) => {
    const r = d.data() as { marketerId: string; flags?: ReferralFlag[]; qualifiedAt?: { toMillis(): number }; milestone: ReferralMilestone };
    if (!names.has(r.marketerId)) return;                 // not a program referrer (e.g. affiliate)
    if (hasBlockingFlag(r.flags)) return;                 // fraud-flagged → no point
    const ms = r.qualifiedAt?.toMillis?.();
    if (typeof ms !== 'number') return;                   // needs qualifiedAt (Task 1); pre-stamp docs skipped until next reconcile
    const mk = monthKeyLagos(ms);
    if (!byMonth.has(mk)) byMonth.set(mk, new Map());
    bump(byMonth.get(mk) as Map<string, LeaderEntry>, r.marketerId);
    bump(allTime, r.marketerId);
  });

  const nowTs = admin.firestore.Timestamp.fromDate(deps.now());
  const currentMonth = monthKeyLagos(deps.now().getTime());
  const batch = deps.db.batch();
  for (const [mk, map] of byMonth) {
    batch.set(deps.db.doc(`leaderboards/${mk}`), { monthId: mk, updatedAt: nowTs, entries: sortEntries(map) });
  }
  // Ensure the current month doc exists even with zero points (page renders an empty board, not an error).
  if (!byMonth.has(currentMonth)) {
    batch.set(deps.db.doc(`leaderboards/${currentMonth}`), { monthId: currentMonth, updatedAt: nowTs, entries: [] });
  }
  batch.set(deps.db.doc('leaderboards/current'), { monthId: currentMonth, updatedAt: nowTs });
  batch.set(deps.db.doc('leaderboards/alltime'), { updatedAt: nowTs, entries: sortEntries(allTime) });
  await batch.commit();
}

export const aggregateFoundingTailorsLeaderboard = functions
  .region(REGION)
  .pubsub.schedule('0 4 * * *')          // 04:00 Africa/Lagos daily, after reconcileReferrals (03:30)
  .timeZone(ACTIVE_DAY_TIMEZONE)
  .onRun(async () => { await aggregateFoundingTailorsLeaderboardHandler({ db: admin.firestore(), now: () => new Date() }); });
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd functions && npx jest src/__tests__/referral/foundingTailorsLeaderboard.test.ts`
Expected: PASS.

- [ ] **Step 5: Wire exports + deploy allow-list**

`index.ts`: `export { aggregateFoundingTailorsLeaderboard } from './referral/foundingTailorsLeaderboard';`
`package.json`: add `aggregateFoundingTailorsLeaderboard` to the `deploy` allow-list.

- [ ] **Step 6: Lint + commit**

```bash
cd functions && npm run lint && npx tsc --noEmit
git add src/referral/foundingTailorsLeaderboard.ts src/index.ts package.json src/__tests__/referral/foundingTailorsLeaderboard.test.ts
git commit -m "feat(referral): daily Founding Tailors leaderboard aggregator"
```

---

### Task 4: `getFoundingTailorsLeaderboard` (public read callable)

**Files:**
- Modify: `functions/src/referral/foundingTailorsLeaderboard.ts` (add the read callable + handler)
- Modify: `functions/src/index.ts`, `functions/package.json`
- Test: `functions/src/__tests__/referral/foundingTailorsLeaderboard.test.ts` (add cases)

**Interfaces:**
- Consumes: the public `leaderboards/*` docs from Task 3; `REFERRAL_CODES`, `MARKETERS`.
- Produces: callable `getFoundingTailorsLeaderboard(data: { code?: string })` → `{ updatedAt: number; monthId: string; top: PublicRow[]; you: { rank: number; points: number } | null }`, `PublicRow = { rank: number; name: string; points: number }`. Top rows carry NO codes/marketerIds. Consumed by the web page (Task 6).

- [ ] **Step 1: Write the failing test**

```ts
import { getFoundingTailorsLeaderboardHandler } from '../../referral/foundingTailorsLeaderboard';

test('returns ranked top rows without codes and resolves you from ?code', async () => {
  const db = makeFakeDb({
    'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': { monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'),
      entries: [ { marketerId: 'mA', name: 'Ada Styles', points: 3 }, { marketerId: 'mB', name: 'Bola Wears', points: 1 } ] },
    'referralCodes/CODEB': { marketerId: 'mB' },
  });

  const res = await getFoundingTailorsLeaderboardHandler({ code: 'CODEB' }, { db });

  expect(res.monthId).toBe('2026-08');
  expect(res.top).toEqual([
    { rank: 1, name: 'Ada Styles', points: 3 },
    { rank: 2, name: 'Bola Wears', points: 1 },
  ]);
  expect((res.top[0] as any).marketerId).toBeUndefined();
  expect(res.you).toEqual({ rank: 2, points: 1 });
});

test('unknown code yields you=null and never leaks code existence', async () => {
  const db = makeFakeDb({ 'leaderboards/current': { monthId: '2026-08' },
    'leaderboards/2026-08': { monthId: '2026-08', updatedAt: ts('2026-08-25T00:00:00Z'), entries: [] } });
  const res = await getFoundingTailorsLeaderboardHandler({ code: 'NOPE' }, { db });
  expect(res.you).toBeNull();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd functions && npx jest src/__tests__/referral/foundingTailorsLeaderboard.test.ts -t "top rows"`
Expected: FAIL — handler undefined.

- [ ] **Step 3: Implement the read callable**

Append to `foundingTailorsLeaderboard.ts`:

```ts
import { REFERRAL_CODES } from './referralConstants';

export interface PublicRow { rank: number; name: string; points: number }
export interface LeaderboardResponse { updatedAt: number; monthId: string; top: PublicRow[]; you: { rank: number; points: number } | null }
export interface ReadDeps { db: admin.firestore.Firestore }
const TOP_LIMIT = 25;

export async function getFoundingTailorsLeaderboardHandler(
  data: { code?: unknown }, deps: ReadDeps,
): Promise<LeaderboardResponse> {
  const monthId = ((await deps.db.doc('leaderboards/current').get()).data()?.monthId as string) ?? monthKeyLagos(Date.now());
  const board = (await deps.db.doc(`leaderboards/${monthId}`).get()).data() as
    | { updatedAt?: { toMillis(): number }; entries?: LeaderEntry[] } | undefined;
  const entries = board?.entries ?? [];

  const top: PublicRow[] = entries.slice(0, TOP_LIMIT).map((e, i) => ({ rank: i + 1, name: e.name, points: e.points }));

  let you: { rank: number; points: number } | null = null;
  const code = typeof data?.code === 'string' && data.code.trim() ? data.code.trim() : null;
  if (code) {
    const marketerId = (await deps.db.doc(`${REFERRAL_CODES}/${code}`).get()).data()?.marketerId as string | undefined;
    if (marketerId) {
      const idx = entries.findIndex((e) => e.marketerId === marketerId);
      if (idx >= 0) you = { rank: idx + 1, points: entries[idx].points };
      else you = { rank: 0, points: 0 }; // valid referrer, no points yet this month
    }
  }
  return { updatedAt: board?.updatedAt?.toMillis?.() ?? 0, monthId, top, you };
}

export const getFoundingTailorsLeaderboard = functions
  .region(REGION)
  .https.onCall(async (data) => getFoundingTailorsLeaderboardHandler(data as { code?: unknown }, { db: admin.firestore() }));
```

Note: `onCall` is invocable by unauthenticated clients (`context.auth` is simply undefined); no auth guard is added on purpose so the public web page can read it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd functions && npx jest src/__tests__/referral/foundingTailorsLeaderboard.test.ts`
Expected: PASS.

- [ ] **Step 5: Wire exports + deploy allow-list**

`index.ts`: `export { getFoundingTailorsLeaderboard } from './referral/foundingTailorsLeaderboard';`
`package.json`: add `getFoundingTailorsLeaderboard` to the `deploy` allow-list.

- [ ] **Step 6: Lint + commit**

```bash
cd functions && npm run lint && npx tsc --noEmit
git add src/referral/foundingTailorsLeaderboard.ts src/index.ts package.json src/__tests__/referral/foundingTailorsLeaderboard.test.ts
git commit -m "feat(referral): public getFoundingTailorsLeaderboard read callable"
```

---

### Task 5: `/founding-tailors` web page (Astro/Vercel)

**Files (in `~/Desktop/Business/StitchPad/StitchPad-IT/stitchpad-web`):**
- Create: `src/lib/foundingTailors.ts`
- Create: `src/pages/founding-tailors.astro`

**Interfaces:**
- Consumes: `giftFunctions()` from `src/lib/firebase.ts` (region `europe-west1`); the `getFoundingTailorsLeaderboard` callable from Task 4.
- Produces: a public page at `getstitchpad.com/founding-tailors` that renders the board and highlights the viewer via `?code=`.

This runs in the separate `stitchpad-web` repo, committed there (not the app repo).

- [ ] **Step 1: Add the typed callable wrapper**

Create `src/lib/foundingTailors.ts` (mirror the shape of `src/lib/admin.ts`'s `getReferralDashboard` wrapper):

```ts
import { httpsCallable } from 'firebase/functions';
import { giftFunctions } from './firebase';

export interface PublicRow { rank: number; name: string; points: number }
export interface LeaderboardResponse {
  updatedAt: number; monthId: string; top: PublicRow[];
  you: { rank: number; points: number } | null;
}

export async function fetchLeaderboard(code: string | null): Promise<LeaderboardResponse> {
  const call = httpsCallable<{ code?: string }, LeaderboardResponse>(giftFunctions(), 'getFoundingTailorsLeaderboard');
  const { data } = await call(code ? { code } : {});
  return data;
}
```

- [ ] **Step 2: Create the page**

Create `src/pages/founding-tailors.astro`. Use `BaseLayout` and the existing Adire Atelier Tailwind tokens (indigo primary, sienna, paper). No em dashes in copy.

```astro
---
import BaseLayout from '../layouts/BaseLayout.astro';
---
<BaseLayout title="Founding Tailors Leaderboard">
  <main class="mx-auto max-w-2xl px-4 py-10">
    <h1 class="font-serif text-3xl text-indigo-500">Founding Tailors</h1>
    <p class="mt-2 text-ink/70">Refer other serious tailors. The top 3 each month win a free customized StitchPad shirt.</p>

    <p id="updated" class="mt-4 text-sm text-ink/50"></p>
    <div id="you" class="mt-4 hidden rounded-xl bg-indigo-500/10 p-4 font-medium text-indigo-500"></div>

    <ol id="board" class="mt-6 space-y-2" aria-live="polite"></ol>
    <p id="empty" class="mt-6 hidden text-ink/60">No points yet this month. Be the first to refer a tailor.</p>
    <p id="error" class="mt-6 hidden text-sienna-500">Could not load the leaderboard. Please try again.</p>
  </main>

  <script>
    import { fetchLeaderboard } from '../lib/foundingTailors';
    const code = new URLSearchParams(location.search).get('code');
    const $ = (id: string) => document.getElementById(id)!;
    fetchLeaderboard(code).then((data) => {
      $('updated').textContent = data.updatedAt
        ? `Updated ${new Date(data.updatedAt).toLocaleDateString('en-NG', { day: 'numeric', month: 'short' })}`
        : '';
      const board = $('board');
      if (data.top.length === 0) $('empty').classList.remove('hidden');
      data.top.forEach((row) => {
        const li = document.createElement('li');
        const mine = data.you && data.you.rank === row.rank;
        li.className = `flex items-center justify-between rounded-xl border p-3 ${mine ? 'border-indigo-500 bg-indigo-500/5' : 'border-ink/10'}`;
        li.innerHTML = `<span class="flex items-center gap-3"><span class="w-6 font-mono text-ink/50">${row.rank}</span><span class="font-medium">${escapeHtml(row.name)}</span></span><span class="font-mono text-indigo-500">${row.points}</span>`;
        board.appendChild(li);
      });
      if (data.you) {
        const y = $('you');
        y.textContent = data.you.rank > 0
          ? `You are number ${data.you.rank} with ${data.you.points} point${data.you.points === 1 ? '' : 's'} this month.`
          : 'You have no points yet this month. Share your link to get started.';
        y.classList.remove('hidden');
      }
    }).catch(() => $('error').classList.remove('hidden'));

    function escapeHtml(s: string) {
      return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!));
    }
  </script>
</BaseLayout>
```

If the exact Tailwind token classes (`text-ink`, `bg-indigo-500`) differ in this repo, match the names used in an existing page such as `src/pages/upgrade.astro`.

- [ ] **Step 3: Verify locally**

Run: `npm run dev` and open `http://localhost:4321/founding-tailors` and `.../founding-tailors?code=<a real minted code>`.
Expected: board renders; with a valid code the "You are number N" banner shows and the matching row is highlighted; unknown code shows no banner; a functions error shows the error line, not a blank page.

- [ ] **Step 4: Build gate + commit (in the web repo)**

```bash
npm run build   # static build must pass; keeps CSP/lighthouse config intact
git add src/lib/foundingTailors.ts src/pages/founding-tailors.astro
git commit -m "feat: Founding Tailors leaderboard page"
```

---

## Phase 1b — App

### Task 6: Add `referralCode` to `User` (model + DTO + mapper)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/User.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/UserDto.kt`
- Modify: the User mapper (find with `grep -rl "fun UserDto.toUser\|fun UserDto.toDomain" composeApp/src`)
- Test: the mapper's test (find with `grep -rl "UserMapper\|toUser" composeApp/src/*/kotlin` under a `test` source set)

**Interfaces:**
- Produces: `User.referralCode: String?` (null until minted), read from `users/{uid}.referralCode`. Consumed by Task 8.

- [ ] **Step 1: Write the failing mapper test**

Add to the existing User mapper test suite (mirror its style; backtick names use letters/digits/spaces/hyphens only):

```kotlin
@Test
fun `maps referralCode from dto to domain`() {
    val dto = UserDto(id = "u1", email = "a@b.com", displayName = "Ada", referralCode = "CODE0")
    val user = dto.toUser()
    assertThat(user.referralCode).isEqualTo("CODE0")
}

@Test
fun `referralCode is null when absent`() {
    val dto = UserDto(id = "u1", email = "a@b.com", displayName = "Ada")
    assertThat(dto.toUser().referralCode).isNull()
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*UserMapper*"` (adjust class filter to the actual test class)
Expected: FAIL — `referralCode` unresolved.

- [ ] **Step 3: Add the field in three places**

`User.kt` — add after `bankAccountNumber` group:
```kotlin
    /** The tailor's own outbound referral code for the Founding Tailors program.
     *  Null until first minted server-side via getOrCreateMyReferralLink. */
    val referralCode: String? = null,
```
`UserDto.kt` — add:
```kotlin
    @SerialName("referralCode")
    val referralCode: String? = null,
```
Mapper — add `referralCode = referralCode,` to the `toUser()` construction.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*UserMapper*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/User.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/UserDto.kt
git add -A composeApp/src   # mapper + its test
git commit -m "feat(user): read own referralCode from user doc"
```

---

### Task 7: `getOrCreateMyReferralLink()` in the referral repository

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/domain/ReferralRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/referral/data/CloudFunctionsReferralRepository.kt`
- Test: the existing repository test (find with `grep -rl "CloudFunctionsReferralRepository\|ReferralRepository" composeApp/src` under `commonTest`)

**Interfaces:**
- Consumes: the `getOrCreateMyReferralLink` callable (Task 2); the app's GitLive `Functions` wrapper used by `recordAttribution` in `CloudFunctionsReferralRepository`.
- Produces: `suspend fun getOrCreateMyReferralLink(): Result<ReferralLink, DataError.Network>` where `ReferralLink(code, url, playUrl)`. Consumed by Task 8.

- [ ] **Step 1: Write the failing test**

Mirror the existing referral-repo test (fake `Functions`/callable). Assert the DTO maps to `ReferralLink` and a thrown call maps to `Result.Error(DataError.Network)`:

```kotlin
@Test
fun `returns referral link on success`() = runTest {
    val repo = CloudFunctionsReferralRepository(functions = fakeFunctionsReturning(
        mapOf("code" to "CODE0", "url" to "https://link.getstitchpad.com/r/CODE0",
              "playUrl" to "https://play.google.com/store/apps/details?id=com.danzucker.stitchpad&referrer=ref%3DCODE0")
    ))
    val result = repo.getOrCreateMyReferralLink()
    assertThat(result).isEqualTo(Result.Success(ReferralLink(
        code = "CODE0", url = "https://link.getstitchpad.com/r/CODE0",
        playUrl = "https://play.google.com/store/apps/details?id=com.danzucker.stitchpad&referrer=ref%3DCODE0")))
}

@Test
fun `maps a thrown call to a network error`() = runTest {
    val repo = CloudFunctionsReferralRepository(functions = fakeFunctionsThatThrows())
    assertThat(repo.getOrCreateMyReferralLink()).isEqualTo(Result.Error(DataError.Network.UNKNOWN))
}
```

Match the actual constructor/fake shape used by the existing `recordAttribution` tests in this file.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReferralRepository*"`
Expected: FAIL — `getOrCreateMyReferralLink`/`ReferralLink` unresolved.

- [ ] **Step 3: Implement**

Add the model + interface method + implementation. In `ReferralRepository.kt`:
```kotlin
data class ReferralLink(val code: String, val url: String, val playUrl: String)

interface ReferralRepository {
    // ...existing recordAttribution(...)...
    suspend fun getOrCreateMyReferralLink(): Result<ReferralLink, DataError.Network>
}
```
In `CloudFunctionsReferralRepository.kt`, mirror the existing `recordAttribution` call (same GitLive `functions.httpsCallable("...")` + `runCatching` → `Result` mapping the app already uses):
```kotlin
override suspend fun getOrCreateMyReferralLink(): Result<ReferralLink, DataError.Network> =
    runCatching {
        val data = functions
            .httpsCallable("getOrCreateMyReferralLink")
            .invoke()                              // no args; server reads the authed uid
            .data<MyReferralLinkDto>()
        ReferralLink(code = data.code, url = data.url, playUrl = data.playUrl)
    }.fold(
        onSuccess = { Result.Success(it) },
        onFailure = { Result.Error(DataError.Network.UNKNOWN) },
    )
```
Add the DTO next to the existing referral DTOs in the data package:
```kotlin
@Serializable
data class MyReferralLinkDto(val code: String = "", val url: String = "", val playUrl: String = "")
```
Match the exact GitLive invocation/deserialization style already present for `recordReferralAttribution` (e.g. `.data<...>()` vs `.data(serializer)`), and use the `@Serializable` typed DTO — never `Map<String, Any?>` (crashes on iOS).

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ReferralRepository*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A composeApp/src/commonMain composeApp/src/commonTest
git commit -m "feat(referral): app getOrCreateMyReferralLink repository method"
```

---

### Task 8: Founding Tailors screen (ViewModel + State/Action/Event)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/foundingtailors/presentation/FoundingTailorsViewModel.kt`
- Create: `.../feature/foundingtailors/presentation/FoundingTailorsContract.kt` (State, Action, Event)
- Modify: DI module for the feature (mirror an existing `viewModelOf` registration)
- Test: `composeApp/src/commonTest/.../feature/foundingtailors/FoundingTailorsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReferralRepository.getOrCreateMyReferralLink()` (Task 7); `UserRepository.observeUser` for the existing `referralCode` (Task 6); `AuthRepository.getCurrentUser()`.
- Produces: `FoundingTailorsState(isLoading, referralUrl: String?, error: UiText?)`, `FoundingTailorsAction { LoadLink, ShareLink, OpenLeaderboard }`, `FoundingTailorsEvent { ShareText(String), OpenUrl(String) }`. The leaderboard URL is `https://getstitchpad.com/founding-tailors?code=<code>`.

- [ ] **Step 1: Write the failing ViewModel test**

Mirror an existing ViewModel test (UnconfinedTestDispatcher + Turbine). Cover: on `LoadLink` with an existing `referralCode` it does NOT call the mint and exposes the URL; on `LoadLink` with a null code it calls `getOrCreateMyReferralLink`; `OpenLeaderboard` emits `OpenUrl` with the `?code=` URL; `ShareLink` emits `ShareText`.

```kotlin
@Test
fun `LoadLink uses existing referral code without minting`() = runTest {
    val repo = FakeReferralRepository()          // fail if getOrCreateMyReferralLink is called
    val vm = FoundingTailorsViewModel(repo, userWith(referralCode = "CODE0"), authWithUid("u1"))
    vm.onAction(FoundingTailorsAction.LoadLink)
    vm.state.test { assertThat(awaitItem().referralUrl).isEqualTo("https://link.getstitchpad.com/r/CODE0") }
}

@Test
fun `OpenLeaderboard emits OpenUrl carrying the code`() = runTest {
    val vm = FoundingTailorsViewModel(FakeReferralRepository(), userWith("CODE0"), authWithUid("u1"))
    vm.onAction(FoundingTailorsAction.LoadLink)
    vm.events.test {
        vm.onAction(FoundingTailorsAction.OpenLeaderboard)
        assertThat(awaitItem()).isEqualTo(
            FoundingTailorsEvent.OpenUrl("https://getstitchpad.com/founding-tailors?code=CODE0"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*FoundingTailorsViewModel*"`
Expected: FAIL — classes unresolved.

- [ ] **Step 3: Implement contract + ViewModel**

`FoundingTailorsContract.kt`:
```kotlin
data class FoundingTailorsState(
    val isLoading: Boolean = false,
    val referralUrl: String? = null,
    val error: UiText? = null,
)
sealed interface FoundingTailorsAction {
    data object LoadLink : FoundingTailorsAction
    data object ShareLink : FoundingTailorsAction
    data object OpenLeaderboard : FoundingTailorsAction
}
sealed interface FoundingTailorsEvent {
    data class ShareText(val text: String) : FoundingTailorsEvent
    data class OpenUrl(val url: String) : FoundingTailorsEvent
}
```
`FoundingTailorsViewModel.kt` — expose `state: StateFlow` + `events: Flow` (via the app's existing `Channel`/`ObserveAsEvents` pattern). On `LoadLink`: read the current user's `referralCode`; if present, build the link locally; if null, call `getOrCreateMyReferralLink()` and map `Result.Error` to a `UiText` via the existing `toUiText()`. Hold the resolved `code` to build:
- share text (no em dashes) using a `compose.resources` string with the link substituted,
- leaderboard URL `"https://getstitchpad.com/founding-tailors?code=$code"`.

Keep all state in the ViewModel; no business logic in composables.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*FoundingTailorsViewModel*"`
Expected: PASS.

- [ ] **Step 5: Register DI + commit**

Add `viewModelOf(::FoundingTailorsViewModel)` to the feature's Koin module (mirror an existing `viewModelOf` registration and ensure the module is assembled in the app module list).

```bash
git add -A composeApp/src/commonMain composeApp/src/commonTest
git commit -m "feat(founding-tailors): screen ViewModel + contract"
```

---

### Task 9: Founding Tailors UI + entry points (Root/Screen, dashboard card, Settings)

**Files:**
- Create: `.../feature/foundingtailors/presentation/FoundingTailorsRoot.kt` + `FoundingTailorsScreen.kt`
- Modify: navigation graph (add a `@Serializable` route + composable destination; mirror an existing feature route)
- Modify: the dashboard (add a "Founding Tailors" card that navigates to the route) and the Settings list (add an entry)
- Add: `compose.resources` strings for all labels/share text (no hardcoded strings, no em dashes)

**Interfaces:**
- Consumes: `FoundingTailorsViewModel` + contract (Task 8); the existing `LocalUriHandler` pattern for opening URLs and the existing WhatsApp/share pattern (`core/sharing` / `buildWhatsAppUrl`) for `ShareText`.
- Produces: a reachable Founding Tailors screen; a dashboard card; a Settings entry.

- [ ] **Step 1: Add string resources**

In the shared strings file add (exact keys are yours; no em dashes):
```xml
<string name="founding_tailors_title">Founding Tailors</string>
<string name="founding_tailors_subtitle">Refer other tailors. Top 3 each month win a free StitchPad shirt.</string>
<string name="founding_tailors_share_cta">Share my invite link</string>
<string name="founding_tailors_view_board">View leaderboard</string>
<string name="founding_tailors_share_text">Join me on StitchPad, the app for tailors. Use my link: %1$s</string>
```

- [ ] **Step 2: Build the stateless Screen + a Preview**

`FoundingTailorsScreen.kt` — stateless: takes `state: FoundingTailorsState` + `onAction: (FoundingTailorsAction) -> Unit`. Renders the subtitle, a share button (`ShareLink`), a "View leaderboard" button (`OpenLeaderboard`), a loading state, and an error slot. MUST include a `@Preview` with a sample state (`referralUrl = "https://link.getstitchpad.com/r/CODE0"`).

- [ ] **Step 3: Build the Root (wires ViewModel + events)**

`FoundingTailorsRoot.kt` — `koinViewModel()`, collects state, and via the app's `ObserveAsEvents` handles:
- `FoundingTailorsEvent.OpenUrl(url)` → `uriHandler.openUri(url)` (the existing pattern; opens the system browser),
- `FoundingTailorsEvent.ShareText(text)` → the existing WhatsApp/share launcher.
Trigger `LoadLink` once on first composition (`LaunchedEffect(Unit)`).

- [ ] **Step 4: Wire navigation + entry points**

- Add a `@Serializable data object FoundingTailorsRoute` and a `composable<FoundingTailorsRoute> { FoundingTailorsRoot(...) }` destination (mirror an existing feature route).
- Add a Settings entry that navigates to `FoundingTailorsRoute`.
- Add a dashboard "Founding Tailors" card that navigates to `FoundingTailorsRoute` (follow the dashboard's existing card/section composition; keep it within the `Scaffold`).

- [ ] **Step 5: Build + previews render**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL. Confirm the `@Preview` renders in the IDE.

- [ ] **Step 6: Detekt + commit**

Run: `./gradlew detekt` (if the screen file trips `TooManyFunctions` from multiple previews, add `@file:Suppress("TooManyFunctions")` per the project pattern).
```bash
git add -A composeApp/src
git commit -m "feat(founding-tailors): screen UI, dashboard card, settings entry, navigation"
```

- [ ] **Step 7: QA smoke steps (Daniel, manual — Android + iOS)**

1. Open Settings (and the dashboard card) → Founding Tailors opens.
2. First open on a fresh account: link is created and shown; "Share my invite link" opens WhatsApp with the message + link (no em dashes).
3. "View leaderboard" opens `getstitchpad.com/founding-tailors?code=...` in the browser; your row/banner is highlighted once you have a point.
4. Reopen: no second code is minted (same link).
5. Repeat on iOS (share sheet + leaderboard link both work).

---

## Deploy & rollout order

1. Deploy functions first: `cd functions && npm run lint && firebase deploy --only functions:reconcileReferrals,functions:getOrCreateMyReferralLink,functions:aggregateFoundingTailorsLeaderboard,functions:getFoundingTailorsLeaderboard` (confirm all four are in the `deploy` allow-list).
2. Let one `aggregateFoundingTailorsLeaderboard` run (or invoke a one-off) so `leaderboards/current` exists before the page is shared.
3. Deploy the web page (Vercel via the `stitchpad-web` repo push).
4. Ship the app change through the normal PR + store pipeline.
5. Announce in the community with the launch message from the spec.

---

## Self-review notes (coverage vs spec)

- B1 link mint → Task 2. B2 aggregator → Task 3. B3 read callable → Task 4. B4 `qualifiedAt` → Task 1. W1 page → Task 5. A1 `referralCode` → Task 6. A2 surface → Tasks 7-9.
- D1 self-serve → Task 2. D2 payout-disabled (`payoutRatePerUser:0` + `program` marker) → Task 2 (+ enforced by `gradeReferral` rate gate). D3 business-name-first → Task 2 `name` selection. D4 bucket by `qualifiedAt` month → Tasks 1 + 3. D5 `/founding-tailors` route/name → Tasks 5, 8.
- Anti-gaming: reuses `qualified` + `hasBlockingFlag` (Tasks 3). No new public Firestore read rule needed — the page reads only via the callable, and the aggregator/read run under the Admin SDK (bypasses rules).
