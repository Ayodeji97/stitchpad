# Slice 8b — /private backfill runbook

One-off backfill of the owner-only `/private` sub-docs for every existing order and
customer, stamping `ownerId` + the parent id so the Slice 8a owner read
(`collectionGroup("private").where("ownerId","==",uid)`) is complete for legacy and
seeded docs.

This is **additive and idempotent**. It writes with `merge:true` and **never deletes
base fields** — the base-strip is a separate, later, deliberate step (Slice 8d).

## Prerequisites

1. **Slice 8a (#323) is merged and released.** The sub-doc shape written here
   (`ownerId` + `orderId`/`customerId`) is 8a's. Running earlier is harmless but the
   `ownerId` stamp is only *useful* once 8a's read path is live.
2. **The collection-group rule + index are deployed** (they ship with 8a):
   `firebase deploy --only firestore:rules,firestore:indexes`. The index must finish
   building before the owner read uses it (until then the app safely falls back to
   base money/contact).
3. **Application-default credentials** with Firestore access on the machine running
   the script: `gcloud auth application-default login`.

Two equivalent run mechanisms exist; **use the script** (simpler, ADC-based):

- `functions/scripts/backfillSensitiveFields.js` — standalone, ADC, dry-run default.
- `migrateSensitiveFields` callable (already in the deploy allow-list) — same logic,
  but needs an admin-claimed caller; kept in lockstep as the in-cluster equivalent.

## Procedure

### 1. Dry run (writes nothing)

```
cd functions
GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js
```

Expect: `DRY RUN — users=<N> contactDocs=<C> moneyDocs=<M>`. Sanity-check `C`/`M`
against the rough number of customers/orders in the project (Firestore console
usage). If the counts look wildly off, stop and investigate before committing.

### 2. Apply

```
GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js --commit
```

Expect: `COMMITTED — users=<N> contactDocs=<C> moneyDocs=<M>` with the same counts.

### 3. Verify

- Spot-check a **seeded/legacy** order and customer in the Firestore console: the
  `.../orders/{oid}/private/money` and `.../customers/{cid}/private/contact` docs now
  exist and carry `ownerId` (= the owner uid) + `orderId`/`customerId`.
- On a device signed in as that owner, confirm money/balances and customer contact
  still display correctly (they now come from `/private`, no longer the fallback).

## Safety notes

- **Idempotent**: safe to re-run. `merge:true` only fills gaps + refreshes `ownerId`.
- **No base mutation**: base order/customer docs are untouched — money/contact still
  live on the base during the dual-write window, so old app versions are unaffected.
- **Reversible**: nothing here is destructive. (The irreversible step is the Slice 8d
  base-strip, gated behind the version floor + a Firestore export.)
- If the process dies mid-run, just re-run — merges are idempotent per doc.

## Where this sits in Slice 8

8a (owner reads /private, resilient fallback) → **8b (this backfill)** → 8c (version
floor) → wait for adoption → 8d (strip base) → 8e (flip `allow list`; staff data
lights up). See the Slice 8 plan for the full sequence.
