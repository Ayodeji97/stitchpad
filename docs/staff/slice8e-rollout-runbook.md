# Slice 8e Rollout — Strip Base Docs + Flip Staff LIST

Sequencing (from 8b/8c runbooks): 8a → 8b (backfill) → release 8d-1 client →
8c (version floor) → wait for adoption → **8d-2 strip (this, irreversible)** →
**8e rules flip (this)**.

## Gate checklist — all YES before any `--commit`

**Order matters: the Firestore export comes BEFORE the first write of this
rollout**, not just before the strip. Both the 8b backfill re-run and the strip
mutate production; the export is the only rollback for either.

- [ ] The 8d-1 client (branch `feat/staff-slice8d1-stop-dual-write`, merged in
      `feat/staff-slice8e`) is released on BOTH stores.
- [ ] 8c floor set to the 8d-1 build numbers; verify a below-floor build shows the
      force-update screen:
      ```bash
      cd functions && ANDROID_FLOOR=<versionCode> IOS_FLOOR=<CFBundleVersion> GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/setUpdateFloor.js --commit
      ```
- [ ] **1. 8b backfill DRY RUN** (writes nothing):
      ```bash
      cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js
      ```
      Expect `DRY RUN — users=<N> ordersMirrorCreated=… ordersHealedLegacyDeposit=…
      ordersAlreadyMirrored=… customersMirrorCreated=… customersAlreadyMirrored=…`.
      **Expected steady state after 8b + 8d-1 adoption: `ordersAlreadyMirrored` =
      total orders and `customersAlreadyMirrored` = total customers, with both
      `MirrorCreated` counts 0.** A non-zero `MirrorCreated` means some docs never
      got a mirror (legacy/seeded, or written by a pre-8a client) — investigate
      those users before committing. `ordersHealedLegacyDeposit > 0` is expected
      only if legacy `depositPaid` orders were never healed.
- [ ] **2. Firestore export taken** (save the operation folder name) — do this
      BEFORE the first `--commit` of this rollout:
      ```bash
      gcloud firestore export gs://stitchpad-30607.appspot.com/exports/pre-8d-strip-$(date +%Y%m%d) --project=stitchpad-30607
      ```
- [ ] **3. 8b backfill `--commit`** (only if step 1 showed work to do):
      ```bash
      cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js --commit
      ```
      This build is mirror-first and safe to run post-8d-1: it reads each
      `/private` mirror first and only writes when the mirror is missing or
      unstamped (full build from base, **overlaid** with any payments/costs the
      unstamped mirror already holds — 8d-1's `recordPayment`/`updateCosts` create
      it without the `ownerId` stamp, so it can be newer than the base: payments are
      unioned by id, non-empty mirror costs win), or when a stamped order mirror is still
      legacy-deposit-incomplete (**payments-only** heal, prepending the synthesized
      `legacy-deposit` onto the mirror's own payments). Stamped, complete mirrors
      are skipped entirely — it can no longer overwrite authoritative mirror data
      with a stale/empty base doc. Counts must match the dry run.
      Re-run the dry run afterwards: the heal/create counts must now be 0.
- [ ] Spot-check `/private/money.ownerId` on a legacy order in the console.

## Strip (irreversible)

1. Dry run: `cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js`
   - Expect `DRY RUN — …`; investigate every `ordersSkippedUnstamped` /
     `customersSkippedUnstamped` / `ordersSkippedLegacyDeposit` warning BEFORE
     committing. Unstamped docs are never stripped (fix by re-running the 8b
     backfill for those users). Non-zero `ordersSkippedLegacyDeposit` means the
     8b re-run (gate item 3 above) was skipped or incomplete — fix by re-running
     it with `--commit`, then re-dry-run until the count is 0.
2. Apply: same command + `--commit`. Counts must match the dry run.
3. Verify: owner device still shows correct money and contact (reads come from
   `/private` via the collection-group join); console spot-check shows clean
   base docs.

## Rules flip

**HARD GATE — a FRESH strip dry run must report all of these as zero before you
deploy:** `ordersStripped=0`, `customersStripped=0`, `ordersSkippedUnstamped=0`,
`ordersSkippedLegacyDeposit=0`, `customersSkippedUnstamped=0` (nothing left to
strip, nothing skipped).

```bash
cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js
```

Why: the flipped rule opens `list` to staff and **LIST has no field guard** —
rules are not query filters, so any base doc still carrying money/contact
(including every doc the strip *skipped*) streams straight to a staff device on
the first list query. Non-zero on any of the five counts ⇒ do not deploy; fix and
re-run the strip cycle first.

### What else this deploy ships: the base-doc money/contact write-deny

The same `firestore.rules` also **denies money/contact keys on every client write
to a base order/customer doc** — `create` rejects any payload containing
`totalPrice`/`discount`/`discountReason`/`depositPaid`/`balanceRemaining`/
`payments`/`costs` (orders) or `phone`/`email`/`address` (customers), and the
owner `update` branch rejects any write whose `affectedKeys()` touches them.
Legacy fields already stored on a doc may **remain** (untouched keys aren't
"affected", so honest edits of a not-yet-stripped doc still succeed) — the
Admin-SDK strip is what removes them, and it bypasses rules.

Two consequences, both load-bearing:

- **The post-flip monitoring below becomes a backstop, not the only defence.** A
  queued offline write from a pre-8d-1 build now bounces with
  `permission-denied` when it flushes, instead of landing on the base doc and
  leaking to staff LIST until the next re-strip. That offline edit is **lost** —
  the SDK drops the mutation and the user is not told. Accepted trade: the window
  is small (below-floor clients are already blocked from new edits by the UI
  gate) and silent re-contamination of the money/contact wall is worse.

  A bounced create does **not** leave a half-written doc behind. `createOrder`/
  `createCustomer` are two writes — `set(dto)` then
  `set({serverCreatedAt}, merge=true)` — and the SDK ships them in one atomic
  commit, so denying the money/contact-bearing create denies the stamp with it and
  no blank `serverCreatedAt`-only stub lands. (The rules do **not** separately
  deny stamp-only creates: a guard doing that was tried and reverted because it
  also denied the current client's own create commit — see the comment on the
  `allow create` blocks.) Only the rare case where the two halves end up in
  *different* commits can orphan a stub; that is accepted and sweepable.
- **These rules must NOT be deployed before the 8c version floor is enforced.**
  A pre-8d-1 client writes money/contact on *every* save (GitLive encodes the
  full DTO), so deploying early breaks saving wholesale for anyone not yet
  updated. The version-floor gate in the checklist above already covers this —
  confirm it is live and that both stores show the 8d-1 build before deploying.

1. `firebase deploy --only firestore:rules --project=stitchpad-30607`
2. Verify on devices: staff account's Orders + Customers lists populate; no
   money or contact visible anywhere on the staff build; owner unaffected.
3. Verify on an **owner** device (8d-1+): creating and editing an order and a
   customer still succeeds, and money/contact still round-trip (they now write
   only to `/private`). A `permission-denied` here means a below-floor build is
   still in play — roll the rules back (see Rollback) and re-check the floor.

## Post-flip monitoring (re-contamination watch)

The 8c version floor is a **UI gate, not a write block**: a below-floor client
that has queued offline writes will still flush them when it reconnects (the
Firestore SDK replays its local mutation queue regardless of what screen the app
is showing). Those replayed writes use the OLD base-doc shape, so they *would*
put money/contact fields back onto base docs after the strip — and LIST would
then serve them to staff.

**As of this deploy those writes are rejected by the rules** (see "What else this
deploy ships" above), so re-contamination should be impossible. Keep the checks
below anyway: they confirm the deny is actually working, and they still catch
contamination from any non-client writer (an Admin-SDK script, a Console edit, or
a Cloud Function that bypasses rules).

- After an adoption window (**1–2 weeks post-flip**), re-run the strip **DRY RUN**:
  ```bash
  cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js
  ```
- If any count is non-zero, re-contamination happened: take a fresh Firestore
  export, then re-run `stripBaseSensitiveFields.js --commit`, then dry-run again
  until everything reads zero.
- Repeat the check on a slower cadence until two consecutive runs are clean.

**Shipped in this deploy (was previously deferred):** the rules-level *deny* of
money/contact keys on base order/customer writes. It closes re-contamination
permanently rather than leaving it merely detectable. It is safe to ship here
precisely because the gate above already requires the 8c version floor to be
enforced and the 8d-1 build to be live on both stores — i.e. every writing client
is confirmed 8d-1+. See "What else this deploy ships" under Rules flip.

## Rollback

- Rules: redeploy the previous `firestore.rules` (git revert of the Task 3
  commit) — staff lists go dark again, and the base-doc money/contact write-deny
  lifts, so below-floor clients can save (and re-contaminate) again. If you roll
  back for that reason, resume the post-flip monitoring above as the sole defence
  until the floor is fixed and the rules are redeployed.
- Strip: restore from the export — full-DB restore; only for catastrophic data
  loss, expect to lose writes made since the export.
  1. Find the operation folder created by the export:
     ```bash
     gsutil ls gs://stitchpad-30607.appspot.com/exports/pre-8d-strip-<date>/
     ```
  2. Import the discovered operation folder:
     ```bash
     gcloud firestore import gs://stitchpad-30607.appspot.com/exports/pre-8d-strip-<date>/<operation-subfolder> --project=stitchpad-30607
     ```
- Panic switch: set `staffFeatureEnabled: false` on `config/app` — every staff
  session resolves to owner-of-self and stops reading the workshop tree.
