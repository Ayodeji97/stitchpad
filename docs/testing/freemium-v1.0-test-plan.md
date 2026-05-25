# Freemium V1.0 — Manual Test Plan

A scenario-based test plan for the V1.0 freemium model, designed to use the in-app **Debug menu** (Settings → Debug) so a single tester can land every state without manually creating dozens of customers or burning real Vertex AI calls.

Each section is one feature. Each row is: **Setup** → **Action** → **Expected result** → **Pass?**

> **Before you start**
> - Run on an Android emulator OR an iPhone simulator. iOS sim is preferred since it exercises the GitLive Firebase Kotlin SDK path that's bitten us most.
> - Sign in as one of the test accounts (Fola / Gabby) before running any scenario.
> - The freemium server function (`smartDraftMessage`) must be deployed with the post-V1.0-review fix — see [Prerequisites](#prerequisites).

---

## Quick reference — every debug-menu action

| Section | Action | What it does | Use it to test |
|---|---|---|---|
| **Seed data** | Brand-new tailor | Wipes all customer/order data | Reset to empty state |
| | Active workshop | 8 customers, 4 measurements, 4 orders (deterministic IDs) | Realistic populated dashboard |
| | All-reconnect | 6 customers all >100 days inactive | Reconnect NBA card |
| | Clear active state | Clears the "Active scenario" pill | Cosmetic only |
| **Bulk seed** | Seed N demo customers… | Adds N "Demo Customer #" rows (additive — does **not** wipe), with optional measurements + orders | Welcome 30-cap, post-welcome 15-cap, swap, slot lock |
| **Session** | Reset onboarding flags | Re-shows onboarding + workshop setup on next launch | Onboarding QA |
| | Sign out | Signs out + returns to login | Account switch, cache reset |
| **Switch account** | Switch to Fola / Gabby | Hot-swaps to the other test account | Cross-account regression |
| **Freemium · tier** | Set tier: Free / Pro / Atelier | Writes `subscriptionTier` on user doc | Tier-derived caps + Smart quotas |
| **Freemium · welcome window** | Expire welcome window | Backdates `welcomeBonusAppliedAt` by 35 days → Free 15-cap takes effect | Post-welcome state |
| | Reset welcome window (now) | Sets `welcomeBonusAppliedAt` to now + refills bonus coins to 30 | Fresh welcome state |
| **Freemium · Smart coins** | Drain bonus coins (→ 0) | Sets `bonusCoins` on user doc to 0 (user-doc field only) | Bonus drained, on-doc reads |
| | Refill bonus coins (→ 30) | Sets `bonusCoins` on user doc back to 30 | Re-arm welcome bonus path |
| | Reset Smart usage doc | Deletes `users/{uid}/usage/smart_drafts` entirely | Brand-new Smart user simulation |
| | **Set Smart usage…** | Writes `count` + `bonusBalance` directly to the usage doc | Land at any Smart state without burning real AI calls |
| **Freemium · slots** | Reconcile customer slots | Calls the `reconcileSlots` cloud function | Trigger slot relock after cap shrinks |
| **Danger zone** | Wipe my data | Wipes ALL customers + orders + measurements + styles for this user | Hard reset between scenarios |

---

## Prerequisites

Before running any Smart-draft scenario:

```bash
cd functions && firebase deploy --only functions:smartDraftMessage
```

This is required because the deployed `smartDraftMessage` was missing the `limit` fallback fix from commit `b6bec0b`. Without redeploying, **every** Smart draft attempt fails with `Cannot use "undefined" as a Firestore value (found in field "limit")`. Confirm the deploy succeeded by looking at the function version in the Firebase Console.

---

## Scenario 1 — Sandbox reset (run before every other scenario)

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 1.1 | — | Debug → Wipe my data | All customers/orders gone from Customers + Dashboard | ☐ |
| 1.2 | — | Debug → Reset Smart usage doc | `users/{uid}/usage/smart_drafts` deleted in Firestore | ☐ |
| 1.3 | — | Debug → Set tier: Free | Settings → PlanCard shows "Free" | ☐ |
| 1.4 | — | Debug → Reset welcome window (now) | User doc: `welcomeBonusAppliedAt` ≈ now, `bonusCoins` = 30 | ☐ |

**You are now at the "brand-new signed-up tailor" baseline.**

---

## Scenario 2 — First Month + 200-customer cap

Tests that during the First Month, Free users have effectively unlimited customers (the 200 ceiling exists for system safety but is never exposed in the UI per the V1.0 design spec).

The PlanCard during First Month uses a **different layout** from post-First-Month:

| What you see during First Month | Why |
|---|---|
| `[FIRST MONTH · N days left]` pill | Period badge — counts down to month-end transition |
| `✨ AI drafts ▓▓░░░░ X of 30 used` | AI is the primary conversion signal during First Month |
| `N customers added · no limits this month` (no fraction) | Customer count is shown but **never as a fraction** — 200 is hidden |
| `How First Month works →` affordance | Links to founder note / Settings explainer |

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 2.1 | Sandbox reset done | Open Settings → PlanCard | **First Month layout.** `[FIRST MONTH · ~30 days left]` pill, AI progress at `0 of 30 used`, customer line reads `0 customers added · no limits this month`. **No "200" anywhere on the card.** | ☐ |
| 2.2 | — | Debug → Bulk seed → total=50, measurements=10, orders=10 → Seed | All 50 succeed cleanly; PlanCard still in **First Month inline** layout. Customer line now reads `50 customers added · no limits this month` | ☐ |
| 2.3 | — | Add 1 more customer manually (FAB) | Customer count = 51. PlanCard still inline; First Month chip still visible | ☐ |
| 2.4 | — | Debug → Bulk seed → total=100, measurements=0, orders=0 → Seed | All 100 succeed; count = 151. PlanCard still inline (well below 200) | ☐ |
| 2.5 | — | Debug → Bulk seed → total=49 → Seed | Lands at 200 customers. The 200th customer should hit the **white-glove escalation screen** (PR 6) — "You're moving fast! Reach out to support…" with WhatsApp CTA. | ☐ |
| 2.6 | — | Verify Firestore: `users/{uid}.customerCount == 200` | Server-side counter agrees | ☐ |
| 2.7 | — | Tap WhatsApp CTA on the escalation screen | WhatsApp opens with support number + intro message | ☐ |

> **Why this isn't a typical "fill the cap" test.** During First Month the cap is essentially invisible to users. Hitting 200 is a rare power-user case routed to a dedicated white-glove screen (PR 6), not a generic "you've reached your limit" state. The standard inline/warn/locked PlanCard states only fire **post-First-Month** when the cap drops to 15 — see Scenario 4 for that test.
>
> **Why bulk-seed 50, then 100, then 49?** Demonstrates the cap is unlimited from the user's perspective. The 200 is a system safety ceiling reached only by a power user uploading their entire customer book.
>
> **Skip 2.5–2.7 if PR 6 hasn't landed yet.** Without the white-glove screen, hitting 200 will show a generic CAP_REACHED error from the cap-reached bottom sheet.

---

## Scenario 3 — Welcome window ending warning (3-day banner)

Tests the "Your welcome ends in N days" dashboard banner.

> The welcome window is a **rolling 30 days** from signup — see `EntitlementsCalculator.WELCOME_WINDOW_DAYS`. The 3-day warning fires when `welcomeDaysLeft <= 3`. Use the new debug helper to land at any day-left state instantly, no clock mocking needed.

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 3.1 | Sandbox reset done | Debug → Freemium · welcome window → **Set welcome days left… → 2 → Apply** | Snackbar: "Welcome window: 2 days left". App state propagates within a beat | ☐ |
| 3.2 | — | Open Dashboard | "Your first month ends in 2 days" banner appears with Upgrade CTA | ☐ |
| 3.3 | — | Tap CTA | UpgradeScreen opens | ☐ |
| 3.4 | — | Debug → Set welcome days left → **1** | Banner copy uses singular form: "Your first month ends tomorrow" (validates plural string from PR 1) | ☐ |
| 3.5 | — | Debug → Set welcome days left → **0** | Banner disappears; PlanCard transitions to post-First-Month state (15-cap visible). Foreground triggers `reconcileSlots` per PR 5 | ☐ |
| 3.6 | — | Debug → Set welcome days left → **30** | Fresh full-window state: PlanCard pill "First month · 30 days left", no warning banner | ☐ |

If you can't run this in the calendar window, document as **skipped** and re-test at month-end.

---

## Scenario 4 — Welcome expired → Free 15-cap + slot reconcile

Tests what happens after the welcome window ends: cap drops to 15, oldest-inactive customers get locked.

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 4.1 | 30 customers from Scenario 2, mixed activity | Debug → Bulk seed → total=30, orders=15 (so half have a recent order, half don't) | Seed succeeds; 30 customers total | ☐ |
| 4.2 | — | Debug → Expire welcome window | User doc: `welcomeBonusAppliedAt` backdated ~35 days | ☐ |
| 4.3 | — | Debug → Reconcile customer slots | Server runs reconcile; snackbar "Slots reconciled" | ☐ |
| 4.4 | — | Open Customers tab | ~15 customers grayed out / marked "locked" (the inactive half) | ☐ |
| 4.5 | — | Open Settings → PlanCard | Counter reads `15 / 15` (active), Upgrade CTA visible | ☐ |
| 4.6 | — | Tap a locked customer | SwapSheet opens, lists active customers | ☐ |
| 4.7 | — | Pick an active customer from the sheet | Swap completes; snackbar "Swapped in"; the chosen customer is now locked instead | ☐ |
| 4.8 | — | Try to add a new customer | Upgrade prompt fires (cap = 15) | ☐ |

> **Note on the 50/50 split.** The "active" determination is server-side via `reconcileSlots` and depends on each customer's last-order timestamp. Bulk-seed gives all customers the **same** createdAt, so the split depends on which 15 had orders (`orders=15` in 4.1). If your reconcile result looks all-or-nothing, check the cloud function logic — it's server logic and not in scope here.

---

## Scenario 5 — Smart drafts: welcome bonus consumed before free quota

Tests that bonus coins are consumed silently before the visible 5/month free quota.

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 5.1 | Sandbox reset + at least 1 customer with 1 open order | Debug → Set Smart usage → count=0, bonus=30 → Apply | Firestore `users/{uid}/usage/smart_drafts` has `count=0, bonusBalance=30` | ☐ |
| 5.2 | — | Smart Suggestions → Draft a Message → pick customer + order + intent → Generate | Draft text appears | ☐ |
| 5.3 | — | Check Firestore | `bonusBalance` decremented to 29; `count` still 0 | ☐ |
| 5.4 | — | Dashboard chip | Still shows "5 of 5 free drafts left this month" (chip reflects monthly count, not bonus) | ☐ |
| 5.5 | — | Generate 4 more drafts | Bonus drains 29 → 28 → 27 → 26 → 25; count stays 0; chip stays 5/5 | ☐ |

---

## Scenario 6 — Smart drafts: free quota exhaustion + upgrade sheet

Tests the transition from bonus → free quota → exhausted.

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 6.1 | Customer + open order ready | Debug → Set Smart usage → count=0, bonus=0 → Apply | Firestore: count=0, bonusBalance=0 | ☐ |
| 6.2 | — | Draft a Message → Generate | Draft appears; Firestore count → 1 | ☐ |
| 6.3 | — | (Optional, just to refresh the chip) Sign out + sign back in, navigate to Dashboard | Chip reads "4 of 5 free drafts left" | ☐ |
| 6.4 | — | Debug → Set Smart usage → count=4, bonus=0 → Apply | Firestore: count=4 | ☐ |
| 6.5 | — | Draft a Message → Generate | Draft appears; count → 5; chip (if refreshed) reads "0 of 5" | ☐ |
| 6.6 | — | Debug → Set Smart usage → count=5, bonus=0 → Apply | Firestore: count=5 | ☐ |
| 6.7 | — | Draft a Message → Generate | **Modal upgrade sheet** opens ("Out of free drafts this month") — NOT inline text | ☐ |
| 6.8 | — | Tap "Upgrade to Pro" on the sheet | UpgradeScreen opens | ☐ |

> **Inline banner vs modal upgrade sheet.** The red inline text above the Generate button (`Out of free drafts this month. Upgrade to keep drafting.`) appears when the **in-memory cache** says `remainingFreeQuota == 0`. The modal bottom sheet only fires when you tap Generate AND the server returns exhausted. They are different surfaces and both can appear in the same session. Trust the modal sheet — it's authoritative.

---

## Scenario 7 — Pro tier (50/month Smart drafts + unlimited customers)

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 7.1 | Sandbox reset | Debug → Set tier: Pro | Settings → PlanCard shows "Pro", customer limit shows "unlimited" | ☐ |
| 7.2 | — | Debug → Bulk seed → total=50, orders=0 | All 50 succeed (no cap on Pro) | ☐ |
| 7.3 | — | Debug → Set Smart usage → count=49, bonus=0 → Apply | Firestore: count=49 | ☐ |
| 7.4 | — | Generate a draft | Succeeds; count → 50 | ☐ |
| 7.5 | — | Debug → Set Smart usage → count=50, bonus=0 → Apply | Firestore: count=50 | ☐ |
| 7.6 | — | Generate a draft | **Snackbar** "You've used your 50 Pro drafts this month" — NOT upgrade sheet (Pro doesn't get a Pro→Pro upgrade prompt) | ☐ |

> Pro hits **`pro_quota_exhausted`** server-side, which the client routes to a snackbar instead of the upgrade sheet. This is the tier-aware copy that ships with V1.0.

---

## Scenario 8 — Atelier tier (unlimited everything)

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 8.1 | Sandbox reset | Debug → Set tier: Atelier | Settings → PlanCard shows "Atelier"; unlimited customers | ☐ |
| 8.2 | — | Debug → Bulk seed → total=100, orders=0 | All 100 succeed (Pro/Atelier have no cap) | ☐ |
| 8.3 | — | Generate Smart drafts repeatedly (e.g. 10 of them, or set count=1000) | All succeed; no quota exhaustion at any number | ☐ |
| 8.4 | — | Check Firestore | `usage/smart_drafts` may or may not exist (Atelier bypasses the reservation transaction) | ☐ |

---

## Scenario 9 — Upgrade flow

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 9.1 | Free + cap-hit state (Scenario 2 step 2.6) | Tap upgrade CTA in PlanCard | UpgradeScreen opens with Pro + Atelier options | ☐ |
| 9.2 | — | Pick Pro → Continue | Paystack web view opens (test mode) | ☐ |
| 9.3 | — | Complete Paystack test transaction | Returns to app | ☐ |
| 9.4 | — | Wait for entitlements to refresh OR | Tier upgrades to Pro automatically (V1.0 manual server flip if Paystack webhook not wired) | ☐ |
| 9.5 | — | Verify Settings hero badge | Shows "Pro" badge on profile hero | ☐ |

---

## Scenario 10 — Account switch regression

Quick check that signing out + back in doesn't leak state.

| # | Setup | Action | Expected | Pass? |
|---|---|---|---|---|
| 10.1 | Active session as Fola with non-zero data | Debug → Switch to Gabby | Login + onboarding skip; Gabby's customers shown | ☐ |
| 10.2 | — | Open Smart → Draft a Message | Dashboard chip is **hidden** (Gabby's in-memory cache is null) | ☐ |
| 10.3 | — | Debug → Switch to Fola | Back to Fola; her customer count + chip restore | ☐ |

---

## Known V1.0 quirks (NOT bugs)

These are expected behaviors that may look confusing during testing:

- **Smart chip lag.** The dashboard "X of 5 free drafts" chip reads from a **process-local in-memory cache** that only updates after a real successful draft. If you use `Set Smart usage` debug action to change the Firestore doc, the chip won't reflect it until you (a) sign out + back in, or (b) generate one successful draft. **The server is still authoritative** — the upgrade sheet fires correctly even when the chip is stale. V1.1 will add Firestore-backed chip hydration.
- **Rolling 30-day welcome window.** First Month is a rolling 30 days from `welcomeBonusAppliedAt`, NOT calendar-month-aligned. The Debug → "Set welcome days left…" action backdates `welcomeBonusAppliedAt` so the chip shows exactly N days remaining without waiting in real time.
- **`Drain bonus coins`** writes to the user-doc `bonusCoins` field (the seed), NOT to the `bonusBalance` on the usage doc (the lifted balance). Use **Set Smart usage** to control the lifted balance the server actually checks during Smart calls.
- **Bulk-seed is additive.** It doesn't wipe — pair with `Wipe my data` first if you need a clean slate. Subject to current tier cap (won't seed past 30 on Free welcome).
- **Welcome 30-cap collisions.** If you've already added customers and then `Reset welcome window`, the cap remains at 30. To re-test the cap-hit copy, use `Wipe my data` first.

---

## When tests fail — diagnostics

| Symptom | First place to look |
|---|---|
| `Something went wrong. Please try again` on draft | Firebase Functions logs (`smartDraftMessage`) — usually a Vertex AI error or a Firestore field validation error |
| Customer cap not enforced | Check `users/{uid}.subscriptionTier` in Firestore — must be lowercase `"free"`, not `"FREE"` |
| Locked customers don't appear after expire | Confirm `reconcileSlots` ran (debug action snackbar) — check Functions logs |
| Upgrade sheet doesn't fire | Check Functions logs for `permission-denied` + the marker (`free_tier_exhausted` or `pro_quota_exhausted`) — must match client's `SmartFunctionsRepository.recoverFromMessage` markers |
| Smart chip wrong number | Confirmed quirk above. Source of truth is Firestore `usage/smart_drafts.count` |

---

## Going from this doc to a Google Sheet

To convert to a Google Sheet:
1. Copy each table block.
2. Paste into a Google Sheet — the columns will auto-split.
3. Add a "Tester", "Date", "Notes" column on the right.
4. Use checkbox cells for the Pass column (`Insert → Checkbox`).

To track across multiple test runs, duplicate the sheet per release candidate and date it.
