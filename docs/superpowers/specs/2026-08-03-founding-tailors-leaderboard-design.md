# Founding Tailors — designer-refers-designer leaderboard

**Date:** 2026-08-03
**Status:** Design — awaiting approval
**Working name:** "Founding Tailors" (changeable)

## Goal

Turn our existing community of ~108 fashion designers into referrers who bring
in other serious tailors. Community growth first, app adoption as the deeper
goal. The mechanism is a **monthly points leaderboard** with a tangible,
on-brand reward: the **top 3 each month win a free customized, branded StitchPad
shirt**, and points also bank toward **free Pro/Atelier months** when paid tiers
launch. The app is free today, so the design deliberately avoids cash rewards to
members.

A "point" is one **genuinely-active referral**, not a raw signup — reusing the
referral system's existing, server-hardened `qualified` milestone.

## Why this shape (alternatives considered)

- **Flat cash-per-referral for members** — rejected. Expensive with no revenue,
  invites forging, and turns colleagues into lead-hunters, corroding the
  community.
- **Recognition only, no prize** — nearly free but fades without something
  tangible to chase.
- **Monthly leaderboard + physical prize + banked future credit** — chosen. The
  prize is a garment (what this audience values), every winner becomes a walking
  billboard in front of the exact right crowd, status drives recurring
  competition without spending cash, and banked points pre-build our first
  paying evangelists. At 108 people it is small enough to run intimately and
  verify winners by hand.

## What already exists (and is reused)

The referral backend (`functions/src/referral/`) is mature and does most of the
heavy lifting:

- **`ReferrerType = 'affiliate' | 'user'`** — the system already models
  *user-referrers*, not just paid affiliates.
- **Server-hardened "genuinely active" definition** — a referred user reaches
  the `qualified` milestone after meaningful writes (customers/orders/
  measurements) on `QUALIFY_DISTINCT_DAYS` (4) distinct Africa/Lagos calendar
  days within `QUALIFY_WINDOW_DAYS` (14) of attribution. App-opens never count.
  Computed only server-side by `reconcileReferrals`.
- **Fraud flags** — `self_referral`, `device_reuse`, `velocity` are BLOCKING;
  the milestone still advances but the referral is marked. We treat any blocking
  flag as **not a point**.
- **Collections** — `marketers/{id}`, `referralCodes/{code}`, `referrals/{uid}`
  (carries `marketerId`, `milestone`, `flags`, server timestamps), all
  Admin-SDK-only per `firestore.rules`.
- **Web pattern** — the real marketing site (`getstitchpad.com`, a separate
  Astro/Vercel repo at `~/Desktop/Business/StitchPad/StitchPad-IT/stitchpad-web`)
  already calls Cloud Functions via the Firebase **callable** SDK
  (`httpsCallable`) and already consumes `getReferralDashboard` on its `/admin`
  page. Brand tokens (Adire Atelier) are already defined there.

## Two real gaps this feature must close

1. **No outbound referral link for members.** The app only handles *inbound*
   codes (the code you were invited with). No per-user shareable code exists on
   the user doc, and `createMarketer` is admin-only. A community designer today
   has no way to get "their link." **We must add a link-minting path.**
2. **No leaderboard aggregation or public read.** Points must be tallied and
   exposed without leaking the Admin-SDK-only referral collections.

## Architecture

```
[users create/setup activity]
        │  reconcileReferrals (existing) → referral reaches `qualified`
        ▼
[referrals/{uid}]  marketerId, milestone, flags, qualifiedAt   ← +qualifiedAt (new, tiny)
        │
        ├─► [scheduled: aggregateFoundingTailorsLeaderboard]  daily
        │        counts qualified & non-blocked referrals per user-referrer,
        │        bucketed by qualifiedAt month + all-time
        │        writes ONE public doc → leaderboards/{monthId}  (+ current pointer)
        │
        ▼
[getFoundingTailorsLeaderboard]  (public onCall)
        returns { updatedAt, monthId, top:[{rank,name,points}], you:{rank,points}|null }
        resolves ?code → `you` server-side; top rows carry NO codes
        ▲
        │ httpsCallable (same pattern as getReferralDashboard)
[getstitchpad.com/founding-tailors]  (Astro/Vercel, static + client JS)
        ▲
        │ opened via LocalUriHandler.openUri(".../founding-tailors?code=CODE")
[StitchPad app]  Dashboard card + Settings entry + "Your invite link" (share)
        ▲
        │ getOrCreateMyReferralLink (onCall) → mints user-referrer, stores users/{uid}.referralCode
```

## Components (isolated, each independently buildable/testable)

### Backend — `functions/` (same repo)

**B1. `getOrCreateMyReferralLink` (onCall, authed) — NEW.** Any signed-in user
calls it. Idempotently mints a `type:'user'` marketer + unique code (reusing the
race-safe mint from `marketerAdmin`/`giftBilling.generateCode`), stores the code
on `users/{uid}.referralCode`, and returns the share link
(`https://link.getstitchpad.com/r/CODE` + Play URL). Program referrers are minted
**payout-disabled** (see Decision D2) so the shirt program never queues cash.
One code per uid; rate-limited.

**B2. `aggregateFoundingTailorsLeaderboard` (scheduled, daily) — NEW.** Reads
`referrals` where `milestone == 'qualified'` and no blocking flag, groups by
`marketerId`, resolves each program user-referrer's display name, buckets counts
by `qualifiedAt` month (`YYYY-MM`, Africa/Lagos) and all-time. Writes a single
public doc per month `leaderboards/{monthId}` plus a `leaderboards/current`
pointer. Only aggregated, non-PII fields (display name + point count) leave the
private collections. On month rollover, the prior month's top 3 are frozen into
a `pastWinners` record for the winners strip and the shirt-shipping checklist.

**B3. `getFoundingTailorsLeaderboard` (onCall, public/unauthed) — NEW.** Reads
the pre-computed public doc (cheap; no per-request Firestore scans). Returns the
ranked `top` list (no codes) and, when a `code` arg is supplied, resolves it
server-side into a `you: { rank, points }` block. Unknown codes return
`you: null` (never leak which codes exist).

**B4. `reconcileReferrals` — stamp `qualifiedAt` — NEW (small).** When a
referral first flips to `qualified`, write a server `qualifiedAt` timestamp.
Gives clean monthly-reset semantics (a point lands in the month it became
active) and an all-time total. Back-fill: referrals already `qualified` without
`qualifiedAt` get stamped on next reconcile (bucketed by `serverCreatedAt` as a
fallback).

Both new callables and the schedule go in `functions/src/index.ts` exports and
the `package.json` `deploy` allow-list (deploy-allowlist gotcha).

### Web — `stitchpad-web` (Astro/Vercel, separate repo)

**W1. `/founding-tailors` page — NEW.** Static page + client JS. Reads `?code`
from the URL, calls `getFoundingTailorsLeaderboard` via `httpsCallable` (region
`europe-west1`, reusing `src/lib/firebase.ts`), renders the ranked board,
highlights the viewer's row and "You are #N", shows "last updated", the prize
explainer, how-to-earn, past winners, and a "share your link" CTA. Mobile-first,
Adire Atelier styling. No CORS/CSP change needed — same functions origin already
allowlisted. Works standalone on the web too.

### App — `composeApp/` (KMP)

**A1. `User.referralCode` — NEW (small).** Add nullable `referralCode` to
`User` domain model + `UserDto` (read-only client-side; the field is written
server-side by B1). Surfaced through the existing `UserRepository.observeUser`
stream.

**A2. Founding Tailors surface — NEW.**
- A **"Founding Tailors" card** on the app dashboard + a **Settings entry**.
- **"Your invite link"**: on first entry, if `user.referralCode` is null, call
  `getOrCreateMyReferralLink`; then show the link with a **Share** action that
  reuses the existing WhatsApp/`openUri` sharing pattern (no new abstraction).
- **"View leaderboard"**: opens
  `https://getstitchpad.com/founding-tailors?code=<referralCode>` via the
  existing `LocalUriHandler.openUri` pattern (plain system-browser handoff —
  matching every other external link; **no Custom Tab / SafariVC** is built,
  reversing the earlier idea after finding none exists in the codebase).

## Data flow (happy path)

1. Designer opens Founding Tailors in the app → gets/sees their invite link →
   shares it in WhatsApp to tailor friends.
2. A friend installs via the link, signs up, and does real work over several
   days → `reconcileReferrals` advances their `referrals/{uid}` to `qualified`
   and stamps `qualifiedAt`.
3. The daily aggregator counts that as +1 point for the referrer in the current
   month and all-time, and republishes the public leaderboard doc.
4. The designer opens the leaderboard (in-app link) → sees their rank and the
   top 3.
5. On the 1st, the month resets; the prior top 3 are frozen; you verify them by
   hand and ship shirts.

## Anti-gaming

Reuses the referral system's existing defenses — points come only from
server-verified `qualified` referrals, and `self_referral` / `device_reuse` /
`velocity` blocking flags exclude a referral from the count. Aggregation runs
server-side over Admin-SDK-only data, so raw referral records never reach a
client. At 108 people, top-3 winners are eyeballed before any shirt ships. This
closes, rather than inherits, the "forgeable referral" concern flagged against
the older system.

## Decisions (please confirm at review)

- **D1 — How members get their link:** self-serve `getOrCreateMyReferralLink`
  (recommended) vs admin bulk-mint of the 108. Recommendation: self-serve — it
  works for whoever is on the app without collecting 108 uids, scales past 108,
  and has a low abuse surface (one code/uid, no cash attached, rate-limited).
- **D2 — Payout coupling:** Founding Tailors referrers are minted
  **payout-disabled** (payout rate 0 / a `program: 'founding_tailors'` marker)
  so qualifying referrals count for the leaderboard but never enter the cash
  payout pipeline. Keeps the shirt program cleanly separate from the paid
  affiliate program.
- **D3 — Name shown on the board:** business/workshop name, falling back to
  first name (recommended, doubles as self-marketing) vs first-name-only.
- **D4 — Point bucketing:** by `qualifiedAt` month (recommended, clean "this
  month's active referrals") — requires B4.
- **D5 — Route/name:** `/founding-tailors` and "Founding Tailors" — confirm or
  rename.

## Phasing

- **Phase 1a (backend + web):** B1–B4 + W1. Launchable immediately — you can
  seed a few links, share the board URL in the community, and validate appetite
  before any app UI ships.
- **Phase 1b (app):** A1–A2. Makes get-link + view-board seamless in-app.
- **Phase 2 (later):** self-serve is already in; add Pro-credit redemption UI
  once paid tiers exist, and a two-step (community-join + activate) funnel if
  wanted.

## Testing

- **B1:** unit tests — idempotent mint, one-code-per-uid, payout-disabled,
  writes `users/{uid}.referralCode`. Mirror existing `createMarketer.test.ts`.
- **B2:** unit tests — counts only `qualified` + non-blocked, correct month
  bucketing across Lagos boundary, all-time total, month-rollover freeze.
- **B3:** unit tests — public rows carry no codes, `?code` resolves `you`,
  unknown code → `you: null`.
- **B4:** unit tests — `qualifiedAt` stamped once on first qualify, back-fill
  fallback.
- **W1:** manual — highlight works with/without `?code`, empty state, mobile.
- **A1/A2:** ViewModel tests + `@Preview`; QA smoke steps (Daniel) for
  get-link, share, and open-leaderboard on Android + iOS.

## Out of scope (YAGNI)

- Embedded in-app WebView / Custom Tabs.
- Web login / full personal web dashboard.
- Automated shirt fulfillment (manual at this scale).
- Cash rewards to members.

## Launch playbook (community message, WhatsApp-ready, no em dashes)

> **Founding Tailors — let's grow this room together** 🧵
>
> You are one of the first designers on StitchPad, and I want to reward the ones
> who help other serious tailors find us.
>
> How it works:
> 1. Share your personal invite link with tailor friends and colleagues.
> 2. Every time one of them joins StitchPad and actually starts using it, you
>    earn 1 point.
> 3. At the end of each month, the top 3 with the most points win a **free
>    customized StitchPad shirt**, made for you.
>
> Your points also keep adding up over time and will unlock free Pro months when
> premium launches. The earlier you start, the more you bank.
>
> Grab your link inside the app under Founding Tailors. Let's build the biggest
> tailor community in Nigeria.
