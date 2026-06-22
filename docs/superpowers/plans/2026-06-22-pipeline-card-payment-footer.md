# Pipeline Card: solid avatar + payment·value footer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Replace the dashboard pipeline card's decorative dashed-ring avatar with the app-standard `CustomerAvatar`, and replace the hardcoded "Custom size · Bespoke · Created" footer with a useful "deposit status + order value" footer.

**Architecture:** Task 1 adds the data (a pure `pipelinePaymentStatusOf` helper + enum, two `DashboardOrderRow` fields, the `BucketCalculator` mapper) — unit-tested. Task 2 rewires `PipelineOrderRow`'s avatar + footer and removes the dead code/strings. On branch `feat/pipeline-card-payment-footer`.

**Tech Stack:** KMP, Compose Multiplatform, JUnit5.

**Spec:** `docs/superpowers/specs/2026-06-22-pipeline-card-payment-footer-design.md`.

---

## Task 1: Data — payment status helper, model fields, mapper

**Files:**
- Create: `feature/dashboard/domain/PipelinePaymentStatus.kt`
- Modify: `feature/dashboard/domain/model/DashboardOrderRow.kt`
- Modify: `feature/dashboard/domain/BucketCalculator.kt`
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/domain/PipelinePaymentStatusTest.kt` (new)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/feature/dashboard/domain/BucketCalculatorTest.kt` (extend)

- [ ] **Step 1: The enum + pure helper**

Create `PipelinePaymentStatus.kt`:
```kotlin
package com.danzucker.stitchpad.feature.dashboard.domain

enum class PipelinePaymentStatus { DepositDue, DepositPaid, Paid }

/**
 * Derives the pipeline footer's payment chip from collected vs payable amount.
 * - payableTotal <= 0  -> Paid (nothing to collect)
 * - nothing collected  -> DepositDue (the actionable state)
 * - collected >= total -> Paid
 * - otherwise          -> DepositPaid (partial)
 */
fun pipelinePaymentStatusOf(depositPaid: Double, payableTotal: Double): PipelinePaymentStatus =
    when {
        payableTotal <= 0.0 -> PipelinePaymentStatus.Paid
        depositPaid <= 0.0 -> PipelinePaymentStatus.DepositDue
        depositPaid >= payableTotal -> PipelinePaymentStatus.Paid
        else -> PipelinePaymentStatus.DepositPaid
    }
```

- [ ] **Step 2: Write the failing helper test**

Create `PipelinePaymentStatusTest.kt` (kotlin.test — match sibling dashboard tests' style):
```kotlin
package com.danzucker.stitchpad.feature.dashboard.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PipelinePaymentStatusTest {
    @Test fun nothingCollected_isDepositDue() =
        assertEquals(PipelinePaymentStatus.DepositDue, pipelinePaymentStatusOf(0.0, 40000.0))

    @Test fun partial_isDepositPaid() =
        assertEquals(PipelinePaymentStatus.DepositPaid, pipelinePaymentStatusOf(20000.0, 40000.0))

    @Test fun fullyPaid_isPaid() =
        assertEquals(PipelinePaymentStatus.Paid, pipelinePaymentStatusOf(40000.0, 40000.0))

    @Test fun overpaid_isPaid() =
        assertEquals(PipelinePaymentStatus.Paid, pipelinePaymentStatusOf(50000.0, 40000.0))

    @Test fun zeroTotal_isPaid() =
        assertEquals(PipelinePaymentStatus.Paid, pipelinePaymentStatusOf(0.0, 0.0))
}
```
Run: `./gradlew :composeApp:testDebugUnitTest --tests '*PipelinePaymentStatus*' -q` → PASS (Step 1 already implements it; this locks behavior).

- [ ] **Step 3: Add the model fields**

`DashboardOrderRow.kt` — add (nullable so non-pipeline / preview call-sites omit them):
```kotlin
    /** Order payable total (after discount). Null → omit the value in the footer. */
    val orderValue: Double? = null,
    /** Deposit/payment state for the footer chip. Null → omit the chip. */
    val paymentStatus: PipelinePaymentStatus? = null,
```
Add `import com.danzucker.stitchpad.feature.dashboard.domain.PipelinePaymentStatus` if the model is in a different package (it's in `...domain.model`, helper in `...domain` — import needed).

- [ ] **Step 4: Populate in the mapper**

`BucketCalculator.kt` — in `Order.toPipelineRow(...)`'s `DashboardOrderRow(...)` add:
```kotlin
        orderValue = payableTotal,
        paymentStatus = pipelinePaymentStatusOf(depositPaid, payableTotal),
```
`payableTotal`/`depositPaid` are `Order` computed props — in scope on `this`. Also add the same two lines to `Order.toRow(...)` (the daysLate mapper) for safety. (Grep which composable renders `toRow`'s output; if it definitely never uses `PipelineOrderRow`, you may skip it, but setting them is harmless.)

- [ ] **Step 5: Extend BucketCalculatorTest**

In `BucketCalculatorTest.kt`, find an existing test that asserts a pipeline row's fields (or add one). Assert that a pipeline row built from an order with `totalPrice` and no payments has `orderValue == <payableTotal>` and `paymentStatus == PipelinePaymentStatus.DepositDue`. (Use the test's existing order-builder helpers; match how it constructs orders + payments.)

- [ ] **Step 6: Verify + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` then `./gradlew :composeApp:testDebugUnitTest --tests '*Pipeline*' --tests '*BucketCalculator*' -q`. Green.
```bash
git add -A
git commit -m "feat(dashboard): pipeline row carries order value + payment status"
```

---

## Task 2: UI — solid avatar + payment·value footer

**Files:**
- Modify: `feature/dashboard/presentation/components/PipelineOrderRow.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: Strings**

Add to `strings.xml`:
```xml
    <string name="dashboard_pipeline_deposit_due">Deposit due</string>
    <string name="dashboard_pipeline_deposit_paid">Deposit paid</string>
    <string name="dashboard_pipeline_paid">Paid</string>
```

- [ ] **Step 2: Swap the avatar**

In `PipelineOrderRow.kt`, replace `DashedAvatar(initials = pipelineInitialsOf(row.customerName))` with:
```kotlin
                CustomerAvatar(name = row.customerName, size = AVATAR_SIZE)
```
Delete the `DashedAvatar` composable and `pipelineInitialsOf` (grep first — if unused elsewhere). Add `import com.danzucker.stitchpad.ui.components.CustomerAvatar`. Remove now-unused imports that only `DashedAvatar` used (e.g. `drawBehind`, `PathEffect`, `Stroke`, `Offset`, `LocalDensity`, `CircleShape`, `background` — grep each before removing; some may still be used).

- [ ] **Step 3: Rewrite the footer**

Replace `MetadataFooter` with the payment-chip + value version:
```kotlin
@Composable
private fun MetadataFooter(row: DashboardOrderRow) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        row.paymentStatus?.let { status ->
            val (labelRes, color) = when (status) {
                // Sienna (tertiary) = brand warmth accent: a soft "attention", not an
                // alarming error and NOT saffron. Other states stay calm.
                PipelinePaymentStatus.DepositDue ->
                    Res.string.dashboard_pipeline_deposit_due to scheme.tertiary
                PipelinePaymentStatus.DepositPaid ->
                    Res.string.dashboard_pipeline_deposit_paid to scheme.onSurfaceVariant
                PipelinePaymentStatus.Paid ->
                    Res.string.dashboard_pipeline_paid to scheme.onSurfaceVariant
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
        if (row.paymentStatus != null && row.orderValue != null) MetaSeparator()
        row.orderValue?.let { value ->
            Text(
                text = formatPrice(value),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
```
Update the call site: `MetadataFooter(createdAt = row.createdAtEpochMillis)` →
`MetadataFooter(row = row)`. Gate the footer block (Spacer + HorizontalDivider +
MetadataFooter) on `row.paymentStatus != null || row.orderValue != null` so a row with
neither shows no divider/footer.

- [ ] **Step 4: Remove dead code + imports**

Delete `formatCreatedDate`, `MONTH_ABBREV`, and the now-unused `MetaItem` (the new footer
uses plain `Text`; keep `MetaSeparator`). Remove imports: `Straighten`, `Schedule`,
`CalendarToday` (only if the DueInPill no longer needs it — it DOES, keep `CalendarToday`),
`Instant`/`LocalDate`/`TimeZone`/`toLocalDateTime` (only `formatCreatedDate` used them — grep),
and the string imports `dashboard_pipeline_row_custom_size` / `dashboard_pipeline_row_bespoke`
/ `dashboard_pipeline_row_created`. Add `import com.danzucker.stitchpad.core.sharing.formatPrice`.
In `strings.xml`, remove `dashboard_pipeline_row_custom_size` + `dashboard_pipeline_row_bespoke`
(zero refs after this), and `dashboard_pipeline_row_created` only if grep shows no remaining use.

- [ ] **Step 5: Update the preview**

In `PipelineOrderRowPreview`, add `orderValue = 40000.0, paymentStatus = PipelinePaymentStatus.DepositDue` to the `DashboardOrderRow(...)` so the preview shows the new footer. Import `PipelinePaymentStatus`.

- [ ] **Step 6: Verify + commit**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileTestKotlinIosSimulatorArm64 detekt -q` then `./gradlew :composeApp:testDebugUnitTest --tests '*Dashboard*' --tests '*Pipeline*' --tests '*BucketCalculator*' -q`. Green. Watch detekt for unused imports (avatar/created cleanup).
```bash
git add -A
git commit -m "feat(dashboard): pipeline card uses solid avatar + deposit/value footer"
```

---

## Manual smoke test (device — Daniel is QA)
1. Dashboard "Work pipeline" rows show a **solid app-standard avatar** (per-customer color), no dashed ring.
2. Footer reads e.g. **"Deposit due · ₦40,000"** — "Deposit due" in warm sienna when nothing's collected; a deposited order reads calm "Deposit paid · ₦…"; a settled order reads "Paid · ₦…".
3. No "Custom size · Bespoke" anywhere.

## Self-review notes
- Task 1 (data + pure helper + tests) → Task 2 (UI). ✓
- `CustomerAvatar` (app-standard); footer = sienna-due chip + `formatPrice` value; noise removed. ✓
- Nullable fields degrade gracefully; no hardcoded hex; no saffron; strings resourced. ✓
- iOS test compile in every gate; helper + mapper tests in commonTest. ✓
