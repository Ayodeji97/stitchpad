# Freemium Model — Design Spec

**Date:** 2026-05-17
**Status:** Approved (brainstorming complete; ready for implementation plan)
**Memory references:** [[project_overview]], [[project_freemium_plan_card]], [[project_premium_tier_candidates]], [[project_ai_assistant]], [[smart-tier-field-consolidation]], [[project_rebrand_terminology]], [[project_rebrand_styleols]]

## Goal

Lock StitchPad's three-tier freemium business model — **Free**, **Tailor Pro**, **Tailor Atelier** — with full feature gating, AI cost-of-goods modeling, a signup welcome mechanic, and a **Sponsor a Tailor** gift-subscription feature for growth in the Nigerian tailor / fashion-designer market.

## Why

Per [[project_overview]] memory, StitchPad was scoped freemium-funded with early ₦500 / ₦1,000 anchors. Those anchors are stale — Smart Suggestions V1 shipped on a placeholder `subscriptionTier` field with no real monetization wired, and the survey's "81% willing to pay" stat anchors no specific amount. Without a locked freemium design:

- `PlanCard.kt` from PR #35 stays dormant ([[project_freemium_plan_card]])
- Paystack integration can't start (the stack is documented but no code wired)
- Smart Suggestions has no path beyond its 5-drafts/month free counter
- The `tier` vs `subscriptionTier` field divergence ([[smart-tier-field-consolidation]]) can't be cleanly resolved

This spec is the unlock for all of the above.

## Scope

**In:**
- 3-tier structure, names, monthly + annual pricing
- Per-tier feature gating (Smart coins, receipts, customer cap, offline, Smart help intents, reports, perks)
- COGS math against Gemini 3.1 Flash Lite + Paystack at May 2026 rates
- Welcome mechanic for new signups (bonus coins + customer-cap boost)
- Gray-out policy when caps re-tighten (welcome end + post-cancel)
- Annual billing discount
- Cancel behavior
- **Sponsor a Tailor** gift subscription — design + data model
- Partner / referral code data-model placeholder
- Subscription tier field consolidation (close [[smart-tier-field-consolidation]])

**Out (deferred to separate specs):**
- Paystack subscription billing wiring (recurring charges, webhooks, retry on failure)
- Entitlement system implementation (the Koin-injected `Entitlement` check interface)
- Paywall UX implementation (re-landing `PlanCard.kt`, building the Upgrade screen)
- Sponsor a Tailor full implementation (public sponsor page, sponsor checkout flow, recipient notification plumbing)
- Pause / freeze accounts, family-shared accounts, team accounts

## Decisions locked

| Decision | Value |
|---|---|
| Tier count | 3 |
| Tier names | Free / Tailor Pro / Tailor Atelier |
| Monthly pricing (NGN) | ₦0 / ₦2,000 / ₦4,000 |
| Annual pricing (NGN, ~17% off) | ₦0 / ₦20,000 / ₦40,000 (pay 10 months, get 12) |
| Smart coins / month | 5 / 50 / 500 |
| Smart help intents | Drafting only / Drafting only / All three (drafting + pricing + replying) |
| Pidgin / English toggle | All tiers |
| Receipts / month | 5 (watermark) / 50 (no watermark) / Unlimited (logo + brand colors) |
| Customer cap | 15 / Unlimited / Unlimited |
| Custom measurement types | Pro + Atelier |
| Offline | Basic / Full / Full |
| Reports | None / Monthly earnings + top customers / Detailed sales insights + top spenders |
| Priority support | Atelier only |
| Early access to new features | Atelier only |
| Welcome (new signup) | 30 bonus Smart coins + 30-customer cap for first calendar month |
| Gray-out trigger | Welcome window ends, or paid user cancels and reverts to Free, with >15 customers |
| Gray-out selection rule | 50/50 mix of active + inactive customers beyond #15, with 3-day advance warning + permanent swap mechanic |
| Cancel behavior | Keep paid features until end of paid billing period, then drop to Free with the gray-out rules |
| Sponsor a Tailor | One-time Paystack payment by any third party for 1/3/6/12 months of Pro or Atelier on behalf of a tailor |
| Partner / referral codes | Data-model placeholder only; implementation deferred |
| Payment provider | Paystack WebView Checkout |
| Source of truth for tier | `users/{uid}.subscriptionTier` Firestore field (existing) |

## Tier matrix

| Feature | Free | Tailor Pro | Tailor Atelier |
|---|:---:|:---:|:---:|
| Smart coins / month | 5 | 50 | 500 |
| Receipts / month | 5 (watermark) | 50 (no watermark) | Unlimited (logo + brand colors) |
| Customers | 15 cap | Unlimited | Unlimited |
| Custom measurement types | ✗ | ✓ | ✓ |
| Offline | Basic | Full | Full |
| Smart help: drafting messages | ✓ | ✓ | ✓ |
| Smart help: pricing a new order | ✗ | ✗ | ✓ |
| Smart help: replying to customers | ✗ | ✗ | ✓ |
| Pidgin / English toggle | ✓ | ✓ | ✓ |
| Monthly earnings + top customers | ✗ | ✓ | ✓ |
| Detailed sales insights + top spenders | ✗ | ✗ | ✓ |
| Priority support | ✗ | ✗ | ✓ |
| Early access to new features | ✗ | ✗ | ✓ |

### Smart coin definition

One **Smart coin** = one Smart help action (one drafted WhatsApp message, one pricing suggestion, one reply suggestion, etc.).

- Monthly coins reset on the 1st of each calendar month.
- Bonus / sponsored coins are tracked separately and **don't expire on month rollover**; they're consumed first-in-first-out, before the monthly allocation, on every Smart help call.
- Coins are server-enforced via the existing `smartDraftMessage` Cloud Function counter at `users/{uid}/usage/smart_drafts`.

### Customer cap definition

The cap is on **active customer slots**, defined as: visible in the customer list, editable, can be the subject of a Smart help call, can receive a receipt.

Customers beyond the cap stay in the Firestore collection — **no data is ever deleted on a tier change**. Over-cap customers are flagged `slotState = "locked"`, displayed in the list with a lock icon, and tapping shows an upgrade prompt + swap option.

### Offline definition

- **Basic offline (Free):** Firestore default offline persistence + the existing offline banner + pending-write queue. The tailor can add customers, orders, and measurements without internet — they sync when the signal returns. No extended cache, no manual "Sync now," no background sync.
- **Full offline (Pro + Atelier):** the V1.5 offline-first surface from [[project_premium_tier_candidates]] — extended cache window, "Sync now" pull-to-refresh, "Last synced" timestamp in Settings, background sync via WorkManager / BGTaskScheduler. The whole app usable without internet, automatic sync on signal return.

## COGS & profitability

### Cost anchors (May 17, 2026)

| Input | Value | Source |
|---|---|---|
| Gemini 3.1 Flash Lite input | $0.25 / 1M tokens | Google AI pricing |
| Gemini 3.1 Flash Lite output | $1.50 / 1M tokens | Google AI pricing |
| USD/NGN | ≈ ₦1,370 / $1 | Mid-market, May 2026 |
| Paystack local card | 1.5% + ₦100 (₦100 waived ≤ ₦2,500), capped at ₦2,000 | Paystack support |

### Per-coin COGS

For today's Draft Message intent: ~1,000 input + ~300 output tokens per call.

- Raw inference: $0.00025 + $0.00045 = $0.00070 ≈ ₦0.96 per coin
- Vertex AI overhead + safety buffer for longer prompts (Price-This, Reply Helper may use more context): conservative **₦2 per coin**

### Per-subscriber margin (realistic 30% utilization)

| | Free | Tailor Pro (₦2,000/mo) | Tailor Atelier (₦4,000/mo) |
|---|---|---|---|
| Revenue | ₦0 | ₦2,000 | ₦4,000 |
| Paystack fee | — | ₦30 (sub-₦2,500 threshold) | ₦160 |
| AI COGS (realistic 30% util) | ₦10 | ₦30 | ₦200 |
| **Net per subscriber / month** | **−₦10** | **₦1,940 (97%)** | **₦3,640 (91%)** |

Free is intentionally loss-leading; ₦10/mo even at full utilization is negligible at any realistic scale.

### Worst-case stress test (power user, full coins, 3× average prompt length)

| Tier | Coins | Worst-case AI COGS | Worst-case margin |
|---|---|---|---|
| Tailor Pro | 50 | ₦300 | ₦1,670 (84%) |
| Tailor Atelier | 500 | ₦3,000 | ₦840 (21%) |

Atelier worst-case (21%) is the tightest scenario. Mitigations:

- Monthly coin reset prevents compounding across months
- Server-side prompt-length caps already enforced (custom notes ≤ 200 chars)
- Coin allocation is server-side config — can be tuned post-launch if real usage approaches worst-case

## Welcome mechanic (new signups)

When a new tailor creates their account:

| | Welcome window (first calendar month) | After welcome |
|---|---|---|
| Smart coins | 30 bonus + 5 monthly = **35 total** | 5 / month |
| Customer cap | **30** | 15 (extras grayed-out per gray-out policy) |
| Receipts | 5 / month (watermark) | 5 / month (watermark) |
| Everything else | Free tier defaults | Free tier defaults |

The welcome window ends at the end of the **calendar month** they signed up in (not 30 days from signup), so it aligns with the natural monthly billing cycle and the Smart coin reset boundary.

The 30 bonus coins are seeded as `bonusCoins = 30` on the user doc at signup. They're consumed before the monthly allocation on each Smart help call and persist across month boundaries until depleted.

## Gray-out policy

Triggered when:

1. The welcome window ends and the tailor has > 15 customers
2. A paying tailor cancels and reverts to Free with > 15 customers
3. (Future) any case where the customer cap re-tightens for that user

### Selection rule

Of the customers beyond #15, gray out a **50/50 mix** of active + inactive customers (rounded to nearest whole number):

- **Active** = had any order, message, or update in the last 30 days
- **Inactive** = no activity in the last 30 days
- If the tailor only has actives (or only inactives), gray the N with the oldest last-touch within that bucket

The 50/50 ratio is **server-side config** so we can tune it post-launch based on conversion data without a client release.

### Humanization levers

1. **3-day advance warning** in the dashboard: *"In 3 days your welcome ends. 8 of your customers will be locked unless you upgrade — preview them here."*
2. **Permanent swap mechanic**: any time on Free, the tailor can promote a grayed customer back into her 15 active slots, demoting another. No data loss, always reversible.
3. **No deletion ever**: locked customers stay in Firestore; tier changes only flip `slotState`.

### Grayed-customer UX

Locked customers remain visible in the customer list with a lock icon and faded appearance. Tapping shows: *"This customer is locked because you're over your Free plan's 15-customer limit. Upgrade to Tailor Pro or swap with an active customer."*

## Annual billing

| Tier | Monthly price | Annual price | Effective discount |
|---|---|---|---|
| Tailor Pro | ₦2,000 | **₦20,000 / year** (pay 10 months, get 12) | ~17% |
| Tailor Atelier | ₦4,000 | **₦40,000 / year** (pay 10 months, get 12) | ~17% |

Annual billing is presented alongside monthly on the upgrade screen with the savings highlighted (*"Save ₦4,000 / year"*).

**Annual cancellation:** tailor keeps the tier until the annual period ends, then drops to Free. No proration, no refund. Same rule as monthly cancellation.

## Cancel behavior

When a paying tailor cancels:

1. They keep their paid tier features until the end of the current billing period (monthly or annual).
2. `subscriptionRenews = false` is set immediately; `subscriptionTier` doesn't change until the period ends.
3. At end of paid period, `subscriptionTier` drops to `"free"`.
4. If they had > 15 customers, the gray-out policy applies (50/50 mix + 3-day warning + swap mechanic).
5. Bonus / sponsored coins are preserved — they don't expire on cancel.
6. The tailor can resubscribe at any time; new billing starts fresh on the resubscribe date.

## Sponsor a Tailor

A gift-subscription feature: any third party (family member, customer, fabric supplier, training program, diaspora relative, etc.) can sponsor a tailor's Tailor Pro or Tailor Atelier subscription for a chosen duration. No StitchPad account needed for the sponsor.

### Why this is a sales driver in the Nigerian context

- **Diaspora gifting** — Nigerians abroad regularly send money home; a "sponsor my sister's tailoring business" gift is more tangible than cash
- **Customer-to-tailor loyalty** — satisfied customers who can't tip in cash can sponsor a month as a thank-you
- **Community / church / cooperative sponsorship** — "sponsor a young tailor" programs become trivial to run
- **Partner channel** — fabric suppliers, training programs, etc. can sponsor their tailors' subscriptions as a relationship gesture
- **Acquisition** — the sponsored tailor's first experience of StitchPad is *"someone believed in me enough to pay for this"* — strong emotional onboarding

### Sponsor flow

1. Sponsor opens either a tailor's **public sponsor link** (e.g. `stitchpad.com/sponsor/folake-tailoring`) or the general `stitchpad.com/sponsor` page where they enter the tailor's phone or email.
2. Sponsor chooses **tier** (Pro or Atelier) and **duration** (1, 3, 6, or 12 months).
3. Sponsor optionally adds a **personal message** (≤ 200 chars).
4. Sponsor enters their **name + email** (for receipt + refund routing — no StitchPad account required).
5. Sponsor pays via **Paystack one-time payment** — no recurring charge on the sponsor's card.
6. On payment confirmation, recipient resolution + subscription extension run per rules below.

### Recipient resolution

| Sponsor input | Behavior |
|---|---|
| Phone / email matches an existing StitchPad user | Subscription applied immediately; recipient notified via push + SMS + email + in-app banner |
| Phone / email does not match any account | Sponsorship held as **pending** for 90 days. If the recipient signs up within 90 days, sponsorship is auto-applied on signup. After 90 days, sponsor is notified and offered refund or re-target. |

### Subscription extension rules

| Recipient's current tier | Gifted tier | Behavior |
|---|---|---|
| Free | Pro | Upgraded to Pro for N months, then drops back to Free |
| Free | Atelier | Upgraded to Atelier for N months, then drops back to Free |
| Pro (monthly or annual) | Pro | Extends `subscriptionEndsAt` by N months |
| Pro (monthly or annual) | Atelier | Bumped to Atelier for N months, then reverts to Pro for any remaining Pro time |
| Atelier | Pro | Extends Atelier `subscriptionEndsAt` by N months (down-gifts **never demote**) |
| Atelier | Atelier | Extends `subscriptionEndsAt` by N months |

**Down-gift rule:** sponsors never accidentally downgrade a tailor — a Pro gift to an Atelier subscriber always extends the existing tier.

### Sponsor link

Every signed-in tailor automatically gets a `sponsorLinkSlug` (e.g. `folake-tailoring`, generated from their business name with fallback to UID-prefix). Editable in Settings. Shareable via WhatsApp / SMS / social.

The public sponsor page at `stitchpad.com/sponsor/{slug}` shows the tailor's business name, profile photo (if set), and the sponsor checkout form.

## Partner / referral codes (placeholder only)

Not implemented in V1. The data model leaves room so we don't repaint later:

```
users/{uid}.referredByCode    — optional code recorded at signup
users/{uid}.referralCode      — this user's own referral code, generated on signup

referralCodes/{code}           — future V1.5+ collection
  ownerUid: string
  rewardType: "free_month" | "coin_bonus" | "naira_discount"
  ... (full rules deferred to a dedicated spec)
```

## Subscription tier field consolidation

This spec closes the [[smart-tier-field-consolidation]] memory:

- The Smart Suggestions Cloud Function currently reads `raw.subscriptionTier ?? raw.tier ?? 'free'` (fallback to legacy `tier` for test docs).
- As part of the V1.0 implementation phase below, the function is updated to **read `subscriptionTier` only**, the `tier` fallback is dropped, and any test docs (e.g. Fola's manually edited `tier: "premium"`) are cleaned.
- `subscriptionTier` becomes the single source of truth.
- The string values change from `"free" | "premium"` to **`"free" | "pro" | "atelier"`** to match the new tier names.

## Data model

### Existing fields on `users/{uid}` (per `FirebaseUserRepository.createUserProfile`)

| Field | Today's value | Change |
|---|---|---|
| `subscriptionTier` | `"free" \| "premium"` | Expand to `"free" \| "pro" \| "atelier"`; consolidate (drop `tier`) |
| `subscriptionStatus` | `"active" \| "canceled" \| "expired"` | No change |
| `customerCount` | `number` | No change |

### New fields on `users/{uid}`

| Field | Type | Purpose |
|---|---|---|
| `subscriptionEndsAt` | `timestamp \| null` | When current paid period expires; `null` for Free |
| `subscriptionRenews` | `boolean` | `true` = auto-renew, `false` = canceled but still in paid window |
| `welcomeBonusAppliedAt` | `timestamp \| null` | Set on signup; welcome ends at end of that calendar month |
| `bonusCoins` | `number` | Current bonus-coin balance (welcome + sponsored); consumed before monthly allocation |
| `sponsorLinkSlug` | `string \| null` | Public shareable slug for the sponsor link |
| `referredByCode` | `string \| null` | Placeholder for future referral system |
| `referralCode` | `string \| null` | Placeholder for future referral system |

### Customer-slot state

Tracked on the existing customer document at `users/{uid}/customers/{customerId}`:

| Field | Type | Purpose |
|---|---|---|
| `slotState` | `"active" \| "locked"` | Whether the customer is in the active 15 slots or grayed-out |
| `lockedAt` | `timestamp \| null` | When `slotState` was set to `"locked"` (used to show "locked X days ago") |

### New top-level collection `sponsorships/{sponsorshipId}`

```
sponsorId: { name: string, email: string, phone?: string }
recipientPhone: string?
recipientEmail: string?
recipientUid: string?              // resolved when user signs up or matches existing
tier: "pro" | "atelier"
durationMonths: 1 | 3 | 6 | 12
personalMessage: string?           // <= 200 chars
amountPaid: number                 // in kobo
paystackTransactionRef: string
status: "pending" | "applied" | "expired" | "refunded"
createdAt: timestamp
appliedAt: timestamp?
expiresAt: timestamp               // 90 days after createdAt if no recipient match
```

### Extend existing usage doc `users/{uid}/usage/smart_drafts`

| Field | Type | Purpose |
|---|---|---|
| `monthYear` | `string` (existing) | YYYY-MM rollover key |
| `count` | `number` (existing) | Coins used this month |
| `limit` | `number` (existing) | Coins allowed this month (derived from tier) |
| `bonusBalance` | `number` (NEW) | Bonus coins still available; decremented before `count` increments |

## Server enforcement

| Limit | Enforced where | Notes |
|---|---|---|
| Smart coin / month cap | Cloud Function `smartDraftMessage` (existing) | Update to consume `bonusBalance` before `count` |
| Smart help intent gating (Price-This, Reply Helper) | Cloud Function — reject intent type if `subscriptionTier !== "atelier"` | New |
| Customer cap (15 / unlimited) | Client (slot-aware queries) + server validation on customer create | Server returns error if creating an active-slot customer would exceed cap |
| Receipt cap (5/50/unlimited) | Client (counter on receipt generation) + server validation on receipt-record write | New |
| Watermark presence | Client (composes the receipt) | Server doesn't enforce — receipts are local artifacts |
| Brand customization (logo + colors on receipts) | Client | Atelier-only screens hidden for lower tiers |
| Custom measurement types | Client (with server validation on save) | Server returns error if a Free user writes a custom type |
| Full offline mode | Client (cache size / sync trigger config differs by tier) | No server enforcement — UX-only |
| Reports availability | Client (Atelier-only screens hidden for lower tiers) | No server enforcement — reports run on cached data |
| Subscription tier value | Firestore `users/{uid}.subscriptionTier` is the single source of truth | Client gating is for UX; server gating is for protection |

## Implementation phases

This spec defines the **design**. Implementation will decompose into the following phases — **each gets its own implementation plan**.

| Phase | Scope | Estimated effort |
|---|---|---|
| **V1.0** | Tier rename (`"premium"` → `"pro"`/`"atelier"`), `bonusCoins` field + welcome bonus seeding on signup, customer `slotState` model + gray-out logic + 3-day warning + swap mechanic, basic Upgrade modal → Paystack WebView. Close the [[smart-tier-field-consolidation]] memory. | 5–7 dev days |
| **V1.1** | Paystack subscription billing — recurring charges, webhook integration, annual billing flow, full cancel-and-revert behavior | 3–4 dev days |
| **V1.2** | Custom measurement types, custom receipt branding (logo + colors), Tailor Pro reports screen, Tailor Atelier insights screen | 4–5 dev days |
| **V1.3** | **Sponsor a Tailor** — public sponsor page, sponsor checkout flow, recipient notification, pending-sponsorship resolution + 90-day expiry | 5–6 dev days |
| **V1.4+** | Referral codes implementation, pay-as-you-go coin packs, advanced gating refinements based on real usage data | TBD |

Each phase = its own `superpowers:writing-plans` cycle producing a focused implementation plan.

## Risks & open questions

| Risk / question | Mitigation |
|---|---|
| 50/50 active+inactive gray-out may feel punitive and drive churn | 3-day advance warning + permanent swap mechanic + server-side ratio config so we can tune post-launch |
| Atelier worst-case 21% margin if power users emerge | Monthly coin reset prevents compounding; can revisit Atelier allocation if real data shows it |
| Sponsor a Tailor adoption may be low without marketing | Frame as a growth lever, not a core revenue source; even modest adoption drives organic acquisition |
| Naira depreciation could compress USD-denominated AI COGS | Pricing is in Naira; if FX moves sharply, we adjust Naira prices |
| Tailor cohort may not actually pay at ₦2,000 — survey didn't anchor an amount | Welcome mechanic + Free tier give us time to validate willingness-to-pay before pricing pressure forces a change |
| "Smart coins" naming may not translate well in Pidgin or other languages | Test with first cohort; rename is UI-strings only, no code change needed |
| Sponsor link slug collisions across tailors with the same business name | Auto-disambiguate with numeric suffix; offer slug edit in Settings |

## Open product questions to revisit post-launch

- Should Tailor Pro get a single Atelier-only Smart help intent at a higher coin cost (e.g., 5 coins for a pricing suggestion)?
- Should Free get any Smart help beyond Draft Message (e.g., 1 free reply suggestion per month)?
- Should we offer a weekly billing option for ultra-budget tailors (e.g., ₦600/week)?
- Should the welcome window be 1 calendar month, or fixed 30 days from signup?

---

**Next step:** invoke `superpowers:writing-plans` to produce the implementation plan for **Phase V1.0** (the foundational tier rename + bonus + slot model + Upgrade modal). Later phases get their own plans.
