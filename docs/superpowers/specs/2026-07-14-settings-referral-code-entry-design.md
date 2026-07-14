# Settings Referral Code Entry — Design

- **Date:** 2026-07-14
- **Status:** Design (awaiting review before implementation planning)
- **Feature area:** `feature/referral`, `feature/settings`

## Problem

A referred user enters their marketer's code on the SignUp screen's optional
"Have a referral code?" field. But **SSO users never see that field** — Google/
Apple sign-in is a one-tap flow, and accounts can also be created via the Login
screen's SSO buttons, which have no referral field and no attribution wiring at
all. Automatic capture only rescues Android users who arrived through a Play
referral link (Install Referrer); **iOS SSO users, and anyone who signs up with
SSO, currently have no way to attribute their referral.** The marketer loses a
legitimate payout.

This is now cleanly fixable because the qualification window was re-anchored on
**attribution time** (see `2026-07-13` fraud-decisions work / PR #271): a code
entered after signup still opens a fair 14-day qualification window from the
moment of entry.

## Goal

Give every signed-in user a **persistent, always-available place to enter a
referral code**, so an SSO user can be told by their marketer to open Settings
and enter the code. Entry is explicit and confirms its result.

## Non-goals (deliberately cut)

- **No post-signup nudge / prompt.** Considered and rejected — the marketer
  drives the user to Settings. Removing it eliminates SSO-provider detection,
  a new "nudge seen" flag, and a collision with the Workshop-Setup celebration
  overlay.
- **No changes to the Login or SignUp screens.** The Settings entry subsumes the
  Login-screen SSO gap.
- **No server changes.** `recordReferralAttribution` already handles everything:
  it is idempotent (first-wins), silently rejects unknown codes, and now anchors
  the window on attribution time.
- **No client-side deadline.** Because the window starts at attribution, there is
  no hard cutoff to *enter* a code; entering later simply starts the 14-day active
  window later. "Enter it within a time frame" is soft guidance from the marketer,
  not enforced in code.

## Approach

One new self-contained MVI screen, reached from one new Settings row.

```
Settings ──(new "Referral code" row)──▶ ReferralCodeScreen
                                          └─ text field + Apply
                                          └─ recordAttribution(code, deviceHash, MANUAL)
                                          └─ result → snackbar / inline message
```

This matches the app's existing Settings sub-screen pattern (ChangePassword,
ChangeEmail, DeleteAccount are each their own navigated screen), so a dedicated
screen is more consistent than a bottom sheet and sidesteps the known iOS
`ModalBottomSheet` timing gotcha.

## Components

### 1. `ReferralCodeScreen` (new MVI unit, `feature/referral/presentation/entry/`)
Follows the mandated Root/Screen split and MVI pattern:

- **State** — `ReferralCodeState(codeInput: String = "", isSubmitting: Boolean = false)`.
  A derived `code` normalizes input (strip spaces/hyphens, uppercase — mirrors the
  server's `asCode`) and `canSubmit = code.isNotBlank() && !isSubmitting`.
- **Action** — `OnCodeChange(String)`, `OnApplyClick`, `OnBackClick`.
- **Event** — `NavigateBack`, `ShowMessage(UiText)` (snackbar feedback).
- **ViewModel** — depends on `ReferralRepository` + `ReferralPreferencesStore`.
  On `OnApplyClick`:
  1. `deviceHash = preferences.getOrCreateDeviceId()`
  2. `referralRepository.recordAttribution(code, deviceHash, ReferralSource.MANUAL)`
  3. Map the `Result`:
     - `Success` (attributed **or** `alreadyAttributed`) → success message, mark
       `preferences.setAttributed()` to keep local capture state consistent, emit
       `NavigateBack`.
     - `Error(CODE_NOT_FOUND)` → "That code wasn't recognized" (stay on screen).
     - `Error(NETWORK)` → offline message (stay, allow retry).
     - `Error(UNKNOWN)` → generic failure (stay).
- **`ReferralCodeRoot`** — has the `koinViewModel()`, wires `ObserveAsEvents`,
  owns the `SnackbarHostState`, takes an `onNavigateBack` callback.
- **`ReferralCodeScreen`** — stateless, `@Preview` required.

The user is always authenticated here (Settings is behind auth), so
`recordAttribution`'s `uid` requirement is always satisfied.

### 2. Settings row (`feature/settings`)
- Add `SettingsAction.OnReferralCodeClick`.
- Add a row in `SettingsScreen` ("Have a referral code?") using the existing row
  component, near the invite/gift rows.
- Root maps the action to a new `onNavigateToReferralCode` callback.
- Optional polish (not required for V1): if `preferences.hasAttributed()` is true
  locally, show a subtle "applied" hint. Kept out of V1 to avoid over-reading a
  best-effort local flag; the screen already handles re-entry gracefully.

### 3. Navigation
- New `@Serializable` route object for the referral-code screen.
- Wire it into the settings nav graph; `SettingsScreen`'s
  `OnReferralCodeClick` → navigate; screen's back → pop.

### 4. DI (`di`)
- `viewModelOf(::ReferralCodeViewModel)` (or a `viewModel { }` factory if
  constructor defaults are involved). `ReferralRepository` and
  `ReferralPreferencesStore` are already provided.

## Data flow

Explicit, foreground, returns a result for UI feedback — unlike the automatic
`ReferralAttributionCoordinator`, which is fire-and-forget/silent. The Settings
entry therefore calls `ReferralRepository.recordAttribution` **directly** rather
than through the coordinator, so it can surface success/not-found to the user.

## Error handling

Map every `ReferralError` to a `UiText` via a `toUiText()` extension in the
presentation layer (per the project error-handling rules): `CODE_NOT_FOUND`,
`NETWORK`, `UNKNOWN`. Success covers both `attributed` and `alreadyAttributed`
(both are "your code is on file"). Nothing here blocks the user or throws.

## Strings (compose.resources — no hardcoded strings)

New resources: settings row label, screen title, field label + placeholder, apply
button, success message, already-applied message (may reuse success), and the
three error messages. Use `&apos;`/curly apostrophes per the no-backslash rule.

## Testing

- `ReferralCodeViewModelTest` (commonTest, kotlin.test + Turbine, `FakeReferralRepository`
  + `FakeReferralPreferencesStore` — both already exist from the debug-actions work):
  - success → success message + `NavigateBack` + `setAttributed()` called
  - `alreadyAttributed` → treated as success
  - `CODE_NOT_FOUND` → error message, no navigation
  - `NETWORK` / `UNKNOWN` → error message, no navigation
  - code normalization (spaces/hyphens/case) before submit
  - blank code → `canSubmit` false / no call
- `@Preview` renders for `ReferralCodeScreen` (empty + submitting states).

## Fraud / abuse note

A long-time existing user could enter a code to attribute themselves late. The
window anchors on attribution time, so they would still need 4 distinct active
days *after* entry to qualify (a real ongoing-use signal), and existing fraud
controls (self-referral, device-reuse, velocity, 7-day hold) still apply. This
residual is acceptable for V1 given attribution is marketer-driven; revisit only
if abuse appears.

## Out of scope / future

- Post-signup nudge (cut).
- Activating the gated-off iOS clipboard provenance reader (separate effort).
- Showing the user their own referral status in-app (blocked: `referrals/{uid}`
  is admin-SDK-only).
