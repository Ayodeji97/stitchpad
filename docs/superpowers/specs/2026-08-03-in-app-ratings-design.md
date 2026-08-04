# In-App Ratings — Design

**Date:** 2026-08-03
**Status:** Spec (not built)
**Author:** Daniel Ogunleye (with Claude)

## Problem

StitchPad is live on both stores but has effectively zero ratings — the App Store
shows "not enough ratings or reviews to display an overview," and Google Play has a
single review (the developer's own). We have never asked users to rate from inside
the app, and there is no store-compliant "rate" surface anywhere in the product.

At zero ratings the public average is maximally fragile: with nothing to dilute
them, two early frustrated one-stars *become* the store average. We need to start
collecting ratings from happy users while steering unhappy users toward private
feedback we can act on, instead of a permanent public one-star.

## Goal

Sentiment-gated ratings: ask proven, satisfied users at a happy moment; route the
happy ones to the native store rating UI and the unhappy ones to the existing Tally
Feedback Hub. Drive both rating **volume** and **average** while protecting against
seeding early negatives.

Non-goals: NPS scoring/trend dashboards, in-house star storage, review reply
automation, prompting web/desktop (mobile only).

## Two mechanisms (both required)

"Rate the app" is two distinct platform calls, used in two distinct places:

1. **Automatic prompt** → the **native in-app review API**. Shows the rating UI
   inside the app without leaving it.
   - iOS: `SKStoreReviewController.requestReview` (StoreKit).
   - Android: Google Play In-App Review API (`ReviewManager` / `review-ktx`).
   - OS-rate-limited (Apple ≈ 3 prompts/user/365 days; Google silently no-ops past
     its quota), silent, fire-and-forget. We **cannot** detect whether the user
     rated or whether the sheet even showed. We call it and move on.

2. **Manual "Rate StitchPad" button** → a **store deep-link**, NOT the in-app API.
   Apple and Google both require that a *user-tapped* rate action open the store
   listing's write-review page rather than invoking the in-app API.
   - iOS: `itms-apps://itunes.apple.com/app/id6770673562?action=write-review`
   - Android: `market://details?id=com.danzucker.stitchpad` (fallback to the
     `https://play.google.com/store/apps/details?id=...` URL when Play is absent).

Two thin `expect`/`actual` functions cover both:
`requestInAppReview()` and `openStoreListing()`.

## Architecture

New `feature/review/` package, mirroring the existing `core/presentation/celebration`
system (`CelebrationController` + `Milestone` + one-shot preference flags).

```
feature/review/
  domain/
    ReviewGate.kt            — pure eligibility function (no I/O), fully unit-testable
    ReviewPrompt.kt          — model of an armed prompt + outcome types
  data/
    ReviewPreferencesStore.kt          — common interface
    ReviewPreferences.android.kt       — DataStore-backed (like OnboardingPreferences)
    ReviewPreferences.ios.kt           — NSUserDefaults-backed
  presentation/
    ReviewController.kt      — app-lifetime singleton; owns current prompt state
    ReviewPromptSheet.kt     — stateless Compose bottom sheet + @Preview
    ReviewStrings / UiText   — user-facing copy via compose.resources
  platform/
    StoreReview.kt           — expect requestInAppReview(), openStoreListing()
    StoreReview.android.kt    — ReviewManager + market:// intent
    StoreReview.ios.kt        — SKStoreReviewController + itms-apps:// deep link
```

Koin: `ReviewGate` (singleOf), `ReviewPreferencesStore` (singleOf, platform actual),
`ReviewController` (single, app-lifetime, injected `() -> Long` clock per the iOS
Clock-injection gotcha and `authUserIds` flow as `CelebrationController` does).

### ReviewController responsibilities
- Owns `current: StateFlow<ReviewPrompt?>` (the sentiment sheet to show, or null).
- `suspend fun armFromDelight(userId: String)` — called by ViewModels on a delight
  moment. Reads signals from `ReviewPreferencesStore`, asks `ReviewGate`, and if
  eligible sets `current` to the sentiment prompt. No-op otherwise.
- Handles the branch outcomes (`onLoveIt`, `onNotReally`, `onDismiss`), each of
  which records `lastPromptAt = clock()` and the appropriate cooldown/outcome, then
  clears `current`.
- Clears `current` on auth-user change (same guard as `CelebrationController`) so a
  prompt never leaks across accounts or shows on the login screen.
- **Never arms while a celebration overlay is active** (checked via the celebration
  `current` flow, or naturally avoided since the gate needs ≥3 orders and `FirstOrder`
  fires on order #1 — we assert both).

## The gate

`ReviewGate.isEligible(signals, now)` returns true only when ALL hold. All thresholds
are named constants, tunable in one place.

| Signal | Default | Rationale |
|---|---|---|
| `daysSinceInstall` | ≥ 3 | Don't ask brand-new users. |
| `distinctOpenDays` | ≥ 2 | Opened on ≥2 separate days = engaged, not a one-session tourist. |
| `ordersCreated` | ≥ 3 | Proven real usage; also guarantees no collision with `FirstOrder` confetti. |
| `now − lastPromptAt` | ≥ 75 days (or never prompted) | Respect a hard cooldown floor between any two prompts. |
| `outcome != Rated/HardDeclined` within its window | — | Honor the per-outcome cooldown set below. |

### Signal collection (local, offline-safe, no Firestore reads)
- **Install time:** persisted once on first `ReviewPreferencesStore` access if unset.
- **Distinct open days:** on app foreground, record today's date (local `LocalDate`);
  store a small rolling count / last-recorded-day so the counter only increments once
  per calendar day.
- **Orders created:** a local counter incremented from the order-create success path
  (same spot that would `trigger` a milestone). Lightweight `Int` in prefs.

All counters are per-user (keyed by `userId`), matching the celebration prefs pattern.

## Flow

```
delight moment (payment recorded OR order collected, whichever first)
        │  ViewModel calls reviewController.armFromDelight(userId)
        ▼
   ReviewGate green? ──no──▶ nothing happens
        │ yes
        ▼
   Sentiment bottom sheet:  "Enjoying StitchPad?"
        │        😍 Love it            🙁 Not really          [dismiss / scrim]
        ▼                    ▼                      ▼
   requestInAppReview()   open Tally Feedback Hub   (no action)
   cooldown ~120d         cooldown ~60d             cooldown ~30d
   outcome=Rated          outcome=GaveFeedback      outcome=Dismissed
```

- **Love it** → `requestInAppReview()` (fire-and-forget). Long cooldown (~120d). We
  treat the moment as spent whether or not the OS actually showed the sheet.
- **Not really** → open the existing Tally Feedback Hub
  (`tally.so/r/5BgVVb`, per the feedback-hub setup). Medium cooldown (~60d).
- **Dismiss** (scrim tap / back / explicit close) → short cooldown (~30d) so we can
  re-ask a still-happy user later without nagging.

### Which delight moment
Both arm the prompt; first to occur after the gate goes green wins:
- Payment recorded (peak "this app makes me money" signal).
- Order marked Collected / Delivered (clean job completion).

The two owning ViewModels (payment recording, order status update) each call
`armFromDelight(userId)` on their success path. `ReviewController` dedupes via the
gate, so calling from both is safe.

## Settings entry ("Rate StitchPad")

A "Rate StitchPad ★" row in Settings → `openStoreListing()` (the deep-link
mechanism, not the in-app API). Always available; doubles as the developer's own
test/QA hook. Uses the saffron star sparingly per the design-system heritage-accent
rule (rare accent only).

## Analytics

New `AnalyticsEvent` variants routed through the existing `Analytics` interface:
- `review_prompt_shown` — sentiment sheet displayed.
- `review_sentiment` (param: `positive` | `negative` | `dismissed`).
- `review_inapp_requested` — native in-app review API called.
- `review_feedback_opened` — routed to Tally.
- `review_store_listing_opened` — Settings deep-link tapped.

Note: GA4 custom-dimension registration for new params is still pending per the
analytics-events memo; register these when that batch lands.

## Error handling

- `requestInAppReview()` and `openStoreListing()` never throw to the caller; platform
  failures (no Play services, missing store app, StoreKit unavailable) are caught in
  the `actual` and logged, and for `openStoreListing()` fall back to the `https://`
  store URL. A failed store open surfaces a snackbar ("Couldn't open the store")
  per the notification-patterns rule (snackbar = feedback).
- The prompt flow returns `Result`-free `Unit`; there are no expected user-facing
  failures in the sentiment step itself.

## Testing

- **`ReviewGate`** — pure JVM unit tests covering every threshold boundary
  (just-below / just-at each of the 5 signals, cooldown windows, each prior outcome).
- **`ReviewController`** — fake `ReviewPreferencesStore` + fake `() -> Long` clock +
  Turbine on `current`; assert: gate-green arms, gate-red no-ops, each branch records
  the right cooldown/outcome, auth-user change clears `current`, no arm while a
  celebration is active.
- **`ReviewPromptSheet`** — `@Preview` (light + dark per the both-color-modes rule).
- **Platform `expect`/`actual`** — thin wrappers; manual on-device QA on both stores
  (in-app review API cannot be reliably unit-tested).

## Rollout & QA

- Ship behind the existing debug menu: a "Force review prompt" debug action that
  bypasses the gate, so testers/QA can exercise the full flow on demand.
- Manual smoke steps (Daniel = QA): gate-blocked new user sees nothing; forced prompt
  → Love it → native sheet appears (Android) / StoreKit sheet (iOS); Not really →
  Tally opens; dismiss → no action; Settings row → store listing opens; cooldown
  respected on second delight moment.

## Open questions / tunables to confirm at review

- Exact thresholds (3 days / 2 days / 3 orders) and cooldowns (120/60/30/75).
- Sentiment copy wording and whether to localize now or English-only for launch.
- Whether the "Not really" branch opens Tally in-app (WebView) or the browser.
