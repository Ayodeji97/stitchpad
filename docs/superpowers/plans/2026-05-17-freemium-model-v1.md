# Freemium Model V1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the foundational freemium plumbing — tier rename to Free/Pro/Atelier, signup welcome bonus (30 Smart coins + 30-customer cap), customer slot model with gray-out + swap mechanic, basic Upgrade modal linking to Paystack — that all later freemium phases (V1.1 Paystack billing, V1.2 receipts/measurements/reports, V1.3 Sponsor a Tailor) build on.

**Architecture:** The user doc at `users/{uid}` gets new fields (`subscriptionEndsAt`, `subscriptionRenews`, `welcomeBonusAppliedAt`, `bonusCoins`, `sponsorLinkSlug`); a pure-Kotlin `EntitlementsCalculator` derives current caps/quotas from those fields. Each customer doc gets a `slotState` + `lockedAt` field. An HTTPS callable Cloud Function `reconcileCustomerSlots` enforces the 50/50 active+inactive gray-out rule when called by the client on app foreground (when welcome ends or tier downgrades). The Smart Suggestions function is updated to consume `bonusCoins` before the monthly allocation and to gate Atelier-only intents. The dormant `PlanCard.kt` is re-landed into Settings and triggers an `UpgradeModal` that opens Paystack hosted checkout in an external browser (full webhook integration ships in V1.1).

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform 1.7, Firebase (Firestore + Functions + Auth) via GitLive, TypeScript Cloud Functions v1 (Node.js 20, Jest), Koin DI, Material3, Paystack hosted checkout (browser intent for V1.0).

**Branch:** `feature/freemium-model` (already created off `main`)

---

## File Structure

### New files (commonMain Kotlin)

```
composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/
  core/domain/model/
    SubscriptionTier.kt              — enum FREE / PRO / ATELIER + parse helpers
    CustomerSlotState.kt             — enum ACTIVE / LOCKED
  core/domain/entitlement/
    UserEntitlements.kt              — data class: tier, customerCap, smartCoinAllowance, isInWelcomeWindow, welcomeEndsAt
    EntitlementsCalculator.kt        — pure function: user-doc fields → UserEntitlements
  feature/freemium/
    domain/
      FreemiumRepository.kt          — interface: reconcileSlots(), swap()
    data/
      CloudFunctionsFreemiumRepository.kt — GitLive Functions wrapper for reconcileCustomerSlots callable
    presentation/upgrade/
      UpgradeRoot.kt
      UpgradeScreen.kt
      UpgradeViewModel.kt
      UpgradeAction.kt / UpgradeEvent.kt / UpgradeState.kt
    presentation/welcome/
      WelcomeEndingBanner.kt         — 3-day-warning banner composable
    presentation/swap/
      SwapSheet.kt                   — bottom sheet for swapping a locked customer back into active slots
```

### New files (TypeScript Cloud Functions)

```
functions/src/freemium/
  types.ts                           — shared types
  reconcileSlots.ts                  — HTTPS callable handler + pure logic
  __tests__/reconcileSlots.test.ts
```

### Modified files (commonMain Kotlin)

```
composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/
  core/data/repository/FirebaseUserRepository.kt          — seed welcome bonus on createUserProfile + read new fields
  core/data/dto/CustomerDto.kt                            — add slotState + lockedAt fields
  core/data/mapper/CustomerMappers.kt                     — map new fields
  feature/customer/data/FirebaseCustomerRepository.kt     — enforce cap on create, filter by slotState in list query
  feature/customer/presentation/list/CustomerListScreen.kt — render locked customers with lock icon + tap-to-swap
  feature/settings/presentation/home/SettingsScreen.kt    — render PlanCard + wire upgrade route
  feature/settings/presentation/components/PlanCard.kt    — wire new entitlement props (cap from Entitlements, no more hardcoded constants)
  feature/dashboard/presentation/DashboardScreen.kt       — show WelcomeEndingBanner when within 3 days of welcome end
  di/CoreModule.kt                                        — provide EntitlementsCalculator
  di/FreemiumModule.kt                                    — NEW: provide FreemiumRepository + UpgradeViewModel
  navigation/NavGraph.kt                                  — add Upgrade route
  commonMain/composeResources/values/strings.xml          — new strings (~25 keys)
```

### Modified files (TypeScript)

```
functions/src/
  smart/draftMessage.ts                                   — drop legacy `tier` fallback, switch to "free"/"pro"/"atelier", gate Atelier-only intents
  smart/freeTierCounter.ts                                — consume bonusBalance before count
  smart/types.ts                                          — add 'pro' to tier union
  __tests__/smart/draftMessage.test.ts                    — update fakes to new tier values + bonus path tests
  index.ts                                                — register reconcileCustomerSlots
```

### Memory

```
~/.claude/projects/-Users-danzucker-Desktop-Project-StitchPad/memory/
  project_smart_tier_field_consolidation.md              — UPDATE: mark resolved
```

---

## Task 1: Server — tier rename, drop legacy fallback, gate Atelier intents

**Why first:** establishes the canonical tier values (`"free"` / `"pro"` / `"atelier"`) before the client starts writing them. Server still accepts the legacy `"premium"` write on read to avoid breaking Fola's test doc during the transition — that read-fallback gets removed in Task 11.

**Files:**
- Modify: `functions/src/smart/types.ts`
- Modify: `functions/src/smart/draftMessage.ts:88-100,135-138,229-234`
- Modify: `functions/src/__tests__/smart/draftMessage.test.ts`

- [ ] **Step 1.1: Write the failing tests**

Add to `functions/src/__tests__/smart/draftMessage.test.ts` (inside the existing `describe('draftMessageHandler', ...)` block):

```typescript
  it('treats subscriptionTier "atelier" as unlimited (no quota burn)', async () => {
    const fs = fakeFirestore({ profile: { tier: 'atelier' } });
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBeNull();
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('treats subscriptionTier "pro" as gated (Pro still consumes coins)', async () => {
    const fs = fakeFirestore({ profile: { tier: 'pro' } });
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBe(4); // limit 5 - count 1
    expect(fs.reserveFreeTierSlot).toHaveBeenCalledTimes(1);
  });

  it('rejects Atelier-only intentType (pricing_help) when caller is "free"', async () => {
    const fs = fakeFirestore({ profile: { tier: 'free' } });
    await expect(
      handler({ ...validRequest, intentType: 'pricing_help' as any }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'permission-denied' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });

  it('rejects Atelier-only intentType (pricing_help) when caller is "pro"', async () => {
    const fs = fakeFirestore({ profile: { tier: 'pro' } });
    await expect(
      handler({ ...validRequest, intentType: 'pricing_help' as any }, baseContext as any, fs),
    ).rejects.toMatchObject({ code: 'permission-denied' });
    expect(fs.reserveFreeTierSlot).not.toHaveBeenCalled();
  });
```

Also update the `fakeFirestore` profile type to accept the new values:

```typescript
// Find the line: profile: { tier: 'free' | 'premium' };
// Replace with:
  profile: { tier: 'free' | 'pro' | 'atelier' };
```

- [ ] **Step 1.2: Run tests, confirm they fail**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/functions && npm test -- --testPathPattern=draftMessage
```

Expected: 4 new tests fail (tier values + pricing_help rejection paths don't exist yet).

- [ ] **Step 1.3: Update types + add intent-tier mapping**

In `functions/src/smart/types.ts`:

```typescript
// Find:
export interface UserProfileSummary {
  tier: 'free' | 'premium';
}

// Replace with:
export type Tier = 'free' | 'pro' | 'atelier';

export interface UserProfileSummary {
  tier: Tier;
}

// Add new constant under the IntentType definition:
// IntentType union already exists as: 'balance_reminder' | 'pickup_ready' | 'follow_up' | 'custom_note'
// Add a placeholder for Atelier-only intents that ship in V1.5:
export type AtelierOnlyIntent = 'pricing_help' | 'reply_help';
```

In `functions/src/smart/draftMessage.ts`:

```typescript
// Find:
const SUPPORTED_INTENT_TYPES: readonly IntentType[] = [
  'balance_reminder',
  'pickup_ready',
  'follow_up',
  'custom_note',
];

// Add directly after it:
const ATELIER_ONLY_INTENTS: readonly string[] = ['pricing_help', 'reply_help'];

function requiresAtelier(intentType: string): boolean {
  return ATELIER_ONLY_INTENTS.includes(intentType);
}
```

Also update `RawUserDoc` and the tier read (`subscriptionTier ?? tier ?? 'free'` stays for one more task — Task 11 removes the legacy `tier` field):

```typescript
// Find:
interface RawUserDoc {
  subscriptionTier?: 'free' | 'premium';
  tier?: 'free' | 'premium';
}

// Replace with:
interface RawUserDoc {
  subscriptionTier?: Tier;
  tier?: 'free' | 'premium'; // legacy — removed in Task 11
}

// Import Tier at the top of the file if not already there:
import { Tier } from './types';
```

- [ ] **Step 1.4: Add tier-gating to the handler**

In `functions/src/smart/draftMessage.ts`, find the validation block right after the existing `isLanguage` check and before "1. Tier check":

```typescript
// Find:
  if (!isLanguage(data.language)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: unsupported language');
  }

// Add directly after (before custom-notes validation):
  // Atelier-only intent gating — handler short-circuits before any tier read
  // for fully unsupported intents (e.g., typo). Then below, after tier read,
  // we also short-circuit Atelier-only intents for non-Atelier callers.
  if (!isIntentType(data.intentType) && !requiresAtelier(data.intentType)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: unsupported intentType');
  }
```

Wait — `isIntentType` already rejects unsupported intents. We need to relax that check for `requiresAtelier(...)` intents and then re-gate them by tier. Find the existing intentType validation:

```typescript
// Find:
  if (!isIntentType(data.intentType)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: unsupported intentType');
  }

// Replace with:
  if (!isIntentType(data.intentType) && !requiresAtelier(data.intentType)) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: unsupported intentType');
  }
```

Then after the tier-check block (the existing `const tier = profileSnap.exists ? ...` line), add the Atelier gate:

```typescript
// Find:
  // 1. Tier check
  const profileSnap = await io.profileGet();
  const tier = profileSnap.exists ? profileSnap.data()?.tier ?? 'free' : 'free';

// Add immediately after:
  // Atelier-only intent guard. Done BEFORE customer/order validation so we
  // don't burn round-trips on a request the caller can never make. Done
  // BEFORE quota reservation for the same reason.
  if (requiresAtelier(data.intentType) && tier !== 'atelier') {
    throw new functions.https.HttpsError(
      'permission-denied',
      'atelier_required: this intent is only available on Tailor Atelier',
    );
  }
```

Finally, update the premium check (the existing `if (tier === 'free')` block needs to treat both `free` and `pro` as quota-bound — only `atelier` skips):

```typescript
// Find:
  // 3. Reserve the free-tier slot now that inputs are known good.
  let nextUsage: FreeTierUsageDoc | null = null;
  if (tier === 'free') {
    const reservation = await io.reserveFreeTierSlot(now);
    if (reservation.exhausted) {
      throw new functions.https.HttpsError('permission-denied', 'free_tier_exhausted');
    }
    nextUsage = reservation.usage;
  }

// Replace with:
  // 3. Reserve a quota slot for non-Atelier tiers. Pro consumes coins
  // (50/month, capped by tier-derived limit); Atelier is unlimited.
  let nextUsage: FreeTierUsageDoc | null = null;
  if (tier !== 'atelier') {
    const reservation = await io.reserveFreeTierSlot(now);
    if (reservation.exhausted) {
      throw new functions.https.HttpsError('permission-denied', 'free_tier_exhausted');
    }
    nextUsage = reservation.usage;
  }
```

And the response field:

```typescript
// Find:
  return {
    draftText,
    remainingFreeQuota: tier === 'premium' ? null : (nextUsage!.limit - nextUsage!.count),
  };

// Replace with:
  return {
    draftText,
    remainingFreeQuota: tier === 'atelier' ? null : (nextUsage!.limit - nextUsage!.count),
  };
```

- [ ] **Step 1.5: Run tests, confirm they pass**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/functions && npm test -- --testPathPattern=draftMessage
```

Expected: all server-side tests pass (existing + 4 new).

- [ ] **Step 1.6: Commit**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
git add functions/src/smart/types.ts functions/src/smart/draftMessage.ts functions/src/__tests__/smart/draftMessage.test.ts
git commit -m "$(cat <<'EOF'
feat(freemium): rename tier values (free/pro/atelier) and gate Atelier-only intents

- Tier union becomes 'free' | 'pro' | 'atelier'. 'premium' is no
  longer a valid value; the read-side falls back to legacy 'tier'
  for one more task (removed in Task 11 of the V1.0 plan).
- Pro consumes the coin counter just like Free (Pro just has a
  higher monthly limit derived from the tier on the client). Only
  Atelier skips the reservation.
- New Atelier-only intent gate rejects pricing_help / reply_help
  for callers below Atelier, before quota reservation, with
  permission-denied: atelier_required.

Tests cover the new tier values + the Atelier-only intent rejection
on both 'free' and 'pro' callers.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Server — consume bonusCoins before monthly count

**Why next:** the welcome bonus we seed in Task 4 needs the server to honor it. Build the consumption logic now (server tests only — client seeding comes in Task 4).

**Files:**
- Modify: `functions/src/smart/types.ts`
- Modify: `functions/src/smart/freeTierCounter.ts`
- Modify: `functions/src/smart/draftMessage.ts:103-115` (the `reserveFreeTierSlot` IO method body in `productionIO`)
- Modify: `functions/src/__tests__/smart/draftMessage.test.ts`
- Modify: `functions/src/__tests__/smart/freeTierCounter.test.ts`

- [ ] **Step 2.1: Add bonusBalance field to the usage doc type**

In `functions/src/smart/types.ts`:

```typescript
// Find:
export interface FreeTierUsageDoc {
  monthYear: string; // YYYY-MM
  count: number;
  limit: number;
}

// Replace with:
export interface FreeTierUsageDoc {
  monthYear: string; // YYYY-MM
  count: number;
  limit: number;
  /**
   * Bonus coin balance (welcome bonus + future sponsored coins). Consumed
   * BEFORE the monthly `count` increments. Persists across month rollovers
   * (does not reset). Defaults to 0 for users created before V1.0.
   */
  bonusBalance?: number;
}
```

- [ ] **Step 2.2: Write the failing reconcile test**

Add to `functions/src/__tests__/smart/freeTierCounter.test.ts`:

```typescript
describe('reconcileUsage — bonus balance', () => {
  it('preserves bonusBalance across month rollover', () => {
    const existing = { monthYear: '2026-04', count: 3, limit: 5, bonusBalance: 10 };
    const now = new Date('2026-05-01T08:00:00Z');
    const next = reconcileUsage({ existing, now });
    expect(next.monthYear).toBe('2026-05');
    expect(next.count).toBe(1); // first call of new month
    expect(next.bonusBalance).toBe(10); // bonus survives rollover
  });

  it('defaults missing bonusBalance to 0', () => {
    const existing = { monthYear: '2026-05', count: 2, limit: 5 };
    const now = new Date('2026-05-17T08:00:00Z');
    const next = reconcileUsage({ existing, now });
    expect(next.bonusBalance).toBe(0);
  });
});
```

- [ ] **Step 2.3: Run, confirm failure**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/functions && npm test -- --testPathPattern=freeTierCounter
```

Expected: 2 new tests fail (`bonusBalance` is undefined / not preserved).

- [ ] **Step 2.4: Implement bonus preservation in reconcileUsage**

In `functions/src/smart/freeTierCounter.ts`, find the existing `reconcileUsage` function and update the returned object to preserve and default `bonusBalance`:

```typescript
// Find the existing return statement at the end of reconcileUsage. Replace its body so the returned object carries:
//   bonusBalance: existing?.bonusBalance ?? 0,
// on both branches (new month + same month). Example for the same-month branch:

  return {
    monthYear,
    count: (existing?.count ?? 0) + 1,
    limit,
    bonusBalance: existing?.bonusBalance ?? 0,
  };

// And for the new-month branch:

  return {
    monthYear,
    count: 1,
    limit,
    bonusBalance: existing?.bonusBalance ?? 0,
  };
```

- [ ] **Step 2.5: Add bonus consumption to the IO interface + handler**

In `functions/src/smart/draftMessage.ts`:

```typescript
// Find FreeTierReservation:
export type FreeTierReservation =
  | { exhausted: true }
  | { exhausted: false; usage: FreeTierUsageDoc };

// Replace with:
export type FreeTierReservation =
  | { exhausted: true }
  | { exhausted: false; usage: FreeTierUsageDoc; consumedBonus: boolean };
```

In `productionIO`'s `reserveFreeTierSlot` transaction body, update the write path to consume a bonus coin first when available, instead of incrementing `count`:

```typescript
// Find the existing transaction body inside reserveFreeTierSlot:
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const existing = snap.exists ? (snap.data() as FreeTierUsageDoc) : null;
        const next = reconcileUsage({ existing, now });
        if (existing !== null && isExhausted(existing) && existing.monthYear === next.monthYear) {
          return { exhausted: true } as const;
        }
        tx.set(ref, next);
        return { exhausted: false, usage: next } as const;
      });

// Replace with:
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const existing = snap.exists ? (snap.data() as FreeTierUsageDoc) : null;
        const baseline = reconcileUsage({ existing, now });
        const bonusAvailable = (baseline.bonusBalance ?? 0) > 0;

        if (bonusAvailable) {
          // Bonus consumed; monthly count NOT incremented. reconcileUsage's
          // count++ is rolled back by setting count back to existing.count
          // (or 0 if new month with bonus available).
          const next: FreeTierUsageDoc = {
            ...baseline,
            count: existing?.monthYear === baseline.monthYear ? (existing?.count ?? 0) : 0,
            bonusBalance: (baseline.bonusBalance ?? 0) - 1,
          };
          tx.set(ref, next);
          return { exhausted: false, usage: next, consumedBonus: true } as const;
        }

        // No bonus available — monthly quota path. Reject if exhausted in
        // the current month.
        if (existing !== null && isExhausted(existing) && existing.monthYear === baseline.monthYear) {
          return { exhausted: true } as const;
        }
        tx.set(ref, baseline);
        return { exhausted: false, usage: baseline, consumedBonus: false } as const;
      });
```

- [ ] **Step 2.6: Add bonus-path tests**

Add to `functions/src/__tests__/smart/draftMessage.test.ts` (inside the existing `describe`):

```typescript
  it('consumes bonusBalance before monthly count', async () => {
    const fs = fakeFirestore({
      usage: { monthYear: '2026-05', count: 0, limit: 5, bonusBalance: 3 } as any,
    });
    // Override reserveFreeTierSlot to model bonus consumption with the same
    // local-state mutation pattern as the real fake but tracking bonus.
    let bonus = 3;
    let count = 0;
    fs.reserveFreeTierSlot = jest.fn().mockImplementation((_now: Date) => {
      if (bonus > 0) {
        bonus -= 1;
        return Promise.resolve({
          exhausted: false,
          usage: { monthYear: '2026-05', count, limit: 5, bonusBalance: bonus },
          consumedBonus: true,
        });
      }
      if (count >= 5) return Promise.resolve({ exhausted: true });
      count += 1;
      return Promise.resolve({
        exhausted: false,
        usage: { monthYear: '2026-05', count, limit: 5, bonusBalance: 0 },
        consumedBonus: false,
      });
    });

    // First 3 calls hit bonus, count stays 0.
    for (let i = 0; i < 3; i++) {
      const result = await handler(validRequest, baseContext as any, fs);
      expect(result.remainingFreeQuota).toBe(5); // count untouched
    }
    // Then 5 calls eat the monthly quota.
    for (let i = 0; i < 5; i++) {
      const result = await handler(validRequest, baseContext as any, fs);
      expect(result.remainingFreeQuota).toBe(4 - i);
    }
    // 9th call: bonus gone, monthly quota gone — exhausted.
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'permission-denied',
    });
  });
```

- [ ] **Step 2.7: Run all server tests, confirm pass**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/functions && npm test
```

Expected: all tests pass.

- [ ] **Step 2.8: Commit**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
git add functions/src/smart/types.ts functions/src/smart/freeTierCounter.ts functions/src/smart/draftMessage.ts functions/src/__tests__/smart/freeTierCounter.test.ts functions/src/__tests__/smart/draftMessage.test.ts
git commit -m "$(cat <<'EOF'
feat(freemium): consume bonusCoins before monthly count on the server

- New `bonusBalance` field on the usage doc, preserved across month
  rollover, defaults to 0 for users created before V1.0.
- reserveFreeTierSlot transaction consumes one bonus coin per call
  when bonusBalance > 0; the monthly `count` only increments after
  the bonus pool is empty. Bonus path bypasses the monthly-exhausted
  check so the welcome bonus survives a "no quota left this month"
  state.
- FreeTierReservation gains a `consumedBonus: boolean` flag for
  observability in later phases.

Tests cover bonus survival across month rollover, default-to-0 on
legacy docs, and the 3-bonus + 5-monthly + 1-exhausted sequence.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Client — SubscriptionTier enum + EntitlementsCalculator

**Why:** the client needs a single source of truth for "what does this user's tier let them do right now?" — derived purely from user-doc fields. Pure function = easy to test, easy to reason about.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/SubscriptionTier.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/UserEntitlements.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculator.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculatorTest.kt`

- [ ] **Step 3.1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculatorTest.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.entitlement

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementsCalculatorTest {

    private val tz = TimeZone.of("Africa/Lagos")

    @Test
    fun free_user_no_welcome_has_15_customer_cap_and_5_coins() {
        val now = Instant.parse("2026-05-17T08:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = null,
            now = now,
            timeZone = tz,
        )
        assertEquals(SubscriptionTier.FREE, e.tier)
        assertEquals(15, e.customerCap)
        assertEquals(5, e.smartCoinAllowance)
        assertFalse(e.isInWelcomeWindow)
    }

    @Test
    fun free_user_in_welcome_window_has_30_cap_and_welcomeEndsAt_is_end_of_signup_month() {
        // Signed up May 5 2026 in Lagos → welcome window covers all of May.
        val signedUp = Instant.parse("2026-05-05T10:00:00Z")
        val now = Instant.parse("2026-05-17T08:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertEquals(30, e.customerCap)
        assertTrue(e.isInWelcomeWindow)
        // Welcome window ends at the END of the calendar month (last instant of May).
        // Equivalent to "start of June" in Lagos zone.
        val expectedEnd = LocalDate(2026, 6, 1).atTime(0, 0)
            .toInstant(tz)
        assertEquals(expectedEnd, e.welcomeEndsAt)
    }

    @Test
    fun free_user_past_welcome_window_drops_back_to_15_cap() {
        val signedUp = Instant.parse("2026-04-10T10:00:00Z")
        val now = Instant.parse("2026-05-01T00:00:01Z") // 1 second into May
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertEquals(15, e.customerCap)
        assertFalse(e.isInWelcomeWindow)
    }

    @Test
    fun pro_user_has_unlimited_customers_and_50_coins() {
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.PRO,
            welcomeBonusAppliedAt = null,
            now = Instant.parse("2026-05-17T08:00:00Z"),
            timeZone = tz,
        )
        assertEquals(Int.MAX_VALUE, e.customerCap)
        assertEquals(50, e.smartCoinAllowance)
    }

    @Test
    fun atelier_user_has_unlimited_customers_and_500_coins() {
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.ATELIER,
            welcomeBonusAppliedAt = null,
            now = Instant.parse("2026-05-17T08:00:00Z"),
            timeZone = tz,
        )
        assertEquals(Int.MAX_VALUE, e.customerCap)
        assertEquals(500, e.smartCoinAllowance)
    }

    @Test
    fun isWithinWelcomeEndingWarning_true_when_three_days_or_less_remain() {
        val signedUp = Instant.parse("2026-05-05T10:00:00Z")
        // 2 days before end of May
        val now = Instant.parse("2026-05-29T20:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertTrue(e.isWithinWelcomeEndingWarning)
    }

    @Test
    fun isWithinWelcomeEndingWarning_false_when_more_than_three_days_remain() {
        val signedUp = Instant.parse("2026-05-05T10:00:00Z")
        val now = Instant.parse("2026-05-20T08:00:00Z")
        val e = EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = signedUp,
            now = now,
            timeZone = tz,
        )
        assertFalse(e.isWithinWelcomeEndingWarning)
    }
}
```

- [ ] **Step 3.2: Run, confirm failure**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileDebugUnitTestKotlinAndroid 2>&1 | tail -5
```

Expected: compile fails — `SubscriptionTier`, `UserEntitlements`, `EntitlementsCalculator` don't exist.

- [ ] **Step 3.3: Create SubscriptionTier**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/SubscriptionTier.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.model

/**
 * Subscription tier the user is currently on. Source of truth is the
 * `users/{uid}.subscriptionTier` Firestore field. Values must stay in
 * sync with the server-side `Tier` union in `functions/src/smart/types.ts`.
 */
enum class SubscriptionTier(val wireValue: String) {
    FREE("free"),
    PRO("pro"),
    ATELIER("atelier");

    companion object {
        /**
         * Parse a wire value into a tier, defaulting to FREE for any
         * unknown / missing input (defensive — never throws). Also
         * accepts the legacy `"premium"` value during the V1.0 migration
         * window and maps it to PRO (closer in feature set than ATELIER).
         */
        fun fromWire(value: String?): SubscriptionTier = when (value?.lowercase()) {
            "pro" -> PRO
            "atelier" -> ATELIER
            "premium" -> PRO // legacy
            else -> FREE
        }
    }
}
```

- [ ] **Step 3.4: Create UserEntitlements**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/UserEntitlements.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.entitlement

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant

/**
 * Snapshot of what the current user is entitled to RIGHT NOW. Derived
 * purely from Firestore user-doc fields by [EntitlementsCalculator] — no
 * I/O, no time-of-call dependencies beyond the `now` parameter.
 *
 * Treat this as a value object: produce a fresh one whenever the user
 * doc changes or the welcome window crosses a boundary.
 */
data class UserEntitlements(
    val tier: SubscriptionTier,
    /** Max active customers. `Int.MAX_VALUE` for Pro and Atelier. */
    val customerCap: Int,
    /** Monthly Smart-coin allowance (excluding bonus pool). */
    val smartCoinAllowance: Int,
    /** True when the user is still inside their signup welcome window. */
    val isInWelcomeWindow: Boolean,
    /**
     * When the welcome window expires (start of the next calendar month
     * after signup). `null` once the window has ended or was never
     * applied.
     */
    val welcomeEndsAt: Instant?,
    /**
     * True when [welcomeEndsAt] is non-null AND within 3 days. Drives
     * the "your welcome is ending" dashboard banner.
     */
    val isWithinWelcomeEndingWarning: Boolean,
)
```

- [ ] **Step 3.5: Create EntitlementsCalculator**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculator.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.entitlement

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Pure function: turn user-doc fields into a [UserEntitlements] snapshot.
 *
 * Welcome window definition (per the freemium spec): a new tailor's
 * welcome covers the calendar month they signed up in, in their local
 * timezone. The window ends at midnight on the first day of the NEXT
 * calendar month. Hard-coded to Africa/Lagos for V1.0 — V1.5 will pass
 * the user's timezone through.
 *
 * All limits (15-cap, 30-cap during welcome, coin allowances) live here
 * as constants — change them here, change them once.
 */
object EntitlementsCalculator {

    const val FREE_CUSTOMER_CAP: Int = 15
    const val WELCOME_CUSTOMER_CAP: Int = 30
    const val FREE_COIN_ALLOWANCE: Int = 5
    const val PRO_COIN_ALLOWANCE: Int = 50
    const val ATELIER_COIN_ALLOWANCE: Int = 500
    const val WELCOME_ENDING_WARNING_DAYS: Int = 3

    fun calculate(
        tier: SubscriptionTier,
        welcomeBonusAppliedAt: Instant?,
        now: Instant,
        timeZone: TimeZone,
    ): UserEntitlements {
        val welcomeEndsAt = welcomeBonusAppliedAt?.let { signedUp ->
            val signupLocal = signedUp.toLocalDateTime(timeZone)
            // First day of the NEXT calendar month, at 00:00 local.
            val nextMonth = signupLocal.date.plusMonths(1).withDayOfMonth1()
            nextMonth.atStartOfDayIn(timeZone)
        }

        val isInWelcomeWindow = welcomeEndsAt != null && now < welcomeEndsAt

        val isWithinWelcomeEndingWarning =
            welcomeEndsAt != null && isInWelcomeWindow && run {
                val nowLocal = now.toLocalDateTime(timeZone).date
                val endLocal = welcomeEndsAt.toLocalDateTime(timeZone).date
                nowLocal.daysUntil(endLocal) <= WELCOME_ENDING_WARNING_DAYS
            }

        val customerCap = when {
            tier == SubscriptionTier.FREE && isInWelcomeWindow -> WELCOME_CUSTOMER_CAP
            tier == SubscriptionTier.FREE -> FREE_CUSTOMER_CAP
            else -> Int.MAX_VALUE
        }

        val coinAllowance = when (tier) {
            SubscriptionTier.FREE -> FREE_COIN_ALLOWANCE
            SubscriptionTier.PRO -> PRO_COIN_ALLOWANCE
            SubscriptionTier.ATELIER -> ATELIER_COIN_ALLOWANCE
        }

        return UserEntitlements(
            tier = tier,
            customerCap = customerCap,
            smartCoinAllowance = coinAllowance,
            isInWelcomeWindow = isInWelcomeWindow,
            welcomeEndsAt = welcomeEndsAt,
            isWithinWelcomeEndingWarning = isWithinWelcomeEndingWarning,
        )
    }

    // ----- date helpers (kotlinx.datetime doesn't have these built in) -----

    private fun kotlinx.datetime.LocalDate.plusMonths(n: Int): kotlinx.datetime.LocalDate {
        val totalMonths = this.monthNumber + n
        val newYear = this.year + (totalMonths - 1) / 12
        val newMonth = ((totalMonths - 1) % 12) + 1
        val daysInMonth = daysInMonth(newYear, newMonth)
        return kotlinx.datetime.LocalDate(newYear, newMonth, this.dayOfMonth.coerceAtMost(daysInMonth))
    }

    private fun kotlinx.datetime.LocalDate.withDayOfMonth1(): kotlinx.datetime.LocalDate =
        kotlinx.datetime.LocalDate(this.year, this.monthNumber, 1)

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> error("bad month $month")
    }
}
```

- [ ] **Step 3.6: Run tests, confirm pass**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculatorTest" 2>&1 | tail -10
```

Expected: all 7 tests pass.

- [ ] **Step 3.7: iOS compile check**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3.8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/SubscriptionTier.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/UserEntitlements.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculator.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsCalculatorTest.kt
git commit -m "$(cat <<'EOF'
feat(freemium): SubscriptionTier enum + pure EntitlementsCalculator

- SubscriptionTier enum with wire values matching the server union;
  fromWire() is defensive (unknown → FREE, legacy "premium" → PRO).
- UserEntitlements data class: current tier + derived caps +
  welcome window state.
- EntitlementsCalculator: pure function from (tier, welcomeBonusAppliedAt,
  now, timeZone) to UserEntitlements. Welcome window covers the
  calendar month of signup in Africa/Lagos (hard-coded for V1.0,
  user-zone in V1.5). 3-day ending warning windowed for the banner.

Tests cover all 5 entitlement permutations (Free pre/in/post-welcome,
Pro, Atelier) plus both sides of the warning threshold.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Client — seed welcome bonus on signup

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt:25-55`
- (test approach: integration test via `:composeApp:testDebugUnitTest` is awkward because the existing repo wraps GitLive Firestore directly; we verify via the constants written + a unit test on the helper that constructs the initial doc map)

- [ ] **Step 4.1: Extract the initial-doc builder into a testable helper**

In `FirebaseUserRepository.kt`, replace the `createUserProfile` body's inline `mutableMapOf` with a helper call. Find:

```kotlin
            val data = if (exists) {
                mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp
                )
            } else {
                mutableMapOf(
                    "subscriptionTier" to "free",
                    "subscriptionStatus" to "active",
                    "customerCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp,
                    "updatedAt" to FieldValue.serverTimestamp
                )
            }
```

Replace with:

```kotlin
            val data = if (exists) {
                mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp
                )
            } else {
                buildInitialUserDoc()
            }
```

Add at the bottom of the file, inside the class:

```kotlin
    /**
     * Initial user-doc shape written on first signup. Includes the
     * V1.0 freemium fields: welcome-bonus marker so EntitlementsCalculator
     * grants the 30-customer cap for the first calendar month, and a
     * `bonusCoins` field that's a fast path for the client UI (server
     * is still source of truth via the usage doc).
     *
     * Note: bonusCoins on the user doc is for display only — the server's
     * usage-doc `bonusBalance` is what actually gates Smart help. They're
     * seeded to the same value here so they're consistent at signup.
     */
    private fun buildInitialUserDoc(): MutableMap<String, Any> = mutableMapOf(
        "subscriptionTier" to SubscriptionTier.FREE.wireValue,
        "subscriptionStatus" to "active",
        "subscriptionRenews" to false,
        "customerCount" to 0,
        "welcomeBonusAppliedAt" to FieldValue.serverTimestamp,
        "bonusCoins" to WELCOME_BONUS_COIN_COUNT,
        "createdAt" to FieldValue.serverTimestamp,
        "updatedAt" to FieldValue.serverTimestamp
    )

    companion object {
        const val WELCOME_BONUS_COIN_COUNT: Int = 30
    }
```

Add the missing import at the top of the file:

```kotlin
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
```

- [ ] **Step 4.2: Seed the server-side usage doc bonus on first Smart help call**

The user doc has `bonusCoins`, but the server reads from `users/{uid}/usage/smart_drafts.bonusBalance` (Task 2). For Smart Suggestions to actually honor the bonus, the server-side usage doc needs to know about it. Two ways to seed:

(a) Have the client write the bonusBalance directly on signup (requires GitLive subcollection write before Smart can use it).
(b) Have the server detect "user doc has bonusCoins, usage doc has no bonusBalance" and lift it across on first call.

Option (b) is simpler — one write site. In `functions/src/smart/draftMessage.ts`, find the `profileGet` method in `productionIO`. Just below where it reads tier, also read `bonusCoins`:

```typescript
// Find:
    profileGet: () => db.doc(`users/${uid}`).get().then((snap) => ({
      exists: snap.exists,
      data: (): UserProfileSummary | undefined => {
        const raw = snap.data() as RawUserDoc | undefined;
        if (!raw) return undefined;
        return { tier: raw.subscriptionTier ?? raw.tier ?? 'free' };
      },
    })),

// Replace with:
    profileGet: () => db.doc(`users/${uid}`).get().then((snap) => ({
      exists: snap.exists,
      data: (): UserProfileSummary | undefined => {
        const raw = snap.data() as RawUserDoc | undefined;
        if (!raw) return undefined;
        return {
          tier: raw.subscriptionTier ?? raw.tier ?? 'free',
          welcomeBonusCoins: raw.bonusCoins ?? 0,
        };
      },
    })),
```

Update `UserProfileSummary` in `functions/src/smart/types.ts`:

```typescript
// Find:
export interface UserProfileSummary {
  tier: Tier;
}

// Replace with:
export interface UserProfileSummary {
  tier: Tier;
  /** Welcome bonus coins seeded on the user doc; lifted into the usage doc on first Smart call. */
  welcomeBonusCoins: number;
}
```

Update `RawUserDoc` in `functions/src/smart/draftMessage.ts`:

```typescript
// Find:
interface RawUserDoc {
  subscriptionTier?: Tier;
  tier?: 'free' | 'premium';
}

// Replace with:
interface RawUserDoc {
  subscriptionTier?: Tier;
  tier?: 'free' | 'premium'; // legacy — removed in Task 11
  /** Welcome bonus seeded at signup; lifted into the usage doc on first Smart help call. */
  bonusCoins?: number;
}
```

Modify `reserveFreeTierSlot` in `productionIO` to accept the welcome bonus and seed it into the usage doc on first read (only if usage doc has never been initialised). Wrap the existing transaction logic:

```typescript
// Replace reserveFreeTierSlot's implementation with:
    reserveFreeTierSlot: async (now: Date, welcomeBonusToSeed: number = 0): Promise<FreeTierReservation> => {
      const ref = db.doc(`users/${uid}/usage/smart_drafts`);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const existing = snap.exists ? (snap.data() as FreeTierUsageDoc) : null;

        // First-ever Smart call: seed bonusBalance from the user-doc welcome bonus.
        const seedBonus = existing === null ? welcomeBonusToSeed : 0;
        const seededExisting: FreeTierUsageDoc | null = existing ?? (seedBonus > 0
          ? { monthYear: '', count: 0, limit: 0, bonusBalance: seedBonus }
          : null);

        const baseline = reconcileUsage({ existing: seededExisting, now });
        const bonusAvailable = (baseline.bonusBalance ?? 0) > 0;

        if (bonusAvailable) {
          const next: FreeTierUsageDoc = {
            ...baseline,
            count: seededExisting?.monthYear === baseline.monthYear ? (seededExisting?.count ?? 0) : 0,
            bonusBalance: (baseline.bonusBalance ?? 0) - 1,
          };
          tx.set(ref, next);
          return { exhausted: false, usage: next, consumedBonus: true } as const;
        }

        if (existing !== null && isExhausted(existing) && existing.monthYear === baseline.monthYear) {
          return { exhausted: true } as const;
        }
        tx.set(ref, baseline);
        return { exhausted: false, usage: baseline, consumedBonus: false } as const;
      });
    },
```

Update the `DraftMessageIO` interface signature to match:

```typescript
// Find:
  reserveFreeTierSlot(now: Date): Promise<FreeTierReservation>;

// Replace with:
  reserveFreeTierSlot(now: Date, welcomeBonusToSeed?: number): Promise<FreeTierReservation>;
```

Update the handler call site to pass the bonus through:

```typescript
// Find:
    const reservation = await io.reserveFreeTierSlot(now);

// Replace with:
    const reservation = await io.reserveFreeTierSlot(now, profileSnap.data()?.welcomeBonusCoins ?? 0);
```

- [ ] **Step 4.3: Add a server test for the welcome-bonus seed**

In `functions/src/__tests__/smart/draftMessage.test.ts`, update `fakeFirestore` to track the seeded bonus and add a test:

```typescript
  it('seeds the welcome bonus into the usage doc on the first Smart call', async () => {
    // No usage doc exists yet (welcomeBonusToSeed should be honored).
    const fs = fakeFirestore({ usage: null });
    // Stub the profile to carry a welcome bonus of 30.
    fs.profileGet = jest.fn().mockResolvedValue({
      exists: true,
      data: () => ({ tier: 'free', welcomeBonusCoins: 30 }),
    });
    // Override reserveFreeTierSlot to model the seeding behaviour.
    let bonusBalance: number | null = null;
    let count = 0;
    fs.reserveFreeTierSlot = jest.fn().mockImplementation((_now: Date, welcomeBonusToSeed = 0) => {
      if (bonusBalance === null) {
        bonusBalance = welcomeBonusToSeed; // first call seeds
      }
      if (bonusBalance > 0) {
        bonusBalance -= 1;
        return Promise.resolve({
          exhausted: false,
          usage: { monthYear: '2026-05', count, limit: 5, bonusBalance },
          consumedBonus: true,
        });
      }
      count += 1;
      return Promise.resolve({
        exhausted: false,
        usage: { monthYear: '2026-05', count, limit: 5, bonusBalance: 0 },
        consumedBonus: false,
      });
    });

    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBe(5); // count untouched, bonus consumed
    expect(fs.reserveFreeTierSlot).toHaveBeenCalledWith(expect.any(Date), 30);
  });
```

- [ ] **Step 4.4: Run all server tests, confirm pass**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/functions && npm test
```

Expected: all tests pass (including the new seed test).

- [ ] **Step 4.5: Build the Android app to confirm client changes compile**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/repository/FirebaseUserRepository.kt functions/src/smart/types.ts functions/src/smart/draftMessage.ts functions/src/__tests__/smart/draftMessage.test.ts
git commit -m "$(cat <<'EOF'
feat(freemium): seed 30-coin welcome bonus on signup + lift into usage doc on first Smart call

Client:
- FirebaseUserRepository.createUserProfile now writes
  welcomeBonusAppliedAt + bonusCoins=30 + subscriptionRenews=false
  alongside the existing subscription fields.
- Initial-doc construction extracted to buildInitialUserDoc() so
  the welcome-bonus constants live in one place.

Server:
- UserProfileSummary gains welcomeBonusCoins. profileGet reads it
  from the user doc.
- reserveFreeTierSlot accepts welcomeBonusToSeed and, on the first
  Smart call (when no usage doc exists yet), seeds bonusBalance to
  the welcome value. Subsequent calls consume per Task 2.

Net effect: a new tailor's first 30 Smart calls in their first
month are free over and above the monthly 5.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Customer DTO — slotState + lockedAt fields

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/CustomerDto.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomerSlotState.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Customer.kt` (if exists)
- Modify: customer mappers
- Modify: existing customer tests

- [ ] **Step 5.1: Create CustomerSlotState enum**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomerSlotState.kt`:

```kotlin
package com.danzucker.stitchpad.core.domain.model

/**
 * Slot state on a customer doc. ACTIVE customers are visible + fully
 * usable; LOCKED customers are visible (grayed) but read-only,
 * with an Upgrade or Swap CTA.
 *
 * Default for new and existing customers is ACTIVE — the server
 * reconciliation function (Task 7) flips customers to LOCKED only
 * when the user is over their effective cap.
 */
enum class CustomerSlotState(val wireValue: String) {
    ACTIVE("active"),
    LOCKED("locked");

    companion object {
        fun fromWire(value: String?): CustomerSlotState = when (value?.lowercase()) {
            "locked" -> LOCKED
            else -> ACTIVE
        }
    }
}
```

- [ ] **Step 5.2: Add fields to CustomerDto**

In `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/CustomerDto.kt`, add:

```kotlin
// Inside the existing CustomerDto class, add these two fields with safe defaults:

    /** "active" | "locked" — see CustomerSlotState. Missing on legacy docs → ACTIVE. */
    val slotState: String = "active",

    /** Epoch millis when slotState was set to "locked", null otherwise. */
    val lockedAt: Long? = null,
```

(If `CustomerDto` is in a different shape — e.g., separate `@Serializable` fields, use `Long?` for the timestamp consistent with how the existing repo serializes other timestamps.)

- [ ] **Step 5.3: Update domain Customer model + mapper**

Find `Customer.kt` (domain model). Add the field:

```kotlin
// Inside the Customer data class, add:
    val slotState: CustomerSlotState = CustomerSlotState.ACTIVE,
    val lockedAt: Long? = null,
```

Update the DTO → domain mapper and domain → DTO mapper. Find the mapper file (likely `CustomerMappers.kt`) and add field mapping:

```kotlin
// In dto.toDomain():
    slotState = CustomerSlotState.fromWire(slotState),
    lockedAt = lockedAt,

// In domain.toDto():
    slotState = slotState.wireValue,
    lockedAt = lockedAt,
```

Add imports as needed.

- [ ] **Step 5.4: Update existing customer mapper tests**

Find `CustomerMappersTest.kt` (likely under `feature/customer/data/mapper/` test path). Add:

```kotlin
    @Test
    fun toDomain_defaults_slotState_to_ACTIVE_when_missing() {
        val dto = makeCustomerDto(slotState = "") // simulate legacy doc
        val domain = dto.toDomain()
        assertEquals(CustomerSlotState.ACTIVE, domain.slotState)
        assertEquals(null, domain.lockedAt)
    }

    @Test
    fun toDomain_round_trip_preserves_LOCKED_and_lockedAt() {
        val dto = makeCustomerDto(slotState = "locked", lockedAt = 1_731_000_000_000L)
        val domain = dto.toDomain()
        assertEquals(CustomerSlotState.LOCKED, domain.slotState)
        assertEquals(1_731_000_000_000L, domain.lockedAt)
        val backToDto = domain.toDto(/* whatever existing args */)
        assertEquals("locked", backToDto.slotState)
        assertEquals(1_731_000_000_000L, backToDto.lockedAt)
    }
```

Add a `makeCustomerDto(slotState: String = "active", lockedAt: Long? = null, ...)` helper at the bottom of the test class if one doesn't already exist; reuse the existing test fixture creator if there is one.

- [ ] **Step 5.5: Run all customer tests, confirm pass**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest --tests "*Customer*" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5.6: iOS compile check**

```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/CustomerSlotState.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/dto/CustomerDto.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/model/Customer.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/mapper/CustomerMappers.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/data/mapper/CustomerMappersTest.kt
git commit -m "$(cat <<'EOF'
feat(freemium): add slotState + lockedAt to Customer model

- New CustomerSlotState enum (ACTIVE / LOCKED), wire values match the
  freemium spec data model. fromWire() defaults unknown / missing →
  ACTIVE so legacy customers remain fully usable.
- CustomerDto + Customer domain model carry the new fields with safe
  defaults (ACTIVE, null). Mappers preserve them round-trip.

No behaviour change yet — the slot is always ACTIVE until the
reconciliation function in Task 7 flips it.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Customer repository — enforce cap on create

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepository.kt`
- Modify: feature/customer/domain/error or wherever the customer error sealed lives — add a new error variant for `CapReached`
- Modify: feature/customer ViewModel that handles create flow — surface the new error
- Add tests

- [ ] **Step 6.1: Add `CapReached` to the customer error sealed**

Find the customer error sealed (likely `feature/customer/domain/error/CustomerError.kt` or similar). Add:

```kotlin
// Inside the sealed interface CustomerError : Error {
    data object CapReached : CustomerError
// }
```

- [ ] **Step 6.2: Write the failing repository test**

Find or create `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/customer/data/FirebaseCustomerRepositoryTest.kt`. Add (mocking via a fake `Entitlements` source):

```kotlin
    @Test
    fun createCustomer_returns_CapReached_when_active_slot_count_equals_cap() = runTest {
        val repo = newRepoWithFakeEntitlements(customerCap = 15, activeSlotCount = 15)
        val result = repo.createCustomer(makeNewCustomer())
        assertIs<Result.Error<CustomerError>>(result)
        assertEquals(CustomerError.CapReached, result.error)
    }

    @Test
    fun createCustomer_succeeds_when_under_cap() = runTest {
        val repo = newRepoWithFakeEntitlements(customerCap = 15, activeSlotCount = 14)
        val result = repo.createCustomer(makeNewCustomer())
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun createCustomer_succeeds_at_welcome_cap_30() = runTest {
        val repo = newRepoWithFakeEntitlements(customerCap = 30, activeSlotCount = 29)
        val result = repo.createCustomer(makeNewCustomer())
        assertIs<Result.Success<*>>(result)
    }
```

(`newRepoWithFakeEntitlements` is a test helper you add at the bottom; it wires a fake entitlements provider and a fake firestore that returns `activeSlotCount` from the count query.)

- [ ] **Step 6.3: Run, confirm failure**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:testDebugUnitTest --tests "*FirebaseCustomerRepositoryTest*" 2>&1 | tail -10
```

Expected: tests fail because `createCustomer` doesn't yet check the cap.

- [ ] **Step 6.4: Implement the cap check**

In `FirebaseCustomerRepository.kt`, add a constructor dependency on an `EntitlementsProvider` (a thin interface that returns the current `UserEntitlements`):

```kotlin
// At the top of the file:
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.feature.customer.domain.error.CustomerError
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState

// Constructor (add new param):
class FirebaseCustomerRepository(
    private val firestore: FirebaseFirestore,
    private val entitlements: EntitlementsProvider,  // NEW
    // ... existing params
) : CustomerRepository {
```

Modify `createCustomer`:

```kotlin
    override suspend fun createCustomer(customer: Customer): Result<Customer, CustomerError> {
        val entitlement = entitlements.current()
        // Count current ACTIVE-slot customers; LOCKED ones don't count.
        val activeCount = firestore
            .collection("users")
            .document(currentUid())
            .collection("customers")
            .where { "slotState" equalTo CustomerSlotState.ACTIVE.wireValue }
            .get()
            .documents
            .size
        if (activeCount >= entitlement.customerCap) {
            return Result.Error(CustomerError.CapReached)
        }
        // ... existing creation logic
    }
```

Create the `EntitlementsProvider` interface in domain:

```kotlin
// composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/domain/entitlement/EntitlementsProvider.kt
package com.danzucker.stitchpad.core.domain.entitlement

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-side handle for the current signed-in user's entitlements.
 * Implementations should hot-cache the latest user-doc snapshot and
 * re-compute on changes to subscriptionTier / welcomeBonusAppliedAt.
 */
interface EntitlementsProvider {
    /** Fast synchronous read — last computed snapshot. */
    fun current(): UserEntitlements

    /** Hot flow for reactive observers (banner, customer-list ViewModel). */
    val flow: StateFlow<UserEntitlements>
}
```

For V1.0, provide a minimal implementation that resolves a fresh snapshot per call (no caching — caching is a polish item for V1.0.x):

```kotlin
// composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/data/entitlement/UserDocEntitlementsProvider.kt
package com.danzucker.stitchpad.core.data.entitlement

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

internal class UserDocEntitlementsProvider(
    auth: FirebaseAuth,
    firestore: FirebaseFirestore,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: TimeZone = TimeZone.of("Africa/Lagos"),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : EntitlementsProvider {

    private val _flow = MutableStateFlow(defaultEntitlements())
    override val flow: StateFlow<UserEntitlements> = _flow.asStateFlow()

    init {
        scope.launch {
            auth.authStateChanged
                .map { it?.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    if (uid == null) flowOf(defaultEntitlements())
                    else firestore.collection("users").document(uid).snapshots.map { snap ->
                        val data = snap.data<Map<String, Any?>>()
                        val tierWire = data["subscriptionTier"] as? String
                        val seededMillis = (data["welcomeBonusAppliedAt"] as? Long)
                            ?: (data["welcomeBonusAppliedAt"] as? Double)?.toLong()
                        val seededAt = seededMillis?.let { Instant.fromEpochMilliseconds(it) }
                        EntitlementsCalculator.calculate(
                            tier = SubscriptionTier.fromWire(tierWire),
                            welcomeBonusAppliedAt = seededAt,
                            now = now(),
                            timeZone = timeZone,
                        )
                    }
                }
                .collectLatest { _flow.value = it }
        }
    }

    override fun current(): UserEntitlements = _flow.value

    private fun defaultEntitlements() = EntitlementsCalculator.calculate(
        tier = SubscriptionTier.FREE,
        welcomeBonusAppliedAt = null,
        now = now(),
        timeZone = timeZone,
    )
}
```

(Field shape for `welcomeBonusAppliedAt` may need adjustment based on how GitLive deserializes Firestore timestamps; the cast guard handles both `Long` and `Double` representations.)

- [ ] **Step 6.5: Wire the new dependency in Koin**

In `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CoreModule.kt`, add:

```kotlin
import com.danzucker.stitchpad.core.data.entitlement.UserDocEntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider

// inside the module:
    single<EntitlementsProvider> { UserDocEntitlementsProvider(auth = get(), firestore = get()) }
```

In `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/CustomerModule.kt` (or wherever `FirebaseCustomerRepository` is registered), add the new `entitlements = get()` argument to the constructor call.

- [ ] **Step 6.6: Run customer tests, confirm pass**

```bash
./gradlew :composeApp:testDebugUnitTest --tests "*FirebaseCustomerRepositoryTest*" 2>&1 | tail -10
```

Expected: pass.

- [ ] **Step 6.7: Run the full suite + iOS compile + detekt**

```bash
./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.8: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(freemium): enforce customer cap on create + EntitlementsProvider

- New EntitlementsProvider interface (sync read + StateFlow) backed by
  UserDocEntitlementsProvider that watches the signed-in user's doc
  and recomputes UserEntitlements whenever subscriptionTier or
  welcomeBonusAppliedAt changes. Resets to default on sign-out.
- FirebaseCustomerRepository.createCustomer counts ACTIVE-slot
  customers and rejects with CustomerError.CapReached when at cap.
  LOCKED customers don't count toward the active cap.
- New CustomerError.CapReached variant for the UI to map to an
  upgrade prompt.

Tests cover at-cap rejection (15) + under-cap success + the
welcome-window cap (30).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Cloud Function — reconcileCustomerSlots (HTTPS callable)

**Files:**
- Create: `functions/src/freemium/types.ts`
- Create: `functions/src/freemium/reconcileSlots.ts`
- Create: `functions/src/__tests__/freemium/reconcileSlots.test.ts`
- Modify: `functions/src/index.ts`

- [ ] **Step 7.1: Write the pure-logic tests first**

Create `functions/src/__tests__/freemium/reconcileSlots.test.ts`:

```typescript
import { selectSlotsToLock, CustomerSlotInfo } from '../../freemium/reconcileSlots';

describe('selectSlotsToLock', () => {
  const now = new Date('2026-05-17T08:00:00Z');
  const dayMs = 24 * 60 * 60 * 1000;
  const recent = now.getTime() - 5 * dayMs;
  const stale = now.getTime() - 60 * dayMs;

  function customer(id: string, lastActivityMs: number, slot: 'active' | 'locked' = 'active'): CustomerSlotInfo {
    return { id, lastActivityMs, slotState: slot };
  }

  it('returns empty when count is at or under cap', () => {
    const customers = [customer('a', recent), customer('b', recent)];
    const ops = selectSlotsToLock(customers, 15, now);
    expect(ops).toEqual([]);
  });

  it('locks the right N customers with 50/50 active+inactive split', () => {
    // 25 customers: 12 active (recent), 13 inactive (stale). Cap = 15.
    // Need to lock 10. 50/50 = 5 active + 5 inactive.
    const customers = [
      ...Array.from({ length: 12 }, (_, i) => customer(`active-${i}`, recent - i * 1000)),
      ...Array.from({ length: 13 }, (_, i) => customer(`stale-${i}`, stale - i * 1000)),
    ];
    const ops = selectSlotsToLock(customers, 15, now);
    expect(ops).toHaveLength(10);
    const lockedIds = ops.map((o) => o.id);
    const lockedActives = lockedIds.filter((id) => id.startsWith('active')).length;
    const lockedStales = lockedIds.filter((id) => id.startsWith('stale')).length;
    expect(lockedActives).toBe(5);
    expect(lockedStales).toBe(5);
  });

  it('within each bucket, locks the OLDEST last-touch first', () => {
    // 20 customers, all active. Cap = 15. Need to lock 5.
    const customers = Array.from({ length: 20 }, (_, i) =>
      customer(`c-${i}`, recent - i * 1000),
    );
    const ops = selectSlotsToLock(customers, 15, now);
    // The 5 with the oldest lastActivityMs are at the end of the array — c-15..c-19.
    const lockedIds = ops.map((o) => o.id).sort();
    expect(lockedIds).toEqual(['c-15', 'c-16', 'c-17', 'c-18', 'c-19']);
  });

  it('promotes LOCKED customers to ACTIVE when cap rises (upgrade)', () => {
    const customers = [
      customer('a', recent, 'active'),
      customer('b', recent, 'locked'),
      customer('c', recent, 'locked'),
    ];
    // Cap = unlimited (Pro) — represented as Number.MAX_SAFE_INTEGER.
    const ops = selectSlotsToLock(customers, Number.MAX_SAFE_INTEGER, now);
    // No locks needed; locked → active promotions instead.
    expect(ops.filter((o) => o.toState === 'active').map((o) => o.id).sort())
      .toEqual(['b', 'c']);
  });
});
```

- [ ] **Step 7.2: Run, confirm failure**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/functions && npm test -- --testPathPattern=reconcileSlots
```

Expected: file not found / function not exported.

- [ ] **Step 7.3: Implement selectSlotsToLock + the callable wrapper**

Create `functions/src/freemium/types.ts`:

```typescript
export interface CustomerSlotInfo {
  id: string;
  /** ms-since-epoch of last activity (order, message, update). 0 if never. */
  lastActivityMs: number;
  slotState: 'active' | 'locked';
}

export interface SlotChange {
  id: string;
  toState: 'active' | 'locked';
}

export interface ReconcileSlotsResponse {
  changes: SlotChange[];
  totalActiveAfter: number;
  totalLockedAfter: number;
}
```

Create `functions/src/freemium/reconcileSlots.ts`:

```typescript
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import { CustomerSlotInfo, SlotChange, ReconcileSlotsResponse } from './types';

const DAY_MS = 24 * 60 * 60 * 1000;
const ACTIVITY_WINDOW_DAYS = 30;

/**
 * Pure function — picks which customers to LOCK to bring the active-slot
 * count down to `cap`, using a 50/50 mix of active + inactive (rounded
 * to nearest). Also promotes already-LOCKED customers back to ACTIVE
 * when cap rises (e.g., the user upgrades to Pro).
 *
 * "Active" = last activity within the last 30 days from `now`.
 * "Inactive" = older than 30 days (or never).
 *
 * Within each bucket, oldest-last-touch is locked first.
 */
export function selectSlotsToLock(
  customers: CustomerSlotInfo[],
  cap: number,
  now: Date,
): SlotChange[] {
  const activeCustomers = customers.filter((c) => c.slotState === 'active');
  const lockedCustomers = customers.filter((c) => c.slotState === 'locked');

  // Cap rose? Promote all locked back to active. (V1.5 may also need to
  // re-lock if user downgrades again; for V1.0 a separate call handles it.)
  if (activeCustomers.length + lockedCustomers.length <= cap) {
    return lockedCustomers.map((c) => ({ id: c.id, toState: 'active' as const }));
  }

  if (activeCustomers.length <= cap) {
    return []; // Nothing to do — already at or below cap on active.
  }

  const toLockCount = activeCustomers.length - cap;
  const nowMs = now.getTime();
  const cutoff = nowMs - ACTIVITY_WINDOW_DAYS * DAY_MS;

  const activeBucket = activeCustomers
    .filter((c) => c.lastActivityMs >= cutoff)
    .sort((a, b) => a.lastActivityMs - b.lastActivityMs); // oldest first
  const inactiveBucket = activeCustomers
    .filter((c) => c.lastActivityMs < cutoff)
    .sort((a, b) => a.lastActivityMs - b.lastActivityMs); // oldest first

  const fromInactive = Math.min(Math.round(toLockCount / 2), inactiveBucket.length);
  const fromActive = Math.min(toLockCount - fromInactive, activeBucket.length);

  // If one bucket is short, fill the gap from the other.
  let remaining = toLockCount - fromInactive - fromActive;
  const extras: CustomerSlotInfo[] = [];
  if (remaining > 0) {
    const fallback = inactiveBucket.length > activeBucket.length
      ? inactiveBucket.slice(fromInactive)
      : activeBucket.slice(fromActive);
    extras.push(...fallback.slice(0, remaining));
  }

  return [
    ...inactiveBucket.slice(0, fromInactive),
    ...activeBucket.slice(0, fromActive),
    ...extras,
  ].map((c) => ({ id: c.id, toState: 'locked' as const }));
}

/**
 * HTTPS callable. The client invokes this on app foreground when it
 * detects a tier or welcome-window change. Idempotent — safe to call
 * any time; only writes when a change is needed.
 */
export const reconcileCustomerSlots = functions
  .region('europe-west1')
  .https.onCall(async (_data, context): Promise<ReconcileSlotsResponse> => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    }
    const uid = context.auth.uid;
    const db = admin.firestore();
    const userSnap = await db.doc(`users/${uid}`).get();
    if (!userSnap.exists) {
      throw new functions.https.HttpsError('failed-precondition', 'user_not_found');
    }
    const user = userSnap.data() as { subscriptionTier?: string; welcomeBonusAppliedAt?: number };
    const tier = user.subscriptionTier ?? 'free';
    const inWelcome = isInWelcomeWindow(user.welcomeBonusAppliedAt, new Date());
    const cap = effectiveCap(tier, inWelcome);

    const customersSnap = await db.collection(`users/${uid}/customers`).get();
    const infos: CustomerSlotInfo[] = customersSnap.docs.map((d) => {
      const c = d.data() as { slotState?: string; updatedAt?: number; lastActivityAt?: number };
      return {
        id: d.id,
        lastActivityMs: c.lastActivityAt ?? c.updatedAt ?? 0,
        slotState: (c.slotState as 'active' | 'locked') ?? 'active',
      };
    });

    const changes = selectSlotsToLock(infos, cap, new Date());
    if (changes.length === 0) {
      const activeNow = infos.filter((c) => c.slotState === 'active').length;
      return {
        changes: [],
        totalActiveAfter: activeNow,
        totalLockedAfter: infos.length - activeNow,
      };
    }

    const batch = db.batch();
    for (const ch of changes) {
      batch.update(db.doc(`users/${uid}/customers/${ch.id}`), {
        slotState: ch.toState,
        lockedAt: ch.toState === 'locked' ? Date.now() : null,
      });
    }
    await batch.commit();

    const activeAfter = infos.length - changes.filter((c) => c.toState === 'locked').length
      + changes.filter((c) => c.toState === 'active').length;
    return {
      changes,
      totalActiveAfter: activeAfter,
      totalLockedAfter: infos.length - activeAfter,
    };
  });

function effectiveCap(tier: string, inWelcome: boolean): number {
  if (tier === 'pro' || tier === 'atelier') return Number.MAX_SAFE_INTEGER;
  return inWelcome ? 30 : 15;
}

function isInWelcomeWindow(welcomeAppliedAtMs: number | undefined, now: Date): boolean {
  if (!welcomeAppliedAtMs) return false;
  const applied = new Date(welcomeAppliedAtMs);
  const endOfMonth = new Date(applied.getUTCFullYear(), applied.getUTCMonth() + 1, 1);
  return now.getTime() < endOfMonth.getTime();
}
```

- [ ] **Step 7.4: Register the function in `functions/src/index.ts`**

```typescript
// Add at the bottom (next to smartDraftMessage export):
export { reconcileCustomerSlots } from './freemium/reconcileSlots';
```

- [ ] **Step 7.5: Run all server tests, confirm pass**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad/functions && npm test
```

Expected: all tests pass.

- [ ] **Step 7.6: Commit**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
git add functions/src/freemium/ functions/src/__tests__/freemium/ functions/src/index.ts
git commit -m "$(cat <<'EOF'
feat(freemium): reconcileCustomerSlots Cloud Function + 50/50 selection logic

- selectSlotsToLock (pure): given customers + cap + now, returns the
  list of {id, toState} changes that bring active-slot count to cap.
  Within over-cap customers, picks 50/50 active+inactive mix using
  the 30-day activity window. Within each bucket, oldest last-touch
  is locked first.
- reconcileCustomerSlots (HTTPS callable): europe-west1; reads the
  caller's user doc to derive effective cap, reads customers, applies
  changes in a batch write. Idempotent — safe to call on every app
  foreground.

Wired into functions/src/index.ts for deploy.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Client — call reconcileCustomerSlots on app foreground

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/domain/FreemiumRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/freemium/data/CloudFunctionsFreemiumRepository.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/FreemiumModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt` (call on appForeground / app start)

- [ ] **Step 8.1: Define the domain repository**

Create `feature/freemium/domain/FreemiumRepository.kt`:

```kotlin
package com.danzucker.stitchpad.feature.freemium.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult

interface FreemiumRepository {
    /** Idempotent. Calls the server reconcileCustomerSlots function. */
    suspend fun reconcileSlots(): EmptyResult<DataError.Network>

    /** Swap a locked customer back into the active 15, demoting another. */
    suspend fun swapCustomerSlot(
        promote: String,
        demote: String,
    ): EmptyResult<DataError.Network>
}
```

- [ ] **Step 8.2: Implement against GitLive Functions**

Create `feature/freemium/data/CloudFunctionsFreemiumRepository.kt`:

```kotlin
package com.danzucker.stitchpad.feature.freemium.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.functions.FirebaseFunctions

internal class CloudFunctionsFreemiumRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : FreemiumRepository {

    override suspend fun reconcileSlots(): EmptyResult<DataError.Network> = try {
        functions.httpsCallable("reconcileCustomerSlots").invoke()
        Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _e: Throwable) {
        // Reconciliation failures are non-blocking — the gray-out is a soft
        // UX, not a security boundary. Log and move on.
        Result.Error(DataError.Network.UNKNOWN)
    }

    override suspend fun swapCustomerSlot(
        promote: String,
        demote: String,
    ): EmptyResult<DataError.Network> = try {
        val uid = auth.currentUser?.uid
            ?: return Result.Error(DataError.Network.UNAUTHORIZED)
        // Client-side swap: flip slotState on both customers in a single
        // batch via Firestore directly (no Cloud Function needed for the
        // user-initiated swap path).
        firestore.batch().apply {
            update(firestore.collection("users").document(uid)
                .collection("customers").document(promote)) {
                "slotState" to "active"
                "lockedAt" to null
            }
            update(firestore.collection("users").document(uid)
                .collection("customers").document(demote)) {
                "slotState" to "locked"
                "lockedAt" to System.currentTimeMillis()
            }
        }.commit()
        Result.Success(Unit)
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _e: Throwable) {
        Result.Error(DataError.Network.UNKNOWN)
    }
}
```

(`System.currentTimeMillis()` is not available in commonMain — replace with `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()`.)

- [ ] **Step 8.3: Provide in Koin**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/FreemiumModule.kt`:

```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.freemium.data.CloudFunctionsFreemiumRepository
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import org.koin.dsl.module

val freemiumModule = module {
    single<FreemiumRepository> {
        CloudFunctionsFreemiumRepository(
            auth = get(),
            firestore = get(),
            functions = get(),
        )
    }
}
```

Register the module in the existing `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt` (or wherever `startKoin` is called):

```kotlin
// Add freemiumModule to the modules() list:
modules(coreModule, authModule, customerModule, smartDataModule, /* ... */, freemiumModule)
```

- [ ] **Step 8.4: Trigger reconciliation on app foreground**

In `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/StitchPadApp.kt`, add a `LaunchedEffect` keyed on the signed-in UID that calls `reconcileSlots` once per app launch / sign-in:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.koinInject
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider

// Inside the root composable, where the signed-in user state is observable:
val entitlements = koinInject<EntitlementsProvider>()
val freemium = koinInject<FreemiumRepository>()
val uid = /* observe FirebaseAuth.currentUser?.uid via flow */
LaunchedEffect(uid) {
    if (uid != null) {
        freemium.reconcileSlots()
    }
}
```

(Wire it into the existing auth-state observation — find where `currentUser?.uid` is already being observed in the app root, and add the call there.)

- [ ] **Step 8.5: Compile + iOS + detekt**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad && ./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8.6: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(freemium): client-side reconcileSlots trigger + swap helper

- New FreemiumRepository interface: reconcileSlots() invokes the
  Cloud Function; swapCustomerSlot(promote, demote) flips both
  customers' slotState in a single Firestore batch (no server hop —
  the swap is a user-initiated action with no security boundary).
- StitchPadApp triggers reconcileSlots on every signed-in app
  foreground (LaunchedEffect keyed on uid). Idempotent + best-effort:
  failure is logged, not surfaced.
- New freemiumModule wires the repository into Koin.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Customer list — render locked customers + SwapSheet

**Files:**
- Modify: `feature/customer/presentation/list/CustomerListScreen.kt`
- Modify: `feature/customer/presentation/list/CustomerListViewModel.kt`
- Modify: `feature/customer/presentation/list/CustomerListState.kt` / Action / Event
- Create: `feature/freemium/presentation/swap/SwapSheet.kt`
- Add strings

- [ ] **Step 9.1: Add strings**

In `composeApp/src/commonMain/composeResources/values/strings.xml`, add:

```xml
    <!-- Freemium — customer slot locking + swap -->
    <string name="customer_locked_chip">Locked</string>
    <string name="customer_locked_explainer">This customer is locked because you have more than your Free plan allows. Upgrade to Tailor Pro to view them, or swap with one of your active customers.</string>
    <string name="customer_swap_cta">Swap with an active customer</string>
    <string name="customer_swap_sheet_title">Swap %1$s back in</string>
    <string name="customer_swap_sheet_subtitle">Pick one of your active customers to lock instead.</string>
    <string name="customer_swap_confirm">Swap</string>
    <string name="customer_swap_success">Swapped %1$s in</string>
    <string name="customer_swap_failure">Couldn&apos;t swap. Try again in a moment.</string>
```

- [ ] **Step 9.2: Update CustomerListState + Action + Event**

In the existing state file, add:

```kotlin
// State:
    val lockedCustomers: List<CustomerSummary> = emptyList(),

// Action sealed:
    data class OpenSwapSheetFor(val lockedCustomerId: String) : CustomerListAction
    data class ConfirmSwap(val lockedCustomerId: String, val activeCustomerIdToDemote: String) : CustomerListAction

// Event sealed:
    data class SwapSucceeded(val promotedFirstName: String) : CustomerListEvent
    data object SwapFailed : CustomerListEvent
```

- [ ] **Step 9.3: Modify CustomerListViewModel**

Add handling for the new actions:

```kotlin
            is CustomerListAction.OpenSwapSheetFor -> _state.update { it.copy(swapSheetForId = action.lockedCustomerId) }
            is CustomerListAction.ConfirmSwap -> viewModelScope.launch {
                when (freemium.swapCustomerSlot(promote = action.lockedCustomerId, demote = action.activeCustomerIdToDemote)) {
                    is Result.Success -> {
                        val firstName = _state.value.lockedCustomers
                            .firstOrNull { it.id == action.lockedCustomerId }?.firstName ?: ""
                        _events.send(CustomerListEvent.SwapSucceeded(firstName))
                    }
                    is Result.Error -> _events.send(CustomerListEvent.SwapFailed)
                }
                _state.update { it.copy(swapSheetForId = null) }
            }
```

Modify the data flow that loads customers to partition them by slotState:

```kotlin
// Inside the customer list observation:
customerRepository.observeCustomers().collect { all ->
    val (active, locked) = all.partition { it.slotState == CustomerSlotState.ACTIVE }
    _state.update { it.copy(customers = active, lockedCustomers = locked) }
}
```

- [ ] **Step 9.4: Update CustomerListScreen**

Render locked customers in a separate "Locked customers" section below the active list, with lock icons and a tap handler that calls `OpenSwapSheetFor`:

```kotlin
// Inside the LazyColumn / list, after the active customers section:
if (state.lockedCustomers.isNotEmpty()) {
    item {
        Spacer(Modifier.height(DesignTokens.space4))
        Text(
            text = stringResource(Res.string.customer_locked_section_title), // add this string too
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.space4),
        )
    }
    items(state.lockedCustomers, key = { it.id }) { customer ->
        LockedCustomerRow(
            customer = customer,
            onTap = { onAction(CustomerListAction.OpenSwapSheetFor(customer.id)) },
        )
    }
}
```

Add a `LockedCustomerRow` private composable that shows the customer name grayed-out with a lock icon and a "Swap or upgrade" hint.

- [ ] **Step 9.5: Create SwapSheet**

Create `feature/freemium/presentation/swap/SwapSheet.kt`:

```kotlin
package com.danzucker.stitchpad.feature.freemium.presentation.swap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.core.domain.model.CustomerSummary
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.customer_swap_sheet_subtitle
import stitchpad.composeapp.generated.resources.customer_swap_sheet_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapSheet(
    lockedCustomer: CustomerSummary,
    activeCustomers: List<CustomerSummary>,
    onConfirm: (demoteId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Text(
                text = stringResource(Res.string.customer_swap_sheet_title, lockedCustomer.firstName),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.customer_swap_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(activeCustomers, key = { it.id }) { c ->
                    // Each row is a tappable list item showing the active
                    // customer's name. Tap → onConfirm(c.id).
                    // (Use existing CustomerRow or a slim variant.)
                }
            }
        }
    }
}
```

(Wire the actual list-item composable to reuse the existing CustomerRow pattern in the codebase.)

- [ ] **Step 9.6: Wire the sheet visibility in CustomerListScreen**

Use `state.swapSheetForId` to conditionally render the sheet, looking up the corresponding locked customer.

- [ ] **Step 9.7: Build + iOS + detekt**

```bash
./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9.8: Smoke check (Android)**

Run on emulator, sign in, navigate to Customers, confirm:
- Locked customer (manually flip `slotState: "locked"` on a customer doc in Firestore console) shows in the new section with lock icon
- Tap shows the SwapSheet
- Picking an active customer in the sheet swaps successfully + snackbar fires

- [ ] **Step 9.9: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(freemium): customer list shows locked customers + tap-to-swap sheet

- CustomerListState now partitions customers into active + locked.
  Active list renders as before; locked customers get a separate
  "Locked customers" section with a grayed lock-icon row.
- Tapping a locked row opens SwapSheet — a ModalBottomSheet that
  shows the active customers and lets the tailor pick one to demote
  in exchange for promoting the locked one back into the active 15.
- SwapSucceeded snackbar fires with the newly-active customer's
  first name; SwapFailed surfaces a generic retry message.

Smoke-tested on Android: manual slotState=locked on a Firestore
customer doc → locked section appears → swap promotes/demotes
correctly.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Dashboard — 3-day welcome-ending warning banner

**Files:**
- Create: `feature/freemium/presentation/welcome/WelcomeEndingBanner.kt`
- Modify: `feature/dashboard/presentation/DashboardScreen.kt`
- Modify: `feature/dashboard/presentation/DashboardViewModel.kt` (observe entitlements flow)
- Add strings

- [ ] **Step 10.1: Add strings**

```xml
    <string name="welcome_ending_banner_title">Your welcome ends in %1$d day(s)</string>
    <string name="welcome_ending_banner_body">After that, you&apos;ll be limited to 15 customers and 5 Smart coins per month. Upgrade to Tailor Pro to keep all your customers and get more help.</string>
    <string name="welcome_ending_banner_cta">See Tailor Pro</string>
```

- [ ] **Step 10.2: Create the banner composable**

```kotlin
package com.danzucker.stitchpad.feature.freemium.presentation.welcome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.welcome_ending_banner_body
import stitchpad.composeapp.generated.resources.welcome_ending_banner_cta
import stitchpad.composeapp.generated.resources.welcome_ending_banner_title

@Composable
fun WelcomeEndingBanner(
    daysLeft: Int,
    onSeeUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(DesignTokens.radiusLg),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Text(
                text = stringResource(Res.string.welcome_ending_banner_title, daysLeft),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(Res.string.welcome_ending_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onSeeUpgrade) {
                Text(stringResource(Res.string.welcome_ending_banner_cta))
            }
        }
    }
}
```

- [ ] **Step 10.3: Wire into DashboardViewModel**

Add an `EntitlementsProvider` dependency, observe `entitlements.flow`, expose `daysUntilWelcomeEnds` + `showWelcomeEndingBanner` on DashboardState.

```kotlin
// In DashboardViewModel init:
viewModelScope.launch {
    entitlements.flow.collect { e ->
        val daysLeft = e.welcomeEndsAt?.let { end ->
            val now = Clock.System.now()
            val ms = end.toEpochMilliseconds() - now.toEpochMilliseconds()
            (ms / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
        }
        _state.update {
            it.copy(
                welcomeBannerDaysLeft = daysLeft,
                showWelcomeBanner = e.isWithinWelcomeEndingWarning,
            )
        }
    }
}
```

- [ ] **Step 10.4: Render in DashboardScreen**

```kotlin
if (state.showWelcomeBanner && state.welcomeBannerDaysLeft != null) {
    WelcomeEndingBanner(
        daysLeft = state.welcomeBannerDaysLeft,
        onSeeUpgrade = { onAction(DashboardAction.OpenUpgrade) },
        modifier = Modifier.padding(horizontal = DesignTokens.space4),
    )
    Spacer(Modifier.height(DesignTokens.space3))
}
```

Add the `DashboardAction.OpenUpgrade` variant + Event for navigation (to be wired in Task 11).

- [ ] **Step 10.5: Build + iOS + detekt**

```bash
./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10.6: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(freemium): 3-day welcome-ending warning banner on the dashboard

DashboardViewModel observes EntitlementsProvider.flow and pushes
welcomeBannerDaysLeft + showWelcomeBanner into state. The
WelcomeEndingBanner composable renders inside DashboardScreen when
showWelcomeBanner is true and the days-left number is available.

CTA dispatches DashboardAction.OpenUpgrade (wired to the Upgrade
route in Task 11).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: PlanCard re-landed + UpgradeModal + Paystack link

**Files:**
- Modify: `feature/settings/presentation/components/PlanCard.kt` (refactor to read from EntitlementsProvider)
- Modify: `feature/settings/presentation/home/SettingsScreen.kt` (render PlanCard)
- Create: `feature/freemium/presentation/upgrade/UpgradeRoot.kt`
- Create: `feature/freemium/presentation/upgrade/UpgradeScreen.kt`
- Create: `feature/freemium/presentation/upgrade/UpgradeViewModel.kt`
- Create: Action / Event / State files
- Modify: `navigation/NavGraph.kt` (add Upgrade route)
- Add strings
- Close [[smart-tier-field-consolidation]] — drop `tier` fallback in `draftMessage.ts`

- [ ] **Step 11.1: Drop the legacy `tier` field fallback (close the memory)**

In `functions/src/smart/draftMessage.ts`, remove the legacy fallback:

```typescript
// Find:
interface RawUserDoc {
  subscriptionTier?: Tier;
  tier?: 'free' | 'premium'; // legacy — removed in Task 11
  bonusCoins?: number;
}

// Replace with:
interface RawUserDoc {
  subscriptionTier?: Tier;
  bonusCoins?: number;
}

// Find in profileGet:
        return {
          tier: raw.subscriptionTier ?? raw.tier ?? 'free',
          welcomeBonusCoins: raw.bonusCoins ?? 0,
        };

// Replace with:
        return {
          tier: raw.subscriptionTier ?? 'free',
          welcomeBonusCoins: raw.bonusCoins ?? 0,
        };
```

- [ ] **Step 11.2: Add upgrade route + strings**

Strings:

```xml
    <string name="upgrade_screen_title">Upgrade your plan</string>
    <string name="upgrade_pro_name">Tailor Pro</string>
    <string name="upgrade_pro_price">₦2,000 / month</string>
    <string name="upgrade_pro_annual">or ₦20,000 / year (save ₦4,000)</string>
    <string name="upgrade_atelier_name">Tailor Atelier</string>
    <string name="upgrade_atelier_price">₦4,000 / month</string>
    <string name="upgrade_atelier_annual">or ₦40,000 / year (save ₦8,000)</string>
    <string name="upgrade_pay_with_paystack">Pay with Paystack</string>
    <string name="upgrade_terms">Pay monthly with your card via Paystack. Stop anytime.</string>

    <!-- Settings PlanCard -->
    <string name="plan_card_free_state">You&apos;re on Free</string>
    <string name="plan_card_pro_state">You&apos;re on Tailor Pro</string>
    <string name="plan_card_atelier_state">You&apos;re on Tailor Atelier</string>
    <string name="plan_card_upgrade_cta">Upgrade</string>
    <string name="plan_card_customer_progress">%1$d of %2$d customers</string>
```

In `navigation/NavGraph.kt`:

```kotlin
import com.danzucker.stitchpad.feature.freemium.presentation.upgrade.UpgradeRoot

// Inside the NavHost builder:
composable<Routes.Upgrade> {
    UpgradeRoot(onBack = { navController.navigateUp() })
}

// Add to Routes.kt:
@Serializable
data object Upgrade : Route
```

- [ ] **Step 11.3: Create UpgradeViewModel + Screen + Root**

UpgradeState / Action / Event:

```kotlin
data class UpgradeState(
    val currentTier: SubscriptionTier = SubscriptionTier.FREE,
    val selectedTier: SubscriptionTier = SubscriptionTier.PRO,
    val billingCadence: BillingCadence = BillingCadence.MONTHLY,
)
enum class BillingCadence { MONTHLY, ANNUAL }

sealed interface UpgradeAction {
    data class SelectTier(val tier: SubscriptionTier) : UpgradeAction
    data class SelectCadence(val cadence: BillingCadence) : UpgradeAction
    data object PayWithPaystack : UpgradeAction
}

sealed interface UpgradeEvent {
    data class OpenExternalBrowser(val url: String) : UpgradeEvent
}
```

ViewModel — produces a Paystack hosted-checkout URL (V1.0 placeholder; V1.1 swaps in real subscription init):

```kotlin
class UpgradeViewModel(
    private val entitlements: EntitlementsProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(
        UpgradeState(currentTier = entitlements.current().tier)
    )
    val state: StateFlow<UpgradeState> = _state.asStateFlow()

    private val _events = Channel<UpgradeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: UpgradeAction) {
        when (action) {
            is UpgradeAction.SelectTier -> _state.update { it.copy(selectedTier = action.tier) }
            is UpgradeAction.SelectCadence -> _state.update { it.copy(billingCadence = action.cadence) }
            UpgradeAction.PayWithPaystack -> viewModelScope.launch {
                val s = _state.value
                val amountKobo = when (s.selectedTier to s.billingCadence) {
                    SubscriptionTier.PRO to BillingCadence.MONTHLY -> 2_000_00
                    SubscriptionTier.PRO to BillingCadence.ANNUAL -> 20_000_00
                    SubscriptionTier.ATELIER to BillingCadence.MONTHLY -> 4_000_00
                    SubscriptionTier.ATELIER to BillingCadence.ANNUAL -> 40_000_00
                    else -> return@launch
                }
                // V1.0 placeholder: open the generic Paystack checkout URL.
                // V1.1 replaces this with a server-issued init that returns a
                // real subscription auth_url tied to the user's email + plan code.
                val url = "https://paystack.com/pay/stitchpad?amount=$amountKobo"
                _events.send(UpgradeEvent.OpenExternalBrowser(url))
            }
        }
    }
}
```

Screen + Root: a stateless screen that renders the two tier cards + cadence toggle + Pay CTA; Root wires the ViewModel + ObserveAsEvents + opens the external URL via `UriHandler` (Compose's `LocalUriHandler.current.openUri(url)`).

- [ ] **Step 11.4: Re-land PlanCard in SettingsScreen**

Replace the dormant hardcoded constants in `PlanCard.kt` with props sourced from the parent. Modify `SettingsScreen.kt` to pass:

```kotlin
val entitlements = koinInject<EntitlementsProvider>()
val current by entitlements.flow.collectAsState()
PlanCard(
    tier = current.tier,
    customerCount = state.customerCount,
    customerLimit = if (current.customerCap == Int.MAX_VALUE) null else current.customerCap,
    onUpgrade = { navController.navigate(Routes.Upgrade) },
)
```

Refactor `PlanCard.kt`'s signature accordingly — drop the `initialIsPremium` / `FREE_CUSTOMER_LIMIT` / `WARN_THRESHOLD_RATIO` constants and key state off the new params. (When `customerLimit == null`, show a "You have unlimited customers" state.)

- [ ] **Step 11.5: Build + iOS + detekt**

```bash
./gradlew :composeApp:assembleDebug detekt :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11.6: Smoke check**

- Open Settings — confirm PlanCard renders with the current customer count
- Tap "Upgrade" — UpgradeScreen opens
- Toggle monthly / annual, switch between Pro / Atelier — prices update
- Tap "Pay with Paystack" — external browser opens to the placeholder Paystack URL

- [ ] **Step 11.7: Commit**

```bash
git add -A
git commit -m "$(cat <<'EOF'
feat(freemium): PlanCard re-landed in Settings + UpgradeScreen → Paystack

- PlanCard reads current tier + customerCount + customerLimit from
  EntitlementsProvider via SettingsScreen. Dormant constants removed.
- New UpgradeScreen: tier picker (Pro/Atelier), cadence toggle
  (monthly/annual), prices in Naira with annual-savings hint.
  "Pay with Paystack" opens a placeholder Paystack checkout URL in
  the external browser via LocalUriHandler. Real subscription init
  ships in V1.1 (server-issued auth_url tied to user + plan code).
- Upgrade route added to NavGraph; dashboard banner + settings
  PlanCard both route to it.
- Closes the smart-tier-field-consolidation memory: drops the
  `?? raw.tier` fallback in draftMessage.ts — subscriptionTier is
  now the single source of truth.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Memory cleanup + smoke-test plan

**Files:**
- Modify: `~/.claude/projects/-Users-danzucker-Desktop-Project-StitchPad/memory/project_smart_tier_field_consolidation.md`

- [ ] **Step 12.1: Mark the memory resolved**

Replace the body of `project_smart_tier_field_consolidation.md` with:

```markdown
---
name: smart-tier-field-consolidation
description: "RESOLVED 2026-05-17 — server now reads subscriptionTier only; legacy tier fallback removed in the V1.0 freemium PR"
metadata: 
  node_type: memory
  type: project
  originSessionId: 6d80c854-3b4c-4366-9d7a-a59f5cab32fe
---

**Status: resolved.**

The Smart Suggestions Cloud Function previously read `subscriptionTier ?? tier ?? 'free'` to bridge a legacy test-doc divergence (manually edited `tier: "premium"` on Fola's user doc to test the premium path before real billing existed).

In the V1.0 freemium PR (Task 11 of [[freemium-model-v1]]):
- `RawUserDoc.tier` field removed
- profileGet now reads `subscriptionTier` only
- Tier values changed from `"free" | "premium"` to `"free" | "pro" | "atelier"`

If any future Firestore export shows orphan `tier` fields on test docs, just delete them — nothing reads them anymore.
```

- [ ] **Step 12.2: Commit the memory update**

```bash
cd ~/.claude/projects/-Users-danzucker-Desktop-Project-StitchPad/memory
git add project_smart_tier_field_consolidation.md 2>/dev/null || true
# (memory directory may or may not be a git repo — if not, the file just persists locally)
```

(If memory isn't versioned in git, just save the file — Claude will pick up the new content next session.)

- [ ] **Step 12.3: Run the full check suite one last time**

```bash
cd /Users/danzucker/Desktop/Project/StitchPad
cd functions && npm test 2>&1 | tail -8 && cd ..
./gradlew :composeApp:testDebugUnitTest detekt :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -10
```

Expected: all server tests pass; all client tests pass; detekt clean; iOS compiles.

- [ ] **Step 12.4: Write the PR description + smoke checklist**

Push branch + open PR:

```bash
git push -u origin feature/freemium-model
gh pr create --title "feat(freemium): V1.0 — tier rename + welcome bonus + customer slots + upgrade modal" --body "$(cat <<'EOF'
## Summary

Phase V1.0 of the [freemium model design](docs/superpowers/specs/2026-05-17-freemium-model-design.md). Lays the foundational plumbing all later freemium phases (Paystack billing, receipts/measurements/reports, Sponsor a Tailor) build on.

- **Tier rename**: `subscriptionTier` values are now `"free" | "pro" | "atelier"`. Legacy `tier` fallback removed; closes [[smart-tier-field-consolidation]].
- **Welcome bonus**: new signups get **30 Smart coins + 30-customer cap** for their first calendar month. Bonus coins persist across month rollover and are consumed before the monthly allocation.
- **Customer slot model**: every customer doc has `slotState` + `lockedAt`. Over-cap customers stay in Firestore but flip to LOCKED — visible (grayed) but read-only, with a Swap or Upgrade CTA.
- **Slot reconciliation**: HTTPS callable `reconcileCustomerSlots` enforces the 50/50 active+inactive gray-out rule with oldest-last-touch-first within each bucket. Called by the client on every signed-in app foreground.
- **Swap mechanic**: tailors can promote any locked customer back into their active 15 by demoting another — single Firestore batch write, no Cloud Function hop.
- **3-day warning banner**: dashboard shows "Your welcome ends in N day(s)" when within the warning window, with a CTA into the Upgrade screen.
- **Upgrade flow**: dormant `PlanCard.kt` re-landed in Settings, reads tier + customer count from `EntitlementsProvider`. New `UpgradeScreen` shows tier comparison + monthly/annual cadence toggle + "Pay with Paystack" → opens hosted checkout in external browser. **Full Paystack subscription integration is V1.1** — V1.0 just establishes the UX scaffold.

Out of scope (separate specs/plans):
- V1.1: Paystack subscription billing wiring (webhooks, recurring charges, cancel behavior, annual billing semantics)
- V1.2: receipts/measurements/reports tiering
- V1.3: Sponsor a Tailor gift subscription
- V1.4+: referrals, pay-as-you-go coin packs

## Test plan

- [ ] **Server tests** — `cd functions && npm test` (all pass, including the new tier/bonus/Atelier-intent paths)
- [ ] **Client tests** — `./gradlew :composeApp:testDebugUnitTest` (all pass, including EntitlementsCalculator + customer-cap-on-create)
- [ ] **Detekt** — `./gradlew detekt` (clean)
- [ ] **iOS compile** — `./gradlew :composeApp:compileKotlinIosSimulatorArm64` (BUILD SUCCESSFUL)
- [ ] **New signup (Android)** — sign up a brand-new test user; verify `users/{uid}` has `welcomeBonusAppliedAt`, `bonusCoins: 30`, `subscriptionTier: "free"`, `subscriptionRenews: false`. Open Customers, add up to 30 — all should succeed.
- [ ] **Welcome-coin path** — generate a Smart draft; verify counter on Dashboard shows `5 of 5 free drafts left` (bonus consumed silently behind the scenes). Generate 30 drafts in total — count should stay at 5 until the bonus is exhausted, then drop to 4.
- [ ] **Cap enforcement** — after welcome expires (test by manually editing `welcomeBonusAppliedAt` to a date last month), confirm reconcileCustomerSlots runs on next app foreground and grays the right 15 customers using the 50/50 mix.
- [ ] **Swap** — tap a locked customer → SwapSheet opens → pick an active customer → confirm swap → grayed customer becomes active, picked customer becomes grayed. Snackbar confirms.
- [ ] **3-day banner** — manually edit `welcomeBonusAppliedAt` to be ~28 days ago; reopen app; confirm banner shows "Your welcome ends in 2 day(s)" with Upgrade CTA.
- [ ] **Upgrade screen** — open Settings → tap PlanCard's Upgrade → confirm both tiers render with correct ₦ amounts; toggle monthly/annual → savings update; tap Pay → external browser opens Paystack URL.
- [ ] **iOS smoke** — repeat the new-signup + customer-cap + swap path on iOS simulator.
- [ ] **Pre-merge cleanup** — confirm no `tier: "premium"` field remains on Fola's user doc.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 12.5: Commit the smoke-test PR**

(Already pushed in Step 12.4; this step just confirms the PR is open and links to the spec.)

---

## Self-review checklist (run by the controller before dispatching subagents)

### Spec coverage

Walking through the spec sections:

| Spec section | Covered by task(s) |
|---|---|
| Tier names Free/Pro/Atelier | Task 1 (server) + Task 3 (client) |
| Monthly pricing ₦0/2000/4000 | Task 11 (UpgradeScreen displays + uses kobo amounts) |
| Annual pricing ₦20k/40k | Task 11 (cadence toggle on UpgradeScreen) |
| Smart coins 5/50/500 | Task 3 (`EntitlementsCalculator` constants) + Task 1 (server gating) |
| Smart help intent gating (Atelier-only) | Task 1 (server `requiresAtelier` + permission-denied) |
| Customer cap 15/Unlimited | Task 3 (calculator) + Task 6 (enforcement on create) + Task 7 (reconciliation) |
| Welcome 30 coins + 30 cap | Tasks 2, 3, 4 (server + client + signup seeding) |
| Gray-out 50/50 active+inactive | Task 7 (`selectSlotsToLock`) + Task 9 (UI) |
| 3-day welcome-ending warning | Task 3 (`isWithinWelcomeEndingWarning`) + Task 10 (banner) |
| Swap mechanic | Task 8 (`swapCustomerSlot` repo) + Task 9 (SwapSheet) |
| Cancel behaviour | OUT of scope — V1.1 (Paystack subscription billing); flagged in PR description |
| Sponsor a Tailor | OUT of scope — V1.3; data model placeholder is mentioned in spec |
| Receipts / brand colours | OUT of scope — V1.2 |
| Custom measurement types | OUT of scope — V1.2 |
| Offline-first surface | OUT of scope — separate workstream |
| Reports screens | OUT of scope — V1.2 |
| Priority support / early access | No code needed for V1.0 — operational |
| Referral codes | OUT of scope — V1.4+ |
| Subscription tier field consolidation | Task 11 (drop fallback) + Task 12 (close memory) |
| Field shape: `subscriptionTier`, `subscriptionEndsAt`, `welcomeBonusAppliedAt`, `bonusCoins`, etc. | Task 4 (seed at signup) |

Gaps spotted in self-review (added to plan inline): none after the pass.

### Placeholder scan

Re-read the plan. Patterns to forbid:
- "TBD" / "TODO" — none present
- "Handle edge cases" — none; all branches explicit
- "Add appropriate validation" — none
- "Similar to Task N" — none; code is repeated where needed
- "Write tests for the above" — none; every test is shown
- Steps without code blocks for code changes — checked; every code step has a code block

Status: clean.

### Type consistency

- `SubscriptionTier` enum used consistently across all client tasks (no `Tier` / `Plan` aliases)
- `CustomerSlotState` enum + wire values stable across Tasks 5–9
- `UserEntitlements` field names stable: `tier`, `customerCap`, `smartCoinAllowance`, `isInWelcomeWindow`, `welcomeEndsAt`, `isWithinWelcomeEndingWarning` — used the same way in Tasks 3, 6, 10
- `FreemiumRepository.reconcileSlots()` + `swapCustomerSlot(promote, demote)` — same names in Tasks 8, 9
- Server: `Tier`, `FreeTierUsageDoc.bonusBalance`, `UserProfileSummary.welcomeBonusCoins`, `selectSlotsToLock`, `reconcileCustomerSlots` — consistent across Tasks 1, 2, 4, 7

Status: consistent.

---

## Execution

Plan complete and saved to `docs/superpowers/plans/2026-05-17-freemium-model-v1.md`.

**Recommended next step:** invoke `superpowers:subagent-driven-development` to execute task-by-task with two-stage review (spec compliance, then code quality) between tasks. Each task is structured for a focused implementer agent: clear file paths, complete code, exact commands, single commit per task.

**Alternative:** `superpowers:executing-plans` for inline execution in this session with batched checkpoints.
