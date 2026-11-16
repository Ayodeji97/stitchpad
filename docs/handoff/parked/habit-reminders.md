# PARKED — Daily habit reminders (retention)

Status: parked by Daniel 2026-11-15 for later. Do not build yet.

## Idea
Help tailors / fashion designers build a daily habit of recording their work, with a
reminder at a time they choose.

## Reference (from another app, not to copy visually)
`habit-reminder-optin-reference.png`: a full-screen opt-in, "Build a habit with
reminders", a mascot with stacked notification cards ("You're on a roll!", "Almost
there!", "Badge update"), a **Set time** pill (default to the current hour), "You can
always change this later", a primary "Yes, turn on" button and a "Set later" link.

What to take from it:
- Ask once, at a good moment (e.g. after the first order is saved), never on cold start.
- The user picks the reminder time, which is stored per user in their timezone.
- Encouraging, progress-flavoured copy (streaks), not nagging.
- Can be changed later in Settings > Notifications.

## Open questions (Daniel)
1. Which habit counts: recording an order, recording money, or any activity?
2. Skip the reminder on days they've already recorded something? (Recommended: yes.)
3. Streaks / badges, or the reminder alone?
4. How it sits with `dailyDigest` (07:00 Lagos) and `engagementPush` so a tailor isn't
   double-pushed on the same day.

Uses the Adire Atelier brand (Indigo primary, Paper/Ink backgrounds), not the
reference's colours or mascot. Needs per-user timezone from the server time foundation
(`functions/src/time/userLocalTime.ts`).
