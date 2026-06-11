# Push Token Ownership Cleanup — design

> **Date:** 2026-06-11
> **Status:** approved design (Approach A), pre-implementation
> **Branch:** `feat/token-ownership-cleanup` (off `main`)
> **Part of:** notifications pre-rollout follow-ups (gated by `rollout.ts STAGING`)

## Context

The notifications push backbone registers a device's FCM token at
`users/{uid}/notificationTokens/{token}` (doc id = the token; fields `platform`,
`updatedAt`). A clean sign-out removes it (`SignOutUseCase` → `unregisterForUser` +
`invalidateToken`). But the token doc can outlive its owner:

- sign-out's `unregisterForUser` fails (offline / timeout),
- the app is reinstalled or deleted without signing out,
- a shared device switches accounts (A → B).

When that happens the same FCM token exists under **two** users. The morning scan then
pushes user A's digest to A's token doc — but the device now belongs to **B**, so **B
sees A's customer names and order details in the notification.** This is a privacy leak,
and it's worth closing before push rolls out to real testers.

## Goal / invariant

An FCM token belongs to **at most one user at a time.** When token `T` is registered
under user `B`, every other `users/{X}/notificationTokens/T` (X ≠ B) is removed.

## Approach (A — server-enforced on register)

A Firestore **onCreate** trigger on `users/{uid}/notificationTokens/{token}`: when a token
doc is first created under a uid, find the same token under *other* uids (a
`collectionGroup` query) and delete those stale docs with the Admin SDK.

- **onCreate (not onWrite):** the client writes with `set(merge=true)` on every register;
  a re-register under the *same* user is an update (no trigger), while the token appearing
  under a *new* uid is a create — exactly the ownership-transfer moment. Deletes of the
  stale docs are delete events, so there is no trigger loop.
- **Needs a queryable `token` field.** `collectionGroup` queries cannot filter by a doc's
  leaf id across different parents, so the client must also store the token *as a field*
  (`token: T`) and we add a `COLLECTION_GROUP`-scoped single-field index on `token`.
- **Owner uid** of each found doc = `doc.ref.parent.parent.id`; skip the one whose uid
  equals the new owner (that's the doc that just triggered us, plus any same-user dupes).

Rejected alternatives:
- **Lazy dedupe at send time** — doesn't fix the leak; A's per-user scan still targets A's
  stale doc.
- **Client removes the token from other users on register** — impossible; security rules
  (correctly) deny cross-user reads/writes. Only an Admin-SDK function can do this.

## Components

1. **Client — add a `token` field.** `FirebasePushTokenRepository.registerToken` writes
   `token` alongside `platform` + `updatedAt` (so the function's collectionGroup query has an
   indexed field). No rules change — owner create/update on `notificationTokens` is already
   allowed.
2. **Cloud Function — `pruneTokenOwnership`.** `region(europe-west1)`, Firestore
   `document('users/{uid}/notificationTokens/{token}').onCreate`. Pure core
   (`dupeTokenRefsToPrune(ownerUid, docs)` → the refs to delete) behind a thin Firebase
   wiring (collectionGroup query + batched delete), matching the digest functions' testable
   IO style. Logs `{ token: <prefix>, ownerUid, pruned }`.
3. **Firestore index.** A `COLLECTION_GROUP`-scoped single-field index on
   `notificationTokens.token` (via `fieldOverrides` in `firestore.indexes.json`).
4. **Deploy allow-list.** Add `functions:pruneTokenOwnership` to `functions/package.json`'s
   `deploy --only` list (else `npm run deploy` silently skips it).

## Data flow

register T under B → token doc created at `users/B/notificationTokens/T` (with `token: T`)
→ `onCreate` fires → `collectionGroup('notificationTokens').where('token','==',T)` →
for each result whose `parent.parent.id !== B`, batch-delete → stale `users/A/.../T` gone.
Next morning scan: only B's doc for T remains, so A's digest never reaches B's device.

## Edge cases

- **Self / same-user dupes:** the query returns the just-created doc (uid = B) — skipped by
  the `uid !== ownerUid` guard. Multiple devices of the *same* user have *different* tokens,
  so they're never matched (we filter by token value, not by user).
- **Pre-existing docs without `token` field:** not matched by the query → not cleaned.
  Acceptable: this is forward-looking enforcement. A one-time backfill is **out of scope**
  (the existing orphan-cleanup backlog item covers historical test-account cruft).
- **Simultaneous cross-uid registration of the same token** (very rare): each onCreate could
  prune the other, leaving the token under neither; the next register re-establishes
  ownership. Acceptable.
- **No trigger loop:** the function only deletes (delete events don't fire onCreate).

## Testing

- **Unit (Jest):** `dupeTokenRefsToPrune('B', [docA, docB, docC])` → returns A + C refs (all
  but B); single-owner `[docB]` → returns `[]`; empty → `[]`. Pure function, no Firebase.
- **Manual smoke (real device, debug build):** sign in as A → grant push → confirm
  `users/A/notificationTokens/T`. Sign out, sign in as B on the **same device** → confirm
  `users/B/notificationTokens/T` is created **and** `users/A/.../T` is **gone**. Trigger a
  digest for A → confirm B's device gets **no** push for A.

## Out of scope

- Backfilling the `token` field onto historical docs / cleaning pre-existing orphans
  (separate backlog item).
- Any change to the scan, the email path, or the client read path.

## Review

Per the rotation: Cursor + `codex review`. Watch for: the `parent.parent.id` owner
extraction, the self-skip guard, the collectionGroup index being declared, and the deploy
allow-list entry.
