# Notebook scan import: deploy order, release gate and smoke test

Branch: `feat/notebook-scan-import`. Nothing here is deployed yet.

## 1. Deploy order

Do these in order. Do not ship the app build until steps 1 to 3 are live.

1. **Indexes.** Deploy `firestore.indexes.json`. It adds COLLECTION_GROUP field overrides on
   `notebookImports` for `createdAt` and `status`. The cleanup cron queries both, so the
   indexes must finish building before the cron first runs (03:15 Africa/Lagos).
2. **Rules.** Deploy `firestore.rules` and `storage.rules`.
   - Firestore: the `notebookImports` block. Clients can only create a job (`status` open,
     `pageCount` 0), set `status` to saved or abandoned, and update card fields. The card
     fields include the client-writable `keepAsNew`.
   - Storage: owner-only path `users/{uid}/notebookImports/{jobId}/{file}`. Only `.jpg`, under
     6 MB, `image/jpeg`, and only while the job exists with status `open` and has no
     `imagesDeletedAt` (the cron already swept it). Staff get no read and no write on this path.
3. **Functions.** `processNotebookPage` (Storage onFinalize), `retryNotebookPage`, and the
   cron `cleanupNotebookImports` (`15 3 * * *`, Africa/Lagos). Region is europe-west1.
   Use an explicit list:

   `firebase deploy --only functions:processNotebookPage,functions:retryNotebookPage,functions:cleanupNotebookImports`

   Do not use `npm run deploy` in `functions/`. Its `--only` list is hard-coded and does not
   include the three notebook functions, so it would deploy 50+ other functions and skip these.
4. **App build.** Ship last.

### Open PR #414 (Play Billing)

PR #414 also edits `functions/src/index.ts` and `firestore.rules`. Merge the two PRs one at
a time and resolve the conflicts by keeping both sides. Whichever lands second, re-run the
rules deploy (and the functions deploy for its own functions) after both are on main, so
production rules contain both the billing and the notebook blocks. Check the deployed rules
in the console for both.

### Model id

`NOTEBOOK_MODEL_ID` in `functions/src/notebook/notebookModel.ts` is a placeholder
(`gemini-3.1-pro`). Before release:
- Confirm it is enabled on Vertex in project `stitchpad-30607`. The client uses location
  `global` (`functions/src/smart/vertexClient.ts`), so check availability there.
- Choose the final id by running the eval below, then set the constant.

## 2. Release gate: accuracy eval

1. Collect 20 to 30 real notebook pages from 3 to 5 tailors. Get each tailor's permission.
2. Type the truth for each page: `tools/notebook_eval/pages/<name>.truth.json` next to
   `<name>.jpg` (format in `tools/notebook_eval/README.md`).
3. Sign in once: `gcloud auth application-default login`.
4. Run: `cd functions && npx tsx ../tools/notebook_eval/run.ts --model <id>`
5. Ship bar, both must hold:
   - at least 95% of values correct
   - at least 90% of errors flagged
   Errors include wrong values, missing values on a matched person, and every value of a
   person the model lost. Failed pages count. Exit code 1 means fail.
6. Pages are customer data. Never commit or share them. Delete `pages/` and `out/` when done.

## 3. Device smoke (Android and iOS)

Use a 10-page mixed sample on each platform.

**Capture and upload**
- Capture several pages. Leave the screen mid-upload and come back. Uploads continue.
- Sign out mid-upload on a shared device. Sign in as another account. Nothing from the first
  account shows up.
- Force a failed page (for example airplane mode for one page). Tap Try again. It recovers.
- Close the app mid-processing and reopen. The import resumes.
- Open the capture screen in airplane mode (no cached open job). After about 3 seconds it shows
  "We could not check for an earlier scan" with Try again, and both photo buttons stay
  disabled. Turn the network back on and tap Try again. The buttons enable and an earlier open
  job with pages is resumed, not replaced by a new one.
- Retry limit: a page that keeps failing can be retried 3 times. The 4th Try again shows
  "This page can't be read. Try a clearer photo as a new page."
- A page stuck reading for over 10 minutes shows "taking too long" with Try again. (To force
  it, set the page doc to `status: processing` with an old `claimedAt` in the console.)
- A photo the phone cannot decode shows "This photo could not be read. Take it again." and the
  page is marked not uploaded. Nothing is uploaded for it.

**Earlier scan banner**
- Leave an import with checked but unsaved customers. Make the job 7+ days old (or set
  `imagesDeletedAt` on it in the console). Open the capture screen from the dashboard and from
  the Customers entry. A banner says "You have customers from an earlier scan waiting to be
  saved". Review opens that earlier import.
- Take a new photo. It goes into a new job, not the old one. The banner stays until every
  card of the old job is saved or skipped (or the job is saved or discarded).

**Review**
- The review list shows its sections.
- A match shows "Already a customer". Try both "Confirm" and "Save as new".
- Two existing customers with the same name as a card: the card is in "Need a look" with
  "Which customer is this?". "Save all" skips it. In the card, pick one of the listed customers
  (name and phone shown) or "Save as a new customer". Then it can be checked and saved.
- A card whose notebook did not say inches or cm is in "Need a look". The card editor shows
  neither unit selected and "Looks right" is blocked until one is chosen. No default unit is
  ever applied.
- A "Same person?" prompt appears for a two-page person. Try Combine and Keep separate.
- An unclear value blocks "Looks right" until it is fixed.
- Fix a flagged value in the card editor.
- The card photo crop renders (Coil loading the owner Storage URL).
- "Save all" saves every card.
- Discard asks for confirmation.

**After saving, check in the app**
- Customer, measurements and notes are right.
- Phone is stored in `+e164` format.
- Date written is right.

**Limits and access**
- A FREE account past the welcome window stops at the cap and shows the upgrade sheet. The
  sheet shows the count saved before the cap.
- A staff account sees no entry points.
- First-run hop to capture happens only for a brand-new owner: account under 3 days old with
  no customers and no orders.
- The dashboard card shows only for accounts up to 14 days old (and under 5 customers). An
  account whose age is unknown does not see it.

**Backend**
- `updateCard` round-trips `values` (list of maps, `fieldKey` can be null) on the emulator
  or the real backend.
- Photos are gone after cleanup: 7 days old, or the job is saved or abandoned. To force it,
  run the `cleanupNotebookImports` cron from the console. Every run sweeps the photos of every
  job 7+ days old again, so a late upload does not linger.
- A photo uploaded to a job that was saved, abandoned or deleted is deleted by
  `processNotebookPage` and writes no page or cards.
- The job tree (job, pages, cards) is gone at 30 days.

## 4. Known accepted minors

- Process death loses photos that have not finished uploading.
- "Keep separate" is not saved. The "Same person?" prompt returns when the job is reopened.
- Queued customer writes can be rejected server-side after a card is marked SAVED. This is
  an app-wide pattern, not specific to this feature.
