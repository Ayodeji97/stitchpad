# Paystack upgrade: preserve remaining lower-tier time (queue-behind) — design

**Status:** Design approved (2026-06-23)
**Branch / worktree:** `fix/paystack-upgrade-preserve-tier` at `/Users/danzucker/Desktop/Project/StitchPad-upgrade-preserve` (off `main`, isolated).
**Source:** A Tailor Pro user with un-expired Pro time who upgrades to Tailor Atelier via Paystack currently **forfeits the remaining Pro days** — `computeSubscriptionGrant` treats a tier switch as a fresh period. Tester/UX concern (amplified now that PR #215 made the upgrade discoverable). Make a purchase upgrade preserve the remaining time exactly like a gift: queue the lower tier behind the new higher tier.

## Scope & why it's small
- **Paystack/Android only.** `computeSubscriptionGrant` is called only from `paystackBilling.ts`. `appleBilling.ts` does NOT use it — Apple does its own **store-level upgrade proration** (an in-group upgrade switches immediately and credits unused time), so iOS users already don't lose time. No Apple change.
- **The promotion machinery already exists.** Paystack purchases set `subscriptionRenews: false` (prepaid). `expirePrepaidSubscriptionsHandler` already promotes a queued `subscriptionFallbackTier` → active when a `renews == false` subscription's `subscriptionEndsAt` passes. So queueing the remaining Pro behind the new Atelier "just works" end-to-end via the existing cron — no new infra.
- **The queue-behind logic already exists** in `computeGiftGrant` (gift stacking). We reuse it for the purchase-upgrade case.

## Goal
For `computeSubscriptionGrant(mode: 'purchase')`, when the buyer is on an **active lower paid tier** and buys a **strictly higher tier** (Pro → Atelier), produce a grant that:
- starts the new higher tier now (`addPeriods(paidAt, …)`), and
- **queues the remaining lower-tier time as `subscriptionFallbackTier` / `subscriptionFallbackEndsAt`**, re-anchored to resume after the higher tier ends — identical to the gift atelier branch.
Same-tier early renewal still **stacks** (unchanged). Free / expired / downgrade still start a **fresh period** (unchanged).

## Out of scope
Apple (store-handled). Downgrade purchases (Atelier→Pro — rare; keep current fresh-period behavior). The client paywall/UI (no change — the upgrade button already exists from #215). Any analytics event (separate client concern).

---

## 1. The change — `functions/src/billing/subscriptionPeriod.ts`

In `computeSubscriptionGrant`, the **purchase** branch currently:
```ts
  const onActivePaidPlan =
    (userData?.subscriptionTier === 'pro' || userData?.subscriptionTier === 'atelier') &&
    userData?.subscriptionStatus === 'active';
  const currentTier: BillingTier | null = onActivePaidPlan ? (userData?.subscriptionTier as BillingTier) : null;
  const currentEndsAt = toDate(userData?.subscriptionEndsAt);

  const subscriptionEndsAt =
    currentTier === tier && currentEndsAt && currentEndsAt.getTime() > paidAt.getTime()
      ? addPeriods(currentEndsAt, cadence, quantity)
      : addPeriods(paidAt, cadence, quantity);
  return { subscriptionTier: tier, subscriptionEndsAt, fallbackTier: null, fallbackEndsAt: null };
```

Replace the final block with three explicit branches:
```ts
  // 1. Same-tier active → early-renewal STACK (unchanged).
  if (currentTier === tier && currentEndsAt && currentEndsAt.getTime() > paidAt.getTime()) {
    return {
      subscriptionTier: tier,
      subscriptionEndsAt: addPeriods(currentEndsAt, cadence, quantity),
      fallbackTier: null,
      fallbackEndsAt: null,
    };
  }

  // 2. UPGRADE to a strictly higher tier while on an active lower tier →
  //    preserve the remaining lower-tier time by queueing it behind the new
  //    higher period — the SAME "never-lost" stacking gifts use. The prepaid-
  //    expiry cron promotes the queued fallback when the higher period ends.
  //    (Paystack only; Apple does its own store-level upgrade proration and
  //    never calls this function.)
  if (currentTier && PURCHASE_TIER_RANK[tier] > PURCHASE_TIER_RANK[currentTier]) {
    return computeGiftGrant(userData, tier, cadence, paidAt, quantity);
  }

  // 3. Free / expired / downgrade → fresh period from paidAt (unchanged).
  return {
    subscriptionTier: tier,
    subscriptionEndsAt: addPeriods(paidAt, cadence, quantity),
    fallbackTier: null,
    fallbackEndsAt: null,
  };
```
Add a small module-local rank (there is no rank in this file yet):
```ts
const PURCHASE_TIER_RANK: Record<BillingTier, number> = { pro: 1, atelier: 2 };
```

**Why `computeGiftGrant` for branch 2:** its `giftTier === 'atelier'` path does exactly the queue-behind we want — `resolveSchedule` reads the active row (and any pre-existing fallback), grows the Atelier segment from `paidAt`, and re-anchors the remaining Pro behind the new Atelier end. Reusing it guarantees purchase upgrades and gift upgrades stay byte-for-byte identical and compose with any existing queued time.
**Naming note:** `computeGiftGrant` is now shared by gift + purchase-upgrade. Optionally rename it `computeQueuedGrant` (update both call sites + the doc comment) for clarity — nice-to-have, not required; if renaming, do it as a clean rename with no behavior change.

The `subscriptionRenews` flag is unchanged — it's set by the **caller** (`paystackBilling.ts` writes `false`), not by the grant. So the upgraded Atelier remains prepaid (`renews: false`) and the cron will run the unwind. No change to `paystackBilling.ts` is required (it already persists `grant.fallbackTier`/`fallbackEndsAt`).

## 2. No change needed downstream
- `expirePrepaidSubscriptionsHandler` (`paystackBilling.ts`) already: finds `tier ∈ {pro,atelier}` + `renews == false` + `endsAt <= now`, and if a live fallback exists, **promotes it** (sets `subscriptionTier = fbTier`, `subscriptionEndsAt = fbEnd`, clears the fallback); else → Free. Generic — works for the upgrade-queued Pro exactly as for gift-queued segments.
- `paystackBilling.ts` already persists `subscriptionFallbackTier`/`subscriptionFallbackEndsAt` from the grant.

---

## 3. Testing (`functions/`, Jest)

`functions/src/__tests__/billing/subscriptionPeriod.test.ts` — add `mode: 'purchase'` cases:
- **upgrade queues the remainder:** active `pro`, `subscriptionEndsAt = paidAt + 90d`, buy `atelier` (monthly, qty 1) → grant `subscriptionTier: 'atelier'`, `subscriptionEndsAt = paidAt + 1 month`, `fallbackTier: 'pro'`, `fallbackEndsAt = (paidAt + 1 month) + 90d` (assert exact ms from fixed dates).
- **same-tier renewal still stacks:** active `pro` ending `paidAt + 10d`, buy `pro` → `subscriptionEndsAt = (paidAt + 10d) + 1 month`, no fallback.
- **free → fresh:** no active plan, buy `atelier` → `subscriptionEndsAt = paidAt + 1 month`, no fallback.
- **expired → fresh:** `subscriptionStatus != 'active'`, buy `atelier` → fresh, no fallback (the queued path requires an ACTIVE lower tier).
- **downgrade unchanged:** active `atelier`, buy `pro` → fresh `pro` from `paidAt`, no fallback (rank not greater → branch 3).
- Keep all existing `computeSubscriptionGrant` / `computeGiftGrant` tests green.

`functions/src/__tests__/billing/paystackBilling.test.ts` — confirm (or add) the end-to-end unwind: a user doc written by an upgrade grant (active `atelier` + `fallbackTier: 'pro'` + future `fallbackEndsAt`, `renews: false`, `endsAt <= now`) → after `expirePrepaidSubscriptionsHandler`, the doc is `subscriptionTier: 'pro'`, `subscriptionEndsAt = fallbackEndsAt`, fallback cleared. (If the gift path already covers this promotion, just assert it still holds.)

## 4. Verify
```bash
cd /Users/danzucker/Desktop/Project/StitchPad-upgrade-preserve/functions
npm install            # node_modules is gitignored in the worktree
npm run lint           # CI runs lint before jest (single-quote rule fails fast)
npm test               # jest — all billing tests green
```
Server-side change → requires a **functions deploy** (`npm run deploy`) to take effect; not live until then.

## 5. Manual / staging check
On a Paystack test account: be active `pro` with time left → purchase `atelier` → user doc shows `subscriptionTier: 'atelier'` + `subscriptionFallbackTier: 'pro'` + `fallbackEndsAt` = atelier-end + the old Pro remainder. Force the prepaid cron after the atelier period → doc unwinds to `pro` for the preserved remainder.

## 6. Self-review checks
- Purchase upgrade (Pro→Atelier) queues remaining Pro via the shared gift logic; cron promotes it. ✓
- Same-tier stacks; free/expired/downgrade fresh — unchanged. ✓
- Paystack only (Apple store-handled, doesn't call this fn); `renews` flag untouched. ✓
- No downstream change (cron + paystack persistence already generic). ✓
- Tests: grant cases (exact ms) + end-to-end cron unwind; lint before jest. Deploy required. ✓
