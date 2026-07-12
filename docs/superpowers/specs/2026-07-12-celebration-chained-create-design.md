# Celebration Card for Chained Customer→Measurements Create — Design

**Date:** 2026-07-12
**Status:** Approved design (QA finding on PR #264)

## Problem

When "Add measurements next" is toggled on the customer form, create-success fires
the FirstCustomer celebration AND navigates to Add Measurements in the same breath.
The global overlay therefore plays on top of the measurement form — interrupting
the exact task the user chose (screenshot in QA report, 2026-07-12).

## Decision (Option A of three considered)

Keep the celebration at the moment of achievement, but when the create chains into
measurements the card becomes the bridge into that task: contextual body + CTA.
Rejected: deferring until measurement save (new cross-screen state, celebration can
be lost forever or fire disconnected from the moment) and holding navigation until
dismissal (couples form navigation to the overlay system).

## Changes

1. **Model** — `Milestone.FirstCustomer` gains `addingMeasurementsNext: Boolean = false`.
   `key` stays `"first_customer"`; one-shot flag and `celebration_shown` analytics
   unchanged (presentation-only data, like `customerFirstName`).
2. **Trigger** — `CustomerFormViewModel` passes the existing state toggle:
   `Milestone.FirstCustomer(name, addingMeasurementsNext = s.addMeasurementsNext)`
   (`s.addMeasurementsNext` is the same flag `postSaveEvent` uses to pick
   `NavigateToNewCustomerMeasurement`).
3. **Card** — `CelebrationOverlay.body()` / `buttonLabel()` branch on the flag:

   | Case | Body | CTA |
   |---|---|---|
   | Plain create (unchanged) | %1$s is in your workshop. Every great atelier starts with one. | Continue |
   | Chained to measurements | %1$s is in your workshop. Let’s capture their measurements. | Add measurements |

   Two new strings: `celebration_first_customer_body_measurements`,
   `celebration_add_measurements`. "their" keeps the copy gender-neutral.

## Unchanged

Timing, confetti, dismissal paths, queueing, WorkshopReady and FirstOrder cards,
flags, analytics.

## Edge cases

- Scrim/back dismissal of the chained card just dismisses — the user is already on
  the measurement screen either way; no behavior fork.
- Toggle on but not actually first (flag set or list non-empty): nothing shows,
  same as today.

## Testing

- Existing celebration tests pass unchanged (default `false` preserves equality
  assertions; controller tests use the default).
- New VM test: toggle `OnToggleAddMeasurementsNext` before save → milestone equals
  `FirstCustomer("Adaeze", addingMeasurementsNext = true)`.
- Manual: create first customer with toggle ON → card over measurement screen reads
  "Let's capture their measurements" / "Add measurements"; dismiss → form usable.
