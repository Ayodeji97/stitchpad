# Dashboard earnings and Top Customers follow-up

Based on merged currency PR380 (`0523dee3`). Daniel approved the visual concepts
and requested “Total earnings so far” for recorded payments on 4 November 2026.

## Behavior

- Owner dashboard's main card shows current unarchived order balances as “Still to
  collect”, and all recorded payments, including archived jobs, as “Total earnings
  so far”. This is cash received before costs; the card explains that distinction.
- Every currency is displayed separately. Discounts and payments use the domain
  order's existing balance calculation. No exchange rates or profit inference.
- The summary is hidden until both current and archived order snapshots succeed;
  incomplete data must not present a partial lifetime total. Existing dashboard
  content remains available if the archived snapshot is missing/unavailable.
- Closing the card dismisses it for the current workshop/ViewModel session and
  restores the adaptive focus hero. Urgent and pickup guidance stays visible even
  while the summary is shown. “View orders” opens the existing Orders screen.
- Staff never subscribe to archived earnings or receive the money summary in state.
  Identity/workshop changes clear both earnings and dismissal.
- Top Customers removes fixed amount and empty badge slots. Names/order counts
  occupy the left column; amounts/“Total earnings” and optional badges occupy the
  right. Monetary text may wrap rather than truncate; long names allow two lines.
- Compose previews cover light/dark themes, mixed currencies, long names and a
  narrow 320dp layout with increased font scale.

## Validation

Android unit suite: 2,242 tests, zero failures/errors/skips. Android debug assembly,
iOS simulator source compilation, detekt and diff checks passed during implementation.
No backend changes or deployment required. The final layout receives the same checks
before commit. Runtime visual verification of the actual app remains outstanding;
generated concept images are not evidence of runtime rendering.

## Runtime check

1. Ghana example: 3,800 order, 400 payment → 3,400 still to collect and 400 earnings.
2. Record another payment; amounts update. Archive a paid order: earnings remain.
3. Check mixed currencies remain separate; check large text and long customer names.
4. Close summary: adaptive hero returns. Switch workshop/account: no previous totals.
5. Staff dashboard remains money-free; Top Customers badges and chevrons remain usable.
