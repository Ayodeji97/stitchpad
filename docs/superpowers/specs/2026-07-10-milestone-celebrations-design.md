# Milestone Celebrations — Design

**Date:** 2026-07-10
**Status:** Approved design, pending implementation plan

## Summary

Celebrate three one-time milestones with a springy overlay card and tailor-themed
confetti: workshop setup complete (or skipped), first customer created, first order
created. Hand-built in Compose (no new dependencies), triggered from existing
success branches, gated by per-user one-shot flags.

## Goals

- Make the three "firsts" feel like real moments — a celebration, not a snackbar.
- On-brand: Adire Atelier palette, tailoring-shaped confetti, Fraunces/Manrope type.
- Zero disruption to existing flows: navigation events fire unchanged; the
  celebration plays over the destination screen.
- Each celebration shows exactly once per user per device, ever.

## Non-goals

- No server-side/Firestore milestone tracking (device-local flags are acceptable;
  a reinstall may re-show a celebration once — fine).
- No recurring celebrations (10th order etc.) in this slice, though the
  architecture makes them a one-line trigger later.
- No sound. One light haptic only.

## Milestones

| Milestone | Trigger point | Data carried |
|---|---|---|
| `WorkshopReady` | `WorkshopSetupViewModel` — `onContinue()` success AND `onSkip()`, before `NavigateToHome` | none |
| `FirstCustomer` | `CustomerFormViewModel.save()` — create branch success, after analytics | customer first name |
| `FirstOrder` | `OrderFormViewModel` — create branch success, after analytics | customer first name |

Skippers get the workshop celebration too: the moment is "you're in", not "you
filled a form". Edits never trigger (create branches only).

## Architecture

All new code in shared `commonMain`.

### CelebrationController (`core/presentation/celebration/`)

- Koin `single`. Holds `StateFlow<CelebrationState?>` (null = nothing showing).
- `trigger(milestone: Milestone)`: if the per-user flag for this milestone is
  unset → persist the flag **immediately**, log `AnalyticsEvent.CelebrationShown`,
  and set state. If a celebration is already visible, queue the new one (FIFO)
  and play it after dismissal. If the flag is set → no-op.
- `dismiss()`: clears current state, promotes next queued item if any.
- Clears state and queue when the auth user changes (never plays over login).
- ViewModels only report milestones; the controller owns "should it show"
  (tell, don't ask).

### Milestone model

```kotlin
sealed interface Milestone {
    data object WorkshopReady : Milestone
    data class FirstCustomer(val customerName: String) : Milestone
    data class FirstOrder(val customerName: String) : Milestone
}
```

### One-shot flags

Three new per-user boolean keys in `OnboardingPreferencesStore`, mirroring the
existing `completedWorkshopKey(userId)` pattern: `celebratedWorkshop`,
`celebratedFirstCustomer`, `celebratedFirstOrder`. Persisted at trigger time
(not dismissal) so a crash mid-celebration can never cause a re-show. Included
in `resetForDebug()`.

"First" is defined by the flag, not by counting documents — deleting all
customers later never re-fires it.

### CelebrationOverlay (`ui/components/celebration/`)

Hosted once in `App.kt`, layered over `StitchPadNavHost` (App.kt:46) in a `Box`.
Observes the controller's state. Renders scrim + confetti + card when non-null.

## Visual & motion spec

Choreography (~2.5s of motion, then settles until dismissed):

1. **Scrim** fades in ~200ms — black 45% (light mode) / 60% (dark mode).
2. **Confetti burst**: 60–80 particles from top-center and both top corners.
   Initial upward/outward velocity, then gravity; particles rotate, drift, and
   fade out as they fall. Field completes in ~2.5s, no loop.
3. **Card** springs up 150ms after burst start: scale 0.6 → 1.0 with
   `spring(dampingRatio = DampingRatioMediumBouncy)` + fade-in.
4. **Emblem pop**: the card's icon does its own smaller, delayed spring
   (two layered bounces).
5. **Haptic**: one light tap as the card lands (expect/actual —
   Android `HapticFeedback`, iOS light impact).

### Particles (Compose Canvas, no library)

| Shape | Weight | Drawing | Colors |
|---|---|---|---|
| Fabric squares | ~45% | small rounded rects, varied sizes | indigo, sienna, paper/cream |
| Buttons | ~30% | filled circle + 4 stitch holes | Indigo 500, Sienna 500 |
| Thread snippets | ~25% | short curved strokes | indigo, sienna |

Saffron `#E8A800` on ~1 in 12 particles — the rare heritage accent per brand
rules. All colors from `DesignTokens`; **dark mode** swaps paper-tone particles
for lighter indigo/cream so they stay visible on the darker scrim. Both modes
specified up front.

### Card

Standard surface color, `DesignTokens` radius/spacing, centered. Emblem, Fraunces
headline, Manrope body, full-width `StitchPadButton`. All copy via
`compose.resources` string resources with positional args.

Emblem per milestone: `WorkshopReady` = `StitchPadMark` (brand logo);
`FirstCustomer` = the app's existing customer/person icon; `FirstOrder` = the
existing order icon — reuse current iconography, no new assets.

| Milestone | Headline | Body | Button |
|---|---|---|---|
| WorkshopReady | Your workshop is open! | Everything you need to run your tailoring business is right here. | Let's go |
| FirstCustomer | Your first customer! | %1$s is in your workshop. Every great atelier starts with one. | Continue |
| FirstOrder | First order in the books! | You're officially in business. Let's get %1$s's outfit made. | Continue |

### Dismissal

Continue button, tap on scrim, or system back — all call `dismiss()`. No
auto-dismiss.

### Accessibility

- Dialog semantics: scrim + card block interaction beneath; screen readers read
  headline then body; Continue focusable.
- Reduced motion (platform setting): skip confetti, simple fade-in card.

## Edge cases

- **Back-to-back milestones**: FIFO queue in the controller — never two at once,
  never dropped.
- **Sign-out / account switch**: controller clears visible + queued celebrations
  on auth change; flags are per-user so each account gets its own firsts.
- **Process death mid-celebration**: flag already persisted; never re-shows.
- **Offline**: creates succeed only after server ACK (GitLive), so triggers fire
  exactly when existing success paths do.
- **Reinstall / new device**: flags are local; celebration may show once more.
  Accepted trade-off vs. adding Firestore fields.

## Analytics

New `AnalyticsEvent.CelebrationShown(milestone)` logged by the controller when a
celebration actually shows — measures how many users reach each first.

## Debug

Debug menu entry "Reset celebrations" (debug builds only) clearing the three
flags, for QA and animation tuning.

## Testing

- **Unit (commonTest, kotlin.test + Turbine)**: `CelebrationController` — fires
  once then never; per-user isolation; FIFO queueing; clear on auth change.
  Fake preferences store.
- **ViewModel tests**: extend WorkshopSetup/CustomerForm/OrderForm VM tests to
  assert `trigger()` on create success, and not on edit or failure.
- **Manual smoke (PR)**: fresh account → workshop setup → celebration 1 → first
  customer → celebration 2 → first order → celebration 3; second creates don't
  re-fire; light + dark mode; Android + iOS simulator (iOS compile gate).
- Detekt + full `testDebugUnitTest` before PR.
