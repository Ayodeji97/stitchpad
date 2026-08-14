# Staff Dashboard Focus Queue — Design Spec (A×B blend)

Approved by Daniel 2026-08-14 from the mockup page (artifact `f4cf2173`, "A × B — the blend").
Depends on PR #365 (`feat/staff-card-assignee` — assignee fields on `DashboardOrderRow` + viewer resolution); implementation branches from it.

## Goal

The staff dashboard's order list answers "what do I work on next" at a glance: one **Up next hero** with a one-tap stage advance, then a prioritized queue of **ticket cards** whose tear-line footer always carries stage (dots + name) and assignee (chip). Kills today's alarm fatigue (every card red) and the meaningless "Pickup not ready" line.

## Scope

- `StaffDashboardContent` only (staff view). The recently shipped top section (greeting, staff pill, count tiles, pipeline bar) is untouched; this replaces the needs-attention/order-list portion below it.
- Owner dashboard unchanged (it already gains assignee chips from #365).

## Screen structure (below the existing pipeline bar)

1. **"Up next" hero** — the single highest-priority actionable order assigned to the viewer.
   - Selection: assigned-to-viewer, stage != READY/DELIVERED, ordered by (most days late) → (soonest deadline) → (oldest createdAt). None qualifying → no hero; if the viewer has zero assigned open orders, the existing `StaffAllCaughtUp` shows above the shop queue.
   - Contents: customer name (Fraunces), garment + qty, calibrated urgency chip top-right, 5-segment stage stepper (done = indigo, current = saffron — the heritage accent's one legitimate home), stepline labels (previous ✓ / current "— now" / final), full-width CTA **"Mark ‹stage› done → ‹next›"**, tear-line footer: derived order code left, `You` chip right.
   - CTA advances the production stage via the same repository call Order Detail's timeline update uses. Optimistic disable while in flight; on error, snackbar + state unchanged. Reaching READY: order stops qualifying for hero; next order promotes. No undo in v1 (stage remains editable in Order Detail).
2. **"Then" queue** — remaining viewer-assigned open orders, same priority order, as compact tickets:
   - Main row: avatar (initials), name (bold), garment, urgency chip right.
   - Tear-line footer (dashed top border): stage dots (`●●●○○` — done indigo, current saffron, rest neutral) + stage name left; assignee chip right (`You` filled indigo / teammate name quiet outline).
3. **"Unassigned in the shop · N"** — unassigned open orders at 70% opacity, same ticket anatomy, footer chip `Unassigned`. Orders assigned to *other* members do not appear on the staff dashboard queue (unchanged from today's data source) — if the data source already includes them, they render in the shop section with the teammate chip.

## Urgency calibration (all cards + hero)

- `late` chip (red tint): only when `daysLate > 0` — "N days late".
- `soon` chip (amber tint): deadline within 3 days — "Due ‹weekday›" / "Due tomorrow" / "Due today".
- `ok` chip (neutral): otherwise — "On track".
- "Pickup not ready" is removed from these cards entirely.

## Data & model changes

- `DashboardOrderRow` gains `stage: PipelineStage?` (null-safe for legacy call sites). Populated in `BucketCalculator` from the order's current production stage. Assignee fields already exist (#365).
- Derived order code: `"ORD-" + orderId.takeLast(4).uppercase()` — presentation-layer helper, pure, unit-tested. (No stored human order number exists; deterministic derivation gives the shop a speakable shorthand.)
- New VM action `OnAdvanceStage(orderId, fromStage)`: guards re-entrancy (ignore while in flight for that order), calls the existing stage-update repository method, emits snackbar on error. `fromStage` guards a stale tap racing a concurrent update elsewhere: if the order's current stage no longer matches, no-op.

## Strings (new, in strings.xml)

`staff_up_next_header`, `staff_then_header`, `staff_unassigned_header_count` (with %d), `staff_advance_stage_cta` (two %s), `staff_due_today`, `staff_due_tomorrow`, `staff_due_weekday` (%s), `staff_on_track`, `staff_stage_now` (%s). Reuse existing days-late string.

## Error handling

- Stage advance failure → existing snackbar pattern with a new `staff_advance_stage_error` string; card state reverts (no optimistic stage change — the CTA disables, stage only updates when the repository listener echoes it).
- All flows already self-heal via `retryWithFallback` (PR #360); no new listener work.

## Testing

- `BucketCalculator`: stage population (TDD).
- Order-code derivation: pure function tests.
- Urgency calibration mapping (late/soon/ok from daysLate/daysUntilDeadline): pure function tests.
- Hero selection + priority ordering: pure function over rows (extract as testable function, not composable logic) — cases: late beats soon, soon beats ok, READY excluded, none-assigned → null.
- `DashboardViewModel.OnAdvanceStage`: success advances via repo (fake), error emits snackbar, re-entrancy ignored, stale `fromStage` no-ops.
- Previews: hero + queue + unassigned, light and dark, plus all-caught-up variant.

## Out of scope

- Owner dashboard redesign; order-detail changes; undo for stage advance; storing a real order number; any change to the top cards/pipeline bar.
