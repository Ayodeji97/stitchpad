# Smart Suggestions — V1 Design Spec

**Date:** 2026-05-16
**Status:** Approved (brainstorming complete; ready for implementation plan)
**Memory references:** [[project-ai-assistant]], [[project-rebrand-terminology]], [[project-premium-tier-candidates]], [[feedback-dashboard-philosophy]], [[feedback-notification-patterns]], [[feedback-qa-smoke-tests]], [[feedback-pr-workflow]], [[reference-test-environment]], [[project-debug-menu]]

## Goal

Ship the first slice of StitchPad's tailor-purpose-built AI assistant — branded **Smart Suggestions** per the rebrand terminology — as a V1 launch feature.

V1 = a single intent tile (**Draft Message**) inside a dedicated **Smart Suggestions section card** on the Dashboard, powered by Gemini 2.0 Flash via Firebase Vertex AI, gated as free-trial → paid (5 free drafts/month, unlimited on premium).

## Why

Per [[project-ai-assistant]] memory: Daniel committed to AI assistance as a V1 launch feature, "thoroughly built for a fashion business owner — not a generic chatbot." The differentiator vs StyleOS and generic CRM tools is purpose-built tailor capabilities, not a free-form chat box.

Three capabilities were scoped during brainstorming (2026-05-16):
- Customer-facing message generation
- Proactive smart nudges (NBA extension)
- Pricing / customer-response Q&A

V1 ships **only Draft Message** to keep blast radius small for the first round of testers (5–10 Nigerian tailors per [[project-pm-intern]]). The other two intents (Help Me Price This, Customer Reply Helper) are scoped but ride V1.5 after testers validate the section-card pattern + Vertex pipeline.

## Decisions locked

| Decision | Choice |
|---|---|
| **Surface name (user-facing)** | "Smart Suggestions" — never "AI assistant", per terminology mapping |
| **Surface shape** | Specialized intent cards, NOT free-form chat |
| **Entry point** | Dedicated **Smart Suggestions section card** on Dashboard with intent tiles (NOT a FAB or bottom sheet — that pattern is generic across AI-enhanced apps including likely StyleOS; this differentiates) |
| **V1 intent count** | 1 enabled tile (Draft Message); 2 grayed "Coming soon" tiles |
| **LLM provider** | Gemini 2.0 Flash via Firebase Vertex AI |
| **Call topology** | KMP client → Firebase Cloud Function (`europe-west1`) → Vertex AI. Never client-direct. |
| **Context strategy** | Lean per-call — server fetches order/customer from Firestore using UID; client sends only IDs |
| **Gating** | Free trial → paid: 5 drafts/month free, unlimited on premium tier |
| **Premium check** | `users/{uid}/profile.tier` Firestore doc; assume `"free"` if missing |
| **Languages** | English + Pidgin (ISO 639-3 `pcm`) toggle on the form |

## Architecture

**Pattern:** KMP client → Cloud Function → Vertex AI. The KMP client never calls Vertex directly; all LLM traffic routes through a Firebase Cloud Function in `europe-west1`.

### Why route through a Cloud Function

- **API key safety** — Vertex API keys never ship to the client.
- **Tamper-proof free-tier tracking** — usage counter lives in a Firestore doc per UID; the function increments before returning. Client can't forge "I have credits left."
- **Pattern consistency** — existing CI runs `functions-tests`; Vertex calls become one more callable function.
- **Avoids KMP-wrapping the Firebase Vertex SDK** — GitLive Firebase covers Auth/Firestore/Functions/Crashlytics but Vertex AI support is uneven; calling via the Functions client (which GitLive does wrap cleanly) sidesteps the gap.

### Module layout

Per CLAUDE.md package conventions:

```
composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/
  feature/smart/
    domain/
      model/
        SmartIntent.kt           — sealed interface (V1: object DraftMessage)
        DraftMessageRequest.kt   — value object: customerId, orderId, intent, language, notes
        DraftMessageResult.kt    — value object: draftText, remainingFreeQuota
      error/
        SmartError.kt            — DataError variants + tier-specific cases
      repository/
        SmartRepository.kt       — interface
    data/
      SmartFunctionsRepository.kt — implements SmartRepository via FirebaseFunctions
      dto/
        DraftMessageRequestDto.kt
        DraftMessageResponseDto.kt
      mapper/
        DraftMessageMappers.kt    — DTO ↔ domain extension functions
    presentation/
      SmartViewModel.kt          — MVI: SmartDraftState, SmartDraftAction, SmartDraftEvent
      SmartSectionCard.kt        — Dashboard section card host (always-on)
      DraftMessageRoot.kt        — Root composable (has ViewModel)
      DraftMessageScreen.kt      — Stateless Screen composable (previewable)
      components/
        IntentTile.kt            — single tile within the Smart section card
        DraftPreview.kt
        FreeTierCounter.kt

functions/src/smart/
  draftMessage.ts                 — onCall function; main entry
  vertexClient.ts                 — Vertex AI SDK setup (Gemini 2.0 Flash)
  promptBuilder.ts                — system + templated user prompts per intent
  freeTierCounter.ts              — Firestore-backed usage tracking
  __tests__/
    draftMessage.test.ts          — auth/tier/ownership/Vertex-mock tests
```

### Estimate

~6–7 dev days: 3 client / 2 functions / 1–2 polish + tests.

## User flow

### Entry → result, V1 happy path

1. **Dashboard** — tailor scrolls past Pipeline + Next Best Actions and sees a dedicated **Smart Suggestions** section card (always-on, peer to the other dashboard sections per [[feedback-dashboard-philosophy]]). Card header: "Smart Suggestions" + subtitle "Help with messages, pricing, and more" + free-tier counter chip at top-right (e.g., "3 of 5 free drafts left").
2. **Inside the section card** — horizontal row of 3 intent tiles:
   - ✅ **Draft Message** — enabled, full color, brief description ("Draft a WhatsApp message to a customer")
   - 🔒 **Help Me Price This** — grayed, "Coming soon"
   - 🔒 **Customer Reply Helper** — grayed, "Coming soon"
3. **Tap the Draft Message tile** → push to **DraftMessageRoot** → `DraftMessageScreen`. Form fields:
   - **Customer** (required) — search picker over existing customers via `CustomerRepository.observeCustomers()`
   - **Order** (required) — picker over selected customer's open orders. If none: helper text and Generate stays disabled
   - **Intent** (required) — 4 chips: `Balance reminder`, `Ready for pickup`, `Follow up`, `Custom note`
   - **Language** — toggle: `English` / `Pidgin` (default English)
   - **Optional notes** — TextField, "Anything specific to add?"
   - **[ Generate draft ]** primary CTA
4. **Generating** — replace CTA with loading state. ~1–3 sec round-trip.
5. **Result** — editable `TextField` populated with AI draft. Two buttons:
   - **[ Send via WhatsApp ]** primary — invokes platform WhatsApp intent prefilled with customer's `whatsappNumber` (per [[user-phone-vs-whatsappNumber]] distinction). Disabled with helper text if customer has no `whatsappNumber`.
   - **[ Copy text ]** secondary — clipboard fallback
6. **Send / Copy** → snackbar confirmation per [[feedback-notification-patterns]]; navigate back to Dashboard.

### Out of V1 (deferred to V1.5+)

- Free-form composer (no customer/order picker; "draft me a message about X")
- Save-as-draft for later
- Per-customer message history
- Re-roll button ("try again with a different vibe")
- Help Me Price This intent
- Customer Reply Helper intent

## Data layer

### Server: Cloud Function `smartDraftMessage`

Callable function. Request shape:

```ts
{
  intentType: "balance_reminder" | "pickup_ready" | "follow_up" | "custom_note",
  customerId: string,
  orderId: string,
  language: "en" | "pcm",
  customNotes?: string,
}
```

Response shape (success):

```ts
{
  draftText: string,
  remainingFreeQuota: number | null   // null = premium tier
}
```

Server flow per request:

1. **Auth** — extract UID from callable context; reject with `unauthenticated` if missing.
2. **Tier check** — read `users/{uid}/profile.tier`. If `"free"`, fetch usage doc + reject with `permission-denied` `free_tier_exhausted` if `count >= limit`.
3. **Fetch context server-side** — read customer + order from Firestore using UID-scoped collections. Validates ownership AND keeps client payload tiny. Reject with `invalid-argument` `invalid_input` if customer or order doesn't belong to UID or doesn't exist.
4. **Build prompt** — system prompt (fixed) + user prompt (templated per intent).
5. **Call Vertex AI** — Gemini 2.0 Flash, `temperature: 0.7`, `maxTokens: 200`. Strip leading greetings if model adds them (downstream WhatsApp intent already addresses).
6. **Increment counter** (free tier only) — atomic Firestore transaction.
7. **Return** drafted text + remaining quota (null for premium).

### Server: prompts

**System prompt** (fixed):

```
You are a writing assistant for a Nigerian tailor running a small workshop.
You draft polite, professional WhatsApp messages to customers about orders.

Rules:
- Address the customer by their first name only.
- Keep messages short (2-4 sentences).
- Tone: warm, professional, never pushy.
- For Pidgin output, use casual Nigerian Pidgin (not heavy slang).
- Never invent prices or facts not in the order context.
- Output ONLY the message body. No greeting prefix, no signature, no quotes.
```

**User prompt** (templated by intent):

```
Draft a {intent_label} message in {language_label} for this customer:

Customer: {firstName}
Order: {garmentLabel}
Deposit paid: {depositFormatted}
Balance due: {balanceFormatted}
Deadline: {deadlineFormatted}

{custom_notes_section if provided}

Your draft:
```

Where `intent_label` maps:

| `intentType` | `intent_label` |
|---|---|
| `balance_reminder` | "polite reminder about an outstanding balance" |
| `pickup_ready` | "notification that their order is ready for pickup" |
| `follow_up` | "casual check-in to see if they need anything else" |
| `custom_note` | "custom message based on the notes provided" |

### Server: free-tier counter

Firestore doc at `users/{uid}/usage/smart_drafts`:

```
{
  monthYear: "2026-05",   // YYYY-MM, reset on month rollover
  count: 3,
  limit: 5                 // overridable per-user via Firestore for testers
}
```

Reset logic lives on the server — idempotent: read doc → if `monthYear != today.YYYY-MM` then reset count to 0 and update `monthYear` before incrementing.

### Server: response error contracts

| Condition | HTTP-equivalent error | Body |
|---|---|---|
| Free-tier exhausted | `permission-denied` | `{ error: "free_tier_exhausted", remainingFreeQuota: 0 }` |
| Customer/order not found or wrong UID | `invalid-argument` | `{ error: "invalid_input", message: string }` |
| Vertex AI failure | `unavailable` | `{ error: "service_unavailable" }` |
| Unauthenticated | `unauthenticated` | (default callable error) |

### Client: repository contract

```kotlin
// domain/repository/SmartRepository.kt
interface SmartRepository {
    suspend fun draftMessage(
        request: DraftMessageRequest
    ): Result<DraftMessageResult, SmartError>
}

// data/SmartFunctionsRepository.kt
class SmartFunctionsRepository(
    private val functions: FirebaseFunctions
) : SmartRepository {
    override suspend fun draftMessage(
        request: DraftMessageRequest
    ): Result<DraftMessageResult, SmartError> {
        // GitLive Firebase Functions callable
        // Request DTO via mappers; response → domain via mappers
        // Map FirebaseFunctionsException codes → SmartError variants
    }
}
```

Standard project pattern per CLAUDE.md and [[android-data-layer]] skill conventions.

### Why fetch context server-side

- **Smaller client payload** — ~200 bytes (IDs only) vs ~2 KB (full order JSON)
- **Tamper-proof** — client can't pass a fake order with a ₦100M balance
- **Single source of truth** — server reads the same data the dashboard reads
- **Cost** — 1 extra Firestore read per draft is negligible at V1 scale

## Error handling + UX

Per [[feedback-notification-patterns]]: **Snackbar** for transient feedback, **Bottom Sheet** for choices, **Dialog** for destructive, never Toast/Banner.

| Error | Where it surfaces | UX response |
|---|---|---|
| **No internet** | Pre-call `ConnectivityObserver` check | Inline state on Generate button: "Need internet to draft messages" + disabled. No round-trip. |
| **Free tier exhausted** | Server `free_tier_exhausted` | **Bottom sheet** (decision pattern): "You've used your 5 free drafts this month. Upgrade for unlimited." Buttons: `Upgrade` (deep-link to PlanCard per [[project-freemium-plan-card]]) + `Wait until next month`. |
| **Vertex AI failure** | Server `service_unavailable` | Snackbar: "Couldn't draft right now. Try again in a moment." + `Retry` action in snackbar. |
| **Invalid input** (race: customer/order deleted) | Server `invalid_input` | Snackbar: "That order isn't available anymore." + return to form, clear order pick. |
| **Auth failure** | Defensive — shouldn't reach (Dashboard requires auth) | Snackbar: "Sign in again to use Smart drafts." + sign-out → re-auth flow. |
| **Customer has no `whatsappNumber`** | Pre-Send check on result screen | Disable `Send via WhatsApp` button + helper text under it: "Add a WhatsApp number to this customer to send directly." `Copy text` still works. |
| **No customers exist** | Section-card check via `CustomerRepository.observeCustomers().firstOrNull().isNullOrEmpty()` | Smart Suggestions section card is **hidden** entirely from the Dashboard (no dead-end tap). Re-appears once first customer is added. |
| **Customer has no open orders** | Order picker shows "No open orders." | Order picker disabled with helper text: "This customer has no open orders. Pick another or create an order first." Generate stays disabled. |

### MVI state model

Per [[android-presentation-mvi]]:

```kotlin
data class SmartDraftState(
    val customer: CustomerSummary? = null,
    val order: OrderSummary? = null,
    val intent: DraftIntent? = null,
    val language: DraftLanguage = DraftLanguage.English,
    val customNotes: String = "",
    val generationState: GenerationState = GenerationState.Idle,
    val remainingFreeQuota: Int? = null,    // null = premium
    val isOnline: Boolean = true,
)

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Generating : GenerationState
    data class Success(val draftText: String) : GenerationState
}

sealed interface SmartDraftAction {
    data class SelectCustomer(val customer: CustomerSummary) : SmartDraftAction
    data class SelectOrder(val order: OrderSummary) : SmartDraftAction
    data class SelectIntent(val intent: DraftIntent) : SmartDraftAction
    data class ToggleLanguage(val language: DraftLanguage) : SmartDraftAction
    data class UpdateCustomNotes(val notes: String) : SmartDraftAction
    data object GenerateDraft : SmartDraftAction
    data class EditDraft(val text: String) : SmartDraftAction
    data object SendViaWhatsApp : SmartDraftAction
    data object CopyDraft : SmartDraftAction
}

sealed interface SmartDraftEvent {
    data class ShowSnackbar(val text: UiText) : SmartDraftEvent
    data object ShowUpgradeSheet : SmartDraftEvent
    data class LaunchWhatsApp(val phoneE164: String, val message: String) : SmartDraftEvent
    data class CopyToClipboard(val text: String) : SmartDraftEvent
    data object NavigateBack : SmartDraftEvent
}
```

`SmartError.toUiText()` extension lives in `presentation/` — translates each error variant to localized snackbar copy.

### Telemetry (lightweight)

All via existing `AppLogger`; tag for future Crashlytics dashboard per [[project-crashlytics-remote-logging]]:

- `smart_draft_requested` on Generate tap (intent, language, has_custom_notes)
- `smart_draft_succeeded` (token count)
- `smart_draft_sent_via_whatsapp` on Send tap — **the success metric**
- `smart_draft_copied` on Copy tap
- `smart_draft_quota_exceeded` when free tier blocks

## Testing

Per [[android-testing]] skill conventions: JUnit5, Turbine, AssertK, UnconfinedTestDispatcher, fake repositories.

### Unit tests (commonTest)

| Layer | What's tested |
|---|---|
| `SmartViewModel` | Initial state; customer pick triggers order fetch; order picker disabled when customer has no orders; Generate button gated on (customer + order + intent + online); Generate → state transitions Idle → Generating → Success/Error; each `SmartError` maps to the right `SmartDraftEvent` (snackbar vs upgrade-sheet); WhatsApp button disabled when `customer.whatsappNumber == null`; counter display updates after success. ~12–15 tests. |
| `SmartFunctionsRepository` | Thin — fake the FirebaseFunctions callable; verify request DTO matches schema; verify error mapping (HTTP-equivalent codes → `SmartError` variants). ~5–6 tests. |
| Mappers | DTO ↔ domain round-trip for `DraftMessageRequest`/`DraftMessageResult`. ~2–3 tests. |

### Cloud Functions tests (`functions/test/smart/draftMessage.test.ts`)

Extends existing `functions-tests` CI job:

| Path | What's tested |
|---|---|
| Auth | Unauthenticated request → `unauthenticated` error |
| Tier check | Free user under quota → succeeds; free user at quota → `permission-denied` `free_tier_exhausted`; premium user → counter NOT touched, succeeds |
| Counter rollover | Doc with `monthYear: "2026-04"` is reset on first call in May |
| Ownership | Customer/order belonging to a different UID → `invalid-argument` |
| Vertex mocked | All tests use a fake Vertex client returning canned text. No real LLM calls in CI (cost + flake) |
| Counter atomicity | Concurrent requests don't double-increment (Firestore transaction test) |

### Compose UI tests (commonTest + Compose Multiplatform UI test runner)

- `DraftMessageScreen` renders correctly in all 4 `GenerationState` variants (Idle, Generating, Success, Error fallback)
- `SmartSectionCard` shows 1 enabled + 2 disabled "Coming soon" intent tiles in V1; hidden entirely when customer list is empty
- Free-tier counter "3 of 5 free drafts left" rendered as a chip in the card header when `remainingFreeQuota` is set; hidden for premium

### Local dev / debug

- Hook into the planned **Debug menu Tier 1** per [[project-debug-menu]] so Smart drafts have a "use canned response" toggle — avoids burning Vertex tokens during day-to-day dev
- Per-user Firestore override: Daniel can flip `users/{uid}/profile.tier = "premium"` on his test accounts (Fola/Gabby per [[reference-test-environment]]) to exercise the unbounded path

### Manual smoke

Drafted in the implementation plan, run by Daniel before merge per [[feedback-qa-smoke-tests]].

## Out of scope (V1.5+)

- **Help Me Price This intent** — enables the second tile in the Smart Suggestions section card
- **Customer Reply Helper intent** — enables the third tile
- **Free-form composer** — "draft me a message about X" without picking customer/order
- **Save-as-draft** persistence — drafts currently live only in the screen state until sent
- **Per-customer message history** — see prior drafts sent to a customer
- **Re-roll button** — regenerate with a different temperature seed
- **Voice / photo input shortcuts** — speech-to-measurement, photo-to-style (deferred indefinitely; separate feature track)
- **Rich workshop context (RAG)** — past order anchors for pricing, customer history for tone — V2 territory once V1 telemetry shows what testers want

## Open questions

None blocking — all decisions locked in brainstorming. The implementation plan will surface implementation-level decisions (Smart section card position in the Dashboard scroll order, exact tile sizing/grid vs row layout, prompt-tuning iteration pass after first tester drafts).
