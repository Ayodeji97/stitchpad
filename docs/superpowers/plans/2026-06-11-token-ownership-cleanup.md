# Push Token Ownership Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Enforce that an FCM token belongs to at most one user — when token `T` registers under user `B`, an onCreate Cloud Function deletes any `users/{X}/notificationTokens/T` for X ≠ B.

**Architecture:** Firestore `onCreate` trigger (Admin SDK) on the token doc → `collectionGroup` query for the same token → batched delete of docs owned by other uids. Pure core (`dupeTokenRefsToPrune`) behind thin Firebase wiring, matching the digest functions' testable style. Client adds a `token` field so the collectionGroup query has an indexed field.

**Tech Stack:** TypeScript Cloud Functions (`firebase-functions/v1`), Jest, Firestore collectionGroup index; Kotlin client (`FirebasePushTokenRepository`).

Design spec: `docs/superpowers/specs/2026-06-11-token-ownership-cleanup-design.md`

---

### Task 1: Pure core — `dupeTokenRefsToPrune`

**Files:**
- Create: `functions/src/notifications/tokenOwnership.ts`
- Test: `functions/src/__tests__/notifications/tokenOwnership.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// functions/src/__tests__/notifications/tokenOwnership.test.ts
import { dupeTokenRefsToPrune } from '../../notifications/tokenOwnership';

describe('dupeTokenRefsToPrune', () => {
  it('returns every doc not owned by the new owner', () => {
    expect(dupeTokenRefsToPrune('B', [
      { uid: 'A', ref: 'refA' },
      { uid: 'B', ref: 'refB' },
      { uid: 'C', ref: 'refC' },
    ])).toEqual(['refA', 'refC']);
  });

  it('returns empty when only the new owner holds the token', () => {
    expect(dupeTokenRefsToPrune('B', [{ uid: 'B', ref: 'refB' }])).toEqual([]);
  });

  it('returns empty for no docs', () => {
    expect(dupeTokenRefsToPrune('B', [])).toEqual([]);
  });
});
```

- [ ] **Step 2: Run it — expect FAIL (module not found)**

Run: `cd functions && npx jest tokenOwnership`

- [ ] **Step 3: Implement the pure function**

```ts
// functions/src/notifications/tokenOwnership.ts

/** A notificationTokens doc reduced to its owner uid and a deletable ref. */
export interface TokenDocOwner<T> {
  uid: string;
  ref: T;
}

/**
 * Refs to delete to enforce single-ownership of an FCM token: every doc whose owner
 * uid differs from `ownerUid` (the user the token was just registered under). The
 * just-created doc and any same-user dupes (uid === ownerUid) are kept.
 */
export function dupeTokenRefsToPrune<T>(ownerUid: string, docs: TokenDocOwner<T>[]): T[] {
  return docs.filter((d) => d.uid !== ownerUid).map((d) => d.ref);
}
```

- [ ] **Step 4: Run it — expect PASS**

Run: `cd functions && npx jest tokenOwnership`

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/tokenOwnership.ts functions/src/__tests__/notifications/tokenOwnership.test.ts
git commit -m "feat(notifications): pure dupeTokenRefsToPrune for token-ownership cleanup"
```

---

### Task 2: Cloud Function — `pruneTokenOwnership` onCreate trigger

**Files:**
- Create: `functions/src/notifications/pruneTokenOwnership.ts`
- Modify: `functions/src/index.ts` (export the new function — follow the existing `export { … } from './…'` pattern)
- Modify: `functions/package.json` (add `functions:pruneTokenOwnership` to the `deploy --only` list)

- [ ] **Step 1: Implement the trigger**

```ts
// functions/src/notifications/pruneTokenOwnership.ts
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { dupeTokenRefsToPrune } from './tokenOwnership';

const REGION = 'europe-west1';

/**
 * Single-ownership enforcement for FCM tokens. When a token doc is created under a uid,
 * delete the same token under any OTHER uid (stale registrations from failed sign-out /
 * reinstall / account-switch on a shared device) so one tailor's digest can't reach
 * another tailor's device. onCreate (not onWrite): a same-user re-register is an update;
 * the token appearing under a new uid is the create we act on. Deletes don't re-trigger.
 */
export const pruneTokenOwnership = functions
  .region(REGION)
  .firestore.document('users/{uid}/notificationTokens/{token}')
  .onCreate(async (_snap, context) => {
    const ownerUid = context.params.uid as string;
    const token = context.params.token as string;
    const db = admin.firestore();

    const dupes = await db
      .collectionGroup('notificationTokens')
      .where('token', '==', token)
      .get();

    const owners = dupes.docs
      .map((d) => ({ uid: d.ref.parent.parent?.id, ref: d.ref }))
      .filter((d): d is { uid: string; ref: FirebaseFirestore.DocumentReference } => !!d.uid);

    const toDelete = dupeTokenRefsToPrune(ownerUid, owners);
    if (toDelete.length === 0) return;

    const batch = db.batch();
    toDelete.forEach((ref) => batch.delete(ref));
    await batch.commit();

    functions.logger.info('token ownership: pruned stale token docs', {
      tokenPrefix: token.slice(0, 24),
      ownerUid,
      pruned: toDelete.length,
    });
  });
```

- [ ] **Step 2: Export from `functions/src/index.ts`**

Add (matching the file's existing export style):
```ts
export { pruneTokenOwnership } from './notifications/pruneTokenOwnership';
```

- [ ] **Step 3: Add to the deploy allow-list in `functions/package.json`**

Append `,functions:pruneTokenOwnership` to the existing `firebase deploy --only functions:…` chain in the `deploy` script.

- [ ] **Step 4: Lint + build**

Run: `cd functions && npm run lint && npm run build` — expect exit 0 (single-quote rule + tsc).

- [ ] **Step 5: Commit**

```bash
git add functions/src/notifications/pruneTokenOwnership.ts functions/src/index.ts functions/package.json
git commit -m "feat(notifications): pruneTokenOwnership onCreate trigger + deploy allow-list"
```

---

### Task 3: Firestore collectionGroup index for `token`

**Files:**
- Modify: `firestore.indexes.json`

- [ ] **Step 1: Add a COLLECTION_GROUP single-field index**

A `collectionGroup('notificationTokens').where('token','==',…)` query needs a
COLLECTION_GROUP-scoped single-field index. Add a `fieldOverrides` entry (create the
`fieldOverrides` array if it doesn't exist):

```json
"fieldOverrides": [
  {
    "collectionGroup": "notificationTokens",
    "fieldPath": "token",
    "indexes": [
      { "queryScope": "COLLECTION_GROUP", "order": "ASCENDING" },
      { "queryScope": "COLLECTION", "order": "ASCENDING" }
    ]
  }
]
```

- [ ] **Step 2: Validate JSON**

Run: `cd functions && node -e "require('../firestore.indexes.json')"` — expect no error (valid JSON).

- [ ] **Step 3: Commit**

```bash
git add firestore.indexes.json
git commit -m "feat(notifications): collectionGroup index on notificationTokens.token"
```

> Deploy (later, with the function): `firebase deploy --only firestore:indexes` then `npm run deploy`. Index build is async — the trigger's query errors until the index is READY, so deploy the index first.

---

### Task 4: Client — write the `token` field

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/push/FirebasePushTokenRepository.kt`

- [ ] **Step 1: Add `token` to the written map**

Change the `registerToken` data map from:
```kotlin
val data = mapOf(
    "platform" to platform,
    "updatedAt" to FieldValue.serverTimestamp,
)
```
to:
```kotlin
val data = mapOf(
    // Stored as a field (the doc id is also the token) so the server-side
    // pruneTokenOwnership trigger can collectionGroup-query by token.
    "token" to token,
    "platform" to platform,
    "updatedAt" to FieldValue.serverTimestamp,
)
```

- [ ] **Step 2: Compile iOS + Android + detekt**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest detekt --console=plain ; echo "EXIT=$?"` — expect `EXIT=0`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notification/push/FirebasePushTokenRepository.kt
git commit -m "feat(notifications): store token field on the notificationTokens doc"
```

---

### Task 5: Verify + smoke + PR

- [ ] **Step 1: Full functions check** — `cd functions && npm run lint && npm run build && npm test` (expect all green).
- [ ] **Step 2: Kotlin gate** — `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest detekt` green.
- [ ] **Step 3: Open the PR** (base `main`) with the manual smoke-test steps below; review rotation = Cursor + codex.

**Manual smoke (after deploy — index first, then functions):** real device, debug build. Sign in as A → grant push → confirm `users/A/notificationTokens/T` (has `token` field). Sign out, sign in as **B on the same device** → confirm `users/B/notificationTokens/T` created **and** `users/A/.../T` deleted. Check the function log for `token ownership: pruned stale token docs`. Trigger A's digest → B's device gets **no** push for A.

> Deploy order matters: `firebase deploy --only firestore:indexes`, wait for the index to be **Enabled** in the console, then `npm run deploy` (from the worktree). The client `token` field change ships in the app build.
