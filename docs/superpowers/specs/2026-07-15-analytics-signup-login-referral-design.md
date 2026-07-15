# Analytics: SSO sign_up fix, login event, referral_code_applied — Design

**Date:** 2026-07-15
**Target release:** 1.1.0 (in flight — small, release-safe diff)

## Problem

1. **SSO sign-ups are invisible.** Only `SignUpViewModel`'s email path logs `sign_up`.
   BigQuery shows 75 signed-in users but only 39 logged `sign_up` users since 2026-06-23 —
   roughly half the funnel entry is missing, worst on iOS (13 logged vs 31 signed in).
2. **No `login` event** — returning-user re-engagement can't be separated from activation.
3. **No referral event in GA4** — referral attribution lives only in Firestore, so
   acquisition-channel segmentation can't be done inside the GA4/BigQuery funnel.

## Events (all in `core/analytics/domain/AnalyticsEvent.kt`)

| Event | Params | Fired when |
|---|---|---|
| `sign_up` (existing, gains param) | `method`: `email\|google\|apple` | Account created (email signup, or SSO with `isNewUser=true`) |
| `login` (new) | `method`: `email\|google\|apple` | Existing account signs in (email login, or SSO with `isNewUser=false`) |
| `referral_code_applied` (new) | `source`: `manual\|install_referrer\|clipboard`; `surface`: `signup\|settings` | `recordAttribution` succeeds with `alreadyAttributed=false` |

`method` and the referral params are PII-free enums, consistent with the existing
taxonomy. `login` and `sign_up` use GA4's recommended-event names.

## Architecture decision: exposing `isNewUser`

**Chosen: wrap the SSO result.** `AuthRepository.signInWithGoogle()` / `signInWithApple()`
change from `Result<User, AuthError>` to `Result<SsoSignIn, AuthError>`:

```kotlin
/** SSO auth outcome. [isNewUser] distinguishes account creation from a returning login. */
data class SsoSignIn(val user: User, val isNewUser: Boolean)
```

`FirebaseAuthRepository` reads `authResult.additionalUserInfo?.isNewUser` (GitLive),
defaulting to `false` when absent (safer to undercount signups than overcount).
ViewModels decide which event to log — preserves the "analytics fires from ViewModels,
never data sources" pattern.

Rejected: logging inside `FirebaseAuthRepository` (breaks layering); `isNewUser` on the
`User` model (transient auth artifact on a persistent profile model).

## Auth wiring matrix

| Flow | Screen | Event |
|---|---|---|
| Email signup success | SignUp | `sign_up(email)` |
| Email login success | Login | `login(email)` |
| SSO success, `isNewUser=true` | SignUp **or** Login | `sign_up(google/apple)` |
| SSO success, `isNewUser=false` | SignUp **or** Login | `login(google/apple)` |

`LoginViewModel` gains an `Analytics` constructor param (Koin `viewModelOf` resolves it;
no defaulted params, so no lambda form needed).

## Referral wiring

Both `recordAttribution` call paths log on success, skipping when the server reports
`alreadyAttributed=true` (dedupe across retries/idempotent replays):

- `ReferralAttributionCoordinator.attributeOnce` success branch →
  `ReferralCodeApplied(source = source.wire, surface = "signup")`. Coordinator gains an
  `Analytics` constructor param (core-domain interface; wired in `ReferralModule` factory).
- Settings `ReferralCodeViewModel` success branch →
  `ReferralCodeApplied(source = "manual", surface = "settings")`.

## Error handling

No flow-semantics changes. `Analytics` is fire-and-forget by contract (never throws or
blocks). The only behavioral change is the SSO return type, absorbed by the two ViewModels.

## Testing

- `AnalyticsEventTest`: name + param contracts for `SignUp(method)`, `Login(method)`,
  `ReferralCodeApplied(source, surface)`.
- `SignUpViewModelAnalyticsTest` (extend) + `LoginViewModelAnalyticsTest` (new): the
  4-row auth matrix via `FakeAnalytics` + fake `AuthRepository`.
- `ReferralAttributionCoordinatorTest` (extend): logs on fresh success with correct
  source/surface; skips when `alreadyAttributed=true`; no event on failure.
- `ReferralCodeViewModelTest` (extend): settings-surface event + `alreadyAttributed` skip.
- Gates: `:composeApp:testDebugUnitTest`, detekt, and iOS compile
  (`compileKotlinIosSimulatorArm64`) since a KMP interface changes.

## Post-merge ops (not code)

- Register `method` as a GA4 event-scoped custom dimension (`source`/`surface` too if
  console segmentation is wanted; BigQuery has them regardless).
- Update `docs/analytics/ga4-explorations-and-bigquery.md`: `sign_up` is complete from
  1.1.0 onward; funnels spanning the boundary should treat earlier `sign_up` as email-only.
  (Done as part of this change, since the doc lists the SSO gap as a known caveat.)

## Scope

One new file (`LoginViewModelAnalyticsTest`), ~8 modified (AnalyticsEvent, AuthRepository,
FirebaseAuthRepository, SignUpViewModel, LoginViewModel, ReferralAttributionCoordinator,
ReferralCodeViewModel, ReferralModule) + tests + analytics doc. No schema, DI-graph, or
navigation changes beyond added constructor params.
