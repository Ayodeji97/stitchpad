# Notifications — Slice 1: Daily Email Digest (design)

> **Date:** 2026-06-03
> **Status:** approved design, pre-implementation
> **Slice:** 1 of 3 in the notifications feature (email → in-app → push)
> **Branch:** `feature/notifications-email-digest`

## Context

StitchPad has no notification surface today. There is **no FCM/APNs**, no scheduled
Cloud Functions, and no in-app notification collection. Transactional email already
works: `sendVerificationEmail` sends a branded email via **Resend** from a Cloud
Function, with a pure, unit-tested HTML builder (`verificationEmailTemplate.ts`).

The full feature spans three delivery channels for one underlying idea — "an event
the tailor should know about happened." This spec covers **only the first slice:
a daily email digest**, chosen because it is the lowest-risk channel (proven Resend
path, no new vendor, no platform-specific client code) and it serves the stated goal,
**operational reliability** — making sure a tailor never misses a real obligation even
with the app closed.

### Goal & sequencing

- **Primary goal:** operational reliability (don't miss deadlines / owed money), via
  the lowest-risk slice first that also establishes the reusable backbone.
- **Sequence:** PR1 = backbone + email (this spec) → PR2 = in-app inbox (renders a
  notifications collection, reuses the detector) → PR3 = push (FCM/APNs prerequisite,
  same events).
- **Recipient:** the **tailor** (the app's single user), about their own business.
  Customer-facing comms stays on WhatsApp (Smart Draft). These are tailor-facing
  operational emails.

## Design summary

A single scheduled Cloud Function wakes once each morning (≈07:00 Africa/Lagos),
scans each tailor's orders, and — **only if something is actionable** — sends one
digest email. Most order "events" are tailor-initiated (they marked it READY, they
recorded a payment), so the operationally valuable signals are the **time-driven**
ones the tailor can't hold in their head: deadlines and owed money.

### V1 event buckets

| Bucket | Definition (dates compared as **Africa/Lagos calendar dates**) |
|---|---|
| **A — Due soon** | open order, `deadline != null`, deadline's Lagos date ∈ {today, tomorrow} |
| **B — Overdue** | open order, `deadline != null`, deadline's Lagos date < today |
| **C — Outstanding balance** | `status ∈ {READY, DELIVERED}` AND `balanceRemaining ≥ ₦1` |

"Open" = `status != DELIVERED` AND `archivedAt == null`. A/B partition cleanly (an
order is due-soon **or** overdue, never both). Null-deadline orders never appear in
A/B. C is orthogonal.

**Decision — C covers READY and DELIVERED.** Flags both "ready, collect the balance on
pickup" (READY) and the higher-risk "delivered and still owing" / out-of-sight money
(DELIVERED). In-progress orders are excluded (a deposit-in-progress balance is normal,
not actionable). The `≥ ₦1` floor avoids flagging rounding residue from
`totalPrice − payments`.

**Honest note:** A + B are the operational-reliability core. C is revenue-dunning and
is the softest of the three — it's in V1 by choice, and is the first candidate to cut
if the PR grows.

### Form

- **Daily digest, one email per tailor**, ≈07:00 Africa/Lagos. Daily (not weekly) is
  required so a "due tomorrow" order gets >12h notice.
- **Suppress-when-empty:** if all three buckets are empty, send nothing. No "all clear"
  emails — critical so tailors don't learn to ignore the digest.
- **Timezone:** every tailor treated as `Africa/Lagos` (hardcoded), consistent with how
  the app already handles dates. Per-user timezone deferred.
- Single morning send means **no quiet-hours logic** is needed in V1.

### Preferences

- **On by default, opt-out.** Operational reliability means a tailor shouldn't have to
  discover a setting to stop missing deadlines. Defensible as transactional (about the
  tailor's own active orders).
- **Single master toggle** (one digest in V1 ⇒ no per-event granularity yet).
- **Unsubscribe — Settings toggle only for V1.** A true one-click unsubscribe needs an
  unauthenticated HTTPS endpoint + signed token (real surface) for little V1 value given
  this is transactional email to a verified account holder. The email footer points to
  Settings. A tokenised `List-Unsubscribe` endpoint is a fast-follow if deliverability
  ever needs it.

## Architecture (Approach 3: direct-send, pure detector)

Chosen over (1) plain direct-send and (2) a persistent notification collection. Rationale:
the genuinely reusable part is the **event-detection logic**, so that becomes a pure,
tested module; persistence is deferred to the in-app slice when its real shape is known
(avoids guessing the collection schema). The daily-snapshot model is naturally
**idempotent** — recomputed fresh each morning from current state, so a 5-day-overdue
order simply reappears until resolved, with zero dedup bookkeeping. That self-reminding
behaviour is correct for operational reliability and is a direct argument against a
persistent event-log approach.

Mirrors existing repo patterns: a pure tested calculator (`freeTierCounter.ts`), a pure
tested template builder (`verificationEmailTemplate.ts`), and a thin function on top.

### File layout

**Backend (`functions/`, TypeScript, Node 20, `europe-west1`):**

```
functions/src/
  notifications/
    dailyDigest.ts          # onSchedule fn — thin orchestration only
    digestDetector.ts       # PURE: (orders, now, tz) -> DigestModel  ← reusable backbone
    digestEmailTemplate.ts  # PURE: (DigestModel, tailorName) -> { subject, html, text }
    types.ts                # OrderScanDoc, DigestModel, bucket item types
  email/
    resendClient.ts         # extracted shared sendResendEmail({to,subject,html,text})
functions/__tests__/notifications/
    digestDetector.test.ts
    digestEmailTemplate.test.ts
```

- This is the **first `onSchedule` function** in the repo (firebase-functions v2 scheduler,
  supported by `^6.0.1`). Establishes the scheduled-function pattern reused by push (PR3).
- `index.ts` exports `dailyDigest`; the deploy `--only` list in `functions/package.json`
  **must** add `functions:dailyDigest` (known gotcha: a missing entry silently skips deploy —
  confirm with `firebase functions:list`).

**Client (`composeApp/`, KMP) — opt-out toggle only:**

- `User` gains `dailyDigestEmailEnabled: Boolean = true`; matching `UserDto` field
  (default-true on read so legacy docs still send) + mapper line.
- "Daily summary email" switch row in `feature/settings/presentation/home/` (extend the
  existing `SettingsViewModel` / `State` / `Action`).
- Write path through `core/data/repository/FirebaseUserRepository.kt`.

### Data flow

1. Cloud Scheduler fires `dailyDigest` at `0 7 * * *`, `timeZone: "Africa/Lagos"`.
2. Read `users` (Admin SDK). Per user: skip if `dailyDigestEmailEnabled === false`
   (missing/true ⇒ send), skip if no `email`.
3. **Rollout gate:** while staging, only proceed for users on a tester allowlist
   (see Rollout). Flip to all-users after verification.
4. **Idempotency:** skip if `users/{uid}/private/digestState.lastSentDate` equals today's
   Lagos date (guards scheduler retries / manual re-runs).
5. Load the tailor's open orders + delivered-with-balance orders.
6. `digestDetector(orders, now, "Africa/Lagos")` → `DigestModel`.
7. **Suppress-when-empty:** all buckets empty ⇒ continue, send nothing.
8. `digestEmailTemplate(model, businessName ?? displayName)` → `sendResendEmail()` to
   `user.email`.
9. On success, stamp `digestState.lastSentDate`.
10. **Per-tailor try/catch:** one tailor's failure logs and continues — never aborts the
    batch.

**Scale note:** V1 iterates `users` (tens of testers — trivial). Log the cohort size; leave
a comment marking pagination / `collectionGroup("orders")` as the scale path. No silent cap.

### The detector contract (pure)

```
digestDetector(orders: OrderScanDoc[], now: number, tz: string): DigestModel
```

No I/O; fully Jest-testable. `DigestModel = { dueSoon: Item[], overdue: Item[], outstanding: Item[] }`.
Each `Item = { customerName, garmentSummary, deadline?/amount? }`, sorted most-urgent-first.
Lists **capped at 5 per bucket**, with a `+N more` count surfaced in the email — never a
silent truncation. `isEmpty` = all three buckets empty.

### Email template (pure)

`digestEmailTemplate(model, tailorName)` → `{ subject, html, text }`, reusing the Adire
Atelier system from `verificationEmailTemplate.ts` (white/indigo, serif headline,
Storage-hosted logo, plain-text fallback alongside HTML).

- **Subject** front-loads urgency from non-empty buckets in priority order
  (overdue → due-soon → balance), e.g. *"StitchPad: 1 overdue, 2 due tomorrow"*.
- **Body** renders only non-empty buckets; each a short titled list (customer · garment ·
  date/amount); `+N more` when capped.
- **Greeting** uses `businessName ?? displayName`.
- **Footer:** "You're getting this because daily summaries are on. Turn them off in
  Settings → Notifications."

### Preferences + Settings (client)

- Toggle writes `dailyDigestEmailEnabled` via `FirebaseUserRepository` through the
  fire-and-forget offline outbox. The VM applies an optimistic override so the switch
  flips instantly; the offline outbox + snapshot listener handle persistence.
- **No Snackbar on toggle** (revised during implementation): the switch flipping *is* the
  feedback, and this matches the sibling Preferences toggles (measurement unit, appearance),
  which are also silent fire-and-forget. A write that can't surface a real failure (the
  outbox swallows transient Firestore errors by design) shouldn't claim success/failure via
  a Snackbar. This intentionally diverges from the original spec's Snackbar-on-change note.
- Toggle ships in **PR1** — shipping a recurring email with no off-switch is not acceptable,
  and the client change is small (one bool + one switch row).

## Error handling

- **Per-tailor isolation:** each scan+send in its own try/catch; log and continue. Mirrors
  how `smartDraftMessage` contains/masks failures.
- **Resend extraction:** pull the inline `POST https://api.resend.com/emails` out of
  `sendVerificationEmail.ts` into `email/resendClient.ts`, shared by both senders.
  *Risk:* touches the freshly-shipped (#109) verification path. Mitigation: keep the change
  mechanical and add a unit test for the extracted client so verification can't silently
  regress.
- **Function-level:** only throw if the run itself is broken (e.g. can't list users), so
  Cloud Scheduler surfaces a real failure; individual sends never throw out.
- Secret: same `RESEND_API_KEY` via `.runWith({ secrets: ['RESEND_API_KEY'] })`.

## Rollout (the real risk)

The moment the scheduler goes live, a single detector bug (timezone-boundary off-by-one,
a just-delivered order miscounted as overdue) emails **every tester a wrong digest every
morning** — eroding trust and Resend sender reputation right before launch. Therefore:

1. Ship the function with sends **gated to a tester allowlist** (the test accounts).
2. Verify via the debug "send now" trigger and a couple of real scheduled mornings against
   test accounts (Fola).
3. Only then flip the gate to all users.

## Debug menu entry (per per-feature debug rule)

- **"Send daily digest now"** — runs the scan for the current user on demand.
- **"Reset digest lastSentDate"** — clears the idempotency stamp to allow re-trigger.
- Debug-source-set only.

## Testing

- **Jest (gates CI `functions-tests`):**
  - `digestDetector` — null deadline; due-today vs due-tomorrow vs overdue boundaries across
    the Lagos day cutoff; archived/delivered exclusion; balance `< ₦1` floor; empty ⇒
    suppressed; list capping + `+N more`.
  - `digestEmailTemplate` — omits empty buckets; renders `+N more`; subject assembly order.
  - `resendClient` — payload shape / auth header (regression guard for verification path).
- **Client:** `SettingsViewModel` test for the toggle (Turbine, `:composeApp:testDebugUnitTest`).
- **iOS compile** before declaring done (`:composeApp` iOS target).
- **Manual smoke test (Daniel is QA):** seed for Fola — one order due tomorrow, one overdue,
  one delivered-with-balance — invoke `dailyDigest` manually; confirm one email with all
  three sections; toggle off, re-run, confirm suppressed; seed nothing actionable, confirm
  empty-suppression. Steps go in the PR description.

## Out of scope (this slice)

- In-app notification collection / inbox / unread badges (PR2).
- Push (FCM/APNs), device tokens (PR3).
- Per-event preference toggles, configurable send time, per-user timezone.
- Weekly Smart Grow re-engagement nudges (different goal — re-engagement, not reliability).
- True tokenised unsubscribe endpoint (`List-Unsubscribe`).

## Review

Per the review rotation: both **Cursor Bugbot** (auto) and **`codex review`** (pre-push hook)
before merge. Watch for the cross-cutting bugs Cursor reliably catches: client/server constant
drift (the default-on flag semantics), timezone/day-boundary errors, and plural grammar in the
digest copy.
