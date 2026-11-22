# 30 Nov 2026 notice: launch kit

Prepared by Claude on 2026-11-22. This is everything needed for the T-30 notice on **Mon 30 Nov 2026**,
done **without** waiting for the read-only build. Dates: free access ends 30 Dec 2026, grace runs until 6 Jan 2027.

## What was verified (read-only checks, 2026-11-22)
- `config/app`: `billingEnabled=false`, `launchFreeGrantEnabled=false`.
- `PAYSTACK_SECRET_KEY` is a **live** key (`sk_live_…`, version 2, set 2026-07-20). Turning `billingEnabled` on makes Android
  checkout live with no app release. iOS ignores the flag: Apple in-app purchase is always on.
- A launch user (Atelier, `launch_free`, no end date) **can** pay. `computeSubscriptionGrant` case 3 gives a fresh
  period from the payment date (`functions/src/billing/subscriptionPeriod.ts`). Side effect: paying early swaps
  the free period for a paid month that starts that day. The email therefore says there's no need to pay before 30 Dec.
- **Gap found: Atelier users can't reach the Upgrade screen from Settings.** `PlanCardPaid` is only clickable for Pro
  (`PlanCard.kt:413`, `canUpgrade = tier == PRO`). All 20 recipients are on Atelier. Workaround: the email button uses
  the existing deep link `https://link.getstitchpad.com/upgrade?tier=pro&cadence=monthly`
  (parsed by `DeepLinkParser.parseUpgrade`, already used by the renewal-reminder email), which opens the Upgrade
  screen on Android and iOS.

## Daniel's checklist
| When | Step |
|---|---|
| By 27 Nov | **Real purchase test, Android:** on a spare launch-grant account, set `billingEnabled=true`, open `https://link.getstitchpad.com/upgrade?tier=pro&cadence=monthly` on the phone, pay Pro monthly (₦2,000). Check: the app shows Pro, `users/{uid}` has `subscriptionSource: paystack` with an end date one month out, and a `billingTransactions` row shows `paid`. Refund it from the Paystack dashboard afterwards if you like. Then set `billingEnabled` back to `false` until 30 Nov, or leave it on (it's harmless: the button simply works). |
| By 27 Nov | **Purchase test, iOS:** same link on an iPhone. The Upgrade screen should show Apple prices. Buy in the sandbox (TestFlight) or for real. Check that the user doc flips to the paid tier. |
| 29 Nov | Rebuild the audience (command below) and read the count. |
| 29 Nov | Send yourself a test: `test <csv> <your email>`. Open it **on your phone** and tap the button: it must open StitchPad's Upgrade screen. |
| 30 Nov morning | Set `config/app.billingEnabled = true` (if it isn't already). |
| 30 Nov, ~09:00 Lagos | `send <csv> --stage notice` and type `SEND`. Then send the WhatsApp message below to the same 20. |
| 16 Dec | `send <csv> --stage t14` (rebuild the list first and drop anyone who has paid). |
| 23 Dec | `send <csv> --stage t7`. |

Commands (sender and list live outside the repo, in `~/StitchPad Analytics Data/campaigns/`):
```
cd ~/"StitchPad Analytics Data/campaigns"
python3 send_free_period_email.py preview ../local/free_period_audience_YYYY-MM-DD.csv     # no network
python3 send_free_period_email.py test    ../local/free_period_audience_YYYY-MM-DD.csv you@example.com
python3 send_free_period_email.py send    ../local/free_period_audience_YYYY-MM-DD.csv --stage notice
```
The sender reads the Resend key from Secret Manager at run time and keeps a ledger, so a re-run never double-sends.
For the audience rebuild, ask Claude ("rebuild the free-period audience"). The scripts are in this session's notes.

## WhatsApp message (from Daniel, 30 Nov)
> Good day {name}, this is Daniel from StitchPad. Thank you for using StitchPad since we launched. 🙏
> From **30 December**, StitchPad becomes a paid app: Pro ₦2,000 a month or Atelier ₦4,000 a month.
> Your customers, measurements and orders will **not** be deleted. You'll have until 6 January to choose a plan; after that, without a plan you can still view everything, record payments and update jobs already in progress. You just can't add new work.
> Please update the app, then tap here to see the plans: https://link.getstitchpad.com/upgrade?tier=pro&cadence=monthly
> Any question, just reply. I read every message.

## For Codex (add to the paywall work)
1. **Make the Atelier PlanCard open Upgrade** (`PlanCard.kt:413`). A launch Atelier user must be able to reach plans
   from Settings. Needed before 30 Dec (part of gap 8).
2. **Payment during the free period:** decide whether a Paystack purchase before T-0 should start at T-0 instead of the payment date.
   For now the email tells people not to pay early. If changed, it goes in `computeSubscriptionGrant` with a test.
3. **Cutoff runner must skip real payers:** a paid user keeps `grantSource: launch_free` (the webhook merge doesn't clear it).
   Gap 4's runner and the expiry cron must treat `subscriptionSource in (paystack, apple)` with a future
   `subscriptionEndsAt` as paid and never overwrite it.
4. The expiry cron (01:00) sends lapsed payers to FREE, not read-only (gap 5). Fine until 6 Jan, but it must be fixed in the read-only build.
