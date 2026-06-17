# Gift claim email — guaranteed delivery

## In plain terms — what this solves

When someone buys a gift for a tailor on the website (the "gift a tailor by email"
flow), we email the recipient a **claim link + code** so they can unlock their gift in
the app.

That email is sent the instant the payment succeeds. But email providers occasionally
have a bad moment — if **our email service (Resend) happens to be down for those few
seconds**, the email would previously just fail and never be sent again. The gift money
was taken, the gift was valid, but the recipient might never get the message telling them
how to claim it.

This change makes that email **keep trying until it actually goes out.**

How it behaves now:
- We still send it immediately on payment (no change to the happy path).
- If that send fails, the gift is quietly marked "still needs its email."
- A small background job runs **every hour** and re-sends any gift still waiting. It keeps
  trying for a few rounds.
- If the recipient's email address is simply **wrong/undeliverable** (not a temporary
  glitch), we stop after the first try instead of pointlessly retrying forever.

Two things worth knowing:
- The **gift is never blocked** by email trouble — the recipient's plan and their ability
  to claim are completely separate from whether the email went out.
- The **buyer also gets the code on the website's "thank you" page**, so they can always
  pass it on directly (WhatsApp, etc.). The email is a convenience, and now a reliable one.

## How it works (technical)

- The gift doc gets a `needsClaimEmail` flag (set `true` when a public gift becomes
  `paid`). On a successful send it's cleared and `claimEmailSentAt` is stamped.
- `deliverClaimEmail()` is the single send-and-mark routine, used by both the webhook's
  first attempt and the hourly sweep. It never throws — it records the outcome on the gift:
  - success → `needsClaimEmail=false`
  - permanent failure (Resend **4xx**, e.g. bad address) → `needsClaimEmail=false` (give up)
  - transient failure (**5xx**/network) → leave `true`, bump `claimEmailAttempts`, stop after
    `MAX_CLAIM_EMAIL_ATTEMPTS` (5).
- `resendUnclaimedGiftEmails` is an hourly scheduled function that queries
  `gifts where needsClaimEmail == true` (a tiny set — only undelivered gifts; single-field
  index, no scan) and runs `deliverClaimEmail` on each.
- `sendResendEmail` now throws a typed `ResendError` carrying the HTTP status so we can tell
  transient from permanent.
- No security-rule change: the `gifts` collection is already Admin-SDK-only. No composite
  index needed.

## Scope / not included
- The **gift_me** flow's celebratory "you've been gifted" email stays best-effort (the
  tailor sees the upgrade in-app immediately, so a missed email there is low-impact). Same
  pattern could be extended to it later if desired.
