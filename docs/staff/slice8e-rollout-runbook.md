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

1. `firebase deploy --only firestore:rules --project=stitchpad-30607`
2. Verify on devices: staff account's Orders + Customers lists populate; no
   money or contact visible anywhere on the staff build; owner unaffected.

## Post-flip monitoring (re-contamination watch)

The 8c version floor is a **UI gate, not a write block**: a below-floor client
that has queued offline writes will still flush them when it reconnects (the
Firestore SDK replays its local mutation queue regardless of what screen the app
is showing). Those replayed writes use the OLD base-doc shape, so they can put
money/contact fields back onto base docs *after* the strip — and LIST will then
serve them to staff.

- After an adoption window (**1–2 weeks post-flip**), re-run the strip **DRY RUN**:
  ```bash
  cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js
  ```
- If any count is non-zero, re-contamination happened: take a fresh Firestore
  export, then re-run `stripBaseSensitiveFields.js --commit`, then dry-run again
  until everything reads zero.
- Repeat the check on a slower cadence until two consecutive runs are clean.

**Follow-up (not this branch):** a rules-level *deny* of money/contact keys on
base order/customer writes (reject any `create`/`update` whose
`request.resource.data` contains `totalPrice`/`payments`/`costs`/`depositPaid`/
`balanceRemaining`/`phone`/`email`/`address`) would close this permanently, making
re-contamination impossible instead of merely detectable. It is out of scope here
because it must not ship until every writing client is confirmed 8d-1+.

## Rollback

- Rules: redeploy the previous `firestore.rules` (git revert of the Task 3
  commit) — staff lists go dark again, nothing else changes.
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
