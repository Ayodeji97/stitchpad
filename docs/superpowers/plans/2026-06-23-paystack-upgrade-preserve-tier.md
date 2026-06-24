# Paystack upgrade preserve-tier — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.
>
> **WORKTREE:** All work in `/Users/danzucker/Desktop/Project/StitchPad-upgrade-preserve` (branch `fix/paystack-upgrade-preserve-tier`). The main checkout holds another branch — never touch it. `cd` into the worktree at the start of EVERY Bash command (the shell resets cwd). This is a **Cloud Functions (TypeScript/Jest)** change — work under `functions/`.

**Goal:** A Paystack Pro→Atelier upgrade preserves the remaining Pro time (queued behind the new Atelier, resumed by the existing prepaid-expiry cron) instead of forfeiting it.

**Architecture:** One change to `computeSubscriptionGrant` (purchase mode): route an upgrade-to-higher-tier through the existing gift queue-behind logic (`computeGiftGrant`). Same-tier renewal still stacks; free/expired/downgrade unchanged. No downstream change (the cron + Paystack persistence are already generic). Paystack-only — Apple doesn't call this function.

**Tech Stack:** Firebase Cloud Functions, TypeScript, Jest, ESLint.

**Spec:** `docs/superpowers/specs/2026-06-23-paystack-upgrade-preserve-tier-design.md`.

---

## Task 1: Queue the remaining lower tier on a purchase upgrade

**Files:**
- `functions/src/billing/subscriptionPeriod.ts` (the `computeSubscriptionGrant` purchase branch)
- Test: `functions/src/__tests__/billing/subscriptionPeriod.test.ts` (extend)
- Test (confirm): `functions/src/__tests__/billing/paystackBilling.test.ts` (the `expirePrepaidSubscriptionsHandler` unwind)

READ `subscriptionPeriod.ts` (`computeSubscriptionGrant` + `computeGiftGrant` + `resolveSchedule` + `addPeriods` + the `SubscriptionGrant`/`BillingTier` types) and the existing `subscriptionPeriod.test.ts` (how it builds `userData` fixtures + asserts grant fields) BEFORE editing.

- [ ] **Step 1: Install deps (node_modules is gitignored in the worktree)**
```bash
cd /Users/danzucker/Desktop/Project/StitchPad-upgrade-preserve/functions && npm install
```

- [ ] **Step 2: Write the failing test** — in `subscriptionPeriod.test.ts`, add a `mode: 'purchase'` upgrade case. Use FIXED dates so the expected ms are exact:
  - `paidAt = new Date('2026-05-01T00:00:00Z')`, active `pro` with `subscriptionEndsAt = new Date('2026-08-01T00:00:00Z')` (92 days out), `subscriptionStatus: 'active'`.
  - Call `computeSubscriptionGrant({ userData, tier: 'atelier', cadence: 'monthly', paidAt, mode: 'purchase' })`.
  - Assert: `subscriptionTier === 'atelier'`; `subscriptionEndsAt` = `paidAt + 1 month` (= `2026-06-01T00:00:00Z`); `fallbackTier === 'pro'`; `fallbackEndsAt` = atelier-end + the 92-day Pro remainder (compute: `(2026-06-01) + (2026-08-01 − 2026-05-01)` ms — assert the exact resulting Date/ms).
  Run `npm test -- subscriptionPeriod` → this NEW test FAILS (current code forfeits: returns fresh atelier, no fallback).

- [ ] **Step 3: Implement** — in `computeSubscriptionGrant`'s purchase branch, replace the single `subscriptionEndsAt = … ? stack : fresh; return {…fallback null}` with three branches (per spec). Add the module-local rank near the top of the file:
```ts
const PURCHASE_TIER_RANK: Record<BillingTier, number> = { pro: 1, atelier: 2 };
```
Purchase branch:
```ts
  const onActivePaidPlan =
    (userData?.subscriptionTier === 'pro' || userData?.subscriptionTier === 'atelier') &&
    userData?.subscriptionStatus === 'active';
  const currentTier: BillingTier | null = onActivePaidPlan ? (userData?.subscriptionTier as BillingTier) : null;
  const currentEndsAt = toDate(userData?.subscriptionEndsAt);

  // 1. Same-tier active → early-renewal STACK (unchanged).
  if (currentTier === tier && currentEndsAt && currentEndsAt.getTime() > paidAt.getTime()) {
    return {
      subscriptionTier: tier,
      subscriptionEndsAt: addPeriods(currentEndsAt, cadence, quantity),
      fallbackTier: null,
      fallbackEndsAt: null,
    };
  }

  // 2. UPGRADE to a strictly higher tier while on an active lower tier → preserve
  //    the remaining lower-tier time by queueing it behind the new higher period,
  //    the SAME "never-lost" stacking gifts use (the prepaid-expiry cron promotes
  //    the queued fallback when the higher period ends). Paystack only — Apple
  //    does its own store-level upgrade proration and never calls this function.
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
(`computeGiftGrant` is a hoisted `function` declaration later in the file — calling it here is fine. `quantity` is the already-resolved `params.quantity ?? 1`.)

- [ ] **Step 4: Run the upgrade test → PASS.** `npm test -- subscriptionPeriod`.

- [ ] **Step 5: Add the remaining grant cases** (per spec) and run:
  - same-tier renewal still stacks (active pro ending paidAt+10d, buy pro → (paidAt+10d)+1mo, no fallback).
  - free → fresh atelier, no fallback.
  - expired (status != 'active') → fresh, no fallback.
  - downgrade (active atelier, buy pro) → fresh pro from paidAt, no fallback (rank not greater).
  Confirm ALL existing `subscriptionPeriod` + `computeGiftGrant` tests still pass.

- [ ] **Step 6: Confirm the end-to-end unwind** — in `paystackBilling.test.ts`, find the `expirePrepaidSubscriptionsHandler` test. Verify (or add) a case: a user doc with `subscriptionTier: 'atelier'`, `subscriptionRenews: false`, `subscriptionEndsAt <= now`, `subscriptionFallbackTier: 'pro'`, `subscriptionFallbackEndsAt` in the future → after the handler, doc is `subscriptionTier: 'pro'`, `subscriptionStatus: 'active'`, `subscriptionEndsAt = fallbackEndsAt`, fallback cleared. (The gift path likely already exercises this generic promotion — if a test exists, just confirm it covers the tier='atelier'→'pro' unwind; add one if not.)

- [ ] **Step 7: Lint + full test (CI parity — lint runs before jest)**
```bash
cd /Users/danzucker/Desktop/Project/StitchPad-upgrade-preserve/functions
npm run lint
npm test
```
Both green. (ESLint single-quote rule fails fast — fix any style before jest.)

- [ ] **Step 8: Commit**
```bash
cd /Users/danzucker/Desktop/Project/StitchPad-upgrade-preserve
git add -A
git commit -m "fix(billing): Paystack upgrade preserves remaining lower-tier time (queue-behind)"
```

---

## Manual / staging check (post-deploy)
On a Paystack test account: active `pro` with time left → purchase `atelier` → user doc shows `subscriptionTier: 'atelier'` + `subscriptionFallbackTier: 'pro'` + `fallbackEndsAt` = atelier-end + the old Pro remainder. Force the prepaid cron after the atelier period → doc unwinds to `pro` for the preserved remainder. **Requires a `functions` deploy** (`npm run deploy`) to take effect.

## Self-review notes
- One-function change; upgrade routes through the shared gift queue-behind; rank guards the upgrade direction. ✓
- Same-tier stacks; free/expired/downgrade fresh — unchanged + tested. ✓
- No downstream change (cron + Paystack persistence already generic); `renews` flag untouched (caller-set false → cron unwinds). ✓
- TDD: failing upgrade test first; lint-before-jest (CI parity); deploy required. ✓
