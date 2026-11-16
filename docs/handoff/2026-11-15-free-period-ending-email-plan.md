# Plan — "Your free period is ending" email to active users

Decided by Daniel 2026-11-15:
- **Message:** free period ending notice. Unpaid accounts become read-only on a set date; here is how to subscribe.
- **Audience:** tailors who created or updated an order or customer in the **last 30 days**.
- `config/app.launchFreeGrantEnabled` turned off by Daniel (new signups no longer get the endless grant).
  Existing grants are untouched by that switch.

## Blockers before anything is sent
1. **T-0 date and prices.** The email must name the date and the price. These come from the paywall decisions
   (artifact D5NQ3bduBVzaxjobp7HGor). The founder offer, if any, goes in the email.
2. **Read-only enforcement must exist by T-0.** Q5 (approved 2026-11-04) says 30 days' notice **after both stores
   approve the updated app**, then unpaid accounts become read-only. Sending the notice before the read-only build
   is approved promises a date we may not be able to keep. So the order is: build enforcement, get store approval, send email, wait 30 days, T-0.
3. **The "please update the app" line.** Q5 says to explain that the update is required. Older builds can't show
   the paywall, so the email links to the Play Store and App Store, and `minSupportedBuild` (paywall gap 12) must be set.

## Audience query (one-off script, read-only)
- Firestore: owners (not staff) with any `orders` or `customers` doc whose `updatedAt` or `createdAt` falls within the last 30 days.
- Exclude: accounts already on a paid plan (Paystack/Apple active), deleted users, unverified or bounced emails, and staff-only accounts.
- Output a CSV (uid, email, name, country, language, plan, last activity) to `~/StitchPad Analytics Data/local/` (PII, never commit).
  Daniel reviews the list and the count before sending.

## Sending
- Use the existing **Resend** setup (`functions/src` already sends the digest and verification emails through it).
- Write a one-off sender script (or a debug-gated callable) that reads the reviewed CSV and sends with a rate limit.
  It must be idempotent: record `emailCampaigns/free-period-ending/{uid}` so a re-run never double-sends.
- Language: French for `fr` users, English otherwise (French copy needs the native-speaker review).
- Currency: show each user's own currency price (country → currency table; `PaystackPlanPrices` / Play Billing outside NG).
- First, a dry run that sends to Daniel only.

## Email content (draft to finalise)
- Subject: "Your free StitchPad access ends on {T-0 date}"
- Thank them for being early. Their records are safe and stay viewable (read-only, never deleted).
- What changes on {date}: without a plan you can view but can't add or edit orders, customers or measurements.
- Price in their currency, plus the founder offer if Daniel picks one.
- Two steps: update the app, then subscribe in the app. Include store links.
- Reply-to goes to a monitored inbox for questions.

## Follow-ups
- A reminder 7 days before T-0 and one on T-0 to the same audience, minus anyone who has subscribed.
- In-app banner with the same date (paywall work).
