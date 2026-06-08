# Constant-time password-reset email (Cloud Tasks)

**Status:** in progress · **Date:** 2026-06-07 · **Follows:** PR #133 (branded reset email)

## Problem

`sendPasswordResetEmail` is an **unauthenticated** callable (users are locked out, so there's
no uid to authenticate). PR #133 made it **payload-safe** — registered and unregistered emails
return an identical `{ ok: true }`. But a **timing side-channel** remains: a registered email
additionally runs `generatePasswordResetLink` + a Resend HTTP send, so it responds measurably
slower than an unknown email. By timing the response, an unauthenticated caller can enumerate
which addresses have a StitchPad account.

The 60s email-keyed throttle makes this impractical at beta scale (~1 noisy sample/email/min),
but we are ~4 weeks from **public launch**, where a public signup surface makes account
enumeration a live threat (targeted phishing, privacy). This closes it before launch.

## Goal

Make the callable's response time **independent of whether the account exists**, while keeping
reset-email delivery reliable.

## Approach: split into enqueuer + Cloud Tasks worker

Chosen over a Firestore-triggered worker: Cloud Tasks keeps the email only in Google's transient
task payload (no PII at rest, no junk docs from enumeration probes) and brings built-in retries.

### `sendPasswordResetEmail` — callable, becomes a thin enqueuer
1. normalize email → `invalid-argument` if junk
2. reserve throttle (email-keyed `sha256`, `mailThrottle/{key}`, 60s) → `resource-exhausted` if too soon
3. **enqueue `{ email }`** to the `processPasswordResetEmail` queue
4. return `{ ok: true }`

It performs **no** existence check, link generation, or send — identical work for every email,
so response time carries no existence signal. On enqueue failure it releases the throttle (so the
user can retry) and throws `unavailable`.

### `processPasswordResetEmail` — NEW Cloud Tasks worker (`functions.tasks.taskQueue().onDispatch`)
1. `getUserByEmail(email)` → if null, return (account doesn't exist / was deleted; drop silently)
2. `generatePasswordResetLink(email)`
3. `buildPasswordResetEmail` + `sendResendEmail` (same branded template, unchanged)

Throwing propagates to Cloud Tasks → automatic retry (`maxAttempts: 5`, backoff). A capped
`maxConcurrentDispatches` protects Resend quota. A malformed task payload is logged and dropped
(returns without throwing) so it doesn't retry forever.

## Trade-offs (accepted for V1)

- **Throttle not released on send failure.** The callable returns before the send runs, so it
  can't release the throttle on a delivery failure — the user waits out the 60s. Cloud Tasks
  retries cover transient failures, so this only bites on total failure. Acceptable.
- **At-least-once → rare duplicate emails.** If a dispatch's ack is lost after Resend succeeds,
  Tasks retries and a second reset email goes out. Both links are valid; harm is low. Idempotency
  (mark `sentAt` on the throttle doc, skip if already sent in-window) is optional later hardening.
- **IAM.** The enqueuer needs `roles/cloudtasks.enqueuer`; Firebase usually grants the default
  service account this on deploy. If the first enqueue returns `PERMISSION_DENIED`, grant it
  manually to the functions service account.

## Boundaries / files

- `auth/passwordResetShared.ts` — shared `normalizeEmail` (leaf helper used by both).
- `auth/sendPasswordResetEmail.ts` — enqueuer (throttle + enqueue seam). Drops the send logic.
- `auth/processPasswordResetEmail.ts` — worker (existence check + link + send), reuses
  `passwordResetEmailTemplate` + `resendClient` unchanged.
- `index.ts` — export the worker. `package.json` — add `functions:processPasswordResetEmail` to
  the deploy allow-list.
- **Client: zero changes** — still calls the callable, still gets `{ ok: true }`, same error map.

## Testing

- Enqueuer: invalid → `invalid-argument`; throttled → `resource-exhausted` (no enqueue); happy →
  enqueue called with the normalized email, returns `{ ok: true }`; enqueue failure → release +
  `unavailable`.
- Worker: unknown email → no link/no send (no throw); happy → link + send with displayName;
  send failure → throws (so Tasks retries); malformed payload → drop without throw.
- Template tests unchanged (own file).

## Verification

`functions`: jest + tsc + lint(0 errors). Android `assembleDebug` + detekt (no client change, but
sanity). iOS compile. Deploy worker first (creates the queue), then the callable. Smoke: trigger a
reset for a real account → branded email arrives via the worker; confirm an unknown email sends
nothing and the callable response time looks flat either way.
