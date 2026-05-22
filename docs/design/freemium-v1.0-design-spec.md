# Freemium V1.0 — Design Spec

Consolidated design decisions for the V1.0 freemium model, locked through a working session between Daniel (founder) and Claude on 2026-05-21–22.

This doc supersedes scattered conversation notes. If implementation diverges, update this doc first.

---

## Executive summary

StitchPad's freemium V1.0 is structured around three deliberate phases:

1. **First Month (days 1–30)** — Unlimited customers, 30 AI drafts to try. AI is gated from day 1 (real cost); customers are not.
2. **Free post-First-Month (day 31+)** — 15 customers active, the rest locked but visible. 5 AI drafts/month.
3. **Pro / Atelier** — Unlimited customers, generous AI quotas.

The transition at day 30 is engineered to be **respectful, predictable, and conversion-aware** — not a punishment. Eight design decisions across UX, copy, mechanics, and architecture collectively turn what could be a "bait and switch" moment into a designed product experience.

### The four marketing value props

These four lines together describe the entire freemium model in honest, respectful language:

| Line | Source decision |
|---|---|
| **"Your first 30 days, no limits."** | #5 — First Month |
| **"We never delete your customers."** | #2 — Lock framing |
| **"Your data never changes overnight."** | #4 — Lazy reconcile |
| **"Your data is yours — export anytime, free."** | #8 — Trust at transition |

---

## The model in one diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Day 1                  Day 30                  Day 31+         │
│  ──────                 ──────                  ───────         │
│                                                                 │
│  ✨ 30 AI drafts        ⏳ Phased warnings      🔒 15-cap active │
│  👥 Unlimited           📥 Auto-pin moment      ✨ 5 AI drafts/mo│
│  customers              🎉 Setup complete       👥 Locked stay  │
│                                                  searchable     │
│                                                                 │
│  [FIRST MONTH]          [TRANSITION]            [FREE / PRO]    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ At any point:
                              ▼
                  ┌─────────────────────────┐
                  │ Upgrade to Pro          │
                  │ Unlimited customers     │
                  │ 500 AI drafts/month     │
                  │ ₦2,000/mo               │
                  └─────────────────────────┘
```

---

## Decision #1 — Smart Auto-Pinning ("Your Top 15")

### Problem solved
Forcing tailors to manually choose 15 customers out of 50+ at the end of First Month is cognitively brutal. They'd abandon rather than choose.

### Locked rules

**Auto-pin algorithm:**

1. Sort all customers by **last activity** (most recent order/contact/edit), descending.
2. Take top **9** → "Active by usage" bucket.
3. From remaining customers, sort by **created date**, descending.
4. Take top **6** → "Recently added" bucket.
5. Everything else → locked.

Pseudo-code:
```
sortedByActivity = customers.sortedByDescending { it.lastActivityAt }
activeBucket = sortedByActivity.take(9)
remaining = customers - activeBucket
sortedByCreated = remaining.sortedByDescending { it.createdAt }
recentAddBucket = sortedByCreated.take(6)
active = activeBucket + recentAddBucket
locked = customers - active
```

A customer who's both recently active AND recently added naturally lands in the activity bucket — no double-counting.

### Open-order edge case
A customer with an open order from 35+ days ago who isn't among the top 9 by recency will be auto-locked. The tailor would still see the order in their pipeline but couldn't open the customer detail without unlocking or swapping. **We accept this edge case** as rare enough not to warrant special logic.

### Visual — auto-pin transition screen

```
┌─────────────────────────────────────────────┐
│ ←  Welcome to Month 2                       │
│                                             │
│ 🎉 You added 47 customers in your first    │
│ month — that's strong growth.               │
│                                             │
│ Here's your starting setup for month 2 —    │
│ review and adjust before continuing:        │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ 👤 Adaeze Okafor    [Active · 2d]       │ │
│ │ 👤 Folake Adebayo   [Active · 4d]       │ │
│ │ 👤 Chinedu Eze      [Active · 5d]       │ │
│ │ ... 6 more active                        │ │
│ │ 👤 Tunde Bakare     [Just added · 5d]   │ │
│ │ 👤 Femi Akinola     [Just added · 8d]   │ │
│ │ ... 5 more new                           │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ 32 less-active customers are saved to your │
│ locked list — view anytime.                 │
│                                             │
│ [ Review and adjust ]    [ Looks good → ]   │
└─────────────────────────────────────────────┘
```

### Badges
- Active-bucket customers: `[Active · Nd]` (last activity)
- Recent-add bucket: `[Just added · Nd]` (created date)
- These badges appear next to each customer on the transition screen so the auto-pin feels deliberate, not arbitrary.

### Override
A "Review and adjust" CTA lets the tailor manually swap any auto-pinned customer for any locked one before committing.

---

## Decision #2 — "Locked" terminology with visible read-only access

### Problem solved
Tailors panic at "locked" customers, assuming data is lost. The word "locked" survives — but the model around it is rewritten to make the anxiety baseless.

### The core rule

> **"Locked" applies only to NEW WORK — not to viewing data.**
> A locked customer is fully visible, fully searchable, fully read-only-explorable. The lock only gates new orders, new measurements, and profile edits.

### Locked rules

| Touchpoint | Behavior |
|---|---|
| Customer list | Two-section layout: Active (15) on top, **🔒 Locked (32)** expandable section below |
| Search bar | Searches **across both** active + locked. Locked results show a 🔒 badge |
| Tap a locked customer | Full detail screen, read-only, with 🔒 banner + two unlock paths |
| Measurements / order history | Fully viewable read-only |
| New order picker | Locked customers appear dimmed with "Swap to use →" CTA |
| Terminology | "Locked" everywhere (not "archived", "inactive", "paused"). Verb pair: lock / unlock |

### Two unlock paths
Every locked customer detail offers both:
- **Upgrade to Pro** — unlocks all locked customers
- **Swap into active** — picks a current active customer to lock in their place

### Visual — locked customer detail

```
┌─────────────────────────────────────────────┐
│ ← Tunde Bakare                              │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ 🔒 Locked — read-only                   │ │
│ │ All data preserved. To take new orders  │ │
│ │ or measurements, swap or upgrade.       │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ 📞 +234 801 234 5670                        │
│ 📍 5 Marina, Lagos Island                   │
│                                             │
│ ─── Measurements ────────────────────────── │
│ Taken 4 months ago                          │
│ Bust 36 · Waist 28 · Hip 38                 │
│ [ View all measurements ]                   │
│                                             │
│ ─── Order history ─────────────────────────│
│ 3 orders · ₦58,000 total                    │
│                                             │
│ ─── Unlock to start a new order ───────────│
│ [🔓 Upgrade to Pro — unlocks all]           │
│ [🔄 Swap into your active 15]               │
└─────────────────────────────────────────────┘
```

### Copy library — supporting language that drains negative valence

| Surface | String |
|---|---|
| Customer list section header | **🔒 Locked · 32 customers** |
| Section subtitle | **Safe and searchable. Unlock anytime to take new orders.** |
| Locked detail banner | **🔒 Locked — read-only.** All data preserved. |
| Empty locked state | **No locked customers — you're all caught up.** |
| Unlock CTA (primary) | **Unlock to start a new order** |
| Settings marketing copy | **"We never delete your customers."** |

---

## Decision #3 — AI-focused conversion engine

### Problem solved
Under the unlimited-customers First Month, customer count is dead as a conversion signal. AI usage replaces it as the primary daily lever.

### The signal hierarchy

| Period | Primary conversion signal | Secondary |
|---|---|---|
| **First Month (days 1–30)** | AI usage (drafts used / quota / time saved) | Customer count (compact, no fraction) |
| **Day 28–30 (transition)** | AI usage + customer-count framing kicks in | Both compounding |
| **Post-First-Month (day 31+)** | Whichever ratio is higher (AI or customer cap) | The other |

### Value translation everywhere

The single highest-leverage addition: surface a **time-saved estimate** at every AI touchpoint.

```
minutes_saved = drafts_used × 1.5
```

No new tracking required — purely computed from existing usage count. Converts every screen from "you used X" to "you saved Y minutes".

### Milestone interrupts (positive, dismissible)

Fire a brief positive interrupt at 5th, 10th, 20th, 25th draft. Soft upgrade nudge framed as celebration, not pressure.

### Visuals

**PlanCard during First Month — AI is the headline:**
```
┌─────────────────────────────────────────────┐
│ [FIRST MONTH · 23 days left]                │
│                                             │
│ ✨ AI drafts                                │
│ ▓▓▓▓▓▓░░░░░░░░ 5 of 30 used                │
│ ~7 minutes saved drafting messages          │
│                                             │
│ 👥 12 customers added · no limits this month│
│                                             │
│ [ How First Month works → ]                 │
└─────────────────────────────────────────────┘
```

**10th draft milestone — positive interrupt:**
```
┌─────────────────────────────────────────────┐
│ ✨ Nice — that's your 10th AI draft         │
│                                             │
│ You've saved about 15 minutes drafting     │
│ this month. Pro gives you 500 drafts/mo    │
│ and never resets.                          │
│                                             │
│ [ See Pro features ]    [ Keep drafting ]  │
└─────────────────────────────────────────────┘
```

**AI quota exhausted — success-framed:**
```
┌─────────────────────────────────────────────┐
│ [FIRST MONTH · AI USED UP]    🎉            │
│                                             │
│ You used all 30 AI drafts this month —     │
│ ~45 minutes saved on messages.              │
│                                             │
│ Pro tailors get 500/month. Never run out.  │
│                                             │
│ [ Upgrade now ]    AI resets June 1         │
└─────────────────────────────────────────────┘
```

### Deferred to V1.5+
- AI-attributed revenue ("your AI drafts contributed to ₦47k in orders")
- Streak tracker ("you've used AI 6 days in a row")
- Real cohort percentiles ("top 20% of AI users")
- Multi-feature Smart Grow (post-caption, referral message, etc.)

---

## Decision #4 — Lazy reconcile with skeleton-loaded UX moment

### Problem solved
At month-end transitions, server reconcile load could theoretically cluster. The existing architecture is already lazy (foreground-triggered, not cron-driven). What's needed is making the lazy reconcile a **deliberate UX moment**, not invisible plumbing.

### Locked rules

| Gap | Fix |
|---|---|
| No dedup — reconcile retries on every state change | Add `lastReconciledAt` on user doc; server returns early if last reconcile was within Xh and welcome state hasn't flipped |
| Silent failures | Surface reconcile failures via Snackbar + log; track success rate via Cloud Functions metrics |
| No UX connection | First-post-First-Month reconcile triggers the auto-pin transition screen (#1) via skeleton-loaded transition |
| Timeout protection at 200-customer scale | Raise cloud function timeout to 120s as safety margin |
| App.kt LaunchedEffect is fragile | Refactor into a root ViewModel that owns the reconcile lifecycle |

### Visual — skeleton loading instead of "we're organizing" screen

**Wrong pattern (don't do):**
```
"We're organizing your 47 customers"
```
This frames the system as the actor mutating user data — contradicts the "we never change your data overnight" value prop.

**Right pattern (do):**
Show the auto-pin screen **skeleton** immediately. Header + structure render at once; rows shimmer-load as reconcile resolves. User feels in control the entire time.

```
┌─────────────────────────────────────────────┐
│ ←  Welcome to Month 2                       │
│                                             │
│ You added 47 customers in your first       │
│ month. Here's your starting setup...        │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │ │
│ │ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │ │  ← shimmer
│ │ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ [ Looks good, continue → ]    (disabled)   │
└─────────────────────────────────────────────┘

         (rows fade in as reconcile resolves)
```

### Cost characteristics
- Per-user reconcile: ~50ms compute, ~200 reads, ~32 writes
- Cohort of 1,000 users: ~$0.10 per First-Month flip
- Cohort of 10,000 users: ~$1.00 per First-Month flip
- **At V1.0 launch scale, the cost concern is hypothetical.**

### Marketing line earned
> **"Your data never changes overnight."**

Earned by making the transition a foreground moment, not background mutation.

---

## Decision #5 — "First Month" framing (not "Setup Month", not "Welcome Month")

### Problem solved
The previous "Welcome Window" copy doesn't translate from 30 customers to unlimited. "Setup Month" implies users are still configuring their account. **"First Month"** is plain, professional, and accurate.

### Locked rules

| Surface | Wording |
|---|---|
| User-facing name | **First Month** |
| System badge | `[FIRST MONTH · N days left]` |
| Marketing headline | **Your first 30 days, no limits.** |
| Internal data fields | Unchanged (`welcomeBonusAppliedAt`, `WELCOME_CUSTOMER_CAP`, etc.) |
| Customer cap (technical) | 200 (system safety ceiling) |
| Customer cap (user-facing) | "Unlimited" — never expose 200 in normal UI |

### Post-signup splash — sets all three expectations upfront

```
┌─────────────────────────────────────────────┐
│       Your first 30 days, no limits         │
│                                             │
│   ┌─────────────────────────────────────┐   │
│   │ 📥  Bring your business in           │   │
│   │     Add unlimited customers,         │   │
│   │     measurements, and orders         │   │
│   │     this month                       │   │
│   └─────────────────────────────────────┘   │
│                                             │
│   ┌─────────────────────────────────────┐   │
│   │ ✨  Try AI on us                     │   │
│   │     30 free AI drafts to save        │   │
│   │     time on customer messages        │   │
│   └─────────────────────────────────────┘   │
│                                             │
│   ┌─────────────────────────────────────┐   │
│   │ 📅  After 30 days                    │   │
│   │     Your 15 most active customers    │   │
│   │     stay handy. The rest stay        │   │
│   │     safe and searchable.             │   │
│   └─────────────────────────────────────┘   │
│                                             │
│              [ Let's go → ]                 │
└─────────────────────────────────────────────┘
```

### Phased dashboard banners

**Days 1–15 (early — encourage):**
```
┌─────────────────────────────────────────────┐
│ [FIRST MONTH · 23 days left]                │
│                                             │
│ Add as many customers as you need —         │
│ no limits for 23 more days.                 │
│                                             │
│ 12 customers added · 5 of 30 AI drafts used │
└─────────────────────────────────────────────┘
```

**Days 16–25 (mid — plant the upgrade idea):**
```
┌─────────────────────────────────────────────┐
│ [FIRST MONTH · 8 days left]                 │
│                                             │
│ Strong start — 28 customers added.          │
│                                             │
│ After day 30, your 15 most active           │
│ customers stay handy. Want to keep all      │
│ of them?                                    │
│                                             │
│ [ See Pro features ]                        │
└─────────────────────────────────────────────┘
```

**Days 26–30 (ending — clear next step):**
```
┌─────────────────────────────────────────────┐
│ [FIRST MONTH ENDS IN 3 DAYS]  ⏳            │
│                                             │
│ Impressive — 47 customers in your first    │
│ month.                                      │
│                                             │
│ On Friday, your 15 most active customers    │
│ stay handy. The other 32 stay safe — just   │
│ unlock or swap to take new orders.          │
│                                             │
│ [ Keep all 47 — Upgrade ]                   │
│ [ See what'll stay active ]                 │
└─────────────────────────────────────────────┘
```

### Edge case — 200-customer power user
If a tailor hits the technical 200 ceiling during First Month, treat them as a **white-glove escalation**, not a wall.

```
┌─────────────────────────────────────────────┐
│              👑                              │
│       You're moving fast!                   │
│                                             │
│       You've added 200 customers in your    │
│       first month — that's exceptional      │
│       growth.                               │
│                                             │
│       Send us a WhatsApp and we'll help     │
│       you with this volume directly.        │
│                                             │
│       [ 💬 WhatsApp support ]                │
│       [ Or upgrade to Pro now ]             │
└─────────────────────────────────────────────┘
```

Turn the cap into an upsell moment for the highest-conversion-intent user segment.

---

## Decision #6 — Repurpose PlanCard state machine for AI usage

### Problem solved
PlanCard's three-state machine (inline/warn/locked) is wasted if it only fires on customer count. Same code paths, different inputs depending on period.

### State machine rule

```
during First Month (days 1–30):
  drive states from AI usage only
  customer count is hidden (no fraction shown, "unlimited" framing)

post-First-Month (day 31+):
  drive states from MAX(ai_ratio, customer_ratio)
  show the signal with the higher ratio
  tiebreaker: AI wins (higher-priority conversion lever)

priority:
  if either signal ≥ 100% → Locked state
  elif either signal ≥ 80% → Warn state
  else → Inline (both signals visible compactly)
```

### Visuals

**First Month · Inline (AI 0–79%):**
```
┌─────────────────────────────────────────────┐
│ [FIRST MONTH · 23 days left]                │
│ ✨ AI: 5 of 30 used                          │
│ 👥 12 customers added · no limits           │
└─────────────────────────────────────────────┘
```

**First Month · Warn (AI 80–99%):**
```
┌─────────────────────────────────────────────┐
│ [FIRST MONTH · AI ALMOST OUT]   ⚠️           │
│                                             │
│ 4 AI drafts left this month.                │
│ Pro gives you 500 drafts/month.             │
│                                             │
│ [ Upgrade — ₦2,000/mo ]                     │
└─────────────────────────────────────────────┘
```

**First Month · Locked (AI = 100%):**
```
┌─────────────────────────────────────────────┐
│ [FIRST MONTH · AI USED UP]    🎉            │
│                                             │
│ You used all 30 drafts — ~45 min saved.    │
│ Pro tailors get 500/month.                  │
│                                             │
│ [ Upgrade now ]   Resets June 1             │
└─────────────────────────────────────────────┘
```

**Post-First-Month · Inline (both < 80%):**
```
┌─────────────────────────────────────────────┐
│ [FREE]                                      │
│ 👥 9 of 15 customers                        │
│ ✨ 2 of 5 AI drafts used                    │
│                            [ Upgrade ]  ›   │
└─────────────────────────────────────────────┘
```

**Post-First-Month · Locked (customers at 15):**
```
┌─────────────────────────────────────────────┐
│ [FREE · LIMIT REACHED]   🔒                 │
│                                             │
│ You've hit your 15-customer limit.          │
│ Upgrade for unlimited customers, or swap    │
│ with a locked customer.                     │
│                                             │
│ [ Upgrade now ]   [ See locked customers ]  │
└─────────────────────────────────────────────┘
```

### Engineering cost
~100 lines of Compose. Reuses existing PlanCard state-machine plumbing. Only adds a second input pair (`aiDraftsUsed`, `aiDraftLimit`) and a flag (`isFirstMonth`).

---

## Decision #7 — Referral lever for long-tail Free users (V1.1)

### Problem solved
Long-tail Free users (8/15 customers, low AI use) have no upgrade pressure. Turn them into acquisition channels instead.

### Validation criteria

A referral counts only when **all** are true:

1. New tailor downloads via the referrer's link
2. Completes signup (account verified)
3. Creates **5 customers** in their own account

### Reward structure

**Customer slots — STACKS, no cap:**

| Valid referrals | Slots earned | Cap (15 + bonus) |
|---|---|---|
| 1 | +2 | 17 |
| 3 | +6 | 21 |
| 5 | +10 | 25 |
| 10 | +20 | 35 |
| 20 | +40 | 55 |

Each unlock fires when the referred tailor adds their 5th customer.

**Pro time — DISCRETE 1-month grants at every 5 paying referrals:**

| Paying referrals reached | Reward granted at that moment |
|---|---|
| 5 | +1 month Pro |
| 10 | +1 more month Pro |
| 15 | +1 more month Pro |
| 20 | +1 more month Pro |

Each grant extends `proExpiresAt` by 30 days. **No cumulative counter shown** — UI always reads "next reward at N more paying referrals", never "X months total earned".

**"Paying" definition:** referred tailor has paid for at least 1 month of Pro. Lifetime credit (doesn't unrevoke if they later cancel). 14-day chargeback window must pass before counting.

### Privacy rule
**No individual referral identities displayed anywhere.** Aggregate counters only. Notifications use generic phrasing ("a tailor you invited").

### Visuals

**Settings entry point:**
```
┌─ Grow your business ────────────────────────┐
│ 👥  Invite a tailor                         │
│     Earn +customers and free Pro time       │
│     when they join                      ›   │
└─────────────────────────────────────────────┘
```

**Invite screen:**
```
┌─────────────────────────────────────────────┐
│ ←  Invite a tailor                          │
│                                             │
│ Help a fellow tailor get started — earn     │
│ rewards every time they join and use it.    │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ Your invite link                        │ │
│ │ stitchpad.app/i/aF3kQ                   │ │
│ │ [ 📋 Copy ]   [ 💬 Share via WhatsApp ]│ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ ─── Customer slots (stacks +2 each) ────────│
│                                             │
│  ✓  2 valid referrals          +4 customers │
│      Each referral = +2 more                │
│                                             │
│ ─── Pro time (1 free month per 5 paying) ───│
│                                             │
│  Progress: 1 of 5 paying referrals          │
│  ▓░░░░                                       │
│                                             │
│  4 more paying referrals → 1 free month     │
│                                             │
│ ─── Activity ───────────────────────────────│
│                                             │
│  5 tailors signed up                        │
│  2 reached 5+ customers                     │
│  1 upgraded to Pro                          │
│                                             │
└─────────────────────────────────────────────┘
```

**Slot reward notification (no names):**
```
┌─────────────────────────────────────────────┐
│  🎉                                          │
│  A tailor you invited just hit 5 customers! │
│                                             │
│  You've earned +2 customer slots — your     │
│  cap is now 17 customers.                   │
│                                             │
│  [ Invite more tailors ]                    │
└─────────────────────────────────────────────┘
```

**Pro reward notification:**
```
┌─────────────────────────────────────────────┐
│  🎉                                          │
│  Your 5th invited tailor upgraded to Pro!   │
│                                             │
│  You've earned 1 free month of Pro          │
│  — starting today.                          │
│                                             │
│  Refer 5 more paying tailors to earn        │
│  another free month.                        │
│                                             │
│  [ Explore Pro features ]                   │
└─────────────────────────────────────────────┘
```

### WhatsApp share template (pre-filled)

```
Hey [name] 👋

I've been using StitchPad — it's a clean app
for managing customers, measurements, and
orders. First 30 days are unlimited.

Use my link so we both get started:
stitchpad.app/i/aF3kQ
```

### Anti-abuse rules

| Risk | Mitigation |
|---|---|
| Self-referral | Validate referrer ≠ new user (different phone + device fingerprint) |
| Fake customer adds | Customers must have name + phone + at least one of (email / address / measurement) |
| Refund/chargeback | 14-day refund delay before Pro counts |

### Business economics
- 5 paying referrals × ₦2,000/mo = ₦10,000 MRR for company
- Cost = 1 month Pro reward ≈ ₦2,000 one-time
- **5× ROI per milestone, indefinitely sustainable**

### Ship timing: V1.1

---

## Decision #8 — Trust at the transition (no fake social proof)

### Problem solved
Mishandled transition could become public complaints on WhatsApp/Twitter. Address with trust-building tactics, not fabricated social-proof claims.

### V1.0 ships (genuine, no data dependency)

| Tactic | Surface |
|---|---|
| **Founder voice** | One-time screen during transition flow |
| **Visible WhatsApp support bar** | Persistent during transition + first 7 days post |
| **CSV data export** | Settings → "Export all my data" (Free for all plans) |
| **No-surprise communication** | Already locked in #5 (phased banners) |

### V1.1+ ships (requires real data)

| Tactic | When |
|---|---|
| Opt-in testimonials with real names | V1.1+ once collected |
| Aggregate cohort stats | V1.2+ once data exists |

**No fabricated stats anywhere, ever.**

### Visual — founder voice screen

Appears once during the transition flow, dismissible:

```
┌─────────────────────────────────────────────┐
│   👤  A note from Daniel, founder            │
│                                             │
│   "Here's why we cap at 15 after your       │
│    first month."                            │
│                                             │
│   AI calls cost us money per use. The       │
│   15-customer cap on Free lets us keep      │
│   the app sustainable while offering a      │
│   real product to everyone — not a          │
│   demo, not a trial.                        │
│                                             │
│   Your customer data is yours, always.      │
│   Locked customers are searchable,          │
│   viewable, and never deleted.              │
│                                             │
│   If something feels off, message us        │
│   directly — we read everything.            │
│                                             │
│   — Daniel                                  │
│   [ 💬 WhatsApp the team ]                   │
└─────────────────────────────────────────────┘
```

### Visual — persistent WhatsApp support bar

Visible during transition + first 7 days after:

```
┌─────────────────────────────────────────────┐
│ 💬 Questions about your plan?               │
│    Message us — we usually reply within     │
│    an hour.                                 │
│    [ Open WhatsApp ]                        │
└─────────────────────────────────────────────┘
```

### Visual — data export in Settings

```
┌─ Your data ─────────────────────────────────┐
│ 📥  Export all my data                      │
│     Download your customers, orders,        │
│     and measurements as CSV. Free for       │
│     all plans.                          ›   │
└─────────────────────────────────────────────┘
```

### Testimonial collection plan (V1.1+)

1. **Trigger:** Pro tailor for 30+ days with engagement signals
2. **Ask in-app:** opt-in form for name, location, 1-2 line quote
3. **Approval:** Daniel/PM reviews before publishing
4. **Display:** rotating slots on transition screen, Settings, marketing site

V1.0 builds the schema (`testimonial: Testimonial?` on user doc or a separate `testimonials` collection) but ships nothing visible.

---

## Marketing copy library

Centralized so engineering pulls from one source.

### Headlines

| Surface | String |
|---|---|
| Post-signup splash | Your first 30 days, no limits |
| Welcome ending splash | Welcome to Month 2 |
| AI exhausted | First Month · AI USED UP |
| Customer cap reached (post-First-Month) | Free · Limit reached |

### Value props (use in marketing site, App Store, Play Store, share copy)

1. Your first 30 days, no limits.
2. We never delete your customers.
3. Your data never changes overnight.
4. Your data is yours — export anytime, free.

### System badges

| Period | Badge |
|---|---|
| Days 1–25 of First Month | `[FIRST MONTH · N days left]` |
| Days 26–30 of First Month | `[FIRST MONTH ENDS IN N DAYS]` ⏳ |
| Post-First-Month, AI warn | `[FREE · AI ALMOST OUT]` ⚠️ |
| Post-First-Month, customer warn | `[FREE · ALMOST FULL]` ⚠️ |
| Post-First-Month, AI locked | `[FREE · AI USED UP]` 🎉 |
| Post-First-Month, customer locked | `[FREE · LIMIT REACHED]` 🔒 |
| Pro tier (during reward) | `[PRO · 1 FREE MONTH FROM REFERRALS]` ⭐ |

### Plural grammar warning

`%1$d customers left before your free limit` — at N=1, reads "1 customers" (wrong). **Use a plural string resource** before V1.0 ship.

---

## V1.0 vs V1.1+ scope split

### V1.0 ships

- ✅ #1 Smart auto-pinning (9 by activity + 6 by created)
- ✅ #2 Locked terminology with read-only access
- ✅ #3 AI-focused conversion engine (time-saved, milestone interrupts, exhaustion celebration)
- ✅ #4 Lazy reconcile with skeleton-loaded transition screen
- ✅ #5 "First Month" framing + post-signup splash + phased banners + 200 cap with white-glove escalation
- ✅ #6 PlanCard state machine driven by max(AI ratio, customer ratio)
- ✅ #8 Founder voice + WhatsApp support bar + CSV export (no fake testimonials)

### V1.1 ships

- ✅ #7 Referral mechanic (full implementation)
- ✅ #8 Opt-in real testimonials (populating empty slots from V1.0)

### V1.2+ ships

- Aggregate cohort stats (once data exists)
- AI-attributed revenue
- Streak tracker
- Multi-feature Smart Grow surfaces

---

## V1.0 engineering prep checklist

These changes must land in V1.0 even though the features ship later — without them, V1.1 doubles in cost.

### User doc schema additions

| Field | Type | Purpose | Ships in |
|---|---|---|---|
| `referralCode` | String | Generated at signup | V1.0 (V1.1 uses) |
| `referredByCode` | String? | Tracks who invited | V1.0 (V1.1 uses) |
| `earnedBonusCustomerCap` | Int (default 0) | Tracks +slots from referrals | V1.0 (V1.1 uses) |
| `validReferralCount` | Int (default 0) | Count of fully-validated referrals | V1.0 (V1.1 uses) |
| `payingReferralCount` | Int (default 0) | Count of referrals who paid Pro | V1.0 (V1.1 uses) |
| `proExpiresAt` | Timestamp? | When Pro window ends (real or earned) | V1.0 (V1.1 uses) |
| `lastReconciledAt` | Timestamp? | Dedup for reconcile | V1.0 |
| `testimonial` | Object? | Opt-in testimonial | V1.0 (V1.1 uses) |

### Code changes (V1.0)

| Change | File(s) |
|---|---|
| Bump `WELCOME_CUSTOMER_CAP: 30 → 200` | `EntitlementsCalculator.kt` |
| Rename "Welcome Month" → "First Month" in all UI strings | `strings.xml` |
| Replace `plan_card_subtitle_inline` with new copy | `strings.xml` |
| Fix plural grammar bug (`%1$d customers left`) | `strings.xml` (use plural resource) |
| Customer cap calculation = `baseCap + earnedBonusCustomerCap` | `EntitlementsCalculator.kt` |
| PlanCard accepts AI usage inputs + isFirstMonth flag | `PlanCard.kt` |
| `CustomerSlotState` enum review (use LOCKED, not ARCHIVED) | enum file |
| Locked customer detail page (read-only) | new screen + nav |
| Customer list two-section layout (Active + Locked) | customer list refactor |
| Search includes locked customers + badge | search refactor |
| Auto-pin transition screen with skeleton loading | new screen |
| Founder voice screen (one-time, dismissible) | new screen |
| WhatsApp support bar component | new shared component |
| CSV data export from Settings | new feature |
| White-glove escalation at 200 cap | edge-case screen |
| Move reconcile trigger from App.kt LaunchedEffect to root ViewModel | refactor |
| Surface reconcile failures via Snackbar | error handling |
| Add `lastReconciledAt` server-side dedup | cloud function update |
| Raise cloud function timeout to 120s | firebase.json or function decorator |

### Server / cloud function changes (V1.0)

| Change | Purpose |
|---|---|
| Add `lastReconciledAt` write in `reconcileCustomerSlots` | Dedup support |
| Early return if recently reconciled + welcome state unchanged | Cost control |
| Update auto-pin algorithm (9 by activity + 6 by created) | Match locked spec |
| Increase function timeout to 120s | Safety margin at 200-customer scale |
| Add structured logs for reconcile metrics | Observability |

---

## Open items / non-decisions

These were discussed but not locked. Revisit before V1.0 ship:

1. **Cap on referral slot stacking** — currently unlimited; do we want to cap at, say, +50 to prevent pathological cases? Open.
2. **Testimonial schema** — single object on user doc OR separate `testimonials` collection? Open. Probably separate collection for moderation.
3. **Plural grammar** — Compose Multiplatform doesn't have plural string resources the same way Android XML does. Engineering needs to settle on a pattern. Open.
4. **CSV export format** — what fields? Customer details only, or include order/measurement history? Probably full export for completeness. Open.
5. **Founder voice screen frequency** — one-time only, or re-appear at certain trigger points? Probably one-time, but flag for analytics.
6. **AI minute-saved estimate** — locked at 1.5 min/draft. Worth instrumenting in V1.1 to refine. Out of scope for V1.0.

---

## Glossary

| Term | Meaning |
|---|---|
| **First Month** | A rolling 30-day window from signup (`welcomeBonusAppliedAt + WELCOME_WINDOW_DAYS`). Switched from calendar-month-aligned on 2026-05-22 because the latter was unfair to late-month signups. Smart-draft monthly quota is a separate concept and stays calendar-month. |
| **Locked customer** | A customer in the read-only state (not deleted, not hidden). |
| **Active 15** | The 15 most-recent/active customers a Free user keeps post-First-Month. |
| **Auto-pin** | The deterministic algorithm that picks which customers stay active. |
| **Bonus coins** | Welcome AI drafts. 30 at signup, consumed before monthly free quota. |
| **Setup Month** | Rejected naming. Use "First Month" instead. |
| **Archive** | Rejected naming. Use "Locked" instead. |
| **Valid referral** | Referred tailor: link signup + 5 customers added. |
| **Paying referral** | Valid referral who has paid for at least 1 month of Pro (14-day chargeback window passed). |

---

## Decisions log

| Date | Decision | Locked by |
|---|---|---|
| 2026-05-21 | Option C (200-cap First Month + 15-cap post + AI gated day 1) | Daniel + PM |
| 2026-05-21 | "First Month" terminology (vs "Setup Month") | Daniel |
| 2026-05-21 | "Unlimited" public framing (200 is internal safety cap) | Daniel |
| 2026-05-21 | Auto-pin 9 by activity + 6 by created | Daniel |
| 2026-05-21 | "Locked" terminology kept (not "archived") | Daniel |
| 2026-05-22 | Referral: +2 slots stacking, 1 month Pro per 5 paying refs (discrete) | Daniel |
| 2026-05-22 | No individual referral names displayed | Daniel |
| 2026-05-22 | No fabricated testimonials at V1.0 | Daniel |
| 2026-05-22 | **First Month → rolling 30-days from signup** (was calendar-month aligned). Smart-draft monthly quota stays calendar-month. Constant: `WELCOME_WINDOW_DAYS = 30`. | Daniel |
| 2026-05-22 | Add "Set welcome days left…" debug action (0–30) for testing the 3-day warning, singular-day pill, and post-First-Month transition without clock mocking | Daniel |
