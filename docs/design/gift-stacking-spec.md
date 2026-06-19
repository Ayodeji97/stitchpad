# Gift Subscriptions — Stacking & the "Never Lost" Guarantee

## The promise (marketing + product)

**A gift subscription is never lost.** Whatever time a tailor already has is always
preserved, and a gift is always *added on top* — never replaced, never overlapped,
never thrown away.

One rule makes this true:

> **Higher tier runs first; the lower tier is queued behind it.**
> **Total time = time they already had + time gifted.**

So a tailor can be gifted by friends, family, or clients again and again, and every
gifted day shows up in their account.

## Why a "queue"

There are exactly **two paid tiers** — Tailor Pro and Tailor Atelier — so at any moment
a tailor's paid time is at most **two stacked segments**: an Atelier stretch, then a Pro
stretch. The higher tier is always consumed first (you never spend premium time at a
basic level), and the lower tier waits its turn. Any gift simply adds time to its
matching tier's segment.

## Scenarios (complete matrix)

`now` = the moment the gift is applied. "Remaining" = time left, not time used.

| # | Recipient's current state | Gift | Result |
|---|---|---|---|
| A | No active paid plan (Free / expired) | any tier, span S | That tier for S, starting now. |
| B | **Same** tier as the gift, with R remaining | same tier, span S | **Stacked:** that tier for **R + S** (end date pushed out by S). |
| C | **Lower** tier active, R remaining (e.g. Pro, 4 mo) | **higher** tier, span S (e.g. Atelier, 2 mo) | **Upgrade now, pause the lower tier:** higher tier for S (Atelier 2 mo), then the lower tier resumes for its full R (Pro 4 mo). Total **R + S** (6 mo). |
| D | **Higher** tier active, R remaining (e.g. Atelier, 4 mo) | **lower** tier, span S (e.g. Pro, 2 mo) | **Higher keeps running, lower queued:** higher tier for its R (Atelier 4 mo), then the gifted lower tier for S (Pro 2 mo). Total **R + S** (6 mo). |
| E | Already has a queued fallback (a prior mismatched gift) | any tier | The gift extends whichever segment matches its tier (Atelier time adds to the Atelier segment, Pro to the Pro segment); the schedule re-flows higher-first. Still at most two segments. |

In every row, **total paid time after = total paid time before + gifted span.** Nothing
is consumed in parallel and nothing is discarded.

### Worked examples
- Pro, 4 mo left, gifted **2 mo Atelier** → Atelier for 2 mo, then Pro for 4 mo → **6 mo** total.
- Atelier, 4 mo left, gifted **2 mo Pro** → Atelier for 4 mo, then Pro for 2 mo → **6 mo** total.
- Pro, 4 mo left, gifted **2 mo Pro** → Pro for **6 mo**.
- Atelier 5 mo + Pro 2 mo queued, gifted **1 mo Atelier** → Atelier 6 mo, then Pro 2 mo.

## Edge cases

- **Expired but not yet swept** (status still "active" before the daily cron runs, end date
  already past): treated as *no* remaining time — the gift starts fresh from now.
- **Different cadence** (e.g. a monthly gift to an annual subscriber): the gifted span is
  added in the gift's own unit (months or years); existing time is preserved exactly as-is.
- **Roll-over:** when the active segment ends, the system automatically promotes the queued
  segment (e.g. Atelier → Pro). Only when *all* paid time is exhausted does the plan return
  to Free. The app always reads a single "current tier," so nothing changes for the UI.
- **Emails / notifications** name the tier and duration that was *gifted* (e.g. "You've been
  gifted Tailor Pro · 2 months"), so the recipient sees the true gift even when it's queued
  behind a higher tier they're already on.
- **Gifts are prepaid and non-renewing** — they never auto-charge anyone.

## Data model

On `users/{uid}`, alongside the existing server-owned subscription fields:

- `subscriptionTier`, `subscriptionEndsAt` — the **active** segment (the higher one when two exist).
- `subscriptionFallbackTier`, `subscriptionFallbackEndsAt` — the **queued** lower segment (absent when there's only one).

All server-owned (set by the Paystack/gift webhook + the daily expiry cron via the Admin
SDK; locked from client writes in `firestore.rules`).

The daily `expirePrepaidSubscriptions` cron does a **two-step** unwind: active segment ends →
promote fallback to active; fallback ends → return to Free.

## One-line summary

> **Stack, don't replace. Higher tier first. Never lose a day.**
