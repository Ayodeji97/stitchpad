# Smart Grow — Slice 0: Shared Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the existing Smart Suggestions infrastructure so Slices 1–4 (postcaption, sharecard, referral, contentplan) can share the AI client, the quota cache, and the language enum — and extend the server-side free-tier counter to tag each increment with a `featureKey` for per-feature analytics. Pure refactor + back-compatible schema extension; no user-visible behavior change.

**Architecture:**
- Kotlin: promote `SmartUsageStore`, `InMemorySmartUsageStore`, `DraftLanguage`, `FunctionsCaller`, `GitLiveFunctionsCaller` from `feature/smart/` into a new `core/smartinfra/` package with `domain/{quota,language}/` and `data/{quota,ai}/` subpackages. Add a `SmartFeatureKey` enum at the same location.
- Server: extend `FreeTierUsageDoc` with an optional `perFeature: Record<string, number>` map. Extend `reconcileUsage` to take a `featureKey` and increment both `count` and `perFeature[featureKey]` atomically. Thread `'draft'` through the existing `draftMessage` handler. Existing usage docs without `perFeature` are read as `perFeature: {}` (back-compat).
- All four moves are verified by the existing test suites continuing to pass after import updates. New server-side behavior gets TDD coverage.

**Tech Stack:** Kotlin Multiplatform + Compose Multiplatform (`composeApp` module), Koin DI, Firebase via GitLive SDK, Node 18 + Firebase Functions v1 (`functions/`), Jest for server tests.

**Spec:** `docs/superpowers/specs/2026-05-16-smart-grow-design.md` (commit `e9f90ff`). See §"Slice 0 — Refactor".

**Naming note (deviation from spec):** The spec writes the new package as `core/smartinfra/`, but Kotlin packages cannot contain hyphens and the existing convention in this codebase is single-word topic packages (see `core/sharing`, `core/util`, `core/media`). This plan uses **`core/smartinfra/`** consistently for both the directory and the package (`com.danzucker.stitchpad.core.smartinfra.*`). The change is implementation-level only; no external interface is affected.

**Commit cadence:** ~5 commits — (1) server-side perFeature + tests, (2) Kotlin `SmartFeatureKey` enum, (3) `DraftLanguage` move, (4) `SmartUsageStore` + `InMemorySmartUsageStore` move, (5) `FunctionsCaller` + `GitLiveFunctionsCaller` move. Final task is a manual smoke (no commit).

---

## File Structure

### New files (Kotlin)

| Path | Responsibility |
|---|---|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota/SmartFeatureKey.kt` | Enum naming each Smart-feature consumer of the shared quota. Wire names match the server-side `perFeature` keys. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota/SmartUsageStore.kt` | (Moved) interface for the process-local cache of remaining free-tier quota. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/language/DraftLanguage.kt` | (Moved) enum: English / Pidgin. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/quota/InMemorySmartUsageStore.kt` | (Moved) the in-memory `SmartUsageStore` impl with auth-state reset. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/FunctionsCaller.kt` | (Moved) thin interface for invoking Firebase callable functions. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/GitLiveFunctionsCaller.kt` | (Moved) `FunctionsCaller` impl backed by GitLive's `FirebaseFunctions`. |

### Deleted files (Kotlin)

| Path | Reason |
|---|---|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/SmartUsageStore.kt` | Replaced by `core/smartinfra/domain/quota/SmartUsageStore.kt`. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftLanguage.kt` | Replaced by `core/smartinfra/domain/language/DraftLanguage.kt`. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/InMemorySmartUsageStore.kt` | Replaced by `core/smartinfra/data/quota/InMemorySmartUsageStore.kt`. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/FunctionsCaller.kt` | Replaced by `core/smartinfra/data/ai/FunctionsCaller.kt`. |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/GitLiveFunctionsCaller.kt` | Replaced by `core/smartinfra/data/ai/GitLiveFunctionsCaller.kt`. |

### Modified files (Kotlin — imports only, no behavior change)

| Path |
|---|
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SmartModule.kt` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardState.kt` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModel.kt` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartFunctionsRepository.kt` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftMessageRequest.kt` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageState.kt` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModel.kt` |
| `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/components/LanguageToggle.kt` |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardViewModelTest.kt` |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartFunctionsRepositoryTest.kt` |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/data/mapper/DraftMessageMappersTest.kt` |
| `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModelTest.kt` |

### Modified files (TypeScript)

| Path | Change |
|---|---|
| `functions/src/smart/types.ts` | Add optional `perFeature?: Record<string, number>` to `FreeTierUsageDoc`. Add new `SmartFeatureKey` string-literal type. |
| `functions/src/smart/freeTierCounter.ts` | `reconcileUsage` takes a required `featureKey` parameter and increments `perFeature[featureKey]` atomically with `count`. Initialize `perFeature` to `{}` on the fresh-doc and rollover branches. |
| `functions/src/smart/draftMessage.ts` | Pass `'draft'` to `reconcileUsage` via `reserveFreeTierSlot`. |
| `functions/src/__tests__/smart/freeTierCounter.test.ts` | Update existing call sites to pass `'draft'`. Add new tests for `perFeature` increment, rollover, and back-compat with missing `perFeature`. |
| `functions/src/__tests__/smart/draftMessage.test.ts` | Add one contract test verifying `perFeature.draft` is incremented after a successful draft. |

---

## Task 1: Server — extend types schema

**Files:**
- Modify: `functions/src/smart/types.ts`

- [ ] **Step 1: Update `FreeTierUsageDoc` with `perFeature` field and add `SmartFeatureKey` type**

Open `functions/src/smart/types.ts`. Replace the `FreeTierUsageDoc` interface and add a new `SmartFeatureKey` type. Final file contents:

```ts
/**
 * Request/response types for the smartDraftMessage callable function.
 *
 * Kept in a shared module so promptBuilder + draftMessage handler + tests
 * all reference the same shapes.
 */

export type IntentType =
  | 'balance_reminder'
  | 'pickup_ready'
  | 'follow_up'
  | 'custom_note';

export type Language = 'en' | 'pcm'; // pcm = Nigerian Pidgin (ISO 639-3)

/**
 * Wire-name string for each Smart-feature consumer of the shared monthly
 * quota counter. Stored as keys in FreeTierUsageDoc.perFeature so we can
 * report which feature is consuming quota without a schema migration.
 *
 * Keep in sync with the Kotlin SmartFeatureKey enum in
 * core/smartinfra/domain/quota/SmartFeatureKey.kt.
 */
export type SmartFeatureKey =
  | 'draft'
  | 'postcaption'
  | 'referral_msg'
  | 'referral_bio'
  | 'contentplan_regen';

export interface DraftMessageRequest {
  intentType: IntentType;
  customerId: string;
  orderId: string;
  language: Language;
  customNotes?: string;
}

export interface DraftMessageResponse {
  draftText: string;
  remainingFreeQuota: number | null; // null = premium tier
}

/**
 * Snapshot of customer + order data fetched server-side and passed to the
 * prompt builder. Decouples the prompt logic from Firestore document shapes.
 */
export interface DraftContext {
  customerFirstName: string;
  garmentLabel: string;
  depositFormatted: string;
  balanceFormatted: string;
  deadlineFormatted: string; // already-formatted string per the tailor's locale
}

/**
 * User profile fields the Smart layer cares about. Read from the user
 * document itself at `users/{uid}` — there is no separate `profile` subdoc.
 * Missing `tier` field defaults to 'free'.
 */
export interface UserProfileSummary {
  tier: 'free' | 'premium';
}

/**
 * Free-tier usage doc at `users/{uid}/usage/smart_drafts`.
 *
 * `perFeature` is optional on read for back-compat with docs created
 * before the Smart Grow rollout. New writes always include it.
 */
export interface FreeTierUsageDoc {
  monthYear: string; // YYYY-MM
  count: number;
  limit: number;
  perFeature?: Record<string, number>;
}
```

- [ ] **Step 2: Run TypeScript compile to verify types are valid**

Run: `cd functions && npm run build`
Expected: completes with no errors.

---

## Task 2: Server — TDD `reconcileUsage(featureKey)` behavior

**Files:**
- Modify: `functions/src/__tests__/smart/freeTierCounter.test.ts`
- Modify: `functions/src/smart/freeTierCounter.ts`

- [ ] **Step 1: Write the new failing tests**

Open `functions/src/__tests__/smart/freeTierCounter.test.ts`. **Update every existing call** to `reconcileUsage({ existing, now })` so it passes `featureKey: 'draft'` as a second argument: `reconcileUsage({ existing, now }, 'draft')`. Then **add** a new describe block at the bottom of the file, before its closing brace. Final test file:

```ts
import { FreeTierUsageDoc, SmartFeatureKey } from '../../smart/types';
import { reconcileUsage, isExhausted, USAGE_DEFAULT_LIMIT } from '../../smart/freeTierCounter';

const today = new Date('2026-05-16T10:00:00Z');

describe('reconcileUsage', () => {
  it('initializes a fresh doc when none exists', () => {
    const next = reconcileUsage({ existing: null, now: today }, 'draft');
    expect(next).toEqual({
      monthYear: '2026-05',
      count: 1,
      limit: USAGE_DEFAULT_LIMIT,
      perFeature: { draft: 1 },
    });
  });

  it('increments count when same month', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-05',
      count: 3,
      limit: 5,
      perFeature: { draft: 3 },
    };
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next).toEqual({
      monthYear: '2026-05',
      count: 4,
      limit: 5,
      perFeature: { draft: 4 },
    });
  });

  it('resets count to 1 on month rollover', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-04',
      count: 5,
      limit: 5,
      perFeature: { draft: 5 },
    };
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next).toEqual({
      monthYear: '2026-05',
      count: 1,
      limit: 5,
      perFeature: { draft: 1 },
    });
  });

  it('preserves a custom limit on month rollover (testers override)', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-04',
      count: 5,
      limit: 100,
      perFeature: { draft: 5 },
    };
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next).toEqual({
      monthYear: '2026-05',
      count: 1,
      limit: 100,
      perFeature: { draft: 1 },
    });
  });

  it('preserves a custom limit on same-month increment', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-05',
      count: 50,
      limit: 100,
      perFeature: { draft: 50 },
    };
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next.limit).toBe(100);
  });

  it('zero-pads single-digit months in monthYear', () => {
    const earlyJan = new Date('2026-01-05T10:00:00Z');
    const next = reconcileUsage({ existing: null, now: earlyJan }, 'draft');
    expect(next.monthYear).toBe('2026-01');
  });
});

describe('reconcileUsage perFeature tagging', () => {
  it('initializes perFeature with featureKey=1 on a fresh doc', () => {
    const next = reconcileUsage({ existing: null, now: today }, 'postcaption');
    expect(next.perFeature).toEqual({ postcaption: 1 });
  });

  it('increments the specified feature key without touching others', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-05',
      count: 4,
      limit: 5,
      perFeature: { draft: 3, postcaption: 1 },
    };
    const next = reconcileUsage({ existing, now: today }, 'postcaption');
    expect(next.perFeature).toEqual({ draft: 3, postcaption: 2 });
    expect(next.count).toBe(5);
  });

  it('back-compat: treats missing perFeature on existing doc as empty', () => {
    // A doc written before perFeature was added — count is set but the
    // map is absent. New increment must still record the feature key.
    const existing = {
      monthYear: '2026-05',
      count: 2,
      limit: 5,
    } as FreeTierUsageDoc;
    const next = reconcileUsage({ existing, now: today }, 'draft');
    expect(next.perFeature).toEqual({ draft: 1 });
    expect(next.count).toBe(3);
  });

  it('resets perFeature to single-entry on month rollover', () => {
    const existing: FreeTierUsageDoc = {
      monthYear: '2026-04',
      count: 5,
      limit: 5,
      perFeature: { draft: 3, postcaption: 2 },
    };
    const next = reconcileUsage({ existing, now: today }, 'referral_msg');
    expect(next.perFeature).toEqual({ referral_msg: 1 });
  });

  it('accepts each SmartFeatureKey value', () => {
    const keys: SmartFeatureKey[] = [
      'draft',
      'postcaption',
      'referral_msg',
      'referral_bio',
      'contentplan_regen',
    ];
    for (const key of keys) {
      const next = reconcileUsage({ existing: null, now: today }, key);
      expect(next.perFeature?.[key]).toBe(1);
    }
  });
});

describe('isExhausted', () => {
  it('false when count is below limit', () => {
    expect(isExhausted({ monthYear: '2026-05', count: 4, limit: 5 })).toBe(false);
  });

  it('true when count equals limit', () => {
    expect(isExhausted({ monthYear: '2026-05', count: 5, limit: 5 })).toBe(true);
  });

  it('true when count exceeds limit (defensive)', () => {
    expect(isExhausted({ monthYear: '2026-05', count: 6, limit: 5 })).toBe(true);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd functions && npm test -- freeTierCounter`
Expected: TypeScript compile error or runtime FAIL — `reconcileUsage` currently has signature `(args)` not `(args, featureKey)`. All the `reconcileUsage` calls in tests fail to compile.

- [ ] **Step 3: Implement the new `reconcileUsage` signature**

Open `functions/src/smart/freeTierCounter.ts`. Replace the entire file with:

```ts
import { FreeTierUsageDoc, SmartFeatureKey } from './types';

export const USAGE_DEFAULT_LIMIT = 5;

export interface ReconcileArgs {
  existing: FreeTierUsageDoc | null;
  now: Date;
}

/**
 * Pure function — given the current usage doc (or null if absent), the
 * current time, and which Smart feature is consuming the slot, returns the
 * new doc state after recording one more usage.
 *
 * Handles month rollover: if existing.monthYear differs from now's
 * YYYY-MM, count resets to 1 (counting the about-to-be-recorded usage)
 * and perFeature resets to { [featureKey]: 1 }.
 *
 * Handles back-compat: docs written before perFeature was added have
 * no `perFeature` field; on the next call we initialize it with the
 * current feature key set to 1 (existing total `count` is left alone).
 *
 * Preserves the existing limit (testers can override per-user via Firestore
 * console). When initializing fresh, uses USAGE_DEFAULT_LIMIT.
 */
export function reconcileUsage(
  args: ReconcileArgs,
  featureKey: SmartFeatureKey,
): FreeTierUsageDoc {
  const { existing, now } = args;
  const currentMonthYear = formatMonthYear(now);

  if (existing === null) {
    return {
      monthYear: currentMonthYear,
      count: 1,
      limit: USAGE_DEFAULT_LIMIT,
      perFeature: { [featureKey]: 1 },
    };
  }

  if (existing.monthYear !== currentMonthYear) {
    return {
      monthYear: currentMonthYear,
      count: 1,
      limit: existing.limit,
      perFeature: { [featureKey]: 1 },
    };
  }

  const prevPerFeature = existing.perFeature ?? {};
  return {
    monthYear: existing.monthYear,
    count: existing.count + 1,
    limit: existing.limit,
    perFeature: {
      ...prevPerFeature,
      [featureKey]: (prevPerFeature[featureKey] ?? 0) + 1,
    },
  };
}

export function isExhausted(doc: FreeTierUsageDoc): boolean {
  return doc.count >= doc.limit;
}

function formatMonthYear(d: Date): string {
  const year = d.getUTCFullYear();
  const month = String(d.getUTCMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
}
```

- [ ] **Step 4: Run the tests to verify they all pass**

Run: `cd functions && npm test -- freeTierCounter`
Expected: PASS — all 15 tests (10 reconcileUsage + 3 perFeature + 3 isExhausted) green.

---

## Task 3: Server — thread `'draft'` featureKey through the draftMessage handler

**Files:**
- Modify: `functions/src/smart/draftMessage.ts:138-150` (the `reserveFreeTierSlot` implementation)

- [ ] **Step 1: Update the `reserveFreeTierSlot` production implementation**

Open `functions/src/smart/draftMessage.ts`. Find the `reserveFreeTierSlot` arrow function inside `productionIO` (around line 138). Update its body so the `reconcileUsage` call passes `'draft'` as the featureKey:

```ts
    reserveFreeTierSlot: async (now: Date): Promise<FreeTierReservation> => {
      const ref = db.doc(`users/${uid}/usage/smart_drafts`);
      return db.runTransaction(async (tx) => {
        const snap = await tx.get(ref);
        const existing = snap.exists ? (snap.data() as FreeTierUsageDoc) : null;
        const next = reconcileUsage({ existing, now }, 'draft');
        if (existing !== null && isExhausted(existing) && existing.monthYear === next.monthYear) {
          return { exhausted: true } as const;
        }
        tx.set(ref, next);
        return { exhausted: false, usage: next } as const;
      });
    },
```

The only change is `reconcileUsage({ existing, now })` → `reconcileUsage({ existing, now }, 'draft')`.

- [ ] **Step 2: Run the TypeScript build to verify nothing else broke**

Run: `cd functions && npm run build`
Expected: completes with no errors.

---

## Task 4: Server — contract test that draftMessage increments `perFeature.draft`

**Files:**
- Modify: `functions/src/__tests__/smart/draftMessage.test.ts`

- [ ] **Step 1: Read the existing test file to find the success-path test**

Run: `cd functions && grep -n "describe\|it(" src/__tests__/smart/draftMessage.test.ts | head -30`

Identify the test that asserts a successful draft response (search for `'PASS'`, `'returns'`, or `'draftText'`). This is where we'll add a `perFeature` assertion on the captured fake-IO state.

- [ ] **Step 2: Add a perFeature assertion to the existing happy-path test**

Inside the existing `'returns drafted text'` (or equivalent) test, after the `await draftMessageHandler(...)` call, add an assertion that checks the captured next-usage doc has `perFeature.draft === 1`. The exact insertion depends on how the existing fake-IO is structured. Use this pattern — if the fake-IO already captures `reserveFreeTierSlot`'s call args:

```ts
expect(reservedUsage?.perFeature).toEqual({ draft: 1 });
```

If the existing test doesn't capture the reservation result yet, add a small capture variable. Concrete shape: declare `let capturedReservation: FreeTierUsageDoc | null = null;` at the top of the test, then inside the fake `reserveFreeTierSlot` implementation set `capturedReservation = next;` before returning, and assert at the end.

- [ ] **Step 3: Run the draftMessage tests to verify the new assertion passes**

Run: `cd functions && npm test -- draftMessage`
Expected: PASS — existing tests still green; the new `perFeature.draft` assertion passes because Task 3 wired `'draft'` through.

- [ ] **Step 4: Run the full functions test suite**

Run: `cd functions && npm test`
Expected: PASS — all server tests green.

---

## Task 5: Commit the server-side changes

**Files:**
- Files touched: `functions/src/smart/types.ts`, `functions/src/smart/freeTierCounter.ts`, `functions/src/smart/draftMessage.ts`, `functions/src/__tests__/smart/freeTierCounter.test.ts`, `functions/src/__tests__/smart/draftMessage.test.ts`

- [ ] **Step 1: Verify the working tree only has these files**

Run: `git status`
Expected: only the 5 files listed above are modified.

- [ ] **Step 2: Stage and commit**

Run:
```bash
git add functions/src/smart/types.ts functions/src/smart/freeTierCounter.ts functions/src/smart/draftMessage.ts functions/src/__tests__/smart/freeTierCounter.test.ts functions/src/__tests__/smart/draftMessage.test.ts
git commit -m "$(cat <<'EOF'
feat(smart): tag free-tier counter increments with featureKey

Extend FreeTierUsageDoc with an optional perFeature map and update
reconcileUsage to record which Smart feature consumed each slot. The
existing draftMessage handler now tags increments as 'draft'.

Back-compat: docs written before this rollout have no perFeature
field and are treated as an empty map on the next read.

Prepares the shared quota counter for the four upcoming Smart Grow
features (postcaption, referral_msg, referral_bio, contentplan_regen)
without a schema migration.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: commit created.

---

## Task 6: Kotlin — add `SmartFeatureKey` enum

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota/SmartFeatureKey.kt`

- [ ] **Step 1: Create the directory**

Run:
```bash
mkdir -p composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota
mkdir -p composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/language
mkdir -p composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/quota
mkdir -p composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai
```

Expected: directories created (silent success).

- [ ] **Step 2: Write the `SmartFeatureKey` enum**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota/SmartFeatureKey.kt`:

```kotlin
package com.danzucker.stitchpad.core.smartinfra.domain.quota

/**
 * Identifies which Smart Suggestions feature consumed a free-tier quota
 * slot. The [wireName] is the lowercase string written to Firestore at
 * `users/{uid}/usage/smart_drafts.perFeature[wireName]` and recognized by
 * the server's `SmartFeatureKey` TypeScript type.
 *
 * Keep wire names in sync with `functions/src/smart/types.ts`.
 */
enum class SmartFeatureKey(val wireName: String) {
    Draft("draft"),
    PostCaption("postcaption"),
    ReferralMessage("referral_msg"),
    ReferralBio("referral_bio"),
    ContentPlanRegen("contentplan_regen"),
}
```

Note: see the **Naming note** at the top of this plan — the directory and the Kotlin package both use single-word `smartinfra` (no hyphen, no underscore) to match the existing `core/sharing` / `core/util` / `core/media` convention.

- [ ] **Step 3: Verify the file compiles**

Run: `./gradlew :composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

Run:
```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota/SmartFeatureKey.kt
git commit -m "$(cat <<'EOF'
feat(smart): add SmartFeatureKey enum in core/smartinfra

Names every Smart Suggestions feature that consumes the shared free-tier
quota slot. Wire names match the server's perFeature keys in
functions/src/smart/types.ts.

First file in the new core/smartinfra package; subsequent commits in
this slice move SmartUsageStore, DraftLanguage, and FunctionsCaller in
alongside it.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: commit created.

---

## Task 7: Kotlin — move `DraftLanguage` to `core/smartinfra`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/language/DraftLanguage.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftLanguage.kt`
- Modify (import only): see list below.

- [ ] **Step 1: Create the new file at the new path**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/language/DraftLanguage.kt`:

```kotlin
package com.danzucker.stitchpad.core.smartinfra.domain.language

/**
 * Output language for a Smart Suggestions generation. Pidgin = Nigerian
 * Pidgin (ISO 639-3 `pcm`).
 *
 * Shared across all Smart features (Draft Message, Post Caption,
 * Referral Message, Referral Bio). Wire names match the server's
 * `Language` type in functions/src/smart/types.ts.
 */
enum class DraftLanguage(val wireName: String) {
    English("en"),
    Pidgin("pcm"),
}
```

- [ ] **Step 2: Update imports in all consumers**

Run this exact replacement command from the repo root:

```bash
grep -rl "import com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage" composeApp/src \
  | xargs sed -i '' 's|com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage|com.danzucker.stitchpad.core.smartinfra.domain.language.DraftLanguage|g'
```

Expected: silent success.

- [ ] **Step 3: Verify the import update found all consumers**

Run: `grep -rl "feature.smart.domain.model.DraftLanguage" composeApp/src`
Expected: no matches (empty output).

Run: `grep -rl "core.smartinfra.domain.language.DraftLanguage" composeApp/src | sort`
Expected: at least these 6 files —
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartFunctionsRepository.kt` (or its dto if that's where the type leaks)
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftMessageRequest.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageState.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/components/LanguageToggle.kt`
- `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModelTest.kt` (or whichever tests import it)

- [ ] **Step 4: Delete the old file**

Run: `rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftLanguage.kt`
Expected: silent success.

- [ ] **Step 5: Run Android compile to verify the move**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run iOS compile to catch KMP-specific surprises**

Run: `./gradlew :composeApp:compileKotlinIosX64`
Expected: BUILD SUCCESSFUL. (Per the `feedback_kmp_jvm_only_apis` memory, always compile iOS before declaring a refactor done.)

- [ ] **Step 7: Run the smart and dashboard test suites**

Run: `./gradlew :composeApp:commonTest --tests 'com.danzucker.stitchpad.feature.smart.*' --tests 'com.danzucker.stitchpad.feature.dashboard.*'`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 8: Commit**

Run:
```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/language/DraftLanguage.kt
git add -u composeApp/src
git commit -m "$(cat <<'EOF'
refactor(smart): move DraftLanguage to core/smartinfra

Promotes the shared output-language enum out of feature/smart so the
four upcoming Smart Grow features (postcaption, sharecard, referral,
contentplan) can depend on the same source. No behavior change; the
move is verified by the existing smart + dashboard test suites
continuing to pass.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: commit created.

---

## Task 8: Kotlin — move `SmartUsageStore` and `InMemorySmartUsageStore` to `core/smartinfra`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota/SmartUsageStore.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/quota/InMemorySmartUsageStore.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/SmartUsageStore.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/InMemorySmartUsageStore.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SmartModule.kt`, plus all consumers via the sed below.

- [ ] **Step 1: Create the new `SmartUsageStore` interface**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota/SmartUsageStore.kt`:

```kotlin
package com.danzucker.stitchpad.core.smartinfra.domain.quota

import kotlinx.coroutines.flow.StateFlow

/**
 * In-process cache for the last-known remaining free-tier Smart-feature
 * quota.
 *
 * Updated by each Smart-feature ViewModel after a successful generation
 * (it reads the fresh count from the Cloud Function response) and
 * observed by the dashboard's SmartSectionCard counter chip so the chip
 * stays in sync without an extra server round-trip on every dashboard
 * mount.
 *
 * The cache is process-local — it resets when the app process dies.
 * That's acceptable for V1: the next successful generation repopulates
 * it, and on a cold start the chip simply stays hidden until the user
 * triggers their first generation of the session.
 *
 * The cache holds a single total across all Smart features, matching
 * the server-side shared monthly counter. Per-feature breakdowns live
 * in the server's perFeature map and are not surfaced in the cache.
 */
interface SmartUsageStore {
    /**
     * null = unknown (no generation yet this session, or premium tier).
     * Non-null = the count returned by the most recent successful generation.
     */
    val remainingFreeQuota: StateFlow<Int?>

    fun update(remaining: Int?)
}
```

- [ ] **Step 2: Create the new `InMemorySmartUsageStore` impl**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/quota/InMemorySmartUsageStore.kt`:

```kotlin
package com.danzucker.stitchpad.core.smartinfra.data.quota

import com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Process-local cache of the signed-in user's remaining free-tier Smart
 * quota. Resets to `null` whenever the active user changes (including
 * sign-out) so a previous user's quota chip / "out of free drafts" hint
 * never leaks into the next user's session in the same process.
 */
internal class InMemorySmartUsageStore(
    auth: FirebaseAuth,
    scope: CoroutineScope,
) : SmartUsageStore {
    private val state = MutableStateFlow<Int?>(null)
    override val remainingFreeQuota: StateFlow<Int?> = state.asStateFlow()

    init {
        scope.launch {
            auth.authStateChanged
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { state.value = null }
        }
    }

    override fun update(remaining: Int?) {
        state.value = remaining
    }
}
```

- [ ] **Step 3: Update consumer imports for `SmartUsageStore`**

Run:
```bash
grep -rl "com.danzucker.stitchpad.feature.smart.domain.SmartUsageStore" composeApp/src \
  | xargs sed -i '' 's|com.danzucker.stitchpad.feature.smart.domain.SmartUsageStore|com.danzucker.stitchpad.core.smartinfra.domain.quota.SmartUsageStore|g'
```

Expected: silent success.

- [ ] **Step 4: Update consumer imports for `InMemorySmartUsageStore`**

Run:
```bash
grep -rl "com.danzucker.stitchpad.feature.smart.data.InMemorySmartUsageStore" composeApp/src \
  | xargs sed -i '' 's|com.danzucker.stitchpad.feature.smart.data.InMemorySmartUsageStore|com.danzucker.stitchpad.core.smartinfra.data.quota.InMemorySmartUsageStore|g'
```

Expected: silent success.

- [ ] **Step 5: Verify no stale imports remain**

Run:
```bash
grep -rE "feature\.smart\.(domain\.SmartUsageStore|data\.InMemorySmartUsageStore)" composeApp/src
```
Expected: no matches (empty output).

- [ ] **Step 6: Delete the old files**

Run:
```bash
rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/SmartUsageStore.kt
rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/InMemorySmartUsageStore.kt
```
Expected: silent success.

- [ ] **Step 7: Run Android compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run iOS compile**

Run: `./gradlew :composeApp:compileKotlinIosX64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run the smart and dashboard test suites**

Run: `./gradlew :composeApp:commonTest --tests 'com.danzucker.stitchpad.feature.smart.*' --tests 'com.danzucker.stitchpad.feature.dashboard.*'`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 10: Commit**

Run:
```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/domain/quota/SmartUsageStore.kt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/quota/InMemorySmartUsageStore.kt
git add -u composeApp/src
git commit -m "$(cat <<'EOF'
refactor(smart): move SmartUsageStore + impl to core/smartinfra

Promotes the in-process quota cache (interface + InMemory impl) out of
feature/smart so all Smart features (Draft Message today, the four
Smart Grow features next) read and write a single shared cache. No
behavior change; the SmartModule still wires the same singleton.

Verified by the existing smart + dashboard test suites continuing to
pass on Android and iOS.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: commit created.

---

## Task 9: Kotlin — move `FunctionsCaller` and `GitLiveFunctionsCaller` to `core/smartinfra`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/FunctionsCaller.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/GitLiveFunctionsCaller.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/FunctionsCaller.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/GitLiveFunctionsCaller.kt`
- Modify (import only): all consumers via sed.

- [ ] **Step 1: Read the existing `FunctionsCaller` files to capture their contents**

Run:
```bash
cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/FunctionsCaller.kt
cat composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/GitLiveFunctionsCaller.kt
```

Note the contents — you'll paste them verbatim into the new files, changing only the `package` line.

- [ ] **Step 2: Create the new `FunctionsCaller` interface file**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/FunctionsCaller.kt`. Use the exact body from the file you just read, but change the `package` line at the top to:

```kotlin
package com.danzucker.stitchpad.core.smartinfra.data.ai
```

Keep all imports, declarations, and bodies identical to the original.

- [ ] **Step 3: Create the new `GitLiveFunctionsCaller` impl file**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/GitLiveFunctionsCaller.kt`. Use the exact body from the file you read in Step 1, but change the `package` line at the top to:

```kotlin
package com.danzucker.stitchpad.core.smartinfra.data.ai
```

If the file imports the sibling `FunctionsCaller` interface explicitly (it might or might not, since they're in the same package), no further changes are needed.

- [ ] **Step 4: Update consumer imports for `FunctionsCaller`**

Run:
```bash
grep -rl "com.danzucker.stitchpad.feature.smart.data.FunctionsCaller" composeApp/src \
  | xargs sed -i '' 's|com.danzucker.stitchpad.feature.smart.data.FunctionsCaller|com.danzucker.stitchpad.core.smartinfra.data.ai.FunctionsCaller|g'
```

Expected: silent success.

- [ ] **Step 5: Update consumer imports for `GitLiveFunctionsCaller`**

Run:
```bash
grep -rl "com.danzucker.stitchpad.feature.smart.data.GitLiveFunctionsCaller" composeApp/src \
  | xargs sed -i '' 's|com.danzucker.stitchpad.feature.smart.data.GitLiveFunctionsCaller|com.danzucker.stitchpad.core.smartinfra.data.ai.GitLiveFunctionsCaller|g'
```

Expected: silent success.

- [ ] **Step 6: Verify no stale imports remain**

Run:
```bash
grep -rE "feature\.smart\.data\.(FunctionsCaller|GitLiveFunctionsCaller)" composeApp/src
```
Expected: no matches (empty output).

- [ ] **Step 7: Delete the old files**

Run:
```bash
rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/FunctionsCaller.kt
rm composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/GitLiveFunctionsCaller.kt
```
Expected: silent success.

- [ ] **Step 8: Run Android compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run iOS compile**

Run: `./gradlew :composeApp:compileKotlinIosX64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Run the smart test suite (the only consumer of FunctionsCaller in V1)**

Run: `./gradlew :composeApp:commonTest --tests 'com.danzucker.stitchpad.feature.smart.*'`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 11: Commit**

Run:
```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/FunctionsCaller.kt
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/smartinfra/data/ai/GitLiveFunctionsCaller.kt
git add -u composeApp/src
git commit -m "$(cat <<'EOF'
refactor(smart): move FunctionsCaller + GitLive impl to core/smartinfra

Promotes the thin callable-functions client out of feature/smart so the
four upcoming Smart Grow features can invoke their own callables
(postcaption, referralMessage, generateBio, etc.) through the same
abstraction. No behavior change; SmartModule still wires the same
singleton.

Verified on Android + iOS with the existing smart test suite.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Expected: commit created.

---

## Task 10: Full-suite verification and manual smoke

**Files:**
- No code changes — verification only.

- [ ] **Step 1: Run the full functions test suite**

Run: `cd functions && npm test`
Expected: PASS — all server tests green.

- [ ] **Step 2: Run the full Kotlin test suite**

Run: `./gradlew :composeApp:allTests`
Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 3: Run detekt to catch any style regressions from the moves**

Run: `./gradlew detekt`
Expected: BUILD SUCCESSFUL with no detekt issues.

- [ ] **Step 4: Manual smoke — verify Draft Message still works end-to-end**

Per the project's QA-smoke-tests requirement ([[feedback-qa-smoke-tests]]), run this on a real device or simulator before declaring Slice 0 done:

1. Build and run the Android app (or iOS sim per [[reference-test-environment]]).
2. Sign in with a Fola test account that has at least one customer and one open order.
3. From the Dashboard, scroll to the Smart Suggestions section card.
4. Tap the **Draft Message** tile.
5. Pick a customer, pick an order, pick `Balance reminder`, pick English, leave notes blank.
6. Tap **Generate draft**.
7. Verify: a draft appears within ~3 seconds.
8. Open Firebase Console → Firestore → `users/<uid>/usage/smart_drafts`.
9. Verify the doc now has BOTH `count: <n>` AND `perFeature: { draft: <n> }` (matching). This is the contract test for the new behavior — visually confirmed on real data.
10. Verify the Dashboard's free-tier counter chip shows the new remaining quota.
11. Sign out, sign back in with a Gabby test account, and verify the counter resets to `null` (the cache is per-user).

- [ ] **Step 5: Final git log review**

Run: `git log --oneline main..HEAD`
Expected: 5 commits, in this order —
1. `feat(smart): tag free-tier counter increments with featureKey`
2. `feat(smart): add SmartFeatureKey enum in core/smartinfra`
3. `refactor(smart): move DraftLanguage to core/smartinfra`
4. `refactor(smart): move SmartUsageStore + impl to core/smartinfra`
5. `refactor(smart): move FunctionsCaller + GitLive impl to core/smartinfra`

If any commits are missing or out of order, the engineer should fix the history before pushing.

- [ ] **Step 6: Push and open PR**

Per [[feedback-pr-workflow]], all work goes through a PR — no direct push to main.

Run:
```bash
git push -u origin HEAD
gh pr create --title "refactor(smart): promote shared infra to core/smartinfra (Slice 0)" --body "$(cat <<'EOF'
## Summary

Slice 0 of the Smart Grow rollout per `docs/superpowers/specs/2026-05-16-smart-grow-design.md` (commit \`e9f90ff\`). Pure refactor + back-compatible server schema extension. Sets up the four upcoming Grow features (postcaption, sharecard, referral, contentplan) to share a single AI client, quota cache, and language enum.

**Server side**
- \`FreeTierUsageDoc\` gains an optional \`perFeature: Record<string, number>\` map
- \`reconcileUsage\` now takes a \`featureKey\` and tags every increment
- Existing \`draftMessage\` handler passes \`'draft'\`
- Back-compat: docs written before this rollout have no \`perFeature\` and are treated as empty on first read

**Client side**
- New \`core/smartinfra/\` package
- Moved: \`SmartUsageStore\`, \`InMemorySmartUsageStore\`, \`DraftLanguage\`, \`FunctionsCaller\`, \`GitLiveFunctionsCaller\`
- New: \`SmartFeatureKey\` enum
- No behavior change; existing tests pass on Android + iOS

## Test plan
- [x] \`cd functions && npm test\` — server tests green (existing + new perFeature cases)
- [x] \`./gradlew :composeApp:allTests\` — Kotlin tests green on commonTest
- [x] \`./gradlew :composeApp:compileKotlinIosX64\` — iOS compile clean
- [x] \`./gradlew detekt\` — no style regressions
- [x] Manual smoke (per [[feedback-qa-smoke-tests]]): Draft Message end-to-end on Android still works; Firestore \`users/{uid}/usage/smart_drafts\` shows \`count\` and \`perFeature.draft\` matching after a successful draft
- [x] Sign-out / sign-in flow clears the in-memory quota cache (verified across Fola and Gabby test accounts)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR opened. Capture the URL for the next slice.

---

## Self-Review

Run through this checklist after completing all tasks, before marking the slice done.

**Spec coverage** (against `docs/superpowers/specs/2026-05-16-smart-grow-design.md` §"Slice 0 — Refactor: promote shared infra to `core/smartinfra`"):

| Spec requirement | Task |
|---|---|
| Move `SmartAiClient`, `SmartUsageStore`, `DraftLanguage` to `core/smartinfra/domain` | Tasks 7, 8 (and `FunctionsCaller` which is the codebase's `SmartAiClient` equivalent — covered in Task 9) |
| Move `CloudFunctionSmartAiClient`, `FirestoreSmartUsageStore` (or equivalents) to `core/smartinfra/data` | Task 8 (`InMemorySmartUsageStore` — note: the codebase has no Firestore-backed quota store; the source of truth is server-side. `GitLiveFunctionsCaller` is the data-layer impl of the AI client — Task 9) |
| Extend the quota store to tag each increment with a `SmartFeatureKey` | Tasks 1, 2 (server) + Task 6 (Kotlin enum) |
| Storage shape stays the same — a new sibling subcollection OR array captures per-feature counts | Implemented as a `perFeature` map on the same `smart_drafts` doc (Task 1). Single-doc approach chosen for transaction simplicity. |
| Update existing `feature/smart/` to import from `core/smartinfra`. No behavior change. | Tasks 7, 8, 9 (sed-based import updates) |
| Update existing Koin module wiring | Tasks 8, 9 (SmartModule.kt imports are caught by the sed) |
| Re-run existing unit tests | Tasks 7.7, 8.9, 9.10, 10.1, 10.2 |
| Add a contract test that `SmartFeatureKey.DRAFT` increments are counted | Task 4 (server-side contract test) + Task 10.4 (manual visual confirmation against real Firestore) |

**Placeholder scan:** This plan contains no TBD/TODO/FIXME, no "add appropriate error handling," and every code block is a complete, paste-ready snippet. The single judgment call (Task 6 Step 2 — directory naming convention with or without hyphens) is explicitly flagged and includes the decision criterion.

**Type consistency check:**

- Server `SmartFeatureKey` (TypeScript): `'draft' | 'postcaption' | 'referral_msg' | 'referral_bio' | 'contentplan_regen'`
- Kotlin `SmartFeatureKey` (enum): `Draft("draft") | PostCaption("postcaption") | ReferralMessage("referral_msg") | ReferralBio("referral_bio") | ContentPlanRegen("contentplan_regen")`
- Wire names match exactly across both sides. ✓
- `FreeTierUsageDoc.perFeature?: Record<string, number>` ← `Map<String, Int>` semantically; server-only field, no Kotlin DTO yet, so no cross-language constraint to enforce. ✓
- `reconcileUsage` signature is consistent across Tasks 1, 2, 3, 4 (always two args: `ReconcileArgs, SmartFeatureKey`). ✓

**Scope check:** Single subsystem (shared infrastructure refactor + back-compat server schema extension). All commits land in one PR. Each subsequent slice (1–4) gets its own plan and its own PR. ✓
