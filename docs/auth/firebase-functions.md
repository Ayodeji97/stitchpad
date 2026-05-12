# Firebase Functions — deployment + operations runbook

This project has one Cloud Function as of 2026-05-12: `onAuthUserDeleted` (Gen 1, region `europe-west1`). Cleans up Firestore + Cloud Storage data after Firebase Auth account deletion.

## Prerequisites

- Node 20+ locally (`node --version`)
- Firebase CLI installed (`npm install -g firebase-tools`)
- Logged into the Firebase project: `firebase login` (one-time)

## Deploy

From repo root:

```bash
cd functions
npm ci
npm run lint && npm run build && npm test
cd ..
firebase deploy --only functions:onAuthUserDeleted
```

Deploy takes ~60-90 seconds. On success you'll see:

```
✔  functions[onAuthUserDeleted(europe-west1)] Successful update operation.
```

(No `Function URL` since this is an auth trigger, not HTTP.)

## Verify deployment

```bash
firebase functions:list
```

Should show `onAuthUserDeleted` with trigger type `providers/firebase.auth/eventTypes/user.delete` and region `europe-west1`.

In Firebase Console: Functions tab → `onAuthUserDeleted` → "Logs" subtab. Empty initially.

## Smoke test the deployed function

1. Firebase Console → Authentication → Users → "Add user", e.g., `smoke-test@example.com`
2. Note the new UID
3. Firestore Console: manually create `users/{uid}` doc with `{ businessName: "Smoke Test" }`, plus a fake doc in `users/{uid}/customers/test`
4. Cloud Storage Console: upload any file to `users/{uid}/orders/x/fabrics/test.jpg`
5. Authentication → Users → delete the smoke-test user
6. Wait 10-30 seconds, refresh Logs tab → should see two `INFO` entries (`cleanup starting`, `cleanup completed`) with the uid
7. Verify in Firestore Console: `users/{uid}` and subcollections gone
8. Verify in Storage Console: `users/{uid}/` prefix empty

If any subcollection is left behind, check Cloud Logging for a `WARN` entry — that means the subcollection is NOT in the allow-list and the function intentionally skipped it (see "Adding a new subcollection" below).

## View logs

```bash
firebase functions:log --only onAuthUserDeleted
```

Or in Cloud Logging: filter by `resource.labels.function_name = "onAuthUserDeleted"`.

Structured log fields you can filter on:
- `uid` — every entry
- `subcollection` — set on subcollection-cleanup failures
- `unexpectedSubcollection` — set on drift warnings
- `error.name`, `error.message`, `error.stack` — set on failures

## Rollback

There's no automatic rollback. To revert:

```bash
git revert <commit>
cd functions && npm ci && npm run build
cd ..
firebase deploy --only functions:onAuthUserDeleted
```

If you need the function to stop running NOW (faster than a redeploy):

```bash
firebase functions:delete onAuthUserDeleted --region europe-west1
```

Then redeploy when fixed. Note that during the gap, Auth deletions will not trigger any cleanup and will leave orphan data; clean up manually if needed.

## Adding a new subcollection under `users/{uid}/`

If you add a new feature that introduces a subcollection (e.g., `invoices`), **you must update `functions/src/cleanup/firestore.ts`** — add the new name to `ALLOWED_SUBCOLLECTIONS`. If you forget, the function logs a `WARN` (`unexpected subcollection found under users/{uid}; not cleaned up`) the first time a real user with that subcollection is deleted; the data is left orphaned until you fix the allow-list and redeploy.

Workflow:
1. Add the new name to `ALLOWED_SUBCOLLECTIONS` in `functions/src/cleanup/firestore.ts`
2. Run `cd functions && npm test` — existing tests still pass (they iterate the constant)
3. Commit, merge, deploy
