# Analytics Mid-Funnel Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 5 mid-funnel analytics events (`measurement_added`, `order_status_advanced`, `payment_recorded`, `receipt_sent`, `whatsapp_message_sent`) so the user journey is measurable through production, payment, and customer comms.

**Architecture:** Purely additive on the PR #214 foundation. Add 5 `AnalyticsEvent` subtypes; inject the existing `Analytics` into `OrderDetailViewModel` + `MeasurementFormViewModel` (`DraftMessageViewModel` already has it); fire at mapped success branches. No new interfaces/modules/schema.

**Tech Stack:** Kotlin Multiplatform, Compose, Koin (`viewModelOf`), GitLive firebase-analytics, kotlin.test + Turbine (commonTest, plain-JVM via `:composeApp:testDebugUnitTest`).

## Global Constraints

- **No PII, ever** — params are counts/enums/booleans-as-strings only. Never names, phones, amounts, customer/order text, free text.
- **Fire-and-forget** — log AFTER success is established; never gate a user action on the log; never throw (swallowed at the sink).
- **Param value convention** — lowercase strings; enums via `.name.lowercase()`; booleans via `.toString()`.
- **One `AnalyticsEvent` subtype per event name**; all in `core/analytics/domain/AnalyticsEvent.kt`.
- **`measurement_added` is create-only** (`measurementId == null`), mirroring `customer_created`/`order_created`.
- **Koin**: both target VMs use `viewModelOf(::…)` with no defaulted params → add `analytics: Analytics` and it resolves via `get()` (no lambda form needed; see [[feedback_koin_constructor_ref_defaults]]).
- **Verification gate** (every KMP change): run full `:composeApp:testDebugUnitTest` (compiles commonTest for JVM — catches illegal backtick names), `detekt`, and `compileKotlinIosSimulatorArm64`; capture gradle's real exit code, not a piped one. Confirm the GitHub CI checks go green, not just local.

## Testability constraint (read before Tasks 5–8)

`OrderDetailViewModel` is **not unit-constructible in commonTest**: its deps include `OrderReceiptSharer` (an `expect class`), Coil `ImageLoader`, and `PlatformContext` — platform types with no plain-JVM actual. This is why no `OrderDetailViewModel` test exists today (it's tested via extracted pure helpers). Therefore the 4 OrderDetail events get:
- **Param-contract coverage** via `AnalyticsEventTest` (Task 1) — proves name + params are correct.
- **Firing coverage** via the manual DebugView smoke test (Task 9) — same approach #214 used.

`measurement_added` (MeasurementFormViewModel) and the draft `whatsapp_message_sent` (DraftMessageViewModel) ARE unit-constructible and get full VM tests.

## File structure

- `core/analytics/domain/AnalyticsEvent.kt` — +5 subtypes (Task 1)
- `core/analytics/AnalyticsEventTest.kt` — +5 contract tests (Task 1)
- `feature/measurement/.../MeasurementFormViewModel.kt` + `di/MeasurementModule.kt` + `MeasurementFormViewModelTest.kt` (Task 2)
- `feature/smart/.../DraftMessageViewModel.kt` + `DraftMessageViewModelAnalyticsTest.kt` (Task 3)
- `feature/order/.../OrderDetailViewModel.kt` + `di/OrderModule.kt` (Tasks 4–8)

---

### Task 1: Add the 5 AnalyticsEvent subtypes + contract tests

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/core/analytics/domain/AnalyticsEvent.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/core/analytics/AnalyticsEventTest.kt`

**Interfaces:**
- Produces: `AnalyticsEvent.MeasurementAdded` (object); `OrderStatusAdvanced(status: String)`; `PaymentRecorded(isFullyPaid: Boolean)`; `ReceiptSent(documentType: String, format: String)`; `WhatsAppMessageSent(context: String)`.

- [ ] **Step 1: Write failing tests** — append to `AnalyticsEventTest.kt`:

```kotlin
@Test
fun measurementAddedNameNoParams() {
    val e = AnalyticsEvent.MeasurementAdded
    assertEquals("measurement_added", e.name)
    assertTrue(e.params.isEmpty())
}

@Test
fun orderStatusAdvancedCarriesStatus() {
    val e = AnalyticsEvent.OrderStatusAdvanced(status = "ready")
    assertEquals("order_status_advanced", e.name)
    assertEquals(mapOf("status" to "ready"), e.params)
}

@Test
fun paymentRecordedCarriesIsFullyPaidAsString() {
    assertEquals(mapOf("is_fully_paid" to "true"), AnalyticsEvent.PaymentRecorded(isFullyPaid = true).params)
    assertEquals(mapOf("is_fully_paid" to "false"), AnalyticsEvent.PaymentRecorded(isFullyPaid = false).params)
    assertEquals("payment_recorded", AnalyticsEvent.PaymentRecorded(true).name)
}

@Test
fun receiptSentCarriesDocTypeAndFormat() {
    val e = AnalyticsEvent.ReceiptSent(documentType = "invoice", format = "pdf")
    assertEquals("receipt_sent", e.name)
    assertEquals(mapOf("document_type" to "invoice", "format" to "pdf"), e.params)
}

@Test
fun whatsAppMessageSentCarriesContext() {
    val e = AnalyticsEvent.WhatsAppMessageSent(context = "draft_message")
    assertEquals("whatsapp_message_sent", e.name)
    assertEquals(mapOf("context" to "draft_message"), e.params)
}
```

- [ ] **Step 2: Run, verify they fail** — `./gradlew :composeApp:testDebugUnitTest --tests "*AnalyticsEventTest*"` → FAIL (unresolved references).

- [ ] **Step 3: Add the subtypes** to `AnalyticsEvent.kt` (inside the sealed interface, after `UpgradeCompleted`):

```kotlin
data object MeasurementAdded : AnalyticsEvent {
    override val name = "measurement_added"
}

data class OrderStatusAdvanced(val status: String) : AnalyticsEvent {
    override val name = "order_status_advanced"
    override val params = mapOf("status" to status)
}

data class PaymentRecorded(val isFullyPaid: Boolean) : AnalyticsEvent {
    override val name = "payment_recorded"
    // String, not Boolean: GA4 param types are string/number; keep it queryable.
    override val params = mapOf("is_fully_paid" to isFullyPaid.toString())
}

data class ReceiptSent(val documentType: String, val format: String) : AnalyticsEvent {
    override val name = "receipt_sent"
    override val params = mapOf("document_type" to documentType, "format" to format)
}

data class WhatsAppMessageSent(val context: String) : AnalyticsEvent {
    override val name = "whatsapp_message_sent"
    override val params = mapOf("context" to context)
}
```

- [ ] **Step 4: Run, verify pass** — `./gradlew :composeApp:testDebugUnitTest --tests "*AnalyticsEventTest*"` → PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(analytics): add 5 mid-funnel event types"`

---

### Task 2: `measurement_added` (create-only)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModel.kt` (constructor + `save()` ~line 456)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/MeasurementModule.kt:19`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/measurement/presentation/form/MeasurementFormViewModelTest.kt`

**Interfaces:**
- Consumes: `AnalyticsEvent.MeasurementAdded` (Task 1); existing `Analytics` interface + `FakeAnalytics` (`val events: List<AnalyticsEvent>`).
- Produces: `MeasurementFormViewModel` gains trailing ctor param `private val analytics: Analytics`.

- [ ] **Step 1: Write failing tests.** In `MeasurementFormViewModelTest.kt`, add `analytics: Analytics = FakeAnalytics()` as a trailing param to the existing VM-construction helper (the `MeasurementFormViewModel(...)` call ~line 78), passing `analytics = analytics`. Then add:

```kotlin
@Test
fun create_save_logs_measurement_added() = runTest {
    val analytics = FakeAnalytics()
    val vm = createViewModel(/* no measurementId arg → create */, analytics = analytics)
    // ...set gender + at least one positive field so canSave is true (reuse existing test setup)...
    vm.onAction(MeasurementFormAction.OnSaveClick)
    runCurrent()
    assertTrue(analytics.events.contains(AnalyticsEvent.MeasurementAdded))
}

@Test
fun edit_save_does_not_log_measurement_added() = runTest {
    val analytics = FakeAnalytics()
    val vm = createViewModel(/* measurementId = "m1" → edit */, analytics = analytics)
    // ...load + change a field, then save...
    vm.onAction(MeasurementFormAction.OnSaveClick)
    runCurrent()
    assertFalse(analytics.events.any { it is AnalyticsEvent.MeasurementAdded })
}
```

(Match the existing test's exact arg names/setup for create vs edit and field population.)

- [ ] **Step 2: Run, verify fail** — `./gradlew :composeApp:testDebugUnitTest --tests "*MeasurementFormViewModelTest*"` → FAIL (ctor param missing / event not logged).
- [ ] **Step 3: Implement.** Add `private val analytics: Analytics,` as the last constructor param (after `entitlements`). In `save()`, fire inside the existing create block (~line 456):

```kotlin
if (isCreate) {
    linkMeasurementToOrderIfRequested(userId, effectiveId)
    analytics.logEvent(AnalyticsEvent.MeasurementAdded)
}
```

Add the import `com.danzucker.stitchpad.core.analytics.domain.Analytics` and `...AnalyticsEvent`. Update `MeasurementModule.kt:19` — `viewModelOf(::MeasurementFormViewModel)` stays as-is (Koin resolves the new `analytics` via `get()`); confirm `analyticsModule` is in the module graph (it is, from #214).
- [ ] **Step 4: Run, verify pass** — same test command → PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(analytics): log measurement_added on create"`

---

### Task 3: `whatsapp_message_sent` — draft message

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModel.kt` (`sendViaWhatsApp`, ~line 261)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/smart/presentation/draft/DraftMessageViewModelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `AnalyticsEvent.WhatsAppMessageSent` (Task 1); `analytics` is already a ctor param of `DraftMessageViewModel` (from #214).

- [ ] **Step 1: Write failing test** — append to `DraftMessageViewModelAnalyticsTest.kt` (reuse its existing fakes/setup that produce a `GenerationState.Success` + a customer with a `whatsappNumber`):

```kotlin
@Test
fun sendViaWhatsApp_logs_whatsapp_message_sent_draft_context() = runTest {
    // ...arrange a successful draft + selected customer with whatsappNumber (reuse existing helpers)...
    vm.onAction(DraftMessageAction.SendViaWhatsApp)
    runCurrent()
    assertTrue(fakeAnalytics.events.contains(AnalyticsEvent.WhatsAppMessageSent(context = "draft_message")))
}
```

- [ ] **Step 2: Run, verify fail** — `./gradlew :composeApp:testDebugUnitTest --tests "*DraftMessageViewModelAnalyticsTest*"` → FAIL.
- [ ] **Step 3: Implement** — in `sendViaWhatsApp()`, fire after the launch send:

```kotlin
viewModelScope.launch {
    _events.send(DraftMessageEvent.LaunchWhatsApp(phoneE164 = phone, message = draft))
    analytics.logEvent(AnalyticsEvent.WhatsAppMessageSent(context = "draft_message"))
}
```

- [ ] **Step 4: Run, verify pass** — PASS.
- [ ] **Step 5: Commit** — `git commit -am "feat(analytics): log whatsapp_message_sent on draft send"`

---

### Task 4: Inject `Analytics` into OrderDetailViewModel

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/feature/order/presentation/detail/OrderDetailViewModel.kt` (constructor ~line 76)
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/di/OrderModule.kt:23`

**Interfaces:**
- Produces: `OrderDetailViewModel` gains trailing ctor param `private val analytics: Analytics`. Tasks 5–8 rely on this `analytics` field.

> No unit test (see "Testability constraint"). Deliverable: compiles, existing behavior unchanged, app wiring intact.

- [ ] **Step 1: Implement** — add `private val analytics: Analytics,` as the last constructor param (after `entitlementsProvider`). Add imports `...analytics.domain.Analytics` and `...analytics.domain.AnalyticsEvent`. `OrderModule.kt:23` `viewModelOf(::OrderDetailViewModel)` stays as-is (resolves via `get()`).
- [ ] **Step 2: Verify compile + existing tests** — `./gradlew :composeApp:testDebugUnitTest detekt` (real exit code) → BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git commit -am "chore(analytics): inject Analytics into OrderDetailViewModel"`

---

### Task 5: `order_status_advanced`

**Files:** Modify `OrderDetailViewModel.kt` (`performStatusUpdate`, ~line 951).

- [ ] **Step 1: Implement** — fire right after the status update succeeds (after the `Result.Error` guard at ~line 959, before the subStatus update):

```kotlin
val statusResult = orderRepository.updateOrderStatus(userId, orderId, newStatus)
if (statusResult is Result.Error) {
    _state.update { it.copy(errorMessage = statusResult.error.toOrderUiText()) }
    return@launch
}
analytics.logEvent(AnalyticsEvent.OrderStatusAdvanced(status = newStatus.name.lowercase()))
```

(Both the direct path and the balance-warning-confirm path funnel through `performStatusUpdate`, so this is the single fire point.)
- [ ] **Step 2: Verify** — `./gradlew :composeApp:testDebugUnitTest detekt` → SUCCESS. (Param contract already covered by Task 1; firing verified in Task 9 QA.)
- [ ] **Step 3: Commit** — `git commit -am "feat(analytics): log order_status_advanced"`

---

### Task 6: `payment_recorded`

**Files:** Modify `OrderDetailViewModel.kt` (`submitPayment` success branch, ~line 1066).

- [ ] **Step 1: Implement** — in the `is Result.Success ->` branch of `submitPayment`, fire after the optimistic state update, before/with `PaymentRecorded` event:

```kotlin
is Result.Success -> {
    _state.update { current ->
        val existing = current.order ?: return@update current
        current.copy(order = existing.copy(payments = existing.payments + payment))
    }
    analytics.logEvent(
        AnalyticsEvent.PaymentRecorded(isFullyPaid = safeAmount >= order.balanceRemaining)
    )
    _events.send(OrderDetailEvent.PaymentRecorded)
}
```

(`safeAmount` and `order` — the pre-payment snapshot with `balanceRemaining` — are both in scope. `safeAmount` is coerced to `balanceRemaining`, so `>=` means the balance reached zero. No amount logged.)
- [ ] **Step 2: Verify** — `./gradlew :composeApp:testDebugUnitTest detekt` → SUCCESS.
- [ ] **Step 3: Commit** — `git commit -am "feat(analytics): log payment_recorded with is_fully_paid"`

---

### Task 7: `receipt_sent`

**Files:** Modify `OrderDetailViewModel.kt` (`shareReceipt` ~line 635 + its 2 call sites ~lines 225 & 230).

- [ ] **Step 1: Implement** — add a `format: String` param to `shareReceipt` and fire after `share(receiptData)` succeeds:

```kotlin
private fun shareReceipt(format: String, share: suspend (ReceiptData) -> Unit) {
    // ...unchanged up to and including share(receiptData)...
    share(receiptData)
    analytics.logEvent(
        AnalyticsEvent.ReceiptSent(
            documentType = receiptData.documentType.name.lowercase(),
            format = format,
        )
    )
}
```

Update the two call sites in `onAction`:

```kotlin
OrderDetailAction.OnShareAsImageClick -> {
    _state.update { it.copy(showShareSheet = false) }
    shareReceipt(format = "image") { receiptSharer.shareReceiptAsImage(it) }
}
OrderDetailAction.OnShareAsPdfClick -> {
    _state.update { it.copy(showShareSheet = false) }
    shareReceipt(format = "pdf") { receiptSharer.shareReceiptAsPdf(it) }
}
```

(`receiptData.documentType` is a `ReceiptDocumentType` — `INVOICE`/`DEPOSIT_RECEIPT`/`RECEIPT`. Firing after `share(...)` inside the `try` means it only logs on a successful share.)
- [ ] **Step 2: Verify** — `./gradlew :composeApp:testDebugUnitTest detekt` → SUCCESS.
- [ ] **Step 3: Commit** — `git commit -am "feat(analytics): log receipt_sent with document_type + format"`

---

### Task 8: `whatsapp_message_sent` — order update

**Files:** Modify `OrderDetailViewModel.kt` (`launchWhatsApp`, ~line 986).

- [ ] **Step 1: Implement** — fire after the launch send:

```kotlin
viewModelScope.launch {
    val message = WhatsAppMessageBuilder.buildForOrder(order, customer)
    _events.send(OrderDetailEvent.LaunchWhatsApp(customer.phone, message))
    analytics.logEvent(AnalyticsEvent.WhatsAppMessageSent(context = "order_update"))
}
```

- [ ] **Step 2: Verify** — `./gradlew :composeApp:testDebugUnitTest detekt` → SUCCESS.
- [ ] **Step 3: Commit** — `git commit -am "feat(analytics): log whatsapp_message_sent on order update"`

---

### Task 9: Full verification + PR

**Files:** none (verification + PR).

- [ ] **Step 1: Detekt** — `./gradlew detekt` → clean (watch for `LongMethod`/`TooManyFunctions` on the touched VMs; if a cohesive dispatch tips over, extend the existing `@Suppress` rather than split — see [[feedback_detekt_toomanyfunctions_previews]]).
- [ ] **Step 2: Full unit suite** — `./gradlew :composeApp:testDebugUnitTest` (real exit code, NOT piped) → BUILD SUCCESSFUL. This compiles commonTest for JVM (catches any illegal backtick test name — see [[feedback_kmp_backtick_test_names_jvm]]).
- [ ] **Step 3: iOS compile** — `./gradlew :composeApp:compileKotlinIosSimulatorArm64` → SUCCESS (GitLive analytics links on Native).
- [ ] **Step 4: Push + PR** — include manual DebugView smoke-test steps: on Android + iOS with DebugView on, exercise each event and confirm it fires once with correct params —
  - `measurement_added` (add a measurement; confirm NOT on edit)
  - `order_status_advanced` (advance an order's status; check `status`)
  - `payment_recorded` (record a partial then a final payment; check `is_fully_paid` false then true)
  - `receipt_sent` (share as image and as pdf, on an unpaid then partly-paid order; check `document_type` invoice→deposit_receipt and `format`)
  - `whatsapp_message_sent` (draft send → `context=draft_message`; order-detail WhatsApp → `context=order_update`)
- [ ] **Step 5: Confirm CI checks green** on GitHub (`gh pr checks <n>`) — build-android, build-ios, crash-check, detekt — not just local. Note in the PR: GA4 custom-dimension registration for the new params (`status`, `is_fully_paid`, `document_type`, `format`, `context`) is a post-merge console step.

## Self-review notes
- **Spec coverage:** all 5 events have tasks (1 defines; 2,3,5,6,7,8 fire; `whatsapp_message_sent` = Tasks 3+8 for its two contexts). ✓
- **Type consistency:** `OrderStatusAdvanced(status)`, `PaymentRecorded(isFullyPaid)`, `ReceiptSent(documentType, format)`, `WhatsAppMessageSent(context)`, `MeasurementAdded` (object) — used identically in Task 1 defs and Tasks 2–8 call sites. ✓
- **Known limitation (documented):** OrderDetail events have no VM-level unit test (platform-typed deps block commonTest construction); covered by Task 1 contract tests + Task 9 manual QA.
