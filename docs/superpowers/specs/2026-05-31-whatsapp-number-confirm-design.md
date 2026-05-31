# WhatsApp Number Validation + "Confirm on WhatsApp" — Design Spec

**Date:** 2026-05-31
**Branch:** `feature/whatsapp-confirm`
**Status:** Approved (brainstorm), pending implementation plan

## Problem

Tailors enter their own WhatsApp number during onboarding (Workshop Setup) and in
Settings → Edit Profile. The number powers customer follow-up features (Draft Message /
Smart Suggestions, receipts). Today:

- **Workshop Setup** validates only "13 digits starting with 234" via
  `validateNigerianMobileE164()` — it accepts fake numbers like `+234 000 000 0000`.
- **Edit Profile** validates even more weakly: just a digit-count range (10–15), no E.164
  shape. The two screens have drifted.
- Nothing checks the number is actually reachable on WhatsApp, so typos and
  non-WhatsApp numbers silently flow into the contact features.

There is **no free, official, real-time "is X on WhatsApp?" API** (Meta removed the
on-premises `/contacts` endpoint for privacy; a true ownership OTP needs a paid
Meta/Twilio backend). So we harden format validation and add a **no-backend pseudo-OTP**
that confirms the number is *reachable on WhatsApp*.

## Goals

1. Harden Nigerian-mobile format validation; unify both screens onto one validator.
2. Add an optional **"Confirm on WhatsApp"** flow that proves the number resolves to a
   real WhatsApp chat (catches typos / landlines / non-WhatsApp numbers).
3. Persist a `whatsappConfirmed` flag so downstream features can trust the number.

## Non-Goals / Honest Limitations

- **This is not an ownership/identity check.** The confirmation code lives on the device
  and is shown in WhatsApp's compose box; no message is sent or received. It proves the
  number is **WhatsApp-reachable** and that the user completed the round-trip — *not* that
  they own the number. UI copy must say **"WhatsApp confirmed"**, never "Verified owner".
- No new third-party dependency. Explicitly **not** adding `kphonenumber`/libphonenumber
  (JVM-only Google lib breaks iOS native; KMP ports carry iOS-native compile risk and
  binary-size cost). Nigeria-only V1 does not need it; the pseudo-OTP is the real backstop.
- No paid Meta/Twilio OTP backend.

## Why the pseudo-OTP works (and what it guards)

`wa.me/<number>?text=<code>` opens a WhatsApp **compose box with the code prefilled only
if the number is registered on WhatsApp**. A non-WhatsApp number lands on an
"isn't on WhatsApp / invite?" screen with no compose box, so the user cannot retrieve the
code and cannot pass. Typing the code back therefore confirms:

- ✅ the number is a real, WhatsApp-registered number, and
- ✅ the user actually completed the round-trip.

It does **not** confirm ownership (see Limitations). Acceptable because the tailor
self-attests their own number; the real failure mode we guard is typos / non-WhatsApp
numbers.

## Decisions (from brainstorm)

| Decision | Choice |
|---|---|
| Validation engine | Harden existing `PhoneNormaliser` (no dependency, iOS-safe) |
| Scope | Both screens: Workshop Setup + Edit Profile |
| Confirmation requirement | **Optional nudge** (can save without confirming) |
| Code length | **4 digits** (carried across an app-switch) |
| Persistence | Persist `whatsappConfirmed` boolean in `users/{uid}` |

## Design

### 1. Validation hardening — `core/sharing/PhoneNormaliser.kt`

Tighten `validateNigerianMobileE164()` from "13 digits starting with 234" to the standard
Nigerian-mobile leading shape:

```
E.164 must match:  ^234[789][01]\d{8}$
```

Covers operator blocks `70x/71x/80x/81x/90x/91x` (MTN/Glo/Airtel/9mobile/etc.); rejects
`+234 000…`, landlines, and short/garbage input. It does **not** enumerate every operator
block — that is what libphonenumber is for, and the pseudo-OTP catches well-formed fakes
since they won't be WhatsApp-reachable.

- Keep existing `normaliseNigerianPhone`, `applyImpliedNigerianCountryCode`,
  `buildWhatsAppUrl`, `urlEncode` as-is.
- **Unify Edit Profile** onto `validateNigerianMobileE164` + `applyImpliedNigerianCountryCode`,
  removing its weaker digit-count check so both screens behave identically.

### 2. Shared confirm sub-state + component

New reusable UI sub-state used by both screens:

```kotlin
data class WhatsAppConfirmUiState(
    val confirmed: Boolean = false,      // persisted; loaded from User
    val code: String? = null,            // session-only generated code
    val input: String = "",
    val promptVisible: Boolean = false,  // OTP input shown after launch
    val error: StringResource? = null,
)
```

New stateless composable **`WhatsAppConfirmRow`** (in `ui/components/`), rendered under the
WhatsApp field on both screens, with `@Preview`s for the three states:

- **idle** (valid number, not confirmed): "Confirm on WhatsApp" text button.
- **prompting** (`promptVisible`): helper text + 4-digit input + optional error.
  *"Open WhatsApp, read the code we drafted, then enter it here — you don't need to send
  the message."*
- **confirmed**: "WhatsApp confirmed ✓" chip (+ subtle re-confirm affordance).

The button/input only appear once the number passes format validation.

### 3. MVI wiring (identical on both screens)

Add to `WorkshopSetupState` and `EditProfileState`:
`val whatsappConfirm: WhatsAppConfirmUiState = WhatsAppConfirmUiState()`.

New actions on both `WorkshopSetupAction` and `EditProfileAction`:

- `OnConfirmWhatsAppClick` — validate format; generate 4-digit code via injected
  `() -> String`; set `promptVisible = true`; emit `LaunchWhatsApp(phoneE164, message)`.
- `OnConfirmCodeChange(value)` — update `input`, clear `error`; auto-submit when length == 4.
- `OnConfirmCodeSubmit` — match → `confirmed = true`, hide prompt; mismatch → set `error`.
- `OnDismissConfirm` — cancel the prompt row.

New event on both: `LaunchWhatsApp(phoneE164: String, message: String)`, handled in each
**Root** via the existing `WhatsAppLauncher` (the Draft Message pattern) — reuses
`buildWhatsAppUrl`. iOS path is `UIApplication.openURL` (no UIKit sheet → no modal-timing
trap).

**Critical invariant — reset on edit:** `OnWhatsAppNumberChange` must reset
`whatsappConfirm` to `confirmed = false` and clear `code`/`input`/`error`. A confirmed
number that is later edited is no longer confirmed. Enforced client-side and re-asserted at
save time (persist the *current* confirmed state, which is `false` after any edit).

### 4. Code generation (testable)

Inject `confirmCodeGenerator: () -> String` into both ViewModels via Koin (mirrors the
existing `() -> Long` clock-injection pattern), defaulting to a `kotlin.random.Random`-based
4-digit generator. **Pad with `String.padStart(4, '0')`, never `String.format`** (JVM-only,
breaks iOS). Injection makes the code deterministic in ViewModel tests.

### 5. Persistence

- `core/domain/model/User.kt` — add `whatsappConfirmed: Boolean = false`.
- `core/data/dto/UserDto.kt` — add `whatsappConfirmed: Boolean = false` (Firestore field
  `whatsappConfirmed`).
- `core/data/mapper/UserMapper.kt` — map both directions.
- `core/data/repository/FirebaseUserRepository.kt` — read on load, write on save.
- On load, both screens prefill `whatsappConfirm.confirmed` from the user.
- On save, persist current confirmed state (false after any number edit).

### 6. Strings (`composeResources/values/strings.xml`)

New snake_case resources (apostrophes as `&apos;`, never `\'`; interpolation via
compose-resources `%1$s`, never `String.format`):

- `whatsapp_confirm_cta` — "Confirm on WhatsApp"
- `whatsapp_confirmed_badge` — "WhatsApp confirmed"
- `whatsapp_confirm_instructions` — round-trip helper text (no-send note)
- `whatsapp_confirm_input_label` — "Enter the 4-digit code"
- `whatsapp_confirm_message` — "StitchPad confirmation code: %1$s — you can delete this
  message." (prefilled into wa.me)
- `whatsapp_confirm_error_mismatch` — "That code doesn&apos;t match. Check WhatsApp and try
  again."
- Reuse/adjust the existing invalid-format error copy for the hardened validator.

### 7. Debug affordance (debug source set)

Per the per-feature debug-menu convention: in debug builds, surface the generated code
inline (reveal or "fill code" button) so QA/testers can confirm without actually switching
to WhatsApp.

## Testing

- `PhoneNormaliserTest` — new cases: valid prefixes (`803/703/810/814/903/916/705…`),
  rejected (`+234 000…`, landline, too short/long, non-`[789][01]` lead).
- `WorkshopSetupViewModelTest` / `EditProfileViewModelTest` — generate → correct input →
  `confirmed = true` + persisted; wrong input → error; **edit number resets confirmed**;
  Edit Profile now rejects what the old digit-count check accepted.
- `WhatsAppConfirmRow` — `@Preview` for idle/prompting/confirmed (light + dark).
- Run **iOS compile** before declaring done (`[789][01]` regex, `padStart`, `openURL`).

## QA smoke test (for the PR)

1. Onboarding → Workshop Setup: enter a valid Nigerian WhatsApp number, tap **Confirm on
   WhatsApp**, read the 4-digit code in WhatsApp, return, enter it → "WhatsApp confirmed ✓".
2. Edit a digit → chip disappears (confirmed reset).
3. Enter a non-WhatsApp / fake-but-well-formed number → WhatsApp shows "isn't on WhatsApp",
   no code retrievable → cannot confirm; number still saveable (optional).
4. Enter an invalid-format number (e.g. `+234 200…`) → inline validation error, no confirm
   affordance.
5. Repeat 1–2 on Settings → Edit Profile; confirm the flag round-trips through Firestore.
6. iOS + Android, light + dark.

## Files touched

`PhoneNormaliser.kt` · `User.kt` · `UserDto.kt` · `UserMapper.kt` ·
`FirebaseUserRepository.kt` · Workshop {State, Action, ViewModel, Screen, Root} ·
EditProfile {State, Action, ViewModel, Screen, Root} · new `WhatsAppConfirmUiState` +
`WhatsAppConfirmRow` + Koin code-generator binding · `strings.xml` · tests.
