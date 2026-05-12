# `onAuthUserDeleted` Cloud Function — Design Spec

**Date:** 2026-05-12
**Branch:** `feature/auth-cleanup-function`
**Owner:** Daniel Ogunleye
**Related:** Phase 3 SSO (PR #37, merged), Settings redesign (PR #35, in flight)

## Goal

Server-side cleanup of orphan Firestore + Cloud Storage data when a Firebase Auth user is deleted, so that account deletion produces a complete privacy-compliant state without depending on the client device staying alive.

## Motivation

Phase 3 added in-app account deletion (`FirebaseAuthRepository.deleteAccount`) with auth-first ordering:

1. `user.delete()` — removes Auth account
2. `firebaseAuth.signOut()` — flushes local state
3. `runCatching { userRepository.deleteUserDoc(uid) }` — best-effort client-side Firestore cleanup of the user doc only

This client-side flow is fragile:

- The client only deletes `users/{uid}` (the parent doc), **not** the subcollections under it. Firestore does not cascade-delete subcollections — they become orphan documents indefinitely accessible via direct ref.
- The client never deletes Cloud Storage files. Fabric photos and style photos remain on Storage forever.
- If the device loses network between auth-delete and the Firestore call, even the user-doc cleanup never runs.

A server-side function triggered by `auth.user().onDelete` removes all three problems:

- Cleanup runs reliably regardless of client connectivity post-deletion.
- Cascades into subcollections via Admin SDK's `recursiveDelete`.
- Cleans Storage prefixes via `bucket.deleteFiles({ prefix })`.

PR #35 (Settings redesign, in flight) already removes the client-side cleanup in anticipation of this function existing. Once both PRs merge, the function is the sole cleanup path.

## Out of scope

- **Auth user data export before deletion** (GDPR Article 20 / "right to data portability"). Will add later if/when GDPR applies to the user base.
- **Retroactive cleanup of orphan data** from previously-deleted test accounts. We chose `onAuthUserDeleted` only; a separate ad-hoc admin script can be written if old orphans become a problem.
- **Account-deletion confirmation email**. Out of V1 scope; Firebase Auth itself doesn't send one.
- **Soft-delete / tombstone pattern** (mark account as deleted but retain data for N days). V1 is hard-delete only, matching PR #35's UX.
- **CI auto-deploy of functions.** V1 is manual `firebase deploy`. CI deploy requires storing a service-account secret; deferred until we have more than one function.
- **Firebase Local Emulator Suite integration tests.** Chose Jest unit tests + manual smoke; emulator setup is deferred.

## Architecture

### One Cloud Function, Gen 1, TypeScript, region `europe-west1`

**Why Gen 1:** The `functions.auth.user().onDelete()` trigger exists only in Gen 1. Gen 2 has blocking identity triggers (`beforeCreate`, `beforeSignIn`) but no `onDelete`. This is a Firebase capability constraint, not a stylistic choice. Future non-Auth functions can/should be Gen 2.

**Why `europe-west1`:** Firestore database is `europe-west1`. Co-locating the function avoids cross-region read/write latency and egress costs. Cloud Storage default bucket is in the project default region (also europe-west1 effectively for this project).

**Why TypeScript:** Type safety on `admin.firestore()` / `admin.storage()` APIs; matches PR #35's design note ("Cloud Function-bound delete (TypeScript / Firebase Functions)").

### Project structure

```
functions/
├── package.json
├── package-lock.json
├── tsconfig.json
├── tsconfig.dev.json
├── .eslintrc.js
├── .gitignore           // node_modules, lib/, *.log
├── src/
│   ├── index.ts         // export const onAuthUserDeleted = ...
│   └── cleanup/
│       ├── firestore.ts // deleteUserFirestoreData(uid, db)
│       └── storage.ts   // deleteUserStorageData(uid, bucket)
└── src/__tests__/
    ├── index.test.ts        // trigger wiring
    ├── firestore.test.ts
    └── storage.test.ts
```

Plus at repo root:

- `firebase.json` — declares the functions codebase
- `.firebaserc` — pins `stitchpad-30607` as the default project

## Components and data flow

```
Firebase Auth (user.delete())
        │
        │  auth.user().onDelete trigger
        ▼
onAuthUserDeleted(user: UserRecord)
        │
        ├──> deleteUserFirestoreData(user.uid, db)
        │    ├── recursiveDelete(users/{uid}/customers)
        │    ├── recursiveDelete(users/{uid}/orders)
        │    ├── recursiveDelete(users/{uid}/measurements)
        │    ├── recursiveDelete(users/{uid}/styles)
        │    ├── recursiveDelete(users/{uid}/goals)
        │    └── users/{uid}.delete()
        │
        └──> deleteUserStorageData(user.uid, bucket)
             └── bucket.deleteFiles({ prefix: `users/${uid}/` })
```

Both branches run via `Promise.allSettled` so that a failure in one does not prevent the other from running. Each branch wraps its own try/catch and logs via `functions.logger.error` on failure.

The function returns `Promise<void>` with no thrown errors. We **never** propagate failures, because Cloud Functions auto-retries on throw — and a permanently-failing branch (e.g., a Storage permission edge case) would burn retries indefinitely. Best-effort matches Phase 3's `runCatching` ordering philosophy.

## Cleanup contract

### Firestore — recursive delete `users/{uid}/`

Uses Admin SDK's `db.recursiveDelete(docRef)` which handles batching, pagination, and rate limiting automatically. Caller does not need to enumerate documents.

Subcollections to delete (sourced from current codebase grep at 2026-05-12):

- `customers`
- `orders`
- `measurements`
- `styles`
- `goals`

We delete each subcollection explicitly by name (rather than discovering at runtime), so that future subcollections added by app features don't get silently cleaned up before this function is updated. This is intentional — a new subcollection should be explicitly added to the cleanup list, with a code review acknowledging the data-deletion consequence.

After subcollections are cleaned, delete the `users/{uid}` doc itself.

**Drift-warning belt-and-suspenders:** Immediately before cleaning, the function lists all subcollections under `users/{uid}` via `userDocRef.listCollections()` and logs a structured warning for any subcollection name NOT in the allow-list:

```typescript
functions.logger.warn('unexpected subcollection found under users/{uid}; not cleaned up', {
  uid,
  unexpectedSubcollection: name,
  hint: 'update onAuthUserDeleted allow-list',
});
```

The function does NOT auto-delete the unknown subcollection — it leaves it as orphan data, intentionally, so that an accidental write under `users/{uid}/...` cannot silently nuke data. The warning surfaces the drift in Cloud Logging on the first real deletion that touches it, giving operators a same-day signal to update the allow-list.

### Cloud Storage — delete prefix `users/{uid}/`

Uses `bucket.deleteFiles({ prefix: 'users/{uid}/' })` from `@google-cloud/storage` (available via `admin.storage().bucket()`).

This covers both observed storage paths in the codebase:

- `users/{uid}/orders/{orderId}/fabrics/{itemId}.jpg`
- `users/{uid}/customers/{customerId}/styles/{styleId}.jpg`

And any future user-scoped paths that follow the `users/{uid}/...` convention.

### What is NOT deleted

- `deletion_feedback/{anyId}` — anonymous deletion-reason capture per PR #35's analytics design. Retained intentionally.
- Any document or storage object outside the `users/{uid}/...` namespace. (As of 2026-05-12 there are none, but stating it for future-proofing.)

## Error handling

Each cleanup branch wraps its operation in try/catch and logs failures structurally:

```typescript
try {
  await db.recursiveDelete(db.collection('users').doc(uid).collection('customers'));
} catch (error) {
  functions.logger.error('firestore cleanup failed', {
    uid,
    subcollection: 'customers',
    error: error instanceof Error ? { message: error.message, stack: error.stack } : error,
  });
}
```

Cloud Logging will show one structured log entry per failed branch with the uid + which step failed. Operators can grep / alert on these.

The function does not re-throw. Cloud Functions Gen 1 with default retry config would auto-retry on throw — we don't want that because:

1. Some failures are permanent (e.g., a deeply nested storage object with bad metadata). Retrying just burns invocations.
2. Idempotency is hard to guarantee at this granularity (recursive deletes already partially completed).
3. Orphan data is recoverable manually; lost auth user is not. The auth user is already gone before this function runs, so the worst case is some orphan data, not user-facing breakage.

## Idempotency

The function is implicitly idempotent because:

- `recursiveDelete` on an empty collection is a no-op.
- `bucket.deleteFiles({ prefix })` with no matching files is a no-op.
- Deleting an already-deleted document with `.delete()` is a no-op in Firestore.

So if the trigger somehow fires twice for the same uid (Firebase rarely guarantees exactly-once delivery), the second invocation does no harm.

## Testing

Jest with `firebase-functions-test` for the trigger harness. Mock `firebase-admin` so unit tests run without network or emulator.

### Unit test coverage

- **Trigger wiring** (`index.test.ts`): the exported function is bound to `auth.user().onDelete` and is invoked when the harness emits an `onDelete` event. Both cleanup functions are called with the right uid.
- **Firestore cleanup** (`firestore.test.ts`): `recursiveDelete` is called once per known subcollection with the right path; user doc `.delete()` is called last; failure in one subcollection does not skip the others; failure is logged.
- **Storage cleanup** (`storage.test.ts`): `deleteFiles` is called with `prefix: "users/{uid}/"`; failure is logged and swallowed.

### Manual smoke

After deploying to Firebase (`firebase deploy --only functions:onAuthUserDeleted`):

1. Create a test Auth user via Firebase Console (Authentication → Users → Add user).
2. Manually seed under the new uid in Firestore Console:
   - `users/{uid}` doc with `{ businessName: "Smoke Test" }`
   - `users/{uid}/customers/{cid}` with a fake customer
   - `users/{uid}/orders/{oid}` with a fake order
3. Manually upload a file to `users/{uid}/orders/{oid}/fabrics/test.jpg` via Cloud Storage Console.
4. Delete the Auth user from the Console.
5. Check Cloud Logging → filter by `function:onAuthUserDeleted` → should see one structured `INFO` log for "cleanup completed" with the uid.
6. Refresh Firestore Console → `users/{uid}` and all subcollections are gone.
7. Refresh Storage Console → `users/{uid}/` prefix is empty.

Failure mode smoke: same steps but with the function's permission temporarily restricted so Storage delete fails. Verify Firestore still cleaned, error log present, function still returns success.

### CI

A new `functions-tests` job in `.github/workflows/ci.yml`:

```yaml
functions-tests:
  runs-on: ubuntu-latest
  defaults:
    run:
      working-directory: functions
  steps:
    - uses: actions/checkout@v6
    - uses: actions/setup-node@v4
      with: { node-version: 20 }
    - run: npm ci
    - run: npm run test
    - run: npm run lint
```

Independent of Kotlin builds. Runs in parallel. ~30s typical.

## Deployment

Manual first deploy from a Node 20+ environment:

```bash
cd functions
npm ci
npm run build           # tsc to lib/
firebase deploy --only functions:onAuthUserDeleted
```

Daniel runs this from his machine; no CI deploy in V1.

After deploy, verify in Firebase Console:

- Functions tab shows `onAuthUserDeleted` with trigger type "auth" and region "europe-west1"
- Runtime is Node 20
- Logs are empty (nothing has triggered it yet)

A `docs/auth/firebase-functions.md` runbook will be added with deploy + rollback + log-viewing commands.

## Configuration

- **Node runtime:** 20 (current Firebase Functions default; matches local dev expectations)
- **Memory:** 256 MB (default; cleanup is light)
- **Timeout:** 60 s (default; recursive delete of a typical user should finish in < 10 s)
- **Min instances:** 0 (cold-start is acceptable; deletion isn't latency-sensitive)

No environment variables needed — admin SDK gets its credentials from the function runtime automatically.

## Security

- The function runs with the default Firebase Admin SDK identity (`firebase-adminsdk-*@stitchpad-30607.iam.gserviceaccount.com`), which has full project access. This is appropriate — the function needs to delete arbitrary user data.
- No external HTTP endpoint is exposed. The function is triggered only by an internal Firebase Auth event.
- No secrets in source. No GCS bucket arguments — the function uses the project's default bucket, retrieved from `admin.storage().bucket()`.

## Success criteria

- A test Auth user with seeded Firestore + Storage data, when deleted, leaves no residue in either store within 60 seconds.
- Cloud Logging shows the function's structured log entries for each delete operation.
- The function never throws (never auto-retries).
- Jest unit tests pass in CI.
- Manual smoke passes per the checklist above.

## Risks and mitigations

- **Wrong subcollection names** — if the codebase adds a new subcollection (e.g., `invoices`) and we forget to update this function's allow-list, that data is orphaned on delete. Mitigation: the function calls `userDocRef.listCollections()` and emits a `WARN` log for any subcollection NOT in the allow-list (see Cleanup contract → Drift-warning). First real deletion touching the unknown subcollection alerts the operator within the day via Cloud Logging. Plus the runbook note.
- **Storage delete is non-atomic** — if cleanup runs while the user is mid-upload, the upload may race the delete and orphan a single file. Mitigation: the in-app deletion flow signs the user out before the function fires; mid-upload race is essentially impossible. Acceptable.
- **Function fails silently** — if the function itself fails to invoke (e.g., quota exhaustion), no cleanup runs and we don't notice. Mitigation: Cloud Logging has built-in alerting; can wire a basic "function errored" alert post-launch.
- **Cost** — recursive deletes count as Firestore writes, billable. For V1 (~10 testers × ~100 docs each) this is negligible. For scale: each recursive delete batches up to 500 docs/sec; a power user with 10k docs costs ~$0.02 to clean up. Not a concern.
