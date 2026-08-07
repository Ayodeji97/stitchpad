# Slice 8e Rollout — Strip Base Docs + Flip Staff LIST

Sequencing (from 8b/8c runbooks): 8a → 8b (backfill) → release 8d-1 client →
8c (version floor) → wait for adoption → **8d-2 strip (this, irreversible)** →
**8e rules flip (this)**.

## Gate checklist — all YES before the strip

- [ ] 8b backfill: Verify dry-run reports every doc already mirrored; spot-check
      `/private/money.ownerId` on a legacy order.
      ```bash
      cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js
      ```
      Re-run with `--commit` using this branch's build to heal stamped-but-incomplete
      mirrors (legacy-deposit synthesis):
      ```bash
      cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/backfillSensitiveFields.js --commit
      ```
- [ ] The 8d-1 client (branch `feat/staff-slice8d1-stop-dual-write`, merged in
      `feat/staff-slice8e`) is released on BOTH stores.
- [ ] 8c floor set to the 8d-1 build numbers; verify a below-floor build shows the
      force-update screen:
      ```bash
      cd functions && ANDROID_FLOOR=<versionCode> IOS_FLOOR=<CFBundleVersion> GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/setUpdateFloor.js --commit
      ```
- [ ] Firestore export taken (save the operation folder name):
      ```bash
      gcloud firestore export gs://stitchpad-30607.appspot.com/exports/pre-8d-strip-$(date +%Y%m%d) --project=stitchpad-30607
      ```

## Strip (irreversible)

1. Dry run: `cd functions && GOOGLE_CLOUD_PROJECT=stitchpad-30607 node scripts/stripBaseSensitiveFields.js`
   - Expect `DRY RUN — …`; investigate every `ordersSkippedUnstamped` /
     `customersSkippedUnstamped` / `ordersSkippedLegacyDeposit` warning BEFORE
     committing. Unstamped docs are never stripped (fix by re-running the 8b
     backfill for those users). Non-zero `ordersSkippedLegacyDeposit` means the
     8b re-run (gate item above) was skipped or incomplete — fix by re-running
     it with `--commit`, then re-dry-run until the count is 0.
2. Apply: same command + `--commit`. Counts must match the dry run.
3. Verify: owner device still shows correct money and contact (reads come from
   `/private` via the collection-group join); console spot-check shows clean
   base docs.

## Rules flip

1. `firebase deploy --only firestore:rules --project=stitchpad-30607`
2. Verify on devices: staff account's Orders + Customers lists populate; no
   money or contact visible anywhere on the staff build; owner unaffected.

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
