# Notebook Scan Import: Design

**Status:** approved by Daniel 2026-11-24; building now for the next app release.

## Context and why

Many tailors already keep years of customers in paper notebooks: names, phone numbers and measurements.
Typing them into StitchPad one by one is the biggest barrier to switching, so they stay on paper.
With the scanner, a tailor photographs their notebook pages and StitchPad brings the customers and
measurements in for them. The aim is to make moving in feel like minutes, not days. This matters more now
that StitchPad is strictly paid with a 30-day trial: a tailor whose whole book is inside the app by
day 2 is far more likely to pay on day 30.

**Success:** a new tailor imports most of their notebook during the trial, with very few wrong numbers.

## Goals

- Photograph one or many notebook pages and get customers (name, phone if written) and their measurements out of them.
- Read any layout: one page per customer, running diaries, tables and shorthand ("Ch", "L/S", "Rd"). Mostly English, sometimes Yoruba, Hausa, Igbo, Pidgin or French words, in inches or cm.
- **Quick check before saving (Daniel):** nothing is saved until the tailor has seen it. The review shows each reading next to the part of the photo it came from, and highlights uncertain values.
- **Two modes (Daniel: both):**
  - Move-in: a whole notebook (50-200 customers) in one sitting, processed in the background, reviewed in as many sittings as the tailor likes.
  - Single page: a quick scan later, whenever something new was written on paper.
- **Entry points (Daniel):**
  - A skippable step right after workshop setup ("Have customers in a notebook? Scan them in").
  - An "Import from notebook" button on the Customers list and its empty state.
  - A dashboard card shown for the first days to tailors with few customers.
- **Free during the trial (Daniel)**, with a generous per-account page cap to stop abuse.

## Non-goals (this version)

- Orders, due dates, prices and balances. Daniel chose names and measurements only; orders stay manual.
- Style sketches or fabric photos from the notebook.
- Live camera page detection and auto-capture. Normal photos are enough.
- Creating custom measurement fields automatically.

## Anti-goals (a failure even if it "works")

- A wrong number saved without the tailor having seen it.
- Duplicate customers created for people who already exist.
- Customers' notebook photos kept after the import is finished, or used for anything else.
- An import that dies halfway and loses what was already reviewed.

## How it works (decided)

1. **Capture.** The tailor takes photos (reusing `rememberImageCaptureLauncher`) or picks them from the gallery (the Peekaboo picker already used for style photos). Each photo goes through the existing `ImageCompressor`. I'll check whether its 1920 px longest edge is enough for handwriting and raise it for this path only if the test pages say so. Pages upload to Storage under the owner's path, using the existing upload and offline-outbox pattern.
2. **Read on the server.** An import job (`users/{uid}/notebookImports/{jobId}`, one doc per page) is processed by a function. The function sends each page image to Gemini on Vertex through the existing `functions/src/smart/vertexClient.ts`, extended to accept an image. It uses a stronger multimodal Gemini model than the draft model; the builder picks the exact id by testing on the real pages. The function asks for structured JSON of people, each with: name, phone, a gender guess, a unit guess, and a list of (label as written, value as written, matched StitchPad field key or none, confidence). It also returns rough positions on the page, so the review can crop the photo. The prompt is given the field keys and labels from `BodyProfileTemplate`, including the French labels.
3. **Duplicate check.** Each person found is compared with the tailor's existing customers by normalised name, and by phone through the existing phone normaliser, and also with other people in the same import. A likely match is offered as "Add these measurements to ‹existing customer›?" rather than created again.
4. **Review.** When the job is done, the tailor sees "142 customers found, 9 need a look".
   - Each card shows name, phone, gender, unit and the fields, with a photo crop next to them. Low-confidence values are highlighted.
   - The tailor can edit anything, merge two cards, or skip a card.
   - Save goes card by card, or "Save all checked".
   - Review progress is kept, so they can stop and come back.
5. **Save.** The client creates customers and measurements through the existing `CustomerRepository.createCustomer` and `MeasurementRepository.createMeasurement`. Those calls run in a sequential batch, which respects the current cap and rules logic. `dateTaken` is the date written on the page if one was read, otherwise the import date. Labels that didn't match a field go into the measurement's `notes` as "Label: value", so nothing is lost.
6. **Clean up.** Page images are deleted once the job is fully saved or abandoned, or after a fixed time limit. Extracted text is deleted along with the job.

## Constraints

- Follow CLAUDE.md patterns: MVI with Root/Screen, Result<T, E>, UiText, string resources in EN and FR, Koin, a @Preview for every screen. No em dashes in copy.
- Server AI calls are signed-in only and checked against a per-account page cap, using a transaction-based counter like `reserveFreeTierSlot` in `functions/src/smart/draftMessage.ts`.
- Scanning writes records, so a read-only account (trial ended, unpaid) cannot start a new import. A job that is already finished can still be viewed but not saved.
- Customer caps follow today's rules (see "Who can scan"). When the trial work arrives, it must let a trial account do a full move-in.
- Photos and extracted data are personal data. Owner-only rules on the Storage path and the import docs, no logging of image content or names, and the deletion described above.

## Who can scan, and when it ships (Daniel, 2026-11-24)

- **Ship sooner, on today's rules.** Don't wait for the trial/read-only work (expansion 6a). Put every
  access decision in a single domain function, `canStartNotebookImport(entitlements): Boolean`, plus the server-side
  page-cap check, so the trial work changes one place later.
- **Everyone active can scan:**
  - Paying users, `launch_free` grant holders and users inside the welcome window: full import, subject to the page cap.
  - A FREE user past the window can scan and review, but saving stops at the existing 15-customer cap. Saving
    is blocked by the existing `shouldBlockActiveCustomerCreate`, which shows `CustomerCapReachedSheet` and the
    upgrade path. Their checked cards stay saved in the job.
  - When 6a lands: trial = full, read-only = no new imports (as above).
- The scan import must not compete with the paywall deadlines (29 Nov, ~18 Dec, 6 Jan). Order of work is Daniel's call.

## Calls settled after the builder check (mine, Daniel can override)

- **Owner only** in this version: staff don't see the entry points. Jobs, Storage paths and the page counter are keyed by the owner's uid.
- **Existing customer matched:** always create a **new** measurement record (dated), never fill in or overwrite an old one. A person found on several dated diary entries gets one measurement per date.
- **Lifetime:** page images are deleted after 7 days, or earlier once the job is saved or abandoned. The checked text of the cards is kept for 30 days, without the photo crops, so a tailor who hits a limit can still save after paying. Image lifetime and review state are kept separate in the data model.

## Left to the builder (my calls, Daniel can override)

- The exact Gemini model, the prompt wording, and the JSON shape.
- The trial page cap, set around 300 pages per account. Paid accounts are uncapped, with a sanity limit.
- The image time limit (7 days) and the duplicate-matching rules.
- The review screen layout, which will be mocked up before it is built.
- Whether pages are processed one by one as they upload, or as a batch.

## Verification

- **Accuracy test before release:**
  - Daniel collects 20-30 real pages from 3-5 tailors, with their permission. They are stored outside the repo and never shipped.
  - A script runs the reader on these pages and compares the output with typed-in correct answers.
  - It reports how many names and values were read correctly, and how many of the wrong ones were flagged as uncertain.
  - Ship bar (proposed): 95% or more of values correct, and 90% or more of the wrong values flagged.
- **Tests:**
  - Unit tests for the parser and field matching (abbreviations to keys), the duplicate matcher, and notes for unmatched labels.
  - ViewModel tests for review states: editing, skipping, merging, resuming.
  - Functions tests for the job handler with a stubbed model, the page cap, read-only refusal, and deletion.
  - Rules tests for owner-only access.
- **Device run:** import a 10-page sample on Android and iOS, review it, save it, and check the customers, measurements and notes. Then check that the photos are gone after clean-up.
