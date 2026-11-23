# Notifications first, email only for paid customers (Codex task)

Decided by Daniel on 2026-11-23:
- **Push notifications are the main daily channel.** Daniel: "Notification is what I want more, rather than email."
- **The digest email goes only to paid customers, once a week.** Daniel: "email should only be for paid customers."
- **Stay on the Resend free plan.**

Priority: **ship before the Resend monthly allowance resets** (date to be confirmed from Resend → Usage).
Until this ships, the current digest tries about 190 emails every morning. It hits the free plan's cap and stops,
which is now harmless thanks to #396. But after the reset it would use up the month's 3,000 in about 30 days and
starve the verification and password-reset emails.

## Why
- **Resend free plan limits: 100 emails a day and 3,000 a month.** The account is shared with Moyi and Lovegive
  (they are moving to their own accounts).
- **The daily digest needs about 190 a day, about 5,700 a month.** That overran the quota on 21 Nov
  (see the `resend-quota-incident` notes and #396).
- **Push already reaches most of our users** (measured 2026-11-23):
  - 109 of 157 owner accounts have a push token. 104 Android and 43 iOS tokens in total, staff included.
  - 18 of the 20 tailors active in the last 30 days have a push token.

## What to build (functions, `dailyDigest` only)

### 1. Who gets the digest email
Only **paid** owners. A paid owner is one where:
- `subscriptionSource` is `paystack` or `apple`;
- `subscriptionStatus == 'active'`;
- `subscriptionEndsAt` is in the future.

Launch grants (`grantSource: launch_free`, no end date) are **not** paid. Today that means almost nobody gets the
email, which is the intent.

Load these fields in `listRecipients` (`functions/src/notifications/dailyDigest.ts`), which already reads every
user doc, and add `paid: boolean` to `DigestRecipient`.

Everyone else **still** gets everything else from the run, exactly as today:
- the in-app inbox write;
- the owner push;
- staff pushes.

### 2. Weekly, spread across the week
- Each paid owner gets the email on **one fixed weekday**: `hash(uid) % 7` against the Lagos day of week. That keeps
  each day's sends at about 1/7 of payers instead of a Monday spike, which matters while the daily cap is 100.
- Content: the existing digest model on that day (due this week, overdue, money to collect). Change the subject and
  heading to weekly wording in **English and French**. The French copy needs the native-speaker review.
- **No empty weekly email.** If the model is empty, send nothing (drop the `quiet` nudge for the weekly email).
- Guard: keep the existing `lastSentDate` stamp, and skip if the last send was fewer than 6 days ago. This covers
  re-runs and a manual trigger.

### 3. Stop the nudge emails for everyone else
The `setup`, `first_order` and `quiet` nudge emails (`nudgeEmailTemplate.ts`) stop for non-paid owners. Engagement
push (`runEngagementPush`) already covers those nudges by push. Do **not** delete the templates; just stop calling
them for non-paid owners.

### 4. Kill switch in config, no deploy needed
Add `config/app.digestEmailAudience`, with the values `"paid"` (the default when missing), `"none"` and `"all"`.
- `"none"` must stop **all** digest email immediately, as the emergency brake.
- `"all"` restores today's behaviour (weekly cadence still applies).

Read it once per run.

### 5. Unchanged (must keep working for everyone)
- Verification email (`auth/sendVerificationEmail.ts`) and password reset (`auth/processPasswordResetEmail.ts`).
- Billing emails (`billing/paystackBilling.ts`, `billing/subscriptionReminder.ts`, `billing/giftBilling.ts`).
- The internal metrics email to Daniel and the WhatsApp escalation email.
- The #396 behaviour:
  - a 422 still marks a hard bounce;
  - 401, 403 and 429 stop email for the rest of the run, with no suppression.
- The one-off **free-period notice campaign** (30 Nov, plus reminders on 16 and 23 Dec, about 20 people) is sent by
  Daniel's script, not by this function. It is an agreed exception to the paid-only rule.

## App side (small; can ship with the paywall build)
- **Settings email toggle** (`SettingsScreen.kt:360` and `:651`, `dailyDigestEmailEnabled`):
  - for paid owners, relabel it "Weekly email summary" (EN + FR);
  - for non-paid owners, hide it, or show it disabled with "Included with Pro and Atelier". **Daniel to choose.**
  - Keep the field name, because the server reads it.
- **The push toggle stays as it is**; it's now the main channel.
- **Follow-up, not in this PR:** the 48 owners without a push token. This needs an in-app prompt to turn on
  notifications, which fits the parked habit-reminders idea (`docs/handoff/parked/habit-reminders.md`).

## Tests (`functions/src/__tests__/notifications/runDailyDigest.test.ts`)
- A **non-paid** owner gets the inbox and push, and **no** email, including no nudge email.
- A **paid** owner gets the email only on their weekday, and not again within 6 days.
- A paid owner with an empty digest gets no email.
- A launch-grant Atelier user (no end date) counts as non-paid.
- An expired Paystack user (`subscriptionEndsAt` in the past) counts as non-paid.
- `digestEmailAudience: "none"` sends no email; `"all"` emails non-paid owners weekly.
- The existing #396 quota and bounce tests still pass.

## Deploy
Daniel deploys `dailyDigest` only. **Before deploying, run `git pull --ff-only` on local `main`.** On 22 Nov a deploy
went out from a stale local `main` and shipped the old code.
