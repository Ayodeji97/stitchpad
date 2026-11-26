# Google Play Billing: setup, deploy order and smoke

Plan: `docs/superpowers/plans/2026-11-24-play-billing.md` (spec §6b). Branch `feat/play-billing`.
Android outside Nigeria buys through Google Play; Nigeria Android stays on Paystack; iOS stays on Apple.

## 1. Console setup (Daniel only; agents do not do these)

| # | Step | Done |
|---|------|------|
| 1 | Play Console → Monetize → Subscriptions: create `com.danzucker.stitchpad.pro.monthly`, `.pro.annual`, `.atelier.monthly`, `.atelier.annual`. One auto-renewing base plan each (`monthly` or `annual`), USD parity (~$1.30 Pro / ~$2.60 Atelier monthly), available in the wave-1 countries only. Leave Nigeria out. | |
| 2 | Play Console → Setup → API access: link GCP project `stitchpad-30607`; give the functions runtime service account "View financial data" and "Manage orders and subscriptions". | |
| 3 | GCP: create Pub/Sub topic `play-billing-rtdn`; grant `google-play-developer-notifications@system.gserviceaccount.com` the Publisher role on it. Play Console → Monetization setup: set `projects/stitchpad-30607/topics/play-billing-rtdn`, then send the test notification (the function logs "test notification received"). | |
| 4 | Play Console → License testing: add the test Google accounts. | |

## 2. Deploy order

Do NOT run `npm run deploy` for this: its `--only` list includes `initializeSubscriptionCheckout`,
which now carries the Paystack NG-only country guard (step 4 below). Deploy with an explicit list.

| # | Step | Done |
|---|------|------|
| 1 | `firebase deploy --only functions:verifyPlayPurchase,functions:playRealtimeNotifications,functions:reconcilePlaySubscriptions,firestore:rules`. Not `initializeSubscriptionCheckout` yet. Add the three Play functions to the `deploy` script allowlist in the same PR that ships step 4. | |
| 2 | App build with Tasks 5-7 to the internal track. `config/app.playBillingEnabled` stays `false` in production (Play CTA reads "Coming soon"). | |
| 3 | Smoke on the internal track (section 3). Then set `config/app.playBillingEnabled = true`. | |
| 4 | Only after that build is live in production: deploy `initializeSubscriptionCheckout` with the country guard. Earlier, non-NG users on old builds would lose their only checkout. | |

## 3. Smoke

License-test account, device or emulator with the Play Store, workshop country GH, signed in as an owner.
Test purchases are refused unless `PLAY_ALLOW_TEST_PURCHASES=true` is set on the functions (see "Open decisions").

| # | Check | Expected | Result |
|---|-------|----------|--------|
| 1 | Open Upgrade | Play prices in local currency on the tier cards, "Subscribe" CTA, Google Play terms line, "Restore purchases", no Naira note | |
| 2 | Open Upgrade right after sign-in on a slow network | "Coming soon" / no checkout until the country loads; never a Paystack browser redirect | |
| 3 | Buy Pro monthly | Screen pops, tier Pro. `users/{uid}`: `subscriptionSource:'play'`, `playPurchaseToken`, `playPurchaseTokenHash`, `playProductId`. `playSubscriptions/{hash}` → `{uid}`. Play Console order shows acknowledged | |
| 4 | Buy Atelier while Pro is active | One subscription in Play (replacement, not two), tier Atelier; the old Pro token's later expiry does not drop the tier | |
| 5 | Cancel in the Play Store | `subscriptionRenews:false`, access kept to the end date; let the test sub expire (test renewals take minutes) → tier free | |
| 6 | Test card "Slow test card, approves after a few minutes" | "Pending" snackbar. Close the app. After approval: tier granted via RTDN even with the app closed; or on next app open (sweep) | |
| 7 | Uninstall, reinstall, sign in, Upgrade → Restore purchases | Tier restored | |
| 8 | Switch workshop country to NG | Paystack path unchanged ("Pay with Paystack", NGN prices, browser checkout) | |
| 9 | `PLAY_ALLOW_TEST_PURCHASES` unset, license-test purchase | Refused (`test_purchase_not_allowed`), error snackbar, no grant | |
| 10 | iOS (any country) | Apple path unchanged | |
| 11 | Account with an active Paystack or launch-grant plan buys on Play | Snackbar "Your Google Play plan is saved. It starts when your current plan ends."; user doc unchanged; `playSubscriptions/{hash}.appliedStatus == 'active'`. After that plan lapses, the next `reconcilePlaySubscriptions` run applies Play | |
| 12 | Two StitchPad accounts on one Google account: account A has a Play sub, sign in as B, open Upgrade and Restore | B neither restores nor replaces A's subscription | |

## 4. After the test window (mandatory)

| # | Step | Done |
|---|------|------|
| 1 | Unset `PLAY_ALLOW_TEST_PURCHASES` on the production functions and redeploy the three Play functions so the change takes effect. | |
| 2 | Run `reconcilePlaySubscriptions` once by hand (Cloud Scheduler → "Force run", or wait for 02:45 Lagos). | |
| 3 | Confirm every license-test grant is healed: each tester's `users/{uid}` reads `subscriptionTier:'free'`, `subscriptionStatus:'expired'`, and their `playSubscriptions/{hash}.appliedStatus` is `'expired'`. Check the function logs for "play reconcile failed" lines. | |

## Open decisions

1. **Test purchases in production.** License testers buy against production Play; with the flag off they are refused.
   Recommendation: set `PLAY_ALLOW_TEST_PURCHASES=true` on production functions for the smoke window only, then unset
   (the daily reconcile then heals any test grant to free).
2. **Nigeria on Play** (spec "Known risk"): a one-line change in `Country.NG.androidBilling` plus the 15% fee decision. Not in this branch.
