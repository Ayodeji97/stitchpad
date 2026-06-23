# Firebase Analytics — User-Journey Instrumentation (Design)

Date: 2026-06-22
Status: Approved design, pre-implementation
Branch: `feat/analytics-user-journey`

## Problem

StitchPad has **no product analytics today**. We have Crashlytics (Android only) for
crashes and `AppLogger` (Napier) for local logging, plus a single bespoke Firestore
counter (`FirebaseCommunityJoinTracker`). We cannot answer basic questions about how
users use the app: where they drop off, whether they reach value, or whether they
convert to a paid tier. We are flying blind on user behaviour heading into launch.

## Goal

Instrument the app with **Firebase Analytics (GA4)** so we can see the full user
journey — both the screen-to-screen flow and the conversion funnel — and segment it by
subscription tier and platform. Use Firebase only (no new vendor). Read the journey in
the GA4 console now; bank raw events in BigQuery from day one for future deep analysis.

### Why Firebase Analytics (not PostHog / custom Firestore)

- Already provisioned and free on our Blaze project `stitchpad-30607` (europe-west1);
  no new vendor, no new billing, no new privacy-policy surface (Firebase already
  disclosed in our Termly policy).
- Fits the stack: GitLive `firebase-analytics` works across KMP (Android + iOS) through
  the same SDK family we already use for Auth/Firestore/Storage.
- Slots into our MVI + Koin architecture cleanly (injectable interface, called from
  ViewModels).
- Free BigQuery export gives us raw event ownership for custom SQL / dashboards later.
- Trade-off accepted: GA4's console funnel/path UI is clunkier than PostHog's, but for a
  solo dev pre-launch the "free, already here, fits our patterns, owns the raw data" win
  outweighs prettier dashboards. PostHog can be layered later; BigQuery keeps the data
  either way.

## How we will "see the journey"

1. **Funnel exploration** (GA4 console) — ordered milestone events → drop-off bar chart.
2. **Path exploration** (GA4 console) — branching flow tree, powered by `screen_view`.
3. **Retention / cohorts** (GA4 console) — does the journey stick.
4. **BigQuery** (SQL, later) — anything the console can't express; raw event firehose.

Caveat: GA4 standard reports lag ~24h; funnels/paths are only as good as the event
taxonomy; each exploration is configured once in the console.

## Architecture

A thin, injectable abstraction consumed by ViewModels (never composables).

```
core/analytics/
  domain/
    Analytics.kt          interface: logEvent(AnalyticsEvent), logScreenView(name),
                          setUserId(uid?), setUserProperty(key, value)
    AnalyticsEvent.kt     sealed interface; one subtype per milestone (type-safe params)
  data/
    FirebaseAnalytics.kt  implements Analytics via GitLive firebase-analytics
    NoOpAnalytics.kt      no-op impl for previews/tests
di/
  analyticsModule.kt      Koin: single<Analytics> { FirebaseAnalytics(...) }
```

Decisions:
- `Analytics` interface in `domain/`, impl named `FirebaseAnalytics` in `data/`
  (per "name implementations descriptively, never `Impl`" + domain→data layering).
- Provided as a Koin `single`; ViewModels receive it as a constructor param.
- Composables never call it (honours "no business logic in composables").
- `AnalyticsEvent` is a **sealed interface, not loose strings**. Each subtype maps to its
  GA4 snake_case name + params in one place in `FirebaseAnalytics`. Kills
  client/constant drift and makes the taxonomy self-documenting.
- `NoOpAnalytics` keeps screens previewable and lets ViewModel tests avoid Firebase.

## Event taxonomy

### Layer A — Automatic screen views (one hook, zero per-screen code)

A single `DisposableEffect` on the `NavController.currentBackStackEntryFlow` reads each
`@Serializable` route's name and calls `analytics.logScreenView(routeName)`. Every screen
the user lands on is logged automatically — powers Path exploration and screen retention.

### Layer B — Critical funnel milestones (first PR = these 6)

Each maps to a point already handled in an existing MVI ViewModel:

| Event (GA4 name)            | Fires when                          | Fired from                  |
|-----------------------------|-------------------------------------|-----------------------------|
| `sign_up`                   | account created successfully        | AuthViewModel (signup ok)   |
| `workshop_setup_completed`  | business name + phone saved         | Workshop/onboarding VM      |
| `first_customer_created`    | user's first customer saved         | Customer VM (guard: was 0)  |
| `first_order_created`       | user's first order saved            | Order VM (guard: was 0)     |
| `ai_feature_used`           | any Smart Suggestion/Draft used     | Smart VM(s), param `feature`|
| `upgrade_completed`         | Pro/Atelier purchase confirmed      | Billing/entitlement VM, `tier` |

User properties (set once, attached to every event for segmentation):
- `subscription_tier` (free / pro / atelier) — see field-consolidation note below.
- platform — provided automatically by GA4.

Notes:
- **"First X" guard:** `first_customer_created` / `first_order_created` fire exactly once
  per user (guarded on count-was-zero), so the activation funnel is meaningful rather than
  a raw count.
- **No PII**: events carry only counts, enums, and tier — never names, phone numbers,
  business names, customer data, measurements, or any free text.

## Error handling, privacy & cost

- **Never crash or block a flow.** `FirebaseAnalytics` swallows its own errors
  (`AppLogger.w`, never rethrows). Tracking is fire-and-forget: ViewModels never await it
  and never branch on its result. No `Result<>` here — a silent no-op is the correct
  behaviour for a failed analytics ping.
- **No PII, ever** (enforced as a rule so future events don't leak). Keeps us clean under
  the existing Termly privacy policy with no new legal surface.
- **Identity = Firebase `userId` only** (existing uid). Set on login, cleared on logout.
  Enables per-user journey analysis in BigQuery without storing anything identifying.
- **Cost: zero.** GA4 events free/unlimited; BigQuery export free to enable (already on
  Blaze).
- **Debug builds → Firebase DebugView, with a debug-menu toggle** (fits "evaluate a
  debug-menu entry per feature"). Lets us verify events fire during smoke tests while
  keeping production funnels clean. Toggle defaults to DebugView routing in debug builds;
  release builds always send to production.

## Testing & verification

- **ViewModel unit tests** inject a `FakeAnalytics` (records calls); assert the right
  event + params fired, and that "first X" guards fire exactly once. Uses existing
  JUnit5 + Turbine + fake-repo patterns.
- **Previews** use `NoOpAnalytics`; no Firebase in `@Preview`.
- **iOS gate is mandatory.** "Done" requires a clean iOS compile AND a real device/sim run
  confirming events land in Firebase **DebugView on both platforms** — the GitLive
  `firebase-analytics` binding is exactly the kind of thing that compiles then misbehaves
  on iOS.
- **Manual smoke test** (Daniel is QA): fresh install → signup → workshop setup → add
  customer → add order → use AI → upgrade, watching all 6 milestone events + `screen_view`
  appear in DebugView, on Android and iOS.

## One-time ops (outside code)

1. Enable Google Analytics on Firebase project `stitchpad-30607` (links a GA4 property).
2. Add `firebase-analytics` (GitLive) dependency + Android Gradle plugin bits; confirm
   regenerated `google-services.json` / `GoogleService-Info.plist` include the analytics
   block / measurement ID.
3. **Flip the BigQuery export switch immediately** — data only banks from when it is on;
   enabling early pre-launch preserves the earliest-user cohort.
4. iOS: confirm the analytics SDK/pod is pulled and `GoogleService-Info.plist` has the
   measurement ID.
5. R8/ProGuard: add any keep rules the analytics SDK needs and run a release smoke test
   (per the APK-size/R8 note — new deps can need keep rules).

## Scope: first PR

Foundation + critical funnel only:
- `Analytics` interface + `FirebaseAnalytics` + `NoOpAnalytics` + `FakeAnalytics`.
- Koin `analyticsModule` wired into app modules.
- Automatic `screen_view` logging via the NavController hook.
- The 6 milestone events wired into existing ViewModels.
- `subscription_tier` user property + `setUserId` on login/logout.
- Debug-menu DebugView toggle.
- Unit tests + iOS DebugView verification + manual smoke test in the PR.

## Backlog (future PRs)

- **PR 2 — mid-funnel events** (~9 more, same interface): measurements added, order status
  advanced, receipt sent, WhatsApp confirm tapped, customer/order deleted, search used,
  style/fabric photo added, etc. Just more `AnalyticsEvent` subtypes — no new architecture.
- **PR 3 — iOS Crashlytics wiring** (existing known gap); natural to pair with analytics
  since both are Firebase init. Correlates crash + behaviour on iOS.
- **Later — Looker Studio dashboard** over the BigQuery export, once funnels reveal what to
  chart.
- **Later — tap-level events** only where a funnel reveals a specific drop-off worth
  dissecting (avoid tag-everything noise).
- **Field-consolidation dependency:** the `subscription_tier` user property must read
  whichever field wins the `tier` vs `subscriptionTier` consolidation (smart-tier-field
  backlog). Flag so the two don't drift.

## Open questions

None blocking. Tier-field source resolves with the consolidation backlog; until then the
property reads the same field the entitlement layer reads today.
