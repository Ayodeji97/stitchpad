# Pipeline card: solid avatar + payment·value footer — design

**Status:** Design approved (2026-06-22)
**Branch:** `feat/pipeline-card-payment-footer`
**Source:** dashboard "Work pipeline" card review. Two elements carry no information:
the **dashed avatar ring** (pure decoration) and the **"Custom size · Bespoke"** footer
labels (hardcoded constants — identical on every order, per the code comment). Replace
them with the app-standard avatar and a footer that earns its space.

## Goals
1. **Avatar:** replace the bespoke dashed-ring avatar with the app-standard `CustomerAvatar`
   (solid, rounded, per-customer colored like the rest of the app).
2. **Footer:** drop "Custom size", "Bespoke", and the created date. Show **deposit status +
   order value** instead — actionable at a glance for a tailor scanning the pipeline.

## Out of scope
Other dashboard sections; the due-date pill, garment row, and name stay as-is.

---

## 1. Data model

`feature/dashboard/domain/model/DashboardOrderRow.kt` — add two nullable fields (nullable
so legacy/preview call-sites that don't set them simply omit the footer items):
```kotlin
    /** Order payable total (after discount). Null → omit the value in the footer. */
    val orderValue: Double? = null,
    /** Deposit/payment state for the footer chip. Null → omit the chip. */
    val paymentStatus: PipelinePaymentStatus? = null,
```
Keep the existing `createdAtEpochMillis` field (still set by `BucketCalculator`); it's
just no longer rendered. (Optional: remove it if no other reference remains — implementer's
call; not required.)

New pure helper + enum — `feature/dashboard/domain/PipelinePaymentStatus.kt`:
```kotlin
package com.danzucker.stitchpad.feature.dashboard.domain

enum class PipelinePaymentStatus { DepositDue, DepositPaid, Paid }

/**
 * Derives the pipeline footer's payment chip from the order's collected vs payable amount.
 * - payableTotal <= 0  -> Paid (nothing to collect, e.g. a zero/free order)
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

---

## 2. Mapper (`BucketCalculator`)

`Order.payableTotal` and `Order.depositPaid` already exist (computed props). In
`Order.toPipelineRow(...)` add:
```kotlin
        orderValue = payableTotal,
        paymentStatus = pipelinePaymentStatusOf(depositPaid, payableTotal),
```
Also set the same two fields in `Order.toRow(...)` (the overdue/needs-attention mapper) —
both have the `Order`, and if those rows ever render through `PipelineOrderRow` they need
the data too. (Cheap + safe; verify which composable consumes `toRow` and only skip if it
definitely never uses `PipelineOrderRow`.)

---

## 3. `PipelineOrderRow` UI

`feature/dashboard/presentation/components/PipelineOrderRow.kt`:

**Avatar:** replace `DashedAvatar(initials = pipelineInitialsOf(row.customerName))` with
```kotlin
        CustomerAvatar(name = row.customerName, size = AVATAR_SIZE)
```
Delete the `DashedAvatar` composable and `pipelineInitialsOf` (unless used elsewhere —
grep). Add `import ...ui.components.CustomerAvatar`.

**Footer:** replace `MetadataFooter`'s three items with the payment chip + value. Render
the divider + footer only when there's something to show (`paymentStatus != null ||
orderValue != null`):
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
                // Sienna (tertiary) = the brand's warmth accent — a soft "attention",
                // not an alarming error and NOT saffron. The other states stay calm.
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
Update the call site `MetadataFooter(createdAt = row.createdAtEpochMillis)` →
`MetadataFooter(row = row)`; if both `paymentStatus` and `orderValue` are null, skip the
`Spacer/HorizontalDivider/MetadataFooter` block entirely.

**Remove (now-unused):** `formatCreatedDate`, `MONTH_ABBREV`, the `Straighten` +
`Schedule` icon imports, and the `dashboard_pipeline_row_custom_size` /
`dashboard_pipeline_row_bespoke` / `dashboard_pipeline_row_created` string imports.
**Add:** `import com.danzucker.stitchpad.core.sharing.formatPrice` and the three new strings.
(Keep the `Checkroom` import — still used by `GarmentRow`.)

---

## 4. Strings (`strings.xml`)

Add:
```xml
    <string name="dashboard_pipeline_deposit_due">Deposit due</string>
    <string name="dashboard_pipeline_deposit_paid">Deposit paid</string>
    <string name="dashboard_pipeline_paid">Paid</string>
```
Remove `dashboard_pipeline_row_custom_size` and `dashboard_pipeline_row_bespoke` (zero
references after this change). Remove `dashboard_pipeline_row_created` only if grep shows
no remaining reference.

---

## 5. Testing

- **`PipelinePaymentStatusTest`** (new, commonTest): `pipelinePaymentStatusOf` →
  `(0.0, 40000.0)` = DepositDue; `(20000.0, 40000.0)` = DepositPaid; `(40000.0, 40000.0)`
  = Paid; `(50000.0, 40000.0)` = Paid (overpaid); `(0.0, 0.0)` = Paid (zero total).
- **`BucketCalculatorTest`** (extend): a pipeline row built from an order carries
  `orderValue == payableTotal` and the expected `paymentStatus` (e.g. no payments →
  DepositDue).
- **Preview:** update `PipelineOrderRowPreview` to pass `orderValue = 40000.0,
  paymentStatus = PipelinePaymentStatus.DepositDue` so the preview reflects the new footer.
- The card itself is visual — manual device smoke.

## 6. Manual smoke test (device — Daniel is QA)
1. Dashboard "Work pipeline" rows now show a **solid app-standard avatar** (per-customer
   color), no dashed ring.
2. Footer reads e.g. **"Deposit due · ₦40,000"** — "Deposit due" in warm sienna when no
   payment is collected; an order with a deposit reads a calm "Deposit paid · ₦…"; a
   settled order reads "Paid · ₦…".
3. No more "Custom size · Bespoke" on any row.

## 7. Self-review checks
- Avatar → `CustomerAvatar` (app-standard, per-customer color). ✓
- Footer = deposit status (sienna/tertiary for due, calm otherwise) + value via
  `formatPrice`; "Custom size"/"Bespoke"/created removed. ✓
- Status from a pure unit-tested helper; nullable fields degrade gracefully. ✓
- No hardcoded hex (uses `scheme.tertiary`); no saffron; strings resourced. ✓
- iOS test compile in every gate; helper + mapper tests in commonTest. ✓
