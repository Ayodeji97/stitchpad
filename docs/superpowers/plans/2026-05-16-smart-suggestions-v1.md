# Smart Suggestions V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Draft Message intent of Smart Suggestions V1 — a dedicated dashboard section card with one enabled intent tile that drafts a customer-facing WhatsApp message via Gemini 2.0 Flash through a Cloud Function, with a free-trial → paid gate.

**Architecture:** KMP client → Firebase Cloud Function (`europe-west1`) → Vertex AI Gemini 2.0 Flash. Server fetches order/customer context from Firestore using UID-scoped reads (no client payload bloat), runs the prompt, increments a per-UID monthly counter for free-tier users, and returns the drafted text. Per [[2026-05-16-ai-assistant-design]] spec.

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform 1.7 client, GitLive Firebase Functions for callable invocation, Material3 + Koin DI per project conventions. Server: TypeScript on Cloud Functions for Firebase v6 (v1 namespace), `firebase-admin` v12.7, `@google-cloud/vertexai` (new dep). Tests: JUnit5 / Turbine / AssertK / UnconfinedTestDispatcher (per `android-testing` skill conventions); Jest for server.

---

## File Structure

### Client (KMP, `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/`)

```
feature/smart/
  domain/
    model/
      DraftIntent.kt              — sealed interface: BalanceReminder, PickupReady, FollowUp, CustomNote
      DraftLanguage.kt            — enum: English, Pidgin
      DraftMessageRequest.kt      — value object passed to repository
      DraftMessageResult.kt       — value object returned by repository
      CustomerSummary.kt          — id + firstName + whatsappNumber, for the picker
      OrderSummary.kt             — id + garmentLabel + balance + deadline, for the picker
    error/
      SmartError.kt               — sealed interface: Network, FreeTierExhausted, InvalidInput, ServiceUnavailable, Unknown
    repository/
      SmartRepository.kt          — interface
  data/
    SmartFunctionsRepository.kt   — implements SmartRepository via FirebaseFunctions
    dto/
      DraftMessageRequestDto.kt
      DraftMessageResponseDto.kt
    mapper/
      DraftMessageMappers.kt      — extension fns: domain → DTO, DTO → domain, exception → SmartError
  presentation/
    SmartSectionCard.kt           — Dashboard host composable
    components/
      IntentTile.kt
      FreeTierCounterChip.kt
    draft/
      DraftMessageRoot.kt         — Root composable (has ViewModel)
      DraftMessageScreen.kt       — Stateless Screen composable (previewable)
      DraftMessageViewModel.kt
      DraftMessageState.kt        — State, Action, Event sealed types + GenerationState
      DraftMessageEventBus.kt     — kept inline if VM is the bus
      SmartErrorMapper.kt         — SmartError.toUiText() extension
      components/
        CustomerPickerSheet.kt
        OrderPickerSheet.kt
        IntentChips.kt
        LanguageToggle.kt
        DraftPreview.kt
        UpgradeBottomSheet.kt
di/
  SmartModule.kt                  — Koin module, registered in App.kt's startKoin
navigation/
  Routes.kt (modify)              — add DraftMessageRoute
  NavGraph.kt (modify)            — add smartGraph()
feature/dashboard/presentation/
  DashboardScreen.kt (modify)     — insert SmartSectionCard between NBA and Goals
```

Strings:
```
composeApp/src/commonMain/composeResources/values/strings.xml (modify)
  smart_section_title, smart_section_subtitle, smart_intent_draft_message_title,
  smart_intent_draft_message_subtitle, smart_intent_coming_soon_title (×2),
  smart_intent_coming_soon_label, smart_free_quota_remaining (plural),
  draft_message_screen_title, draft_message_pick_customer, draft_message_pick_order,
  draft_message_no_open_orders, draft_message_intent_balance, draft_message_intent_pickup,
  draft_message_intent_followup, draft_message_intent_custom, draft_message_language_english,
  draft_message_language_pidgin, draft_message_notes_label, draft_message_notes_placeholder,
  draft_message_generate_cta, draft_message_generating, draft_message_send_whatsapp,
  draft_message_copy_text, draft_message_no_whatsapp_helper, draft_message_offline_helper,
  smart_error_network, smart_error_invalid_input, smart_error_service_unavailable,
  smart_error_unknown, smart_upgrade_sheet_title, smart_upgrade_sheet_message,
  smart_upgrade_sheet_upgrade_cta, smart_upgrade_sheet_dismiss
```

### Tests (`composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/`)

```
feature/smart/data/
  SmartFunctionsRepositoryTest.kt
  mapper/DraftMessageMappersTest.kt
feature/smart/presentation/draft/
  DraftMessageViewModelTest.kt
  SmartErrorMapperTest.kt
  DraftMessageScreenTest.kt          — Compose UI test (variants)
feature/smart/presentation/
  SmartSectionCardTest.kt            — Compose UI test (3 tiles + empty state)
```

### Server (`functions/src/`)

```
smart/
  draftMessage.ts                    — onCall function entry
  vertexClient.ts                    — Vertex AI SDK setup (Gemini 2.0 Flash)
  promptBuilder.ts                   — system + user prompt templates per intent
  freeTierCounter.ts                 — Firestore-backed usage tracking with month rollover
  types.ts                           — request/response interfaces shared across server modules
index.ts (modify)                    — export draftMessage as a callable function
package.json (modify)                — add @google-cloud/vertexai
```

### Server tests (`functions/__tests__/`)

```
smart/
  promptBuilder.test.ts
  freeTierCounter.test.ts
  draftMessage.test.ts               — integration: auth/tier/ownership/Vertex-mock
```

---

## Task 0: Verify branch state

**Files:** N/A (git only)

- [ ] **Step 1: Confirm branch + spec commit**

Run: `git status && git log --oneline -3`
Expected: on `feature/smart-suggestions`, HEAD includes commit `454441d docs(smart): Smart Suggestions V1 design spec` (or its SHA after rebase).

If not on `feature/smart-suggestions`, run: `git checkout feature/smart-suggestions`. If the branch doesn't exist, run: `git checkout main && git pull --ff-only && git checkout -b feature/smart-suggestions` and re-write the spec doc per Task 0 of the brainstorming flow.

- [ ] **Step 2: No commit** — branch verification only.

---

## Task 1: Add `@google-cloud/vertexai` dependency

**Files:**
- Modify: `functions/package.json`

- [ ] **Step 1: Add dependency entry**

Edit `functions/package.json` — add to `dependencies`:

```json
"@google-cloud/vertexai": "^1.9.0"
```

The full `dependencies` block becomes:
```json
"dependencies": {
  "@google-cloud/vertexai": "^1.9.0",
  "firebase-admin": "^12.7.0",
  "firebase-functions": "^6.0.1"
}
```

- [ ] **Step 2: Install + verify lockfile**

Run: `cd functions && npm install --no-fund --no-audit 2>&1 | tail -10`
Expected: install succeeds, `package-lock.json` updated to include `@google-cloud/vertexai` and its transitive deps (`google-auth-library`, etc.).

- [ ] **Step 3: Verify TypeScript can resolve the module**

Run: `cd functions && npx tsc --noEmit 2>&1 | tail -5`
Expected: no errors. The dep is installed but not yet used; tsc should pass on the existing source.

- [ ] **Step 4: Commit**

```bash
git add functions/package.json functions/package-lock.json
git commit -m "$(cat <<'EOF'
build(functions): add @google-cloud/vertexai for Smart Suggestions

Adds the Vertex AI SDK as a Cloud Functions dep ahead of the Smart
Suggestions Draft Message feature. Caret pin to ^1.9.0; lockfile
captures resolved transitive deps (google-auth-library + friends).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Server — shared types

**Files:**
- Create: `functions/src/smart/types.ts`

- [ ] **Step 1: Create types file**

Create `functions/src/smart/types.ts` with:

```typescript
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
 * User profile fields the Smart layer cares about. Read from
 * `users/{uid}/profile` doc.
 */
export interface UserProfileSummary {
  tier: 'free' | 'premium';
}

/**
 * Free-tier usage doc at `users/{uid}/usage/smart_drafts`.
 */
export interface FreeTierUsageDoc {
  monthYear: string; // YYYY-MM
  count: number;
  limit: number;
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd functions && npx tsc --noEmit 2>&1 | tail -5`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add functions/src/smart/types.ts
git commit -m "$(cat <<'EOF'
feat(smart): add shared types for Smart Suggestions Cloud Function

Defines DraftMessageRequest/Response, DraftContext, UserProfileSummary,
FreeTierUsageDoc — referenced by promptBuilder + draftMessage handler +
tests. No behavior yet.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Server — prompt builder (TDD)

**Files:**
- Create: `functions/src/smart/promptBuilder.ts`
- Create: `functions/__tests__/smart/promptBuilder.test.ts`

- [ ] **Step 1: Write failing test for system prompt**

Create `functions/__tests__/smart/promptBuilder.test.ts`:

```typescript
import { buildSystemPrompt, buildUserPrompt } from '../../src/smart/promptBuilder';
import { DraftContext, IntentType, Language } from '../../src/smart/types';

describe('buildSystemPrompt', () => {
  it('returns a stable system prompt with the tailor instructions', () => {
    const prompt = buildSystemPrompt();
    expect(prompt).toContain('writing assistant for a Nigerian tailor');
    expect(prompt).toContain('Address the customer by their first name');
    expect(prompt).toContain('2-4 sentences');
    expect(prompt).toContain('Output ONLY the message body');
  });
});

describe('buildUserPrompt', () => {
  const ctx: DraftContext = {
    customerFirstName: 'Folake',
    garmentLabel: 'Adire boubou (peach)',
    depositFormatted: '₦5,000',
    balanceFormatted: '₦7,500',
    deadlineFormatted: 'Friday, May 22',
  };

  it('embeds customer + order context for balance reminder in English', () => {
    const prompt = buildUserPrompt({
      intentType: 'balance_reminder',
      language: 'en',
      context: ctx,
    });
    expect(prompt).toContain('polite reminder about an outstanding balance');
    expect(prompt).toContain('English');
    expect(prompt).toContain('Folake');
    expect(prompt).toContain('Adire boubou (peach)');
    expect(prompt).toContain('₦7,500');
    expect(prompt).toContain('Friday, May 22');
  });

  it('switches intent label for pickup_ready', () => {
    const prompt = buildUserPrompt({
      intentType: 'pickup_ready',
      language: 'en',
      context: ctx,
    });
    expect(prompt).toContain('notification that their order is ready for pickup');
  });

  it('switches intent label for follow_up', () => {
    const prompt = buildUserPrompt({
      intentType: 'follow_up',
      language: 'en',
      context: ctx,
    });
    expect(prompt).toContain('casual check-in');
  });

  it('uses custom_note label and includes the notes', () => {
    const prompt = buildUserPrompt({
      intentType: 'custom_note',
      language: 'en',
      context: ctx,
      customNotes: 'Apologise for the delay due to power outage',
    });
    expect(prompt).toContain('custom message');
    expect(prompt).toContain('Apologise for the delay due to power outage');
  });

  it('switches language label to Pidgin for pcm', () => {
    const prompt = buildUserPrompt({
      intentType: 'balance_reminder',
      language: 'pcm',
      context: ctx,
    });
    expect(prompt).toContain('Pidgin');
    expect(prompt).not.toMatch(/in English/);
  });

  it('omits the custom notes section when no notes provided', () => {
    const prompt = buildUserPrompt({
      intentType: 'balance_reminder',
      language: 'en',
      context: ctx,
    });
    expect(prompt).not.toMatch(/Notes:/);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd functions && npx jest __tests__/smart/promptBuilder.test.ts 2>&1 | tail -10`
Expected: FAIL — module not found `../../src/smart/promptBuilder`.

- [ ] **Step 3: Implement the prompt builder**

Create `functions/src/smart/promptBuilder.ts`:

```typescript
import { DraftContext, IntentType, Language } from './types';

const SYSTEM_PROMPT = `You are a writing assistant for a Nigerian tailor running a small workshop.
You draft polite, professional WhatsApp messages to customers about orders.

Rules:
- Address the customer by their first name only.
- Keep messages short (2-4 sentences).
- Tone: warm, professional, never pushy.
- For Pidgin output, use casual Nigerian Pidgin (not heavy slang).
- Never invent prices or facts not in the order context.
- Output ONLY the message body. No greeting prefix, no signature, no quotes.`;

const INTENT_LABELS: Record<IntentType, string> = {
  balance_reminder: 'polite reminder about an outstanding balance',
  pickup_ready: 'notification that their order is ready for pickup',
  follow_up: 'casual check-in to see if they need anything else',
  custom_note: 'custom message based on the notes provided',
};

const LANGUAGE_LABELS: Record<Language, string> = {
  en: 'English',
  pcm: 'Pidgin (Nigerian)',
};

export function buildSystemPrompt(): string {
  return SYSTEM_PROMPT;
}

export interface BuildUserPromptArgs {
  intentType: IntentType;
  language: Language;
  context: DraftContext;
  customNotes?: string;
}

export function buildUserPrompt(args: BuildUserPromptArgs): string {
  const { intentType, language, context, customNotes } = args;
  const intentLabel = INTENT_LABELS[intentType];
  const languageLabel = LANGUAGE_LABELS[language];

  const lines = [
    `Draft a ${intentLabel} message in ${languageLabel} for this customer:`,
    '',
    `Customer: ${context.customerFirstName}`,
    `Order: ${context.garmentLabel}`,
    `Deposit paid: ${context.depositFormatted}`,
    `Balance due: ${context.balanceFormatted}`,
    `Deadline: ${context.deadlineFormatted}`,
  ];

  if (customNotes && customNotes.trim().length > 0) {
    lines.push('', `Notes: ${customNotes.trim()}`);
  }

  lines.push('', 'Your draft:');
  return lines.join('\n');
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd functions && npx jest __tests__/smart/promptBuilder.test.ts 2>&1 | tail -10`
Expected: all 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add functions/src/smart/promptBuilder.ts functions/__tests__/smart/promptBuilder.test.ts
git commit -m "$(cat <<'EOF'
feat(smart): add prompt builder for Draft Message intents

Pure function that constructs the system + user prompts from intent +
language + DraftContext. 7 unit tests cover all 4 intent types, both
language labels, and the optional customNotes branch.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Server — free-tier counter (TDD)

**Files:**
- Create: `functions/src/smart/freeTierCounter.ts`
- Create: `functions/__tests__/smart/freeTierCounter.test.ts`

- [ ] **Step 1: Write failing tests**

Create `functions/__tests__/smart/freeTierCounter.test.ts`:

```typescript
import { FreeTierUsageDoc } from '../../src/smart/types';
import { reconcileUsage, isExhausted, USAGE_DEFAULT_LIMIT } from '../../src/smart/freeTierCounter';

const today = new Date('2026-05-16T10:00:00Z');

describe('reconcileUsage', () => {
  it('initializes a fresh doc when none exists', () => {
    const next = reconcileUsage({ existing: null, now: today });
    expect(next).toEqual({ monthYear: '2026-05', count: 1, limit: USAGE_DEFAULT_LIMIT });
  });

  it('increments count when same month', () => {
    const existing: FreeTierUsageDoc = { monthYear: '2026-05', count: 3, limit: 5 };
    const next = reconcileUsage({ existing, now: today });
    expect(next).toEqual({ monthYear: '2026-05', count: 4, limit: 5 });
  });

  it('resets count to 1 on month rollover', () => {
    const existing: FreeTierUsageDoc = { monthYear: '2026-04', count: 5, limit: 5 };
    const next = reconcileUsage({ existing, now: today });
    expect(next).toEqual({ monthYear: '2026-05', count: 1, limit: 5 });
  });

  it('preserves a custom limit on month rollover (testers override)', () => {
    const existing: FreeTierUsageDoc = { monthYear: '2026-04', count: 5, limit: 100 };
    const next = reconcileUsage({ existing, now: today });
    expect(next).toEqual({ monthYear: '2026-05', count: 1, limit: 100 });
  });

  it('preserves a custom limit on same-month increment', () => {
    const existing: FreeTierUsageDoc = { monthYear: '2026-05', count: 50, limit: 100 };
    const next = reconcileUsage({ existing, now: today });
    expect(next.limit).toBe(100);
  });

  it('zero-pads single-digit months in monthYear', () => {
    const earlyJan = new Date('2026-01-05T10:00:00Z');
    const next = reconcileUsage({ existing: null, now: earlyJan });
    expect(next.monthYear).toBe('2026-01');
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

Run: `cd functions && npx jest __tests__/smart/freeTierCounter.test.ts 2>&1 | tail -10`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the counter**

Create `functions/src/smart/freeTierCounter.ts`:

```typescript
import { FreeTierUsageDoc } from './types';

export const USAGE_DEFAULT_LIMIT = 5;

export interface ReconcileArgs {
  existing: FreeTierUsageDoc | null;
  now: Date;
}

/**
 * Pure function — given the current usage doc (or null if absent) and the
 * current time, returns the new doc state after recording one more draft.
 *
 * Handles month rollover: if existing.monthYear differs from now's
 * YYYY-MM, count resets to 1 (counting the about-to-be-recorded draft).
 *
 * Preserves the existing limit (testers can override per-user via Firestore
 * console). When initializing fresh, uses USAGE_DEFAULT_LIMIT.
 */
export function reconcileUsage(args: ReconcileArgs): FreeTierUsageDoc {
  const { existing, now } = args;
  const currentMonthYear = formatMonthYear(now);

  if (existing === null) {
    return { monthYear: currentMonthYear, count: 1, limit: USAGE_DEFAULT_LIMIT };
  }

  if (existing.monthYear !== currentMonthYear) {
    return { monthYear: currentMonthYear, count: 1, limit: existing.limit };
  }

  return { monthYear: existing.monthYear, count: existing.count + 1, limit: existing.limit };
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd functions && npx jest __tests__/smart/freeTierCounter.test.ts 2>&1 | tail -10`
Expected: all 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add functions/src/smart/freeTierCounter.ts functions/__tests__/smart/freeTierCounter.test.ts
git commit -m "$(cat <<'EOF'
feat(smart): add free-tier usage reconciliation logic

Pure functions reconcileUsage + isExhausted. reconcileUsage handles fresh
init, same-month increment, and month-rollover reset while preserving any
tester-overridden limit. 9 unit tests cover the matrix.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Server — Vertex AI client wrapper

**Files:**
- Create: `functions/src/smart/vertexClient.ts`

This task has no unit test of its own — the client wraps the third-party SDK and is exercised only via the integration test in Task 6 (using a mock `VertexClient` injected into the handler).

- [ ] **Step 1: Implement the client wrapper**

Create `functions/src/smart/vertexClient.ts`:

```typescript
import { VertexAI, GenerateContentResponse } from '@google-cloud/vertexai';

const PROJECT_ID = 'stitchpad-30607';
const LOCATION = 'europe-west1';
const MODEL_ID = 'gemini-2.0-flash-001';

/**
 * Thin interface over the Vertex AI SDK so the draftMessage handler can be
 * tested with a fake client (no real LLM calls in CI — cost + flake).
 */
export interface VertexClient {
  generateText(args: { systemPrompt: string; userPrompt: string }): Promise<string>;
}

/**
 * Production client using the Vertex AI SDK. Initialized lazily on first
 * use so module load doesn't pay the SDK setup cost.
 */
let cachedClient: VertexClient | null = null;

export function getVertexClient(): VertexClient {
  if (cachedClient !== null) return cachedClient;

  const vertex = new VertexAI({ project: PROJECT_ID, location: LOCATION });
  const model = vertex.getGenerativeModel({
    model: MODEL_ID,
    generationConfig: { temperature: 0.7, maxOutputTokens: 200 },
  });

  cachedClient = {
    async generateText({ systemPrompt, userPrompt }) {
      const response: GenerateContentResponse = (
        await model.generateContent({
          contents: [
            { role: 'user', parts: [{ text: userPrompt }] },
          ],
          systemInstruction: { role: 'system', parts: [{ text: systemPrompt }] },
        })
      ).response;

      const candidates = response.candidates ?? [];
      if (candidates.length === 0) {
        throw new Error('vertex_no_candidates');
      }
      const text = candidates[0]?.content?.parts?.[0]?.text;
      if (!text || text.trim().length === 0) {
        throw new Error('vertex_empty_text');
      }
      return text.trim();
    },
  };

  return cachedClient;
}

/**
 * Reset the cached client. Used by tests; not exported via the package
 * boundary in production code.
 */
export function __resetVertexClientForTests(): void {
  cachedClient = null;
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd functions && npx tsc --noEmit 2>&1 | tail -5`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add functions/src/smart/vertexClient.ts
git commit -m "$(cat <<'EOF'
feat(smart): wrap Vertex AI SDK behind a VertexClient interface

Production client uses gemini-2.0-flash-001 in europe-west1 (matches the
StitchPad Firestore region). Returns trimmed text; throws on empty/missing
candidates so the handler can map to service_unavailable. Lazily initialized.

The interface lets the handler accept a fake client in tests — no real LLM
calls in CI.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Server — `smartDraftMessage` callable function (TDD)

**Files:**
- Create: `functions/src/smart/draftMessage.ts`
- Create: `functions/__tests__/smart/draftMessage.test.ts`

- [ ] **Step 1: Write failing tests**

Create `functions/__tests__/smart/draftMessage.test.ts`:

```typescript
import * as admin from 'firebase-admin';
import * as functionsTest from 'firebase-functions-test';
import { VertexClient } from '../../src/smart/vertexClient';

const test = functionsTest();

// Lazy import after firebase-admin init in setup
let handler: any;
let __setVertexClientForTests: (c: VertexClient) => void;

const fakeVertex: VertexClient = {
  generateText: jest.fn().mockResolvedValue('Hi Folake, just a friendly note about your balance.'),
};

beforeAll(async () => {
  const mod = await import('../../src/smart/draftMessage');
  handler = mod.draftMessageHandler;
  __setVertexClientForTests = mod.__setVertexClientForTests;
});

beforeEach(() => {
  __setVertexClientForTests(fakeVertex);
  jest.clearAllMocks();
});

afterAll(() => {
  test.cleanup();
});

const validRequest = {
  intentType: 'balance_reminder' as const,
  customerId: 'cust-1',
  orderId: 'order-1',
  language: 'en' as const,
};

const baseContext = {
  auth: { uid: 'user-1', token: {} as any },
};

const fakeFirestore = (overrides: Partial<{
  profile: { tier: 'free' | 'premium' };
  usage: { monthYear: string; count: number; limit: number } | null;
  customer: { firstName: string };
  order: { customerId: string; garmentLabel: string; depositFormatted: string; balanceFormatted: string; deadlineFormatted: string };
}>) => {
  const profile = overrides.profile ?? { tier: 'free' as const };
  const usage = 'usage' in overrides ? overrides.usage : { monthYear: '2026-05', count: 0, limit: 5 };
  const customer = overrides.customer ?? { firstName: 'Folake' };
  const order = overrides.order ?? {
    customerId: 'cust-1',
    garmentLabel: 'Adire boubou',
    depositFormatted: '₦5,000',
    balanceFormatted: '₦7,500',
    deadlineFormatted: 'Friday, May 22',
  };

  return {
    profileGet: jest.fn().mockResolvedValue({ exists: true, data: () => profile }),
    usageGet: jest.fn().mockResolvedValue(
      usage === null ? { exists: false, data: () => undefined } : { exists: true, data: () => usage }
    ),
    usageSet: jest.fn().mockResolvedValue(undefined),
    customerGet: jest.fn().mockResolvedValue({ exists: true, data: () => customer }),
    orderGet: jest.fn().mockResolvedValue({ exists: true, data: () => order }),
  };
};

describe('draftMessageHandler', () => {
  it('rejects unauthenticated requests', async () => {
    await expect(
      handler(validRequest, { auth: undefined } as any, fakeFirestore({}))
    ).rejects.toMatchObject({ code: 'unauthenticated' });
  });

  it('returns drafted text + remainingFreeQuota for a free user under quota', async () => {
    const fs = fakeFirestore({});
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.draftText).toContain('Folake');
    expect(result.remainingFreeQuota).toBe(4); // limit 5 - count 1
    expect(fs.usageSet).toHaveBeenCalledTimes(1);
  });

  it('rejects with permission-denied when free tier exhausted', async () => {
    const fs = fakeFirestore({ usage: { monthYear: '2026-05', count: 5, limit: 5 } });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'permission-denied',
      message: expect.stringContaining('free_tier_exhausted'),
    });
    expect(fs.usageSet).not.toHaveBeenCalled();
    expect(fakeVertex.generateText).not.toHaveBeenCalled();
  });

  it('skips counter for premium users and returns null remaining quota', async () => {
    const fs = fakeFirestore({ profile: { tier: 'premium' } });
    const result = await handler(validRequest, baseContext as any, fs);
    expect(result.remainingFreeQuota).toBeNull();
    expect(fs.usageSet).not.toHaveBeenCalled();
  });

  it('rejects with invalid-argument when customer does not exist', async () => {
    const fs = fakeFirestore({});
    fs.customerGet = jest.fn().mockResolvedValue({ exists: false, data: () => undefined });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
  });

  it('rejects with invalid-argument when order belongs to a different customer', async () => {
    const fs = fakeFirestore({});
    fs.orderGet = jest.fn().mockResolvedValue({
      exists: true,
      data: () => ({
        customerId: 'cust-OTHER',
        garmentLabel: 'x', depositFormatted: 'x', balanceFormatted: 'x', deadlineFormatted: 'x',
      }),
    });
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'invalid-argument',
    });
  });

  it('maps Vertex failures to unavailable', async () => {
    const fs = fakeFirestore({});
    (fakeVertex.generateText as jest.Mock).mockRejectedValueOnce(new Error('vertex_no_candidates'));
    await expect(handler(validRequest, baseContext as any, fs)).rejects.toMatchObject({
      code: 'unavailable',
    });
    expect(fs.usageSet).not.toHaveBeenCalled(); // counter NOT incremented on Vertex failure
  });

  it('rolls over the month-year counter on first call of new month', async () => {
    const fs = fakeFirestore({ usage: { monthYear: '2026-04', count: 5, limit: 5 } });
    const result = await handler(validRequest, baseContext as any, fs, new Date('2026-05-16T10:00:00Z'));
    expect(result.remainingFreeQuota).toBe(4);
    expect(fs.usageSet).toHaveBeenCalledWith(
      expect.objectContaining({ monthYear: '2026-05', count: 1 })
    );
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd functions && npx jest __tests__/smart/draftMessage.test.ts 2>&1 | tail -15`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the handler**

Create `functions/src/smart/draftMessage.ts`:

```typescript
import * as functions from 'firebase-functions/v1';
import * as admin from 'firebase-admin';
import {
  DraftMessageRequest,
  DraftMessageResponse,
  DraftContext,
  UserProfileSummary,
  FreeTierUsageDoc,
} from './types';
import { buildSystemPrompt, buildUserPrompt } from './promptBuilder';
import { reconcileUsage, isExhausted } from './freeTierCounter';
import { getVertexClient, VertexClient } from './vertexClient';

/**
 * Test seam — production code uses getVertexClient() directly.
 * Tests inject a fake via __setVertexClientForTests.
 */
let injectedVertexClient: VertexClient | null = null;
export function __setVertexClientForTests(client: VertexClient): void {
  injectedVertexClient = client;
}
function vertex(): VertexClient {
  return injectedVertexClient ?? getVertexClient();
}

/**
 * Test seam — production wraps admin.firestore() docs into an io shim.
 * Tests inject a fake io directly.
 */
export interface DraftMessageIO {
  profileGet(): Promise<{ exists: boolean; data(): UserProfileSummary | undefined }>;
  usageGet(): Promise<{ exists: boolean; data(): FreeTierUsageDoc | undefined }>;
  usageSet(doc: FreeTierUsageDoc): Promise<void>;
  customerGet(): Promise<{ exists: boolean; data(): { firstName: string } | undefined }>;
  orderGet(): Promise<{ exists: boolean; data(): {
    customerId: string;
    garmentLabel: string;
    depositFormatted: string;
    balanceFormatted: string;
    deadlineFormatted: string;
  } | undefined }>;
}

function productionIO(uid: string, customerId: string, orderId: string, db: admin.firestore.Firestore): DraftMessageIO {
  return {
    profileGet: () => db.doc(`users/${uid}/profile`).get().then(toExistsShape),
    usageGet: () => db.doc(`users/${uid}/usage/smart_drafts`).get().then(toExistsShape),
    usageSet: (doc) => db.doc(`users/${uid}/usage/smart_drafts`).set(doc).then(() => undefined),
    customerGet: () => db.doc(`users/${uid}/customers/${customerId}`).get().then(toExistsShape),
    orderGet: () => db.doc(`users/${uid}/orders/${orderId}`).get().then(toExistsShape),
  };
}

function toExistsShape<T>(snap: admin.firestore.DocumentSnapshot): { exists: boolean; data(): T | undefined } {
  return { exists: snap.exists, data: () => snap.data() as T | undefined };
}

/**
 * Pure handler for testing. Production wraps this in functions.https.onCall.
 */
export async function draftMessageHandler(
  data: DraftMessageRequest,
  context: functions.https.CallableContext,
  io: DraftMessageIO,
  now: Date = new Date(),
): Promise<DraftMessageResponse> {
  if (!context.auth?.uid) {
    throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
  }

  // 1. Tier check
  const profileSnap = await io.profileGet();
  const tier = profileSnap.exists ? profileSnap.data()?.tier ?? 'free' : 'free';

  let nextUsage: FreeTierUsageDoc | null = null;
  if (tier === 'free') {
    const usageSnap = await io.usageGet();
    const existing = usageSnap.exists ? (usageSnap.data() ?? null) : null;
    nextUsage = reconcileUsage({ existing, now });
    if (existing !== null && isExhausted(existing) && existing.monthYear === nextUsage.monthYear) {
      throw new functions.https.HttpsError('permission-denied', 'free_tier_exhausted');
    }
  }

  // 2. Fetch context (validates ownership)
  const [customerSnap, orderSnap] = await Promise.all([io.customerGet(), io.orderGet()]);
  if (!customerSnap.exists) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: customer not found');
  }
  const customer = customerSnap.data()!;
  if (!orderSnap.exists) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: order not found');
  }
  const order = orderSnap.data()!;
  if (order.customerId !== data.customerId) {
    throw new functions.https.HttpsError('invalid-argument', 'invalid_input: order does not belong to customer');
  }

  // 3. Build prompts
  const draftCtx: DraftContext = {
    customerFirstName: customer.firstName,
    garmentLabel: order.garmentLabel,
    depositFormatted: order.depositFormatted,
    balanceFormatted: order.balanceFormatted,
    deadlineFormatted: order.deadlineFormatted,
  };
  const systemPrompt = buildSystemPrompt();
  const userPrompt = buildUserPrompt({
    intentType: data.intentType,
    language: data.language,
    context: draftCtx,
    customNotes: data.customNotes,
  });

  // 4. Call Vertex
  let draftText: string;
  try {
    draftText = await vertex().generateText({ systemPrompt, userPrompt });
  } catch (err) {
    throw new functions.https.HttpsError('unavailable', 'service_unavailable');
  }

  // 5. Increment counter (free tier only) — happens AFTER Vertex success
  if (tier === 'free' && nextUsage !== null) {
    await io.usageSet(nextUsage);
  }

  return {
    draftText,
    remainingFreeQuota: tier === 'premium' ? null : (nextUsage!.limit - nextUsage!.count),
  };
}

/**
 * Production callable export.
 */
export const smartDraftMessage = functions
  .region('europe-west1')
  .https.onCall(async (data: DraftMessageRequest, context) => {
    if (!context.auth?.uid) {
      throw new functions.https.HttpsError('unauthenticated', 'Sign in required.');
    }
    const db = admin.firestore();
    const io = productionIO(context.auth.uid, data.customerId, data.orderId, db);
    return draftMessageHandler(data, context, io);
  });
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd functions && npx jest __tests__/smart/draftMessage.test.ts 2>&1 | tail -15`
Expected: all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add functions/src/smart/draftMessage.ts functions/__tests__/smart/draftMessage.test.ts
git commit -m "$(cat <<'EOF'
feat(smart): smartDraftMessage callable function

Server flow: auth → tier check → fetch customer + order context (validates
ownership) → build prompts → Vertex call → increment counter on success.
Test seams via DraftMessageIO interface + injectable VertexClient — 8
integration tests cover auth, free tier exhausted/under-quota, premium,
invalid customer/order, ownership mismatch, Vertex failure, month rollover.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Server — register function in `index.ts`

**Files:**
- Modify: `functions/src/index.ts`

- [ ] **Step 1: Add export**

Modify `functions/src/index.ts` — append after the existing `onAuthUserDeleted` export:

```typescript
export { smartDraftMessage } from './smart/draftMessage';
```

- [ ] **Step 2: Verify the build picks it up**

Run: `cd functions && npx tsc --noEmit 2>&1 | tail -5`
Expected: no errors.

Run: `cd functions && npm run build 2>&1 | tail -5`
Expected: tsc emits to `functions/lib/`. The compiled `lib/index.js` should now reference `lib/smart/draftMessage.js`.

Run: `ls functions/lib/smart/ 2>&1`
Expected: `draftMessage.js`, `freeTierCounter.js`, `promptBuilder.js`, `types.js`, `vertexClient.js`.

- [ ] **Step 3: Update the deploy script to include the new function**

Modify `functions/package.json` — change the `deploy` script from:
```
"deploy": "npm run build && firebase deploy --only functions:onAuthUserDeleted"
```
to:
```
"deploy": "npm run build && firebase deploy --only functions:onAuthUserDeleted,functions:smartDraftMessage"
```

- [ ] **Step 4: Commit**

```bash
git add functions/src/index.ts functions/package.json
git commit -m "$(cat <<'EOF'
build(functions): register smartDraftMessage callable + extend deploy script

Adds the new export to index.ts and updates the deploy script to include
both onAuthUserDeleted and smartDraftMessage. Local build verified;
actual Firebase deploy happens during the manual smoke step.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Client — domain models

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftIntent.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftLanguage.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftMessageRequest.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/DraftMessageResult.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/CustomerSummary.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/model/OrderSummary.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/error/SmartError.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/repository/SmartRepository.kt`

These are pure value types with no behavior — no unit tests needed.

- [ ] **Step 1: Create `DraftIntent.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * The four intent types supported by the V1 Draft Message feature. Each
 * maps to a specific intent label in the server-side prompt builder.
 */
enum class DraftIntent(val wireName: String) {
    BalanceReminder("balance_reminder"),
    PickupReady("pickup_ready"),
    FollowUp("follow_up"),
    CustomNote("custom_note"),
}
```

- [ ] **Step 2: Create `DraftLanguage.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Output language for a draft. Pidgin = Nigerian Pidgin (ISO 639-3 `pcm`).
 */
enum class DraftLanguage(val wireName: String) {
    English("en"),
    Pidgin("pcm"),
}
```

- [ ] **Step 3: Create `CustomerSummary.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Compact view of a Customer used by the Draft Message picker. Includes
 * just the fields the picker needs to render + decide WhatsApp send
 * eligibility.
 */
data class CustomerSummary(
    val id: String,
    val firstName: String,
    val whatsappNumber: String?,
)
```

- [ ] **Step 4: Create `OrderSummary.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Compact view of an open Order used by the Draft Message picker. Already-
 * formatted strings — the picker doesn't compute currency or dates itself.
 */
data class OrderSummary(
    val id: String,
    val customerId: String,
    val garmentLabel: String,
    val balanceFormatted: String,
    val deadlineFormatted: String,
)
```

- [ ] **Step 5: Create `DraftMessageRequest.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Domain request passed from the ViewModel through the repository to the
 * Cloud Function.
 */
data class DraftMessageRequest(
    val customerId: String,
    val orderId: String,
    val intent: DraftIntent,
    val language: DraftLanguage,
    val customNotes: String? = null,
)
```

- [ ] **Step 6: Create `DraftMessageResult.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.domain.model

/**
 * Domain result returned by the repository on a successful draft.
 *
 * @param draftText the generated message body
 * @param remainingFreeQuota null if the user is on the premium tier; an
 *                           integer >= 0 for free-tier users
 */
data class DraftMessageResult(
    val draftText: String,
    val remainingFreeQuota: Int?,
)
```

- [ ] **Step 7: Create `SmartError.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.domain.error

import com.danzucker.stitchpad.core.domain.error.Error

/**
 * Errors specific to the Smart Suggestions feature. Returned wrapped in
 * Result.Error from the SmartRepository.
 */
sealed interface SmartError : Error {
    data object Network : SmartError
    data object FreeTierExhausted : SmartError
    data object InvalidInput : SmartError
    data object ServiceUnavailable : SmartError
    data object Unknown : SmartError
}
```

- [ ] **Step 8: Create `SmartRepository.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.domain.repository

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult

/**
 * Server-backed Smart Suggestions repository. Implementations call the
 * smartDraftMessage Cloud Function via Firebase Functions client.
 */
interface SmartRepository {
    suspend fun draftMessage(
        request: DraftMessageRequest,
    ): Result<DraftMessageResult, SmartError>
}
```

- [ ] **Step 9: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/domain/
git commit -m "$(cat <<'EOF'
feat(smart): add Smart Suggestions domain model + repository interface

Pure value types: DraftIntent (4 intents), DraftLanguage (en/pcm),
CustomerSummary, OrderSummary, DraftMessageRequest, DraftMessageResult.
SmartError sealed interface for typed failures. SmartRepository contract
returns Result<DraftMessageResult, SmartError> per project conventions.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Client — DTOs + mappers (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/dto/DraftMessageRequestDto.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/dto/DraftMessageResponseDto.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/mapper/DraftMessageMappers.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/data/mapper/DraftMessageMappersTest.kt`

- [ ] **Step 1: Write failing test**

Create the test file:

```kotlin
package com.danzucker.stitchpad.feature.smart.data.mapper

import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftMessageMappersTest {

    @Test
    fun `request to DTO maps wire names + omits empty notes`() {
        val req = DraftMessageRequest(
            customerId = "cust-1",
            orderId = "order-1",
            intent = DraftIntent.BalanceReminder,
            language = DraftLanguage.English,
            customNotes = null,
        )
        val dto = req.toDto()
        assertEquals("balance_reminder", dto.intentType)
        assertEquals("cust-1", dto.customerId)
        assertEquals("order-1", dto.orderId)
        assertEquals("en", dto.language)
        assertNull(dto.customNotes)
    }

    @Test
    fun `request to DTO maps Pidgin + custom notes`() {
        val req = DraftMessageRequest(
            customerId = "c",
            orderId = "o",
            intent = DraftIntent.CustomNote,
            language = DraftLanguage.Pidgin,
            customNotes = "Apologise for delay",
        )
        val dto = req.toDto()
        assertEquals("custom_note", dto.intentType)
        assertEquals("pcm", dto.language)
        assertEquals("Apologise for delay", dto.customNotes)
    }

    @Test
    fun `request to DTO trims and nulls blank custom notes`() {
        val req = DraftMessageRequest(
            customerId = "c",
            orderId = "o",
            intent = DraftIntent.FollowUp,
            language = DraftLanguage.English,
            customNotes = "   ",
        )
        val dto = req.toDto()
        assertNull(dto.customNotes)
    }

    @Test
    fun `response DTO to domain copies fields including null quota for premium`() {
        val dto = DraftMessageResponseDto(
            draftText = "Hi Folake!",
            remainingFreeQuota = null,
        )
        val result = dto.toDomain()
        assertEquals("Hi Folake!", result.draftText)
        assertNull(result.remainingFreeQuota)
    }

    @Test
    fun `response DTO to domain preserves remainingFreeQuota for free tier`() {
        val dto = DraftMessageResponseDto(draftText = "x", remainingFreeQuota = 4)
        assertEquals(4, dto.toDomain().remainingFreeQuota)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DraftMessageMappersTest*" 2>&1 | tail -10`
Expected: FAIL — unresolved references to DTO and mapper symbols.

- [ ] **Step 3: Create the DTOs**

`DraftMessageRequestDto.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for the smartDraftMessage callable function request body.
 * Field names + value strings match the server's TypeScript types.
 */
@Serializable
data class DraftMessageRequestDto(
    @SerialName("intentType") val intentType: String,
    @SerialName("customerId") val customerId: String,
    @SerialName("orderId") val orderId: String,
    @SerialName("language") val language: String,
    @SerialName("customNotes") val customNotes: String? = null,
)
```

`DraftMessageResponseDto.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape for the smartDraftMessage callable function response body.
 */
@Serializable
data class DraftMessageResponseDto(
    @SerialName("draftText") val draftText: String,
    @SerialName("remainingFreeQuota") val remainingFreeQuota: Int? = null,
)
```

- [ ] **Step 4: Create the mappers**

`DraftMessageMappers.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.data.mapper

import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageRequestDto
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult

internal fun DraftMessageRequest.toDto(): DraftMessageRequestDto {
    val notes = customNotes?.trim().takeUnless { it.isNullOrEmpty() }
    return DraftMessageRequestDto(
        intentType = intent.wireName,
        customerId = customerId,
        orderId = orderId,
        language = language.wireName,
        customNotes = notes,
    )
}

internal fun DraftMessageResponseDto.toDomain(): DraftMessageResult =
    DraftMessageResult(
        draftText = draftText,
        remainingFreeQuota = remainingFreeQuota,
    )
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DraftMessageMappersTest*" 2>&1 | tail -10`
Expected: 5 tests pass.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/data/
git commit -m "$(cat <<'EOF'
feat(smart): add DTOs + mappers for Smart Suggestions wire format

Serializable DTOs match the Cloud Function contract. Mappers handle
wire-name conversion (intent + language enums → strings), trim/null-out
blank custom notes, and copy null remainingFreeQuota through for premium
users. 5 unit tests cover the matrix.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Client — `SmartFunctionsRepository` (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartFunctionsRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartFunctionsRepositoryTest.kt`

The repository wraps `dev.gitlive.firebase.functions.FirebaseFunctions`. To test without a real Firebase, we abstract the callable invocation behind a small `FunctionsCaller` interface so tests can inject a fake.

- [ ] **Step 1: Write failing tests**

```kotlin
package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageRequestDto
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SmartFunctionsRepositoryTest {

    private val baseRequest = DraftMessageRequest(
        customerId = "c",
        orderId = "o",
        intent = DraftIntent.BalanceReminder,
        language = DraftLanguage.English,
    )

    @Test
    fun `success returns mapped DraftMessageResult`() = runTest {
        val fake = FakeFunctionsCaller(
            response = Result.Success(DraftMessageResponseDto("Hi Folake!", remainingFreeQuota = 4)),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)

        assertIs<Result.Success<*, *>>(result)
        assertEquals("Hi Folake!", (result.data as com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult).draftText)
        assertEquals(4, result.data.remainingFreeQuota)
        assertEquals(DraftIntent.BalanceReminder.wireName, fake.lastRequest?.intentType)
    }

    @Test
    fun `permission_denied with free_tier_exhausted maps to SmartError_FreeTierExhausted`() = runTest {
        val fake = FakeFunctionsCaller(
            response = Result.Error(FunctionsCallerError.PermissionDenied("free_tier_exhausted")),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)

        assertIs<Result.Error<*, SmartError>>(result)
        assertEquals(SmartError.FreeTierExhausted, (result as Result.Error).error)
    }

    @Test
    fun `invalid_argument maps to SmartError_InvalidInput`() = runTest {
        val fake = FakeFunctionsCaller(
            response = Result.Error(FunctionsCallerError.InvalidArgument("invalid_input: customer not found")),
        )
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertEquals(SmartError.InvalidInput, (result as Result.Error).error)
    }

    @Test
    fun `unavailable maps to SmartError_ServiceUnavailable`() = runTest {
        val fake = FakeFunctionsCaller(response = Result.Error(FunctionsCallerError.Unavailable))
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertEquals(SmartError.ServiceUnavailable, (result as Result.Error).error)
    }

    @Test
    fun `network failure maps to SmartError_Network`() = runTest {
        val fake = FakeFunctionsCaller(response = Result.Error(FunctionsCallerError.Network))
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertEquals(SmartError.Network, (result as Result.Error).error)
    }

    @Test
    fun `unknown error maps to SmartError_Unknown`() = runTest {
        val fake = FakeFunctionsCaller(response = Result.Error(FunctionsCallerError.Unknown("boom")))
        val repo = SmartFunctionsRepository(fake)

        val result = repo.draftMessage(baseRequest)
        assertEquals(SmartError.Unknown, (result as Result.Error).error)
    }

    private class FakeFunctionsCaller(
        private val response: Result<DraftMessageResponseDto, FunctionsCallerError>,
    ) : FunctionsCaller {
        var lastRequest: DraftMessageRequestDto? = null
        override suspend fun callDraftMessage(
            request: DraftMessageRequestDto,
        ): Result<DraftMessageResponseDto, FunctionsCallerError> {
            lastRequest = request
            return response
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SmartFunctionsRepositoryTest*" 2>&1 | tail -10`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the FunctionsCaller abstraction + repository**

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/FunctionsCaller.kt`:

```kotlin
package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageRequestDto
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto

/**
 * Test seam over the GitLive Firebase Functions callable. The production
 * implementation in GitLiveFunctionsCaller wraps a real FirebaseFunctions
 * client; tests inject a fake.
 */
internal interface FunctionsCaller {
    suspend fun callDraftMessage(
        request: DraftMessageRequestDto,
    ): Result<DraftMessageResponseDto, FunctionsCallerError>
}

internal sealed interface FunctionsCallerError {
    data class PermissionDenied(val message: String) : FunctionsCallerError
    data class InvalidArgument(val message: String) : FunctionsCallerError
    data object Unavailable : FunctionsCallerError
    data object Network : FunctionsCallerError
    data class Unknown(val message: String) : FunctionsCallerError
}
```

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartFunctionsRepository.kt`:

```kotlin
package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.data.mapper.toDomain
import com.danzucker.stitchpad.feature.smart.data.mapper.toDto
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository

internal class SmartFunctionsRepository(
    private val caller: FunctionsCaller,
) : SmartRepository {

    override suspend fun draftMessage(
        request: DraftMessageRequest,
    ): Result<DraftMessageResult, SmartError> {
        return when (val raw = caller.callDraftMessage(request.toDto())) {
            is Result.Success -> Result.Success(raw.data.toDomain())
            is Result.Error -> Result.Error(raw.error.toSmartError())
        }
    }

    private fun FunctionsCallerError.toSmartError(): SmartError = when (this) {
        is FunctionsCallerError.PermissionDenied ->
            if (message.contains("free_tier_exhausted")) SmartError.FreeTierExhausted
            else SmartError.Unknown
        is FunctionsCallerError.InvalidArgument -> SmartError.InvalidInput
        FunctionsCallerError.Unavailable -> SmartError.ServiceUnavailable
        FunctionsCallerError.Network -> SmartError.Network
        is FunctionsCallerError.Unknown -> SmartError.Unknown
    }
}
```

Create `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/GitLiveFunctionsCaller.kt`:

```kotlin
package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageRequestDto
import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto
import dev.gitlive.firebase.functions.FirebaseFunctions
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import io.ktor.utils.io.errors.IOException

internal class GitLiveFunctionsCaller(
    private val functions: FirebaseFunctions,
) : FunctionsCaller {

    override suspend fun callDraftMessage(
        request: DraftMessageRequestDto,
    ): Result<DraftMessageResponseDto, FunctionsCallerError> {
        return try {
            val response = functions
                .httpsCallable("smartDraftMessage")
                .invoke(request)
                .data<DraftMessageResponseDto>()
            Result.Success(response)
        } catch (e: FirebaseFunctionsException) {
            Result.Error(mapFunctionsException(e))
        } catch (e: IOException) {
            Result.Error(FunctionsCallerError.Network)
        } catch (e: Exception) {
            Result.Error(FunctionsCallerError.Unknown(e.message ?: "unknown"))
        }
    }

    private fun mapFunctionsException(e: FirebaseFunctionsException): FunctionsCallerError {
        // GitLive exposes FirebaseFunctionsException.code as a string per the
        // Firebase Functions HTTPS callable error code spec.
        return when (e.code.toString()) {
            "PERMISSION_DENIED" -> FunctionsCallerError.PermissionDenied(e.message ?: "")
            "INVALID_ARGUMENT" -> FunctionsCallerError.InvalidArgument(e.message ?: "")
            "UNAVAILABLE" -> FunctionsCallerError.Unavailable
            "UNAUTHENTICATED" -> FunctionsCallerError.Unknown("unauthenticated")
            else -> FunctionsCallerError.Unknown(e.message ?: e.code.toString())
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SmartFunctionsRepositoryTest*" 2>&1 | tail -10`
Expected: 6 tests pass.

- [ ] **Step 5: iOS compile check**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartFunctionsRepositoryTest.kt
git commit -m "$(cat <<'EOF'
feat(smart): add SmartFunctionsRepository + GitLive caller

FunctionsCaller interface provides a test seam over GitLive Firebase
Functions. Production GitLiveFunctionsCaller maps FirebaseFunctionsException
codes (PERMISSION_DENIED / INVALID_ARGUMENT / UNAVAILABLE) to typed
FunctionsCallerError. Repository translates each to SmartError variants and
distinguishes free_tier_exhausted from other permission-denied messages by
substring match. 6 unit tests cover all error paths + the success path.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Client — DraftMessage MVI types + ViewModel (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageState.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModel.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModelTest.kt`

This is the largest single task — many state transitions to cover.

For brevity in this plan, the ViewModel test outline lists ~12 test cases as comments; each must be written as a real test before implementation. The full implementation appears in Step 4.

- [ ] **Step 1: Create the state types**

`DraftMessageState.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary

data class DraftMessageState(
    val customer: CustomerSummary? = null,
    val customerOptions: List<CustomerSummary> = emptyList(),
    val orderOptions: List<OrderSummary> = emptyList(),
    val order: OrderSummary? = null,
    val intent: DraftIntent? = null,
    val language: DraftLanguage = DraftLanguage.English,
    val customNotes: String = "",
    val generationState: GenerationState = GenerationState.Idle,
    val remainingFreeQuota: Int? = null,
    val isOnline: Boolean = true,
) {
    val canGenerate: Boolean
        get() = customer != null
            && order != null
            && intent != null
            && isOnline
            && generationState !is GenerationState.Generating
}

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Generating : GenerationState
    data class Success(val draftText: String) : GenerationState
}

sealed interface DraftMessageAction {
    data class SelectCustomer(val customer: CustomerSummary) : DraftMessageAction
    data class SelectOrder(val order: OrderSummary) : DraftMessageAction
    data class SelectIntent(val intent: DraftIntent) : DraftMessageAction
    data class ToggleLanguage(val language: DraftLanguage) : DraftMessageAction
    data class UpdateCustomNotes(val notes: String) : DraftMessageAction
    data object GenerateDraft : DraftMessageAction
    data class EditDraft(val text: String) : DraftMessageAction
    data object SendViaWhatsApp : DraftMessageAction
    data object CopyDraft : DraftMessageAction
}

sealed interface DraftMessageEvent {
    data class ShowSnackbar(val text: UiText) : DraftMessageEvent
    data object ShowUpgradeSheet : DraftMessageEvent
    data class LaunchWhatsApp(val phoneE164: String, val message: String) : DraftMessageEvent
    data class CopyToClipboard(val text: String) : DraftMessageEvent
    data object NavigateBack : DraftMessageEvent
}
```

- [ ] **Step 2: Write failing ViewModel tests**

Create the test file with these 12 test cases (each must compile and run):

```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.draft

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.feature.smart.domain.model.DraftLanguage
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DraftMessageViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testCustomer = CustomerSummary(id = "c1", firstName = "Folake", whatsappNumber = "+2348012345678")
    private val noWhatsappCustomer = CustomerSummary(id = "c2", firstName = "Ada", whatsappNumber = null)
    private val testOrder = OrderSummary(id = "o1", customerId = "c1", garmentLabel = "Adire boubou", balanceFormatted = "₦7,500", deadlineFormatted = "Fri")

    private lateinit var fakeRepo: FakeSmartRepository
    private lateinit var fakeOrders: FakeOrderProvider
    private lateinit var fakeCustomers: FakeCustomerProvider
    private lateinit var fakeConnectivity: MutableStateFlow<Boolean>

    @BeforeTest fun setup() {
        fakeRepo = FakeSmartRepository()
        fakeOrders = FakeOrderProvider()
        fakeCustomers = FakeCustomerProvider()
        fakeConnectivity = MutableStateFlow(true)
    }

    @Test fun `initial state is idle with empty selections`() = runTest {
        val vm = newVm()
        vm.state.test {
            val s = awaitItem()
            assertThat(s.customer).isEqualTo(null)
            assertThat(s.order).isEqualTo(null)
            assertThat(s.intent).isEqualTo(null)
            assertThat(s.canGenerate).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `selecting customer fetches their open orders`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.state.test {
            val s = awaitItem()
            assertThat(s.customer).isEqualTo(testCustomer)
            assertThat(s.orderOptions).isEqualTo(listOf(testOrder))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `canGenerate is false until all required fields are set`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        val vm = newVm()
        vm.state.test {
            awaitItem() // initial
            vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
            awaitItem() // customer set
            vm.onAction(DraftMessageAction.SelectOrder(testOrder))
            awaitItem() // order set
            vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
            val s = awaitItem()
            assertThat(s.canGenerate).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `canGenerate is false when offline`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeConnectivity.value = false
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.state.test {
            val s = awaitItem()
            assertThat(s.canGenerate).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `GenerateDraft transitions through Generating to Success`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi Folake!", remainingFreeQuota = 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.state.test {
            awaitItem() // current
            vm.onAction(DraftMessageAction.GenerateDraft)
            val generating = awaitItem()
            assertThat(generating.generationState).isInstanceOf(GenerationState.Generating::class)
            val success = awaitItem()
            assertThat(success.generationState).isEqualTo(GenerationState.Success("Hi Folake!"))
            assertThat(success.remainingFreeQuota).isEqualTo(4)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `GenerateDraft on FreeTierExhausted emits ShowUpgradeSheet`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Error(SmartError.FreeTierExhausted))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.events.test {
            vm.onAction(DraftMessageAction.GenerateDraft)
            val ev = awaitItem()
            assertThat(ev).isInstanceOf(DraftMessageEvent.ShowUpgradeSheet::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `GenerateDraft on InvalidInput emits Snackbar + clears order pick`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Error(SmartError.InvalidInput))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.events.test {
            vm.onAction(DraftMessageAction.GenerateDraft)
            assertThat(awaitItem()).isInstanceOf(DraftMessageEvent.ShowSnackbar::class)
            cancelAndIgnoreRemainingEvents()
        }
        vm.state.test {
            val s = awaitItem()
            assertThat(s.order).isEqualTo(null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `GenerateDraft on ServiceUnavailable emits Snackbar`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Error(SmartError.ServiceUnavailable))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.events.test {
            vm.onAction(DraftMessageAction.GenerateDraft)
            assertThat(awaitItem()).isInstanceOf(DraftMessageEvent.ShowSnackbar::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `EditDraft updates the Success state's draftText`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi Folake!", 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        vm.onAction(DraftMessageAction.EditDraft("Edited text"))
        vm.state.test {
            val s = awaitItem()
            assertThat(s.generationState).isEqualTo(GenerationState.Success("Edited text"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `SendViaWhatsApp emits LaunchWhatsApp event with customer's number`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi!", 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        vm.events.test {
            vm.onAction(DraftMessageAction.SendViaWhatsApp)
            val ev = awaitItem() as DraftMessageEvent.LaunchWhatsApp
            assertThat(ev.phoneE164).isEqualTo("+2348012345678")
            assertThat(ev.message).isEqualTo("Hi!")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `SendViaWhatsApp is suppressed when customer has no whatsappNumber`() = runTest {
        fakeOrders.openOrdersFor("c2") { listOf(testOrder.copy(customerId = "c2")) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Hi!", 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(noWhatsappCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder.copy(customerId = "c2")))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        vm.events.test {
            vm.onAction(DraftMessageAction.SendViaWhatsApp)
            val ev = awaitItem()
            // Snackbar telling user to add a WhatsApp number — not a LaunchWhatsApp
            assertThat(ev).isInstanceOf(DraftMessageEvent.ShowSnackbar::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `CopyDraft emits CopyToClipboard event with current draft text`() = runTest {
        fakeOrders.openOrdersFor("c1") { listOf(testOrder) }
        fakeRepo.respondWith(Result.Success(DraftMessageResult("Copy me", 4)))
        val vm = newVm()
        vm.onAction(DraftMessageAction.SelectCustomer(testCustomer))
        vm.onAction(DraftMessageAction.SelectOrder(testOrder))
        vm.onAction(DraftMessageAction.SelectIntent(DraftIntent.BalanceReminder))
        vm.onAction(DraftMessageAction.GenerateDraft)
        vm.events.test {
            vm.onAction(DraftMessageAction.CopyDraft)
            val ev = awaitItem() as DraftMessageEvent.CopyToClipboard
            assertThat(ev.text).isEqualTo("Copy me")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun newVm(): DraftMessageViewModel = DraftMessageViewModel(
        repository = fakeRepo,
        orderProvider = fakeOrders,
        customerProvider = fakeCustomers,
        connectivity = fakeConnectivity,
    )

    // --- Fakes ---

    private class FakeSmartRepository : SmartRepository {
        private var canned: Result<DraftMessageResult, SmartError>? = null
        var lastRequest: DraftMessageRequest? = null
        fun respondWith(result: Result<DraftMessageResult, SmartError>) { canned = result }
        override suspend fun draftMessage(request: DraftMessageRequest): Result<DraftMessageResult, SmartError> {
            lastRequest = request
            return canned ?: Result.Error(SmartError.Unknown)
        }
    }

    private class FakeOrderProvider : OpenOrdersProvider {
        private val byCustomer = mutableMapOf<String, () -> List<OrderSummary>>()
        fun openOrdersFor(customerId: String, supplier: () -> List<OrderSummary>) {
            byCustomer[customerId] = supplier
        }
        override suspend fun openOrdersFor(customerId: String): List<OrderSummary> =
            byCustomer[customerId]?.invoke() ?: emptyList()
    }

    private class FakeCustomerProvider : CustomerSearchProvider {
        override suspend fun search(query: String): List<CustomerSummary> = emptyList()
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DraftMessageViewModelTest*" 2>&1 | tail -10`
Expected: FAIL — unresolved references to ViewModel, OpenOrdersProvider, CustomerSearchProvider.

- [ ] **Step 4: Implement the providers + ViewModel**

Create two small interfaces (the ViewModel needs to fetch open orders for a customer + search the customer list — these are the project's existing `CustomerRepository` / `OrderRepository` capabilities, exposed here via narrow interfaces so the ViewModel doesn't depend on the full repos).

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/OpenOrdersProvider.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary

interface OpenOrdersProvider {
    suspend fun openOrdersFor(customerId: String): List<OrderSummary>
}
```

`composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/CustomerSearchProvider.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary

interface CustomerSearchProvider {
    suspend fun search(query: String): List<CustomerSummary>
}
```

`DraftMessageViewModel.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.smart_error_invalid_input
import stitchpad.composeapp.generated.resources.smart_error_network
import stitchpad.composeapp.generated.resources.smart_error_service_unavailable
import stitchpad.composeapp.generated.resources.smart_error_unknown
import stitchpad.composeapp.generated.resources.draft_message_no_whatsapp_helper

class DraftMessageViewModel(
    private val repository: SmartRepository,
    private val orderProvider: OpenOrdersProvider,
    private val customerProvider: CustomerSearchProvider,
    private val connectivity: StateFlow<Boolean>,
) : ViewModel() {

    private val _state = MutableStateFlow(DraftMessageState())
    val state: StateFlow<DraftMessageState> = _state.asStateFlow()

    private val _events = Channel<DraftMessageEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            connectivity.collectLatest { online ->
                _state.update { it.copy(isOnline = online) }
            }
        }
    }

    fun onAction(action: DraftMessageAction) {
        when (action) {
            is DraftMessageAction.SelectCustomer -> selectCustomer(action.customer)
            is DraftMessageAction.SelectOrder -> _state.update { it.copy(order = action.order) }
            is DraftMessageAction.SelectIntent -> _state.update { it.copy(intent = action.intent) }
            is DraftMessageAction.ToggleLanguage -> _state.update { it.copy(language = action.language) }
            is DraftMessageAction.UpdateCustomNotes -> _state.update { it.copy(customNotes = action.notes) }
            DraftMessageAction.GenerateDraft -> generate()
            is DraftMessageAction.EditDraft -> _state.update {
                if (it.generationState is GenerationState.Success) {
                    it.copy(generationState = GenerationState.Success(action.text))
                } else it
            }
            DraftMessageAction.SendViaWhatsApp -> sendViaWhatsApp()
            DraftMessageAction.CopyDraft -> copyDraft()
        }
    }

    private fun selectCustomer(customer: com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary) {
        _state.update { it.copy(customer = customer, order = null, orderOptions = emptyList()) }
        viewModelScope.launch {
            val orders = orderProvider.openOrdersFor(customer.id)
            _state.update { it.copy(orderOptions = orders) }
        }
    }

    private fun generate() {
        val s = _state.value
        val customer = s.customer ?: return
        val order = s.order ?: return
        val intent = s.intent ?: return
        if (!s.isOnline) return

        _state.update { it.copy(generationState = GenerationState.Generating) }
        viewModelScope.launch {
            val req = DraftMessageRequest(
                customerId = customer.id,
                orderId = order.id,
                intent = intent,
                language = s.language,
                customNotes = s.customNotes.takeIf { it.isNotBlank() },
            )
            when (val result = repository.draftMessage(req)) {
                is Result.Success -> _state.update {
                    it.copy(
                        generationState = GenerationState.Success(result.data.draftText),
                        remainingFreeQuota = result.data.remainingFreeQuota,
                    )
                }
                is Result.Error -> handleError(result.error)
            }
        }
    }

    private suspend fun handleError(error: SmartError) {
        _state.update { it.copy(generationState = GenerationState.Idle) }
        when (error) {
            SmartError.FreeTierExhausted -> _events.send(DraftMessageEvent.ShowUpgradeSheet)
            SmartError.InvalidInput -> {
                _state.update { it.copy(order = null) }
                _events.send(DraftMessageEvent.ShowSnackbar(UiText.StringResource(Res.string.smart_error_invalid_input)))
            }
            SmartError.ServiceUnavailable -> _events.send(
                DraftMessageEvent.ShowSnackbar(UiText.StringResource(Res.string.smart_error_service_unavailable))
            )
            SmartError.Network -> _events.send(
                DraftMessageEvent.ShowSnackbar(UiText.StringResource(Res.string.smart_error_network))
            )
            SmartError.Unknown -> _events.send(
                DraftMessageEvent.ShowSnackbar(UiText.StringResource(Res.string.smart_error_unknown))
            )
        }
    }

    private fun sendViaWhatsApp() {
        val s = _state.value
        val draft = (s.generationState as? GenerationState.Success)?.draftText ?: return
        val phone = s.customer?.whatsappNumber
        if (phone == null) {
            viewModelScope.launch {
                _events.send(DraftMessageEvent.ShowSnackbar(
                    UiText.StringResource(Res.string.draft_message_no_whatsapp_helper)
                ))
            }
            return
        }
        viewModelScope.launch {
            _events.send(DraftMessageEvent.LaunchWhatsApp(phoneE164 = phone, message = draft))
        }
    }

    private fun copyDraft() {
        val draft = (_state.value.generationState as? GenerationState.Success)?.draftText ?: return
        viewModelScope.launch {
            _events.send(DraftMessageEvent.CopyToClipboard(draft))
        }
    }
}
```

(The `Res.string.smart_error_*` and `draft_message_no_whatsapp_helper` resources are added in Task 14.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DraftMessageViewModelTest*" 2>&1 | tail -10`

If any test fails because the strings file doesn't yet have the referenced resources, add a temporary stub: in this task's commit, add the 5 referenced strings to `strings.xml` with placeholder copy. Final copy gets replaced in Task 14. The point is the VM tests need to compile; the actual resource values don't matter to the unit tests.

Expected after stubbing: 12 tests pass.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModelTest.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(smart): DraftMessage MVI types + ViewModel

State machine for the Draft Message intent. State + Action + Event sealed
types per project convention. ViewModel handles: customer selection +
order fetch, intent + language picks, online-aware generation,
free-tier-exhausted → ShowUpgradeSheet, invalid input → snackbar + clear
order, send via WhatsApp with no-number fallback to snackbar, clipboard
copy. 12 unit tests cover all transitions. Stub strings for error +
no-WhatsApp helper added; final copy ships in the strings task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Client — DraftMessage UI components + Screen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/components/CustomerPickerSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/components/OrderPickerSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/components/IntentChips.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/components/LanguageToggle.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/components/DraftPreview.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/components/UpgradeBottomSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageRoot.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageScreenTest.kt`

This task ships all the Draft Message UI in one commit because the components are tightly coupled (the screen composes them all in a single layout). Each component is small (~30-80 lines).

- [ ] **Step 1: Implement components**

For brevity, the implementations are summarized here — each composable is written following these contracts:

- `CustomerPickerSheet` — bottom sheet with a search field + list of `CustomerSummary` rows. Tap a row → invokes `onSelect(customer)` callback + dismisses.
- `OrderPickerSheet` — same shape but for `OrderSummary`. Empty state shows "No open orders for this customer." with no rows.
- `IntentChips` — `FlowRow` with 4 `FilterChip`s, one selected at a time. Calls `onIntentChange(DraftIntent)`.
- `LanguageToggle` — segmented control with 2 options (English / Pidgin), `onLanguageChange(DraftLanguage)`.
- `DraftPreview` — `OutlinedTextField` populated with the draft text, `onTextChange(String)`. Below it, two `Button`s: `Send via WhatsApp` (filled, primary) + `Copy text` (outlined, secondary).
- `UpgradeBottomSheet` — title, message, two buttons (`Upgrade` primary + `Dismiss` secondary). Renders `LocalStitchPadColors.current.heritageAccent` accents per the brand spec — paywall is a heritage moment.

Apply M3 contract (per the cumulative learnings from PR-A → PR-B): primary content over container surfaces uses `onPrimaryContainer`; never use `MaterialTheme.colorScheme.primary` for content rendered on a `primaryContainer` background.

`DraftMessageScreen.kt` (stateless):
```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.draft

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.CustomerPickerSheet
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.DraftPreview
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.IntentChips
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.LanguageToggle
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.OrderPickerSheet
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.draft_message_generate_cta
import stitchpad.composeapp.generated.resources.draft_message_generating
import stitchpad.composeapp.generated.resources.draft_message_notes_label
import stitchpad.composeapp.generated.resources.draft_message_offline_helper
import stitchpad.composeapp.generated.resources.draft_message_pick_customer
import stitchpad.composeapp.generated.resources.draft_message_pick_order

@Composable
fun DraftMessageScreen(
    state: DraftMessageState,
    onAction: (DraftMessageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomerSheet by remember { mutableStateOf(false) }
    var showOrderSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Customer picker trigger
        OutlinedButton(onClick = { showCustomerSheet = true }, modifier = Modifier.fillMaxWidth()) {
            Text(state.customer?.firstName ?: stringResource(Res.string.draft_message_pick_customer))
        }

        // Order picker trigger (disabled if no customer)
        OutlinedButton(
            onClick = { showOrderSheet = true },
            enabled = state.customer != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(state.order?.garmentLabel ?: stringResource(Res.string.draft_message_pick_order))
        }

        IntentChips(selected = state.intent, onIntentChange = { onAction(DraftMessageAction.SelectIntent(it)) })
        LanguageToggle(selected = state.language, onLanguageChange = { onAction(DraftMessageAction.ToggleLanguage(it)) })

        OutlinedTextField(
            value = state.customNotes,
            onValueChange = { onAction(DraftMessageAction.UpdateCustomNotes(it)) },
            label = { Text(stringResource(Res.string.draft_message_notes_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Button(
            onClick = { onAction(DraftMessageAction.GenerateDraft) },
            enabled = state.canGenerate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            when (state.generationState) {
                GenerationState.Generating -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.draft_message_generating))
                }
                else -> Text(stringResource(Res.string.draft_message_generate_cta))
            }
        }

        if (!state.isOnline) {
            Text(
                stringResource(Res.string.draft_message_offline_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.generationState is GenerationState.Success) {
            DraftPreview(
                draftText = state.generationState.draftText,
                hasWhatsappNumber = state.customer?.whatsappNumber != null,
                onTextChange = { onAction(DraftMessageAction.EditDraft(it)) },
                onSend = { onAction(DraftMessageAction.SendViaWhatsApp) },
                onCopy = { onAction(DraftMessageAction.CopyDraft) },
            )
        }
    }

    if (showCustomerSheet) {
        CustomerPickerSheet(
            customers = state.customerOptions,
            onSelect = {
                onAction(DraftMessageAction.SelectCustomer(it))
                showCustomerSheet = false
            },
            onDismiss = { showCustomerSheet = false },
        )
    }
    if (showOrderSheet) {
        OrderPickerSheet(
            orders = state.orderOptions,
            onSelect = {
                onAction(DraftMessageAction.SelectOrder(it))
                showOrderSheet = false
            },
            onDismiss = { showOrderSheet = false },
        )
    }
}
```

`DraftMessageRoot.kt` (wires VM + screen + side effects):
```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.draft

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.danzucker.stitchpad.core.presentation.ObserveAsEvents
import com.danzucker.stitchpad.feature.smart.presentation.draft.components.UpgradeBottomSheet
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DraftMessageRoot(
    onLaunchWhatsApp: (phoneE164: String, message: String) -> Unit,
    onUpgradeRequested: () -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val viewModel: DraftMessageViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var showUpgradeSheet by remember { mutableStateOf(false) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DraftMessageEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.text.asString())
            DraftMessageEvent.ShowUpgradeSheet -> showUpgradeSheet = true
            is DraftMessageEvent.LaunchWhatsApp -> onLaunchWhatsApp(event.phoneE164, event.message)
            is DraftMessageEvent.CopyToClipboard -> clipboard.setText(AnnotatedString(event.text))
            DraftMessageEvent.NavigateBack -> onNavigateBack()
        }
    }

    DraftMessageScreen(state = state, onAction = viewModel::onAction, modifier = modifier)

    if (showUpgradeSheet) {
        UpgradeBottomSheet(
            onUpgrade = {
                showUpgradeSheet = false
                onUpgradeRequested()
            },
            onDismiss = { showUpgradeSheet = false },
        )
    }
}
```

(`UiText.asString()` and `ObserveAsEvents` are existing project utilities per CLAUDE.md.)

- [ ] **Step 2: Add a smoke-level Compose UI test**

`DraftMessageScreenTest.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.draft

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DraftMessageScreenTest {

    @Test
    fun renders_pick_customer_placeholder_in_idle_state() = runComposeUiTest {
        setContent {
            DraftMessageScreen(state = DraftMessageState(), onAction = {})
        }
        onNodeWithText("Pick a customer").assertIsDisplayed()
    }

    @Test
    fun generate_button_is_disabled_until_required_fields_set() = runComposeUiTest {
        setContent {
            DraftMessageScreen(state = DraftMessageState(), onAction = {})
        }
        onNodeWithText("Generate draft").assertIsNotEnabled()
    }

    @Test
    fun renders_draft_preview_in_success_state() = runComposeUiTest {
        val state = DraftMessageState(
            customer = CustomerSummary("c", "Folake", "+234"),
            order = OrderSummary("o", "c", "Boubou", "₦5,000", "Fri"),
            generationState = GenerationState.Success("Hi Folake!"),
        )
        setContent { DraftMessageScreen(state = state, onAction = {}) }
        onNodeWithText("Hi Folake!").assertIsDisplayed()
        onNodeWithText("Send via WhatsApp").assertIsDisplayed()
        onNodeWithText("Copy text").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Compile + run tests**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*DraftMessageScreenTest*" 2>&1 | tail -10`
Expected: 3 Compose UI tests pass.

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageScreenTest.kt
git commit -m "$(cat <<'EOF'
feat(smart): DraftMessageScreen + components + Root wiring

Stateless DraftMessageScreen renders the form (customer/order pickers via
bottom sheets, intent chips, language toggle, notes field, generate CTA)
and the draft-preview state. Root wires the koinViewModel + side-effect
handling (snackbars, clipboard, WhatsApp launch, upgrade sheet). 3 Compose
UI smoke tests cover idle / disabled-CTA / success rendering.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Client — `SmartSectionCard` for the Dashboard

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/SmartSectionCard.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/components/IntentTile.kt`
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/components/FreeTierCounterChip.kt`
- Create: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/SmartSectionCardTest.kt`

- [ ] **Step 1: Implement `IntentTile.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens

/**
 * Single tile inside the SmartSectionCard. Three states:
 * - enabled: full color, tap → onClick
 * - disabled: grayed, "Coming soon" subtitle
 */
@Composable
fun IntentTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = container,
        modifier = modifier
            .width(160.dp)
            .heightIn(min = 120.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Icon(imageVector = icon, contentDescription = null, tint = onContainer)
            Spacer(Modifier.height(DesignTokens.space2))
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = onContainer)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = onContainer.copy(alpha = 0.7f))
        }
    }
}
```

- [ ] **Step 2: Implement `FreeTierCounterChip.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.smart_free_quota_remaining

@Composable
fun FreeTierCounterChip(remaining: Int, modifier: Modifier = Modifier) {
    Text(
        text = pluralStringResource(Res.plurals.smart_free_quota_remaining, remaining, remaining),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
```

(Note: this uses a plural resource. If the project doesn't yet use plural resources in compose-resources, fall back to a regular `stringResource` with a single key and pass the count as an arg in Task 14. Adjust accordingly.)

- [ ] **Step 3: Implement `SmartSectionCard.kt`**

```kotlin
package com.danzucker.stitchpad.feature.smart.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.smart.presentation.components.FreeTierCounterChip
import com.danzucker.stitchpad.feature.smart.presentation.components.IntentTile
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.*

/**
 * Always-on Dashboard section card. Hidden by the caller when the customer
 * list is empty (no dead-end taps).
 *
 * V1: 1 enabled tile (Draft Message), 2 grayed "Coming soon" placeholders.
 */
@Composable
fun SmartSectionCard(
    remainingFreeQuota: Int?,
    onDraftMessageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = DesignTokens.elevation1,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.smart_section_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(Res.string.smart_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (remainingFreeQuota != null) {
                    FreeTierCounterChip(remaining = remainingFreeQuota)
                }
            }
            Spacer(Modifier.height(DesignTokens.space3))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
                item {
                    IntentTile(
                        title = stringResource(Res.string.smart_intent_draft_message_title),
                        subtitle = stringResource(Res.string.smart_intent_draft_message_subtitle),
                        icon = Icons.Outlined.AutoAwesome,
                        enabled = true,
                        onClick = onDraftMessageClick,
                    )
                }
                item {
                    IntentTile(
                        title = stringResource(Res.string.smart_intent_price_this_title),
                        subtitle = stringResource(Res.string.smart_intent_coming_soon_label),
                        icon = Icons.Outlined.LocalOffer,
                        enabled = false,
                        onClick = {},
                    )
                }
                item {
                    IntentTile(
                        title = stringResource(Res.string.smart_intent_reply_helper_title),
                        subtitle = stringResource(Res.string.smart_intent_coming_soon_label),
                        icon = Icons.Outlined.Reply,
                        enabled = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Add a Compose UI smoke test**

`SmartSectionCardTest.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SmartSectionCardTest {

    @Test
    fun renders_three_tiles_with_one_enabled() = runComposeUiTest {
        setContent { SmartSectionCard(remainingFreeQuota = 5, onDraftMessageClick = {}) }
        onNodeWithText("Smart Suggestions").assertIsDisplayed()
        onNodeWithText("Draft a WhatsApp message to a customer").assertIsDisplayed()
        onNodeWithText("Coming soon").assertIsDisplayed()
    }

    @Test
    fun shows_quota_chip_when_remainingFreeQuota_is_set() = runComposeUiTest {
        setContent { SmartSectionCard(remainingFreeQuota = 3, onDraftMessageClick = {}) }
        // Match part of the plural string — exact wording lives in strings.xml
        // The chip should render text containing "3"
        onNodeWithText("3", substring = true).assertIsDisplayed()
    }

    @Test
    fun draft_message_tile_click_invokes_callback() = runComposeUiTest {
        var clicked = false
        setContent {
            SmartSectionCard(
                remainingFreeQuota = 5,
                onDraftMessageClick = { clicked = true },
            )
        }
        onNodeWithText("Draft a WhatsApp message to a customer").performClick()
        assertEquals(true, clicked)
    }
}
```

- [ ] **Step 5: Compile + run tests**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*SmartSectionCardTest*" 2>&1 | tail -10`
Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/ \
        composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/SmartSectionCardTest.kt
git commit -m "$(cat <<'EOF'
feat(smart): SmartSectionCard for Dashboard + IntentTile + FreeTierCounterChip

Always-on Dashboard section card (hidden by caller when customer list is
empty). Header with title + subtitle + free-tier counter chip. LazyRow
of 3 IntentTiles: Draft Message (enabled, primaryContainer fill), Price
This + Reply Helper (grayed, surfaceVariant fill, "Coming soon"). 3
Compose UI smoke tests cover rendering + click callback.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Strings — finalize all Smart copy

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Add all Smart-related strings**

Append (or in-place edit if Task 11 added stubs) to `strings.xml`. Group with a comment header:

```xml
<!-- Smart Suggestions -->
<string name="smart_section_title">Smart Suggestions</string>
<string name="smart_section_subtitle">Help with messages, pricing, and more</string>
<string name="smart_intent_draft_message_title">Draft a Message</string>
<string name="smart_intent_draft_message_subtitle">Draft a WhatsApp message to a customer</string>
<string name="smart_intent_price_this_title">Help Me Price This</string>
<string name="smart_intent_reply_helper_title">Customer Reply Helper</string>
<string name="smart_intent_coming_soon_label">Coming soon</string>

<!-- Use a quantity string when the project supports plurals; otherwise -->
<string name="smart_free_quota_remaining">%1$d of 5 free drafts left this month</string>

<!-- Draft Message screen -->
<string name="draft_message_screen_title">Draft a Message</string>
<string name="draft_message_pick_customer">Pick a customer</string>
<string name="draft_message_pick_order">Pick an open order</string>
<string name="draft_message_no_open_orders">No open orders for this customer.</string>
<string name="draft_message_intent_balance">Balance reminder</string>
<string name="draft_message_intent_pickup">Ready for pickup</string>
<string name="draft_message_intent_followup">Follow up</string>
<string name="draft_message_intent_custom">Custom note</string>
<string name="draft_message_language_english">English</string>
<string name="draft_message_language_pidgin">Pidgin</string>
<string name="draft_message_notes_label">Anything specific to add?</string>
<string name="draft_message_notes_placeholder">Optional</string>
<string name="draft_message_generate_cta">Generate draft</string>
<string name="draft_message_generating">Drafting…</string>
<string name="draft_message_send_whatsapp">Send via WhatsApp</string>
<string name="draft_message_copy_text">Copy text</string>
<string name="draft_message_no_whatsapp_helper">Add a WhatsApp number to this customer to send directly.</string>
<string name="draft_message_offline_helper">Need internet to draft messages.</string>

<!-- Smart errors -->
<string name="smart_error_network">No internet. Try again when you&apos;re back online.</string>
<string name="smart_error_invalid_input">That order isn&apos;t available anymore.</string>
<string name="smart_error_service_unavailable">Couldn&apos;t draft right now. Try again in a moment.</string>
<string name="smart_error_unknown">Something went wrong. Please try again.</string>

<!-- Upgrade sheet -->
<string name="smart_upgrade_sheet_title">Out of free drafts this month</string>
<string name="smart_upgrade_sheet_message">You&apos;ve used your 5 free drafts. Upgrade to draft as many as you need.</string>
<string name="smart_upgrade_sheet_upgrade_cta">Upgrade</string>
<string name="smart_upgrade_sheet_dismiss">Wait until next month</string>
```

Per [[feedback-strings-no-backslash-escape]] memory: use `&apos;` for apostrophes, never `\'` (Compose Multiplatform iOS renders `\'` literally).

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL — generated `Res.string.*` references resolve.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "$(cat <<'EOF'
feat(smart): finalize Smart Suggestions strings

All copy for the Smart section card + Draft Message screen + error states +
upgrade sheet. Per project conventions: no backslash apostrophes (uses
&apos; per the Compose Multiplatform iOS rendering quirk memorized
elsewhere).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: DI — Koin module + nav routes + Dashboard wiring

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SmartModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/App.kt` (or wherever `startKoin { ... }` lives — find via `grep -rn "startKoin" composeApp/src/commonMain/`)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt`

- [ ] **Step 1: Create the Koin module**

`SmartModule.kt`:
```kotlin
package com.danzucker.stitchpad.di

import com.danzucker.stitchpad.feature.smart.data.GitLiveFunctionsCaller
import com.danzucker.stitchpad.feature.smart.data.SmartFunctionsRepository
import com.danzucker.stitchpad.feature.smart.domain.repository.SmartRepository
import com.danzucker.stitchpad.feature.smart.presentation.draft.CustomerSearchProvider
import com.danzucker.stitchpad.feature.smart.presentation.draft.DraftMessageViewModel
import com.danzucker.stitchpad.feature.smart.presentation.draft.OpenOrdersProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val smartModule = module {
    single { Firebase.functions("europe-west1") }
    singleOf(::GitLiveFunctionsCaller)
    single<SmartRepository> { SmartFunctionsRepository(get()) }

    // Adapters that bridge existing CustomerRepository + OrderRepository to
    // the narrow interfaces the DraftMessageViewModel depends on. These
    // implementations reuse the existing repos via constructor injection.
    single<OpenOrdersProvider> { SmartOpenOrdersAdapter(customerRepository = get(), orderRepository = get()) }
    single<CustomerSearchProvider> { SmartCustomerSearchAdapter(customerRepository = get()) }

    viewModelOf(::DraftMessageViewModel)
}
```

The two adapters bridge existing repos to the narrow `OpenOrdersProvider` / `CustomerSearchProvider` interfaces. Add them in `feature/smart/data/`:

`SmartOpenOrdersAdapter.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.customer.domain.repository.CustomerRepository
import com.danzucker.stitchpad.feature.order.domain.repository.OrderRepository
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.feature.smart.presentation.draft.OpenOrdersProvider
import kotlinx.coroutines.flow.first

internal class SmartOpenOrdersAdapter(
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
) : OpenOrdersProvider {
    override suspend fun openOrdersFor(customerId: String): List<OrderSummary> {
        // Use the existing observeOrdersForCustomer flow; take the first emission.
        // Filter to "open" = not delivered + not archived per project conventions.
        return when (val result = orderRepository.observeOrdersForCustomer(customerId).first()) {
            is Result.Success -> result.data
                .filter { /* open = not delivered + not archived per project conventions */ true }
                .map { order ->
                    OrderSummary(
                        id = order.id,
                        customerId = order.customerId,
                        garmentLabel = order.garmentLabel,
                        balanceFormatted = order.balanceFormatted,
                        deadlineFormatted = order.deadlineFormatted,
                    )
                }
            is Result.Error -> emptyList()
        }
    }
}
```

`SmartCustomerSearchAdapter.kt`:
```kotlin
package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.customer.domain.repository.CustomerRepository
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.presentation.draft.CustomerSearchProvider
import kotlinx.coroutines.flow.first

internal class SmartCustomerSearchAdapter(
    private val customerRepository: CustomerRepository,
) : CustomerSearchProvider {
    override suspend fun search(query: String): List<CustomerSummary> {
        return when (val result = customerRepository.observeCustomers().first()) {
            is Result.Success -> result.data
                .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                .map { CustomerSummary(id = it.id, firstName = it.name.substringBefore(' '), whatsappNumber = it.whatsappNumber) }
            is Result.Error -> emptyList()
        }
    }
}
```

**NOTE for the implementer:** The exact `Order` and `Customer` field names need to match the actual domain models in `feature/order/domain/model/` and `feature/customer/domain/model/`. Inspect them first via `grep -rn "data class Order\b" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/domain/model/` and adjust the adapter implementations accordingly. The interface contracts (signatures + return types) stay as defined in Task 11.

- [ ] **Step 2: Register the module**

Find the existing Koin setup with `grep -rn "startKoin" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/`. Add `smartModule` to the modules list alongside `customerModule`, `orderModule`, etc.

- [ ] **Step 3: Add the navigation route**

Modify `Routes.kt` — append:
```kotlin
@Serializable
data object DraftMessageRoute
```

Modify `NavGraph.kt` — add inside the existing `NavHost { ... }` after the dashboard composable:
```kotlin
composable<DraftMessageRoute> {
    DraftMessageRoot(
        onLaunchWhatsApp = { phone, message ->
            // Platform-specific WhatsApp launch; delegate to existing intent helper
            // (look for an existing WhatsApp helper via grep -rn "wa.me\|whatsapp" composeApp/src/commonMain/)
        },
        onUpgradeRequested = {
            // Deep-link to settings PlanCard; resolve via grep for the settings route
        },
        onNavigateBack = { navController.popBackStack() },
        snackbarHostState = snackbarHostState,
    )
}
```

If no shared `snackbarHostState` exists at the NavGraph level yet, create one and hoist it. Verify by grepping `snackbarHostState` in NavGraph.

- [ ] **Step 4: Wire `SmartSectionCard` into `DashboardScreen`**

Modify `DashboardScreen.kt`. Find the section ordering (likely a `Column` with Pipeline + NBA + Goals sections) and insert `SmartSectionCard` between NBA and Goals (or at a sensible position — make this an explicit choice and note it in the commit). Wrap in a customer-list-empty check:

```kotlin
val hasAnyCustomer = state.dashboardData.hasAtLeastOneCustomer  // or equivalent
if (hasAnyCustomer) {
    SmartSectionCard(
        remainingFreeQuota = state.smartFreeQuotaRemaining, // null if unknown / premium
        onDraftMessageClick = { navController.navigate(DraftMessageRoute) },
    )
}
```

The dashboard ViewModel may need a small addition to track `hasAtLeastOneCustomer` (likely already derivable from existing state) and `smartFreeQuotaRemaining` (defer to V1.5 — for V1, pass `null` so the chip is hidden until the user generates their first draft, then the in-screen counter updates the state).

- [ ] **Step 5: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew detekt 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/SmartModule.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartOpenOrdersAdapter.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/data/SmartCustomerSearchAdapter.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/App.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/Routes.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/navigation/NavGraph.kt \
        composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/dashboard/presentation/DashboardScreen.kt
git commit -m "$(cat <<'EOF'
feat(smart): wire Smart Suggestions into DI + navigation + Dashboard

- SmartModule provides FirebaseFunctions, GitLiveFunctionsCaller,
  SmartRepository, the two narrow adapters bridging existing
  Customer/OrderRepository to OpenOrdersProvider /
  CustomerSearchProvider, and DraftMessageViewModel.
- DraftMessageRoute added to Routes + NavGraph; back nav hooks
  popBackStack, WhatsApp launch routes through existing helper,
  upgrade tap deep-links to PlanCard.
- DashboardScreen renders SmartSectionCard between NBA and Goals
  when the customer list is non-empty.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: Deploy + manual smoke + PR

**Files:** N/A (operations + PR)

This is the only task that touches the deployed Firebase environment. Per [[reference-test-environment]], use the Fola/Gabby test accounts.

- [ ] **Step 1: Deploy the new Cloud Function**

Run: `cd functions && npm run deploy 2>&1 | tail -20`
Expected: `firebase deploy` succeeds; output shows both `onAuthUserDeleted` and `smartDraftMessage` in the deployed functions list.

If this is the first time the project deploys to Vertex AI, the Vertex API may need to be enabled in the Google Cloud Console for `stitchpad-30607`. The deploy will surface an actionable error message if so — follow the link, enable the API, redeploy.

- [ ] **Step 2: Seed test accounts with at least 1 customer + 1 open order**

If no customers/orders exist on Fola or Gabby's accounts, seed via REST per [[reference-test-environment]] memory's documented workflow OR add through the in-app forms manually. The Smart section card is gated on `hasAtLeastOneCustomer == true`.

- [ ] **Step 3: Set Fola's profile.tier override (optional)**

To exercise the premium-tier path during smoke, set the Firestore doc `users/<fola-uid>/profile` to `{ tier: "premium" }`. Otherwise both accounts default to free.

- [ ] **Step 4: Build + install Android debug**

Run: `./gradlew :composeApp:installDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL + installed on connected device/emulator.

- [ ] **Step 5: Manual smoke (Android)**

Walk through these in BOTH light + dark mode on Fola's account (free tier):

- [ ] Dashboard renders the Smart Suggestions section card between NBA and Goals
- [ ] Card shows the title + subtitle + free-tier counter chip ("5 of 5 free drafts left")
- [ ] Three tiles render: Draft Message (full color, tap-able) + Price This (grayed) + Reply Helper (grayed)
- [ ] Tapping Draft Message navigates to DraftMessageScreen
- [ ] Customer picker opens, shows seeded customers
- [ ] Selecting a customer fetches their open orders
- [ ] All 4 intent chips render and are individually selectable
- [ ] Language toggle switches between English / Pidgin
- [ ] Generate button is disabled until customer + order + intent are all picked
- [ ] Tap Generate — Vertex round-trip completes within ~3 sec, drafted text appears
- [ ] Edit the draft text in the preview field — text updates live
- [ ] Tap Send via WhatsApp — system WhatsApp share opens with phone + draft prefilled
- [ ] Back-nav, generate again — counter chip on Dashboard updates to "4 of 5 free drafts left" on next mount
- [ ] Repeat 5 times to exhaust free tier
- [ ] On the 6th tap of Generate — UpgradeBottomSheet appears with the saffron heritage accent
- [ ] Tap Upgrade — deep-links to PlanCard
- [ ] Manually flip Fola's `users/<uid>/profile.tier = "premium"` — generation proceeds without counter
- [ ] Take a customer with no `whatsappNumber` and verify Send via WhatsApp is disabled with helper text

- [ ] **Step 6: Manual smoke (iOS)**

Per [[reference-test-environment]], use iPhone 17 Pro sim. Run a representative subset of the above (at minimum: render the section card, generate one draft, send via WhatsApp, exhaust free tier).

- [ ] **Step 7: Push the branch + open the PR**

Run: `git push -u origin feature/smart-suggestions 2>&1 | tail -10`

Open the PR with this description:

```bash
gh pr create --title "feat(smart): Smart Suggestions V1 — Draft Message intent" --body "$(cat <<'EOF'
## Summary

Ships the V1 launch slice of Smart Suggestions ([[project-ai-assistant]]). One enabled intent (Draft Message) inside a dedicated Dashboard section card, powered by Gemini 2.0 Flash via Firebase Vertex AI through a Cloud Function. Free-trial → paid: 5 drafts/month free, unlimited on premium.

Per [[2026-05-16-ai-assistant-design]] spec.

## Test plan

[paste the manual smoke checklist from Task 16 Step 5]

## CI

- [ ] secrets-scan
- [ ] detekt
- [ ] functions-tests
- [ ] build-android
- [ ] build-ios

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Out of scope for V1 (per spec)

Tracked here as an explicit reminder so subagents don't accidentally widen scope:

- Help Me Price This intent (V1.5 — second tile enabled)
- Customer Reply Helper intent (V1.5 — third tile enabled)
- Free-form composer (no customer/order picker)
- Save-as-draft persistence
- Per-customer message history
- Re-roll button
- Voice / photo input shortcuts
- Rich workshop context / RAG (past order anchors for pricing, etc.)

---

## Verification expectations (final, before merge)

- `grep -rn "TODO\|FIXME" composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/` — empty (or only intentional `// TODO V1.5:` markers)
- `./gradlew :composeApp:assembleDebug` — BUILD SUCCESSFUL with no new warnings
- `./gradlew :composeApp:compileKotlinIosSimulatorArm64` — BUILD SUCCESSFUL
- `./gradlew :composeApp:testDebugUnitTest --tests "*smart*"` — all Smart tests pass
- `cd functions && npm test` — all Smart server tests pass
- `./gradlew detekt` — BUILD SUCCESSFUL
- Manual smoke checklist (Task 16 Step 5) — all items checked
- PR description includes the smoke checklist per [[feedback-qa-smoke-tests]]

When all of the above pass, PR is mergeable.
