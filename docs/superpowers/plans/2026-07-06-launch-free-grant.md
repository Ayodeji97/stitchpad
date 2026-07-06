# Launch-Free Grant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make StitchPad free-for-everyone through 2026 by placing every existing and new user on the top (`atelier`) tier — tagged as a reversible promo grant — so no one hits a paywall, with zero app release.

**Architecture:** Entitlements are derived live from `users/{uid}.subscriptionTier`, so this is a **server + data change only**. Two pieces: (1) a one-off admin **backfill script** that grants all existing users, and (2) an **`onCreate` Cloud Function** that grants every new signup, gated by a Firestore `config/app.launchFreeGrantEnabled` flag so it can be turned off in January without a redeploy. Both share one pure module defining the grant shape and the "skip active paid subscribers" predicate. Every grant carries `grantSource: "launch_free"` + `grantedAt` so the November 2026 pricing review can find promo users precisely and leave real Apple/Paystack/gift/tester subscribers untouched.

**Tech Stack:** TypeScript Cloud Functions (`firebase-functions/v1`, Node 20, `firebase-admin`), Jest + ts-jest, plain-Node admin script.

## Global Constraints

- **Region:** all functions deploy to `europe-west1` (match existing exports).
- **Functions API:** `firebase-functions/v1` (this project pins v1 — see `index.ts`).
- **Deploy allow-list:** any new function MUST be added to `functions/package.json` `deploy --only` list or it is **silently skipped** on deploy.
- **CI order:** `functions-tests` runs `npm run lint` (ESLint, **single-quote** style) BEFORE jest — run lint for every functions change.
- **Grant field shape is defined once** in `functions/src/freemium/launchGrant.ts`; the backfill JS script MUST mirror it exactly.
- **Never touch an active paid subscriber.** Skip any doc where `subscriptionTier ∈ {pro, atelier}` AND `subscriptionStatus === 'active'` (covers real Apple/Paystack/gift subscribers and manual tester grants).
- **Grant shape must be cron-invisible:** omit `subscriptionEndsAt`, `subscriptionRenews`, and `subscriptionSource`. Every billing cron (`expirePrepaidSubscriptions`, `prepaidSubscriptionReminder`, `reconcileAppleSubscriptions`) filters on those fields, so omitting them means nothing can expire or email a promo user.
- **No app release required.** Do not modify `composeApp/` — the installed app reads `subscriptionTier` live and already treats `atelier` as unlimited.

---

## File Structure

- `functions/src/freemium/launchGrant.ts` — **new.** Pure module: grant constants, `shouldGrantLaunchFree(data)` predicate, `buildLaunchGrantFields(now)` field builder. Single source of truth for the grant.
- `functions/src/freemium/onUserCreated.ts` — **new.** Pure `handleUserCreated(deps, uid, data)` + the production `grantLaunchFreeOnSignup` onCreate trigger (reads the `config/app` flag, writes the grant).
- `functions/src/__tests__/freemium/launchGrant.test.ts` — **new.** Unit tests for predicate + field shape.
- `functions/src/__tests__/freemium/onUserCreated.test.ts` — **new.** Unit tests for the handler (flag on/off, skip-managed, write).
- `functions/src/index.ts` — **modify.** Export `grantLaunchFreeOnSignup`.
- `functions/package.json` — **modify.** Add `functions:grantLaunchFreeOnSignup` to the `deploy` allow-list.
- `functions/scripts/backfillLaunchFree.js` — **new.** One-off admin backfill (dry-run default, `--commit` to write), mirrors `setAdminClaim.js` conventions.

---

### Task 1: Shared launch-grant module (pure)

**Files:**
- Create: `functions/src/freemium/launchGrant.ts`
- Test: `functions/src/__tests__/freemium/launchGrant.test.ts`

**Interfaces:**
- Produces:
  - `const LAUNCH_GRANT_SOURCE = 'launch_free'`
  - `const LAUNCH_GRANT_TIER = 'atelier'`
  - `interface UserSubscriptionFields { subscriptionTier?: string; subscriptionStatus?: string }`
  - `shouldGrantLaunchFree(user: UserSubscriptionFields | undefined): boolean`
  - `buildLaunchGrantFields(now: Date): { subscriptionTier: string; subscriptionStatus: string; grantSource: string; grantedAt: admin.firestore.Timestamp; updatedAt: admin.firestore.Timestamp }`

- [ ] **Step 1: Write the failing test**

Create `functions/src/__tests__/freemium/launchGrant.test.ts`:

```ts
import {
  shouldGrantLaunchFree,
  buildLaunchGrantFields,
  LAUNCH_GRANT_SOURCE,
} from '../../freemium/launchGrant';

describe('shouldGrantLaunchFree', () => {
  it('grants a brand-new doc with no subscription fields', () => {
    expect(shouldGrantLaunchFree(undefined)).toBe(true);
    expect(shouldGrantLaunchFree({})).toBe(true);
  });

  it('grants an active free user', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'free', subscriptionStatus: 'active' }),
    ).toBe(true);
  });

  it('grants an expired former subscriber', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'pro', subscriptionStatus: 'expired' }),
    ).toBe(true);
  });

  it('skips an active paid subscriber (real payer / tester / gift)', () => {
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'pro', subscriptionStatus: 'active' }),
    ).toBe(false);
    expect(
      shouldGrantLaunchFree({ subscriptionTier: 'atelier', subscriptionStatus: 'active' }),
    ).toBe(false);
  });
});

describe('buildLaunchGrantFields', () => {
  it('sets tier=atelier + active + tag, and omits expiry/renew/source', () => {
    const fields = buildLaunchGrantFields(new Date('2026-07-06T00:00:00Z'));
    expect(fields.subscriptionTier).toBe('atelier');
    expect(fields.subscriptionStatus).toBe('active');
    expect(fields.grantSource).toBe(LAUNCH_GRANT_SOURCE);
    expect(fields).not.toHaveProperty('subscriptionEndsAt');
    expect(fields).not.toHaveProperty('subscriptionRenews');
    expect(fields).not.toHaveProperty('subscriptionSource');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest src/__tests__/freemium/launchGrant.test.ts`
Expected: FAIL — `Cannot find module '../../freemium/launchGrant'`.

- [ ] **Step 3: Write minimal implementation**

Create `functions/src/freemium/launchGrant.ts`:

```ts
import * as admin from 'firebase-admin';

/**
 * Marker written to every promo grant so the November 2026 pricing review can
 * find launch-free users precisely and leave real Apple/Paystack/gift payers
 * (and manual tester grants) alone.
 */
export const LAUNCH_GRANT_SOURCE = 'launch_free';

/** Top tier — unlocks every entitlement cap for the free-for-everyone period. */
export const LAUNCH_GRANT_TIER = 'atelier';

export interface UserSubscriptionFields {
  subscriptionTier?: string;
  subscriptionStatus?: string;
}

/**
 * True when a user should receive the launch-free grant.
 *
 * Skips anyone already on an ACTIVE paid tier — that is real Apple/Paystack
 * subscribers, gift recipients, and manual tester grants, none of which we want
 * to relabel as `launch_free`. Everyone else (free, expired, or brand-new docs
 * with no subscription fields) is granted.
 *
 * A launch grant itself writes tier=atelier + status=active, so this predicate
 * also makes the backfill idempotent: a second run sees the granted user as
 * active-paid and skips it, preserving the original grantedAt.
 */
export function shouldGrantLaunchFree(user: UserSubscriptionFields | undefined): boolean {
  const tier = user?.subscriptionTier;
  const isPaidTier = tier === 'pro' || tier === 'atelier';
  const isActive = user?.subscriptionStatus === 'active';
  return !(isPaidTier && isActive);
}

/**
 * The exact fields a launch grant writes (merge-set).
 *
 * Deliberately omits subscriptionEndsAt, subscriptionRenews and
 * subscriptionSource:
 *   - no endsAt  -> Settings shows no expiry line (resolveSubscriptionStatus
 *                   returns null on a null endsAt) and the app treats it as an
 *                   open-ended grant.
 *   - no renews / endsAt / source -> invisible to expirePrepaidSubscriptions,
 *                   prepaidSubscriptionReminder and reconcileAppleSubscriptions,
 *                   which all filter on those fields. Nothing can expire it.
 */
export function buildLaunchGrantFields(now: Date) {
  const ts = admin.firestore.Timestamp.fromDate(now);
  return {
    subscriptionTier: LAUNCH_GRANT_TIER,
    subscriptionStatus: 'active',
    grantSource: LAUNCH_GRANT_SOURCE,
    grantedAt: ts,
    updatedAt: ts,
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest src/__tests__/freemium/launchGrant.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 5: Lint**

Run: `cd functions && npm run lint`
Expected: no errors (single-quote style, no unused imports).

- [ ] **Step 6: Commit**

```bash
git add functions/src/freemium/launchGrant.ts functions/src/__tests__/freemium/launchGrant.test.ts
git commit -m "feat(freemium): launch-free grant module (shape + skip-payer predicate)"
```

---

### Task 2: onCreate trigger — grant every new signup

**Files:**
- Create: `functions/src/freemium/onUserCreated.ts`
- Test: `functions/src/__tests__/freemium/onUserCreated.test.ts`
- Modify: `functions/src/index.ts` (add export)
- Modify: `functions/package.json` (add to deploy allow-list)

**Interfaces:**
- Consumes (from Task 1): `buildLaunchGrantFields`, `shouldGrantLaunchFree`, `UserSubscriptionFields`.
- Produces:
  - `type GrantOutcome = 'granted' | 'skipped-disabled' | 'skipped-managed'`
  - `interface UserCreatedDeps { isGrantEnabled: () => Promise<boolean>; writeGrant: (uid: string, now: Date) => Promise<void>; now: () => Date }`
  - `handleUserCreated(deps: UserCreatedDeps, uid: string, data: UserSubscriptionFields | undefined): Promise<GrantOutcome>`
  - `const grantLaunchFreeOnSignup` (Firestore onCreate trigger on `users/{uid}`)

- [ ] **Step 1: Write the failing test**

Create `functions/src/__tests__/freemium/onUserCreated.test.ts`:

```ts
import { handleUserCreated, UserCreatedDeps } from '../../freemium/onUserCreated';

function makeDeps(overrides: Partial<UserCreatedDeps> = {}): {
  deps: UserCreatedDeps;
  writes: string[];
} {
  const writes: string[] = [];
  const deps: UserCreatedDeps = {
    isGrantEnabled: async () => true,
    writeGrant: async (uid) => {
      writes.push(uid);
    },
    now: () => new Date('2026-07-06T00:00:00Z'),
    ...overrides,
  };
  return { deps, writes };
}

describe('handleUserCreated', () => {
  it('grants a brand-new free user when the flag is on', async () => {
    const { deps, writes } = makeDeps();
    const outcome = await handleUserCreated(deps, 'uid-1', {});
    expect(outcome).toBe('granted');
    expect(writes).toEqual(['uid-1']);
  });

  it('skips (no write) when the flag is off', async () => {
    const { deps, writes } = makeDeps({ isGrantEnabled: async () => false });
    const outcome = await handleUserCreated(deps, 'uid-1', {});
    expect(outcome).toBe('skipped-disabled');
    expect(writes).toEqual([]);
  });

  it('skips (no write) an active paid subscriber', async () => {
    const { deps, writes } = makeDeps();
    const outcome = await handleUserCreated(deps, 'uid-1', {
      subscriptionTier: 'pro',
      subscriptionStatus: 'active',
    });
    expect(outcome).toBe('skipped-managed');
    expect(writes).toEqual([]);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest src/__tests__/freemium/onUserCreated.test.ts`
Expected: FAIL — `Cannot find module '../../freemium/onUserCreated'`.

- [ ] **Step 3: Write minimal implementation**

Create `functions/src/freemium/onUserCreated.ts`:

```ts
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import {
  buildLaunchGrantFields,
  shouldGrantLaunchFree,
  UserSubscriptionFields,
} from './launchGrant';

const REGION = 'europe-west1';

export type GrantOutcome = 'granted' | 'skipped-disabled' | 'skipped-managed';

export interface UserCreatedDeps {
  isGrantEnabled: () => Promise<boolean>;
  writeGrant: (uid: string, now: Date) => Promise<void>;
  now: () => Date;
}

/**
 * Pure handler: decides and (optionally) applies the launch grant for one new
 * user doc. Production wraps this in the onCreate trigger below; tests drive it
 * with fakes.
 */
export async function handleUserCreated(
  deps: UserCreatedDeps,
  uid: string,
  data: UserSubscriptionFields | undefined,
): Promise<GrantOutcome> {
  if (!(await deps.isGrantEnabled())) {
    return 'skipped-disabled';
  }
  if (!shouldGrantLaunchFree(data)) {
    return 'skipped-managed';
  }
  await deps.writeGrant(uid, deps.now());
  return 'granted';
}

function productionDeps(): UserCreatedDeps {
  const db = admin.firestore();
  return {
    isGrantEnabled: async () => {
      const snap = await db.doc('config/app').get();
      return snap.get('launchFreeGrantEnabled') === true;
    },
    writeGrant: async (uid, now) => {
      await db.doc(`users/${uid}`).set(buildLaunchGrantFields(now), { merge: true });
    },
    now: () => new Date(),
  };
}

/**
 * Grants the launch-free entitlement to every newly-created user doc while
 * config/app.launchFreeGrantEnabled === true. Writing back to users/{uid} does
 * NOT re-trigger onCreate, so there is no loop. Turn the promo off in January by
 * setting the flag false (no redeploy needed).
 */
export const grantLaunchFreeOnSignup = functions
  .region(REGION)
  .firestore.document('users/{uid}')
  .onCreate(async (snap, context) => {
    const uid = context.params.uid as string;
    const outcome = await handleUserCreated(
      productionDeps(),
      uid,
      snap.data() as UserSubscriptionFields | undefined,
    );
    functions.logger.info('launch-free grant', { uid, outcome });
  });
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd functions && npx jest src/__tests__/freemium/onUserCreated.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Export the trigger from `index.ts`**

In `functions/src/index.ts`, add after the last `export { ... }` line (the `dailyOnboardingMetrics` export):

```ts
export { grantLaunchFreeOnSignup } from './freemium/onUserCreated';
```

- [ ] **Step 6: Add to the deploy allow-list in `package.json`**

In `functions/package.json`, in the `"deploy"` script, append `,functions:grantLaunchFreeOnSignup` to the end of the `--only` list (after `functions:debugRunOnboardingMetrics`). The tail becomes:

```
...,functions:dailyOnboardingMetrics,functions:debugRunOnboardingMetrics,functions:grantLaunchFreeOnSignup"
```

- [ ] **Step 7: Build + lint + full test to verify nothing else broke**

Run: `cd functions && npm run build && npm run lint && npm test`
Expected: `tsc` clean, lint clean, all jest suites PASS.

- [ ] **Step 8: Commit**

```bash
git add functions/src/freemium/onUserCreated.ts \
        functions/src/__tests__/freemium/onUserCreated.test.ts \
        functions/src/index.ts functions/package.json
git commit -m "feat(freemium): grant launch-free tier on signup, gated by config flag"
```

---

### Task 3: Backfill script for existing users

**Files:**
- Create: `functions/scripts/backfillLaunchFree.js`

**Interfaces:**
- Consumes: the same grant shape + predicate as Task 1 (mirrored in plain JS — this is a standalone Node script, not part of the TS build).
- Produces: a runnable CLI — dry-run by default, `--commit` to write.

- [ ] **Step 1: Write the script**

Create `functions/scripts/backfillLaunchFree.js`:

```js
#!/usr/bin/env node
/*
 * One-off backfill: place every EXISTING user on the launch-free grant
 * (tier=atelier, tagged grantSource=launch_free) so no one hits a paywall
 * during the 2026 free-for-everyone period. Idempotent and safe to re-run.
 *
 * Skips anyone already on an ACTIVE paid tier — real Apple/Paystack/gift
 * subscribers and manual tester grants are left untouched.
 *
 * Field shape + predicate MUST stay in lockstep with
 * functions/src/freemium/launchGrant.ts.
 *
 * Usage:
 *   # dry run (default) — prints what WOULD change, writes nothing:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillLaunchFree.js
 *   # apply:
 *   GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillLaunchFree.js --commit
 *
 * Auth: application-default credentials with Firestore access, e.g.
 *   gcloud auth application-default login
 */
const admin = require('firebase-admin');

const LAUNCH_GRANT_SOURCE = 'launch_free';

function shouldGrant(data) {
  const tier = data.subscriptionTier;
  const isPaidTier = tier === 'pro' || tier === 'atelier';
  const isActive = data.subscriptionStatus === 'active';
  return !(isPaidTier && isActive);
}

async function main() {
  const commit = process.argv.includes('--commit');
  admin.initializeApp({ projectId: process.env.GOOGLE_CLOUD_PROJECT });
  const db = admin.firestore();

  const snap = await db.collection('users').get();
  const now = admin.firestore.Timestamp.now();

  let granted = 0;
  let skipped = 0;
  let batch = db.batch();
  let pending = 0;

  for (const doc of snap.docs) {
    if (!shouldGrant(doc.data())) {
      skipped += 1;
      continue;
    }
    granted += 1;
    if (commit) {
      batch.set(
        doc.ref,
        {
          subscriptionTier: 'atelier',
          subscriptionStatus: 'active',
          grantSource: LAUNCH_GRANT_SOURCE,
          grantedAt: now,
          updatedAt: now,
        },
        { merge: true },
      );
      pending += 1;
      if (pending === 400) {
        await batch.commit();
        batch = db.batch();
        pending = 0;
      }
    }
  }
  if (commit && pending > 0) {
    await batch.commit();
  }

  console.log(
    `${commit ? 'COMMITTED' : 'DRY RUN'} — total=${snap.size} granted=${granted} skipped=${skipped}`,
  );
  if (!commit) console.log('Re-run with --commit to apply.');
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error(err);
    process.exit(1);
  },
);
```

- [ ] **Step 2: Verify it runs as a dry run (writes nothing)**

Run:
```bash
cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillLaunchFree.js
```
Expected: prints `DRY RUN — total=42 granted=<n> skipped=<m>` (numbers depend on live data; ~37 granted / ~5 skipped given current tester/paid docs). No Firestore writes.
(If auth fails: run `gcloud auth application-default login` first.)

- [ ] **Step 3: Commit**

```bash
git add functions/scripts/backfillLaunchFree.js
git commit -m "chore(freemium): backfill script to grant launch-free to existing users"
```

---

### Task 4: Ship & operate (runbook — no new code)

Do these in order **after the PR is merged**. Each is a manual ops step; there is no test cycle, so this is a runbook rather than a TDD task.

- [ ] **Step 1: Pre-push review + open PR**

Follow the usual flow: `codex review` (pre-push hook, `-c model=gpt-5.5`) + Cursor Bugbot on the PR. Branch → PR → CI green (functions lint + tests) → merge. Do **not** push to `main` directly.

- [ ] **Step 2: Deploy the new function**

```bash
cd functions && npm run deploy
```
Confirm the deploy summary lists `grantLaunchFreeOnSignup(europe-west1)` as created. (If it is absent, the allow-list edit in Task 2 Step 6 was missed.)

- [ ] **Step 3: Turn ON new-signup grants (config flag)**

```bash
TOKEN=$(gcloud auth print-access-token)
curl -X PATCH \
  "https://firestore.googleapis.com/v1/projects/stitchpad-30607/databases/(default)/documents/config/app?updateMask.fieldPaths=launchFreeGrantEnabled" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"fields":{"launchFreeGrantEnabled":{"booleanValue":true}}}'
```
Expected: response echoes the `config/app` doc now containing `launchFreeGrantEnabled: true`.

- [ ] **Step 4: Backfill existing users — dry run, then commit**

```bash
cd functions
GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillLaunchFree.js          # review counts
GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillLaunchFree.js --commit # apply
```
Expected: `COMMITTED — total=42 granted=<n> skipped=<m>`.

- [ ] **Step 5: Verify**

Re-run the tier census and expect `free` to drop toward 0 and `atelier` to rise:
```bash
TOKEN=$(gcloud auth print-access-token)
URL="https://firestore.googleapis.com/v1/projects/stitchpad-30607/databases/(default)/documents:runAggregationQuery"
for T in free pro atelier; do
  curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -X POST "$URL" \
    -d "{\"structuredAggregationQuery\":{\"aggregations\":[{\"count\":{},\"alias\":\"c\"}],\"structuredQuery\":{\"from\":[{\"collectionId\":\"users\"}],\"where\":{\"fieldFilter\":{\"field\":{\"fieldPath\":\"subscriptionTier\"},\"op\":\"EQUAL\",\"value\":{\"stringValue\":\"$T\"}}}}}}" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print('$T', d[0]['result']['aggregateFields']['c']['integerValue'])"
done
```
Then create a fresh test signup in the app and confirm the new `users/{uid}` doc gets `subscriptionTier: atelier` + `grantSource: launch_free` within a second or two (Cloud Function logs: `firebase functions:log --only grantLaunchFreeOnSignup`).

- [ ] **Step 6: Note — iOS / existing subscribers (no action required now)**

Existing Apple subscribers are skipped by the predicate and keep auto-renewing (managed by Apple, not this code). With everyone on `atelier`, no user hits a cap, so the paywall never surfaces. Removing the in-app upgrade UI and the App Store IAP products is a **separate future app release** (fold into the January pricing decision), not part of this change.

---

## Reversal (January 2027 — out of scope, documented for the review)

When pricing returns: (1) set `config/app.launchFreeGrantEnabled = false` (new signups get Free again — no redeploy); (2) query `users` where `grantSource == 'launch_free'` to apply the chosen grandfathering (e.g. a founding-tailor discount) or drop them to Free; (3) send the email + in-app notice **before** flipping. Real Apple/Paystack/gift subscribers are untouched because they were never tagged.

## Known limitation

A user whose real gift/Paystack subscription **expires during** the promo drops to Free and would hit caps until the backfill is re-run (the onCreate trigger only fires for brand-new docs). Mitigation: the backfill is idempotent and safe — re-run it (Task 3 with `--commit`) periodically, or once, if this affects anyone. Given the tiny user base and no active sales, this is expected to affect ~0 users; not worth a scheduled job.
