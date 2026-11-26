# Notebook Scan Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An owner photographs notebook pages; the server reads each page with Gemini into review cards; the owner checks the cards and StitchPad saves them as customers and measurements.

**Architecture:**
- **Upload:** the client creates an import job and uploads compressed page photos to Storage.
- **Read:** a Storage `onFinalize` trigger reads each page, so processing continues if the app is closed. It enforces the page cap, calls Gemini through the existing `vertexClient.ts`, validates the JSON and writes one card per person found.
- **Review and save:** the client reviews the cards from Firestore. A saver creates the customers and measurements through the existing repositories, with deterministic ids, so a retry never duplicates.
- **Clean up:** a daily cron deletes page images after 7 days, and the whole job after 30 days.

**Tech Stack:** Kotlin Multiplatform + Compose, Koin, GitLive Firebase (Firestore, Storage, Functions), Firebase Functions v1 TypeScript, `@google/genai` (Vertex, `global`), Jest, JUnit5/AssertK/Turbine.

**Spec:** `docs/superpowers/specs/2026-11-24-notebook-scan-import-design.md`

## Global Constraints

- **Scope:** names, phones and measurements only. No orders, money or sketches.
- **Owner only:** staff never see the entry points. Every path is keyed by the owner's uid: `users/{uid}/notebookImports/{jobId}` (job doc), `.../pages/{pageId}`, `.../cards/{cardId}`, and Storage `users/{uid}/notebookImports/{jobId}/{pageId}.jpg`.
- **Nothing is saved without review:** a card is saved only when the owner taps Save on it, or "Save all checked". Cards with `needsReview = true` are never part of "Save all checked" until opened and confirmed.
- **Existing customer matched:** add a **new** dated measurement and never edit an old one. One measurement per card. A diary person on several dated entries yields several cards.
- **Unmatched labels** go into `Measurement.notes`, one per line, as `"<label>: <value>"`.
- **Lifetime:** images are deleted 7 days after job creation, or at the next cron once the job is `saved` or `abandoned`. Job, pages and cards are deleted 30 days after creation.
- **Page cap (server):** paid subscribers (`isPaidSubscriber` in `functions/src/notifications/digestAudience.ts`) get 1000 pages per calendar month (Africa/Lagos). Everyone else gets 300 pages per account lifetime.
- **Access (client):** a single function, `canStartNotebookImport(session, entitlements): Boolean`, returns `session.isOwner` today. The trial/read-only work changes only that function.
- **Customer cap:** saving uses `CustomerRepository.createCustomer`. On `DataError.Network.CAP_REACHED` the batch stops, the cap sheet and upgrade path are shown, and the remaining cards keep their state.
- **Image for this path:** `ImageCompressor.compress(bytes, maxEdgePx = 2560, jpegQuality = 90)`. The Task 4 eval may lower these.
- **Model:** one constant, `NOTEBOOK_MODEL_ID`, in `functions/src/notebook/notebookModel.ts`. Start with the strongest multimodal Gemini model available on Vertex `global`. Task 4's eval decides the final choice. Temperature 0. JSON output with a response schema.
- **Privacy:** never log image bytes, names, phones or values. Log only ids, counts and status.
- **Project rules (CLAUDE.md):** MVI Root/Screen, Result<T, E>, UiText, strings in `values/` and `values-fr/`, Koin constructor refs, a @Preview per Screen. **No em dashes in any copy.**

## Review Focus

1. **A page with zero readable people** (a cover page, a blank page, a sketch). Expected: the page is marked done with 0 cards and the review says "No customers found on page 3". It is neither a failure nor a stuck spinner. Test: Task 2 `emptyPage_doneWithZeroCards`.
2. **Fractions and segmented values** ("14½", "14 1/2", "40/38", "16.5"). Expected: kept exactly as written, with no rounding and no false "unclear" flag. Test: Task 1 `plausibility_acceptsFractionsAndSegments`.
3. **Duplicate trigger delivery or a retry after a crash.** Expected: no duplicate cards and no duplicate customers. Tests: Task 2 `finalizeTwice_cardsWrittenOnce`, Task 6 `saveRetry_sameIdsNoDuplicate`.
4. **The model returns malformed or partial JSON.** Expected: the page is marked `failed` with a Retry option, and no partial cards. Test: Task 1 `parse_rejectsMalformed`, Task 2 `badModelOutput_pageFailedNoCards`.
5. **Cap reached mid-batch.** Expected: cards saved before the cap stay saved, the rest stay `checked`, and the upgrade sheet shows. Test: Task 6 `capReached_stopsAndKeepsRest`.

---

### Task 1: Server: extraction contract (prompt, schema, parser, plausibility)

**Files:**
- Create: `functions/src/notebook/types.ts`, `functions/src/notebook/extractionPrompt.ts`, `functions/src/notebook/parseExtraction.ts`, `functions/src/notebook/fieldCatalog.ts`
- Test: `functions/src/__tests__/notebook/parseExtraction.test.ts`, `.../extractionPrompt.test.ts`

**Interfaces:**
- Produces:
  ```ts
  export type Gender = 'female' | 'male' | 'unknown';
  export type Unit = 'inches' | 'cm' | 'unknown';
  export interface ExtractedValue { label: string; value: string; fieldKey: string | null; flagged: boolean; }
  export interface Box { x: number; y: number; w: number; h: number; } // 0..1 of page
  export interface ExtractedPerson {
    name: string; phone: string | null; gender: Gender; unit: Unit;
    dateWritten: string | null; // ISO yyyy-mm-dd
    values: ExtractedValue[]; box: Box | null; needsReview: boolean;
  }
  export const FIELD_CATALOG: { key: string; gender: 'female' | 'male'; labels: string[] }[]; // fieldCatalog.ts
  export function buildSystemPrompt(): string;                    // extractionPrompt.ts
  export const RESPONSE_SCHEMA: object;                           // Gemini responseSchema
  export function parseExtraction(raw: string): ExtractedPerson[]; // throws ExtractionParseError
  export class ExtractionParseError extends Error {}
  export function isPlausible(value: string, unit: Unit): boolean;
  ```
- `FIELD_CATALOG` mirrors `BodyProfileTemplate.kt` keys (female: `shoulder_width`, `bust_circumference`, `bust_point`, `shoulder_to_underbust`, `bust_span`, `waist`, `neck_circumference`, `underbust_circumference`, `shoulder_to_waist`, `hip_circumference`, `full_length_gown`, `sleeve_length`, `wrist_circumference`, `nape_to_waist`, `full_front_length`, `arm_length`, `trouser_waist`, `trouser_length`, `trouser_hip`, `thigh_circumference`, `inseam`; male: `shoulder_back`, `chest`, `shirt_length`, `long_sleeve_length`, `elbow_circumference`, `neck_circumference`, `tummy`, `trouser_waist`, `trouser_hip`, `trouser_length`, `thigh_circumference`, `knee_circumference`). For each key, take the EN and FR labels from `values/strings.xml` and `values-fr/strings.xml` (`measurement_field_<key>`), plus common shorthand: `Ch` → chest, `Sh`/`Shd` → shoulder, `Wst`/`W` → waist, `L/S` → long_sleeve_length, `S/S` → sleeve_length, `Rd`/`Round` → circumference fields, `Tr L` → trouser_length, `Hp` → hip. Add a test asserting every catalog key exists in `BodyProfileTemplate.kt` (read the file text in the test).

**Rules:**
- The prompt tells the model to copy values exactly as written; never convert units or guess missing numbers; use `fieldKey: null` when unsure; split a running diary into one person per dated entry; ignore money, prices and order text; return `[]` for pages with no people.
- `parseExtraction` drops people with a blank name and no values, and trims strings. It sets `flagged = modelFlag || !isPlausible(value, unit)`, and `needsReview = gender == 'unknown' || unit == 'unknown' || any value flagged || name.length < 2`.
- `isPlausible`: the value has 1-4 numeric parts separated by `/`, `,` or spaces, where each part is a number with an optional `.5`, `½`, `¼`, `¾` or ` 1/2`. Every part must be within 3..90 for inches, 8..230 for cm, or 3..230 for unknown units.

- [ ] **Step 1: Write the failing tests:**
  - `parse_validArray`: two people round-trip with their fields.
  - `parse_rejectsMalformed`: `"not json"`, `"{}"` and `'[{"name":5}]'` throw `ExtractionParseError`.
  - `parse_emptyArray_returnsEmpty`.
  - `parse_dropsBlankPeople`.
  - `plausibility_acceptsFractionsAndSegments`: `"14½"`, `"14 1/2"`, `"40/38"`, `"16.5"` and `"40, 45, 56"` are plausible in inches.
  - `plausibility_rejects`: `"400"` (inches), `"abc"` and `""`.
  - `needsReview_unknownGender`, `needsReview_flaggedValue`, `needsReview_falseWhenClean`.
  - `prompt_containsEveryCatalogKey`.
  - `catalog_keysExistInBodyProfileTemplate`.
- [ ] **Step 2: Run** `cd functions && npx jest notebook` and check it FAILS.
- [ ] **Step 3: Implement** the four files.
- [ ] **Step 4: Run** `cd functions && npx jest notebook && npm run build` and check it PASSES.
- [ ] **Step 5: Commit** `feat(notebook): extraction prompt, schema and validated parser`

---

### Task 2: Server: page processing trigger, Gemini image call, page cap, retry

**Files:**
- Modify: `functions/src/smart/vertexClient.ts` (add `generateJsonFromImage` to `VertexClient`; the draft path is unchanged)
- Create: `functions/src/notebook/notebookModel.ts` (`NOTEBOOK_MODEL_ID`), `functions/src/notebook/pageCap.ts`, `functions/src/notebook/processPage.ts`
- Modify: `functions/src/index.ts` (export `processNotebookPage`, `retryNotebookPage`)
- Test: `functions/src/__tests__/notebook/processPage.test.ts`, `.../pageCap.test.ts`

**Interfaces:**
- Consumes: Task 1 `buildSystemPrompt`, `RESPONSE_SCHEMA`, `parseExtraction`; `isPaidSubscriber` from `notifications/digestAudience.ts`.
- Produces:
  ```ts
  // VertexClient addition
  generateJsonFromImage(args: { model: string; systemPrompt: string; imageBase64: string; mimeType: 'image/jpeg';
    responseSchema: object; maxOutputTokens?: number }): Promise<string>; // temperature 0, responseMimeType application/json
  export function parseNotebookPath(name: string): { uid: string; jobId: string; pageId: string } | null;
  // matches exactly users/{uid}/notebookImports/{jobId}/{pageId}.jpg
  export type CapResult = { ok: true } | { ok: false; reason: 'page_cap_reached' };
  export async function reservePage(db, uid: string, now: Date): Promise<CapResult>;
  // tx on users/{uid}/usage/notebookImport: { lifetimePages, monthKey: 'YYYY-MM', monthPages }
  export interface ProcessDeps { db; readImage(path: string): Promise<Buffer>; vertex: VertexClient; now: () => Date; }
  export async function processPageHandler(objectName: string, deps: ProcessDeps): Promise<void>;
  export async function retryNotebookPageHandler(data: { jobId?: unknown; pageId?: unknown }, context, deps): Promise<{ status: string }>;
  ```
- Firestore writes:
  - Page doc `users/{uid}/notebookImports/{jobId}/pages/{pageId}`: `{ status: 'processing'|'done'|'failed', error?: 'page_cap_reached'|'read_failed', personCount, storagePath, updatedAt }`.
  - Card docs `.../cards/{pageId}_{i}`: the `ExtractedPerson` fields plus `{ pageId, personIndex: i, status: 'new', matchedCustomerId: null, savedCustomerId: null, createdAt }`.
  - The page doc and its cards are written in one batch.

- [ ] **Step 1: Write the failing tests** (fake db, fake vertex, fake `readImage`):
  - `path_parses` / `path_rejectsOtherPrefixes` (an order photo path and nested extra segments both return `null`).
  - `process_writesCardsAndDone`.
  - `emptyPage_doneWithZeroCards`.
  - `badModelOutput_pageFailedNoCards`, with `error: 'read_failed'`.
  - `vertexThrows_pageFailed`.
  - `finalizeTwice_cardsWrittenOnce`: a second run on a `done` page is a no-op and does not reserve again.
  - `cap_freeLifetime300`: the 301st page fails with `page_cap_reached` and the model is not called.
  - `cap_paidMonthly1000_resetsNextMonth`.
  - `retry_onlyOwnFailedPage`: unauthenticated → `unauthenticated`; a page that isn't failed → `failed-precondition`; a failed page → re-processed.
  - `retry_capFailureDoesNotDoubleCount`.
- [ ] **Step 2: Run** `cd functions && npx jest notebook` and check it FAILS.
- [ ] **Step 3: Implement.**
  - The trigger is `functions.region('europe-west1').runWith({ memory: '1GB', timeoutSeconds: 120 }).storage.object().onFinalize(o => processPageHandler(o.name!, prodDeps))`. It returns immediately when `parseNotebookPath` returns null.
  - It marks the page `processing` before calling the model.
  - Retry is an `onCall` that re-reads the stored image.
- [ ] **Step 4: Run** `cd functions && npx jest && npm run build` and check it PASSES.
- [ ] **Step 5: Commit** `feat(notebook): process uploaded notebook pages with Gemini into review cards`

---

### Task 3: Server: cleanup cron, Firestore and Storage rules

**Files:**
- Create: `functions/src/notebook/cleanup.ts`; export `cleanupNotebookImports` from `index.ts`
- Modify: `firestore.rules` (inside `match /users/{uid}`), `storage.rules:37-41`
- Test: `functions/src/__tests__/notebook/cleanup.test.ts`, `functions/src/__tests__/firestore.rules.test.ts`, the Storage rules test (create one beside it if no Storage rules test exists, using the same emulator harness)

**Interfaces:**
- Produces: `export async function cleanupHandler(deps: { db; deleteObject(path: string): Promise<void>; now: () => Date }): Promise<void>`. Schedule `'15 3 * * *'`, Africa/Lagos.
- Job doc: `{ createdAt, status: 'open'|'saved'|'abandoned', pageCount, imagesDeletedAt?: Timestamp }`. Find jobs with a `collectionGroup('notebookImports')` query and add the index to `firestore.indexes.json`.

**Rules:**
- Firestore:
  - Owner may create, read and update a job doc. On update only `status` may change, and only to `saved` or `abandoned`.
  - Owner may read pages; writes are server-only.
  - Owner may read and delete cards. On update only these fields may change: `name`, `phone`, `gender`, `unit`, `dateWritten`, `values`, `status` (`new`|`checked`|`saved`|`skipped`), `matchedCustomerId`, `savedCustomerId`, `updatedAt`. Card create is server-only.
  - Staff have no access to any of it.
- Storage:
  - Change line 40 so staff read excludes `notebookImports/`.
  - Add `match /users/{uid}/notebookImports/{jobId}/{file}`: owner write only when `request.resource.size < 6 * 1024 * 1024` and `request.resource.contentType == 'image/jpeg'`, and owner delete.

- [ ] **Step 1: Write the failing tests:**
  - `cleanup_deletesImagesAfter7Days` sets `imagesDeletedAt`, and the docs remain.
  - `cleanup_deletesImagesWhenSavedOrAbandoned` runs on the next cron even at day 1.
  - `cleanup_deletesJobTreeAfter30Days`, covering pages and cards.
  - `cleanup_oneFailureDoesNotStopLoop`.
  - Rules:
    - Owner can read cards and update `values`, but cannot update `pageId`.
    - Owner cannot create a card or write a page.
    - Staff of the workshop cannot read a job.
    - In Storage, staff cannot read a page image, and an owner upload of 7 MB or `image/png` is denied.
- [ ] **Step 2: Run** `cd functions && npx jest notebook/cleanup` and the rules suite (`npm run test:rules` or the script in `functions/package.json`). Check that they FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same commands and check that they PASS. If the emulator can't start, say so in the report; do not skip the tests.
- [ ] **Step 5: Commit** `feat(notebook): owner-only rules and 7/30-day cleanup for notebook imports`

---

### Task 4: Accuracy eval tool

**Files:**
- Create: `tools/notebook_eval/README.md`, `tools/notebook_eval/run.ts` (run with `npx ts-node` from `functions/` so it reuses `src/notebook/*` and `vertexClient`), `tools/notebook_eval/score.ts`
- Test: `functions/src/__tests__/notebook/score.test.ts` (score logic only)
- Modify: `.gitignore` (add `tools/notebook_eval/pages/` and `tools/notebook_eval/out/`)

**Interfaces:**
- Input folder: `tools/notebook_eval/pages/<name>.jpg` plus `<name>.truth.json` (an `ExtractedPerson[]` without `flagged` or `needsReview`). Pages are never committed.
- `score(truth: ExtractedPerson[], got: ExtractedPerson[])`: `{ namesCorrect, namesTotal, valuesCorrect, valuesTotal, wrongValues, wrongFlagged }`. People are matched by normalised name (lowercase, collapsed spaces, accents stripped). A value is correct when `fieldKey` matches and the value string is equal after whitespace normalisation.
- `run.ts --model <id>` writes `out/<model>.json` and prints the totals and the ship bar check (values correct ≥ 95%, wrong values flagged ≥ 90%).

- [ ] **Step 1: Write the failing tests** `score_perfect`, `score_wrongValueFlagged`, `score_missingPerson`.
- [ ] **Step 2: Run** `cd functions && npx jest notebook/score` and check it FAILS.
- [ ] **Step 3: Implement** the scorer, runner and README. The README covers how Daniel adds pages and truth files, and that pages never get committed.
- [ ] **Step 4: Run** the tests and check they PASS. The real eval runs when Daniel's pages arrive and is not part of CI.
- [ ] **Step 5: Commit** `feat(notebook): offline accuracy eval for the page reader`

---

### Task 5: Client domain: models, access rule, duplicate matcher, card-to-records mapper

All client paths are under `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/notebookimport/`.

**Files:**
- Create: `domain/NotebookImportModels.kt`, `domain/NotebookImportRepository.kt`, `domain/NotebookImportAccess.kt`, `domain/DuplicateMatcher.kt`, `domain/CardToRecords.kt`
- Test: `commonTest/.../feature/notebookimport/domain/*Test.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class CardStatus { NEW, CHECKED, SAVED, SKIPPED }
  enum class PageStatus { UPLOADING, PROCESSING, DONE, FAILED }
  data class CardValue(val label: String, val value: String, val fieldKey: String?, val flagged: Boolean)
  data class PageBox(val x: Float, val y: Float, val w: Float, val h: Float)
  data class ReviewCard(val id: String, val jobId: String, val pageId: String, val name: String, val phone: String?,
      val gender: CustomerGender?, val unit: MeasurementUnit?, val dateWritten: LocalDate?, val values: List<CardValue>,
      val box: PageBox?, val needsReview: Boolean, val status: CardStatus,
      val matchedCustomerId: String?, val savedCustomerId: String?)
  data class ImportPage(val id: String, val status: PageStatus, val personCount: Int, val error: String?, val localThumb: ByteArray?)
  data class ImportJob(val id: String, val createdAt: Instant, val status: String, val pageCount: Int, val imagesDeletedAt: Instant?)

  interface NotebookImportRepository {
      suspend fun createJob(ownerUid: String): Result<String, DataError.Network>
      suspend fun uploadPage(ownerUid: String, jobId: String, jpeg: ByteArray): Result<String, DataError.Network>
      fun observePages(ownerUid: String, jobId: String): Flow<Result<List<ImportPage>, DataError.Network>>
      fun observeCards(ownerUid: String, jobId: String): Flow<Result<List<ReviewCard>, DataError.Network>>
      fun observeOpenJob(ownerUid: String): Flow<ImportJob?>   // newest job with status open, for resume
      suspend fun updateCard(ownerUid: String, card: ReviewCard): EmptyResult<DataError.Network>
      suspend fun deleteCard(ownerUid: String, jobId: String, cardId: String): EmptyResult<DataError.Network>
      suspend fun retryPage(jobId: String, pageId: String): EmptyResult<DataError.Network>
      suspend fun finishJob(ownerUid: String, jobId: String, abandoned: Boolean): EmptyResult<DataError.Network>
  }

  fun canStartNotebookImport(session: WorkshopSession, entitlements: UserEntitlements): Boolean // = session.isOwner today

  object DuplicateMatcher {
      fun normaliseName(name: String): String  // lowercase, trim, collapse spaces, strip accents, drop titles mr/mrs/miss/madam/mama/alhaji/chief
      fun findExisting(card: ReviewCard, customers: List<Customer>, country: Country): Customer?
      // phone match (normalisePhone on both, non-blank) wins; else exact normalised name match; else null
      fun groupWithinImport(cards: List<ReviewCard>): List<List<String>> // card-id groups with equal normalised name and no conflicting phone
  }

  data class RecordsToSave(val customer: Customer?, val measurement: Measurement)
  fun cardToRecords(card: ReviewCard, ownerUid: String, existingCustomerId: String?, importDate: LocalDate, defaultUnit: MeasurementUnit): RecordsToSave
  fun importCustomerId(jobId: String, cardId: String): String     // "nb_" + jobId + "_" + cardId
  fun importMeasurementId(jobId: String, cardId: String): String  // "nbm_" + jobId + "_" + cardId
  ```

**Mapper rules:**
- `customer` is null when `existingCustomerId != null`. Otherwise it is `Customer(id = importCustomerId(...), userId = ownerUid, name = card.name.trim(), phone = normalised phone or "")`.
- The measurement has:
  - `fields`: `fieldKey → value` for keyed values. When a key repeats, the first one wins and the rest go to notes.
  - `notes`: unmatched values as `"label: value"` lines.
  - `unit`: `card.unit ?: defaultUnit`.
  - `dateTaken`: `card.dateWritten ?: importDate`.
  - `gender`: `card.gender`, of the existing `CustomerGender` type. It must be non-null here; the review UI forces a choice.
- `dateTaken` and `createdAt` are epoch milliseconds (`Long`), as in `Measurement`.

- [ ] **Step 1: Write the failing tests:**
  - `access_ownerTrue_staffFalse`.
  - `normaliseName_stripsTitlesAccentsCase` (`"  Mrs. Adébáyọ̀  Bisi"` → `"adebayo bisi"`).
  - `findExisting_phoneBeatsName`, `findExisting_nameOnly`, `findExisting_none`.
  - `groupWithinImport_samePersonTwoPages`.
  - `groupWithinImport_conflictingPhonesNotGrouped`.
  - `mapper_newCustomerDeterministicIds`.
  - `mapper_existingCustomer_noCustomerRecord`.
  - `mapper_unmatchedToNotes`.
  - `mapper_duplicateKeyToNotes`.
  - `mapper_dateFallsBackToImportDate`.
  - `mapper_unitFallsBackToDefault`.
- [ ] **Step 2: Run** `./gradlew :composeApp:testDebugUnitTest --tests "*notebookimport*"` and check it FAILS.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same command and check it PASSES.
- [ ] **Step 5: Commit** `feat(notebook): import domain models, duplicate matcher and record mapper`

---

### Task 6: Client data: Firebase repository and the saver

**Files:**
- Create: `data/FirebaseNotebookImportRepository.kt`, `data/NotebookImportDtos.kt` (+ mappers), `domain/NotebookImportSaver.kt`
- Create: `di/NotebookImportModule.kt`; register it where the other feature modules are assembled
- Test: `commonTest/.../feature/notebookimport/domain/NotebookImportSaverTest.kt`, `.../data/NotebookImportMapperTest.kt`

**Interfaces:**
- Consumes: Task 5 interfaces; `CustomerRepository.createCustomer`, `MeasurementRepository.createMeasurement`; Storage upload as in `FirebaseOrderRepository.kt:915` with `ByteArray.toStorageData()` (`feature/style/data/StorageData.kt:5`); the Functions instance from `di/SmartModule.kt:28` for the `retryNotebookPage` callable.
- Produces:
  ```kotlin
  sealed interface SaveOutcome {
      data class Done(val savedCount: Int) : SaveOutcome
      data class StoppedAtCap(val savedCount: Int, val remaining: Int) : SaveOutcome
      data class Failed(val savedCount: Int, val error: DataError.Network) : SaveOutcome
  }
  class NotebookImportSaver(customerRepository: CustomerRepository, measurementRepository: MeasurementRepository,
      importRepository: NotebookImportRepository, clock: Clock) {
      suspend fun save(ownerUid: String, cards: List<ReviewCard>, defaultUnit: MeasurementUnit): SaveOutcome
  }
  ```

**Saver rules:**
- Cards are processed in order, one at a time. Skip any card already `SAVED` or with `savedCustomerId` set.
- The customer id is `card.matchedCustomerId ?: importCustomerId(...)`.
- Create the customer only when it isn't matched. Then create the measurement with `importMeasurementId`, then update the card to `SAVED` with `savedCustomerId`.
- `CAP_REACHED` returns `StoppedAtCap`. Any other error returns `Failed`. Both stop the run.
- `uploadPage` writes to `users/{uid}/notebookImports/{jobId}/{pageId}.jpg`, where `pageId` is `p` + zero-padded upload index + `_` + 8 random hex characters.

- [ ] **Step 1: Write the failing tests** (fakes for all three repositories):
  - `save_newAndMatchedCards`.
  - `capReached_stopsAndKeepsRest`: the third card hits the cap → `StoppedAtCap(2, n-2)`, and later cards are untouched.
  - `saveRetry_sameIdsNoDuplicate`: the customer create succeeded but the card update failed; a second `save` reuses the same ids and the fake records each id once.
  - `skipsSavedCards`.
  - `failed_otherError`.
  - DTO mapper round-trip for a card, including a null box and a null gender.
- [ ] **Step 2: Run** `./gradlew :composeApp:testDebugUnitTest --tests "*notebookimport*"` and check it FAILS.
- [ ] **Step 3: Implement** the saver, the repository and the Koin module (`singleOf(::FirebaseNotebookImportRepository) { bind<NotebookImportRepository>() }`, `factoryOf(::NotebookImportSaver)`).
- [ ] **Step 4: Run** the tests plus `./gradlew :composeApp:assembleDebug` and check they PASS.
- [ ] **Step 5: Commit** `feat(notebook): Firebase import repository and idempotent saver`

---

### Task 7: Capture screen (intro, snap pages, upload progress)

**Files:**
- Create: `presentation/capture/NotebookCaptureState.kt`, `...Action.kt`, `...Event.kt`, `NotebookCaptureViewModel.kt`, `NotebookCaptureScreen.kt` (Root + Screen + @Preview for empty, three pages uploading, and a failed page)
- Modify: `composeResources/values/strings.xml`, `values-fr/strings.xml`
- Test: `commonTest/.../presentation/capture/NotebookCaptureViewModelTest.kt`

**Behavior:**
- On open, resume `observeOpenJob`; otherwise create a job lazily on the first photo.
- "Take photo" uses `rememberImageCaptureLauncher`, and "Choose from gallery" uses the Peekaboo multi-picker. Each photo is compressed with the values from Global Constraints, shown as a thumbnail with its status, and uploaded.
- Failed pages show Retry, which calls `retryPage` for server failures and re-uploads for upload failures. A `page_cap_reached` page shows the cap message instead of Retry.
- "Review N customers" is enabled once at least one page is `DONE` and navigates to Review. The tailor can leave the screen while pages are processing.

**Copy (EN):**
- `notebook_capture_title`: "Scan your notebook"
- `notebook_capture_intro`: "Take a clear photo of each page. We read the names and measurements, then you check them before anything is saved."
- `notebook_capture_take_photo`: "Take photo"
- `notebook_capture_from_gallery`: "Choose from gallery"
- `notebook_capture_reading`: "Reading page %1$d"
- `notebook_capture_no_people`: "No customers found on this page"
- `notebook_capture_failed`: "Could not read this page"
- `notebook_capture_retry`: "Try again"
- `notebook_capture_cap`: "You have reached the page limit for scanning"
- `notebook_capture_review`: "Review %1$d customers"

FR: translate these with the same terms used elsewhere in `values-fr`.

- [ ] **Step 1: Write the failing tests:**
  - `firstPhoto_createsJobAndUploads`.
  - `resume_openJobPagesShown`.
  - `uploadFailure_showsRetryAndReuploads`.
  - `serverFailure_retryCallsRetryPage`.
  - `capFailure_noRetry`.
  - `reviewEnabled_afterFirstDone`.
  - `staff_cannotStart` (`canStartNotebookImport` false → `NavigateBack` event).
- [ ] **Step 2: Run** `./gradlew :composeApp:testDebugUnitTest --tests "*NotebookCapture*"` and check it FAILS.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the tests and check they PASS.
- [ ] **Step 5: Commit** `feat(notebook): capture screen to snap and upload notebook pages`

---

### Task 8: Review list and card editor

**Files:**
- Create:
  - `presentation/review/NotebookReview{State,Action,Event,ViewModel,Screen}.kt` (list)
  - `presentation/card/NotebookCard{State,Action,Event,ViewModel,Screen}.kt` (one card, with the photo crop)
- Modify: strings EN and FR
- Test: `NotebookReviewViewModelTest.kt`, `NotebookCardViewModelTest.kt`

**Behavior:**
- **List:**
  - The header reads "%1$d customers found, %2$d need a look". Sections: "Need a look" (`needsReview` and not saved), "Ready" (`CHECKED`), "Saved", "Skipped".
  - A card with a `DuplicateMatcher.findExisting` hit shows "Matches ‹name›". Groups from `groupWithinImport` show "Same person as ‹name›?", with Merge (keep the first card, append the other's values, delete the other card).
  - "Save all checked" runs the saver on `CHECKED` cards only. The `StoppedAtCap` outcome emits an event that opens the existing `CustomerCapReachedSheet` and upgrade route.
  - "Discard import" asks for confirmation, then calls `finishJob(abandoned = true)` and deletes the images.
  - When every card is `SAVED` or `SKIPPED`, call `finishJob(abandoned = false)`.
- **Card editor:**
  - Shows the page image cropped to `box`, or the full page when `box` is null. Load the image from Storage while `imagesDeletedAt` is unset; otherwise show "Photo no longer available".
  - Editable fields: name, phone, gender (required), unit, date, and each value. A value's field key can be changed with the existing field labels.
  - Flagged values are highlighted with the design system's warning colour.
  - The matched-customer toggle reads "Add to ‹existing›" or "Create new customer".
  - Actions: "Looks right" sets `CHECKED`; "Save now" sets `CHECKED` and saves this card; "Skip" sets `SKIPPED`.
  - "Looks right" is disabled until a gender is chosen.
- Measurement values use JetBrains Mono, matching the existing measurement screens.

- [ ] **Step 1: Write the failing tests:**
  - `list_countsAndSections`.
  - `list_saveAllChecked_onlyChecked`.
  - `list_capOutcome_emitsCapEvent`.
  - `list_merge_combinesValuesDeletesOther`.
  - `list_allDone_finishesJob`.
  - `list_discard_abandons`.
  - `card_editValue_persists`.
  - `card_looksRight_requiresGender`.
  - `card_saveNow_savesOne`.
  - `card_matchedToggle_setsMatchedCustomerId`.
- [ ] **Step 2: Run** `./gradlew :composeApp:testDebugUnitTest --tests "*NotebookReview*" --tests "*NotebookCard*"` and check it FAILS.
- [ ] **Step 3: Implement**, with @Previews for each state.
- [ ] **Step 4: Run** the tests and check they PASS.
- [ ] **Step 5: Commit** `feat(notebook): review list and card editor`

---

### Task 9: Routes and the three entry points

**Files:**
- Modify: `navigation/Routes.kt` (`NotebookCaptureRoute`, `NotebookReviewRoute(jobId: String)`, `NotebookCardRoute(jobId: String, cardId: String)`), `feature/main/presentation/MainScreen.kt` (composables)
- Modify: the `feature/customer/presentation/list/CustomerList*` files ("Import from notebook" action in the top bar overflow and in the empty state, owner only)
- Modify: the `feature/dashboard/presentation/Dashboard*` files (a dismissible card "Have customers in a notebook? Scan them in", shown to owners with fewer than 5 active customers until dismissed; plus a one-time event on the first `BrandNew` dashboard after setup that navigates to `NotebookCaptureRoute`)
- Create: `core/domain/preferences/NotebookImportPreferencesStore.kt` (`suspend fun introShown(): Boolean`, `suspend fun setIntroShown()`, `fun observeCardDismissed(): Flow<Boolean>`, `suspend fun dismissCard()`), with the implementation following `ReceiptImagePreferences` (expect/actual)
- Test: `DashboardViewModelTest` and `CustomerListViewModelTest` additions

**Copy (EN):**
- `notebook_entry_customers`: "Import from notebook"
- `notebook_entry_dashboard_title`: "Have customers in a notebook?"
- `notebook_entry_dashboard_body`: "Snap the pages and we bring them in for you."
- `notebook_entry_dashboard_cta`: "Scan them in"

FR: translate these as in Task 7.

- [ ] **Step 1: Write the failing tests:**
  - `dashboard_brandNewOwner_firstTime_navigatesToCaptureOnce` (a second load does not navigate).
  - `dashboard_staff_noCardNoNav`.
  - `dashboard_cardHiddenAt5Customers`.
  - `dashboard_dismissPersists`.
  - `customerList_importVisibleForOwnerOnly`.
- [ ] **Step 2: Run** the dashboard and customer list tests and check they FAIL.
- [ ] **Step 3: Implement**, including routes and back stack: Capture → Review → Card, and back returns along the same path.
- [ ] **Step 4: Run** `./gradlew :composeApp:allTests detekt :composeApp:assembleDebug` and `./gradlew :composeApp:compileKotlinIosSimulatorArm64`, and check they PASS.
- [ ] **Step 5: Commit** `feat(notebook): entry points from setup, dashboard and customers`

---

### Task 10: QA doc and release notes

**Files:**
- Create: `docs/qa/2026-11-24-notebook-scan-smoke.md`
- Modify: `docs/handoff/2026-11-13-NEXT-WORK.md` (add a status line under §2 Faster recording)

**Smoke checklist (Daniel, on device):**
- A 10-page mixed sample on Android and iOS.
- Close the app mid-processing and reopen: the import resumes.
- Review, fix a flagged value, merge a two-page person, add to an existing customer, then save all.
- Check the customer, measurement fields and notes in the app.
- A FREE account past the welcome window stops at the cap with the upgrade sheet.
- Staff login: no entry points.
- After `finishJob`, the images are gone after the next cleanup run.

**Deploy order:**
1. `firestore.rules`, `storage.rules` and indexes.
2. The functions `processNotebookPage`, `retryNotebookPage` and `cleanupNotebookImports`, using an explicit `--only` list.
3. The app build.

Check that the Vertex model id in `notebookModel.ts` is enabled in project `stitchpad-30607`.

- [ ] **Commit** `docs(notebook): smoke checklist and deploy order`
