# Order Costing & Profit — Design

**Date:** 2026-07-24
**Status:** Approved (design), pending implementation plan
**Branch/worktree:** `worktree-order-costing-profit` (branched fresh from `origin/main`)

## Context

An active user (Mabel) reported the core pain over WhatsApp: she gets plenty of orders but isn't "making more." When she analysed a ₦50,000 three-piece suit, her real profit was only ~₦6,000 — fabric, logistics, and finishing had quietly eaten the rest. StitchPad today tracks only **money coming in**: `Order.totalPrice`, `discount`, `payableTotal`, and a list of `Payment`s drive Revenue / Collected / Outstanding. There is **no field anywhere for money going out**, so the app cannot show profit — only revenue. Mabel explicitly asked for "a section that helps tailor do proper costing… to evaluate profit properly."

This is the first slice of a larger future "business/admin" area (expenses ledger, P&L, inventory). We ship **only per-order costing → profit** now, designed so the data feeds later work.

**Honesty principle (drove every decision):** costing records the tailor's *own real numbers*, never market estimates or AI guesses. Speculating prices would hand back the same illusion that burned Mabel. Recording actual costs is factual, offline, free, and can't hallucinate. As a bonus, this real cost history is exactly the grounding data the separately-scoped **"Help Me Price This"** AI intent (docs/superpowers/specs/2026-05-16-ai-assistant-design.md, currently a "Coming soon" tile) needs to give honest selling-price advice later — but that feature is out of scope here.

## Decisions (all user-approved)

1. **Cost structure:** categorized cost lines — one amount per fixed category, optional note.
2. **Category set (Complete 6, research-backed):** `FABRIC`, `MATERIALS_TRIMS`, `EMBELLISHMENT`, `LABOUR`, `LOGISTICS`, `OTHER`. Embellishment is broken out because in Nigerian tailoring it's often a separate vendor bill (embroidery/beading/stoning). Every category is optional; only filled ones render.
3. **Placement:** a **separate, private** "Costs & Profit" card in order detail, directly below the existing Payment card. Kept apart from customer-facing payment figures.
4. **Reports:** add a **Profit** KPI tile, honestly scoped to orders that have costs recorded, with a coverage line ("on 8 of 20 orders"). Inherits the existing Reports **Pro** paywall.
5. **Tier gating:** cost entry + per-order profit are **free**; the aggregated Profit tile in Reports is **Pro** (matches today's Reports gating). Note: the launch-free grant makes everything free through 2026 regardless; this sets the eventual model.
6. **Profit basis:** profit is computed on the full order value (`payableTotal`), independent of how much has been collected — matching how a tailor reasons about a job. Negative profit renders honestly as a red **Loss**.

## Data model — `core/domain/model/Order.kt`

```kotlin
enum class CostCategory { FABRIC, MATERIALS_TRIMS, EMBELLISHMENT, LABOUR, LOGISTICS, OTHER }

data class OrderCost(
    val category: CostCategory,
    val amount: Double,
    val note: String? = null,
)
```

Add to `Order`:
```kotlin
val costs: List<OrderCost> = emptyList(),
```

Derived helpers on `Order` (alongside existing `payableTotal` / `balanceRemaining`):
- `totalCost: Double get() = costs.sumOf { it.amount }`
- `profit: Double get() = payableTotal - totalCost`
- `profitMargin: Double? get() = if (payableTotal > 0.0) profit / payableTotal else null`  *(null → show amount only, no %)*
- `hasCosts: Boolean get() = costs.isNotEmpty()`

Notes:
- One `OrderCost` per category at most (Option A = categorized lines). Modelled as a `List` (not a `Map`) for forward flexibility and stable serialization; UI/editor enforces one-per-category.
- No new "own-time / notional labour" concept — `LABOUR` means real cash out (apprentice/handwork/embroidery vendor). Valuing the tailor's own unpaid time is a deliberately deferred future idea.

## Persistence — DTO, mapper, repository

**`core/data/dto/OrderDto.kt`:** add
```kotlin
val costs: List<OrderCostDto> = emptyList(),
```
and a new `OrderCostDto(category: String = "OTHER", amount: Double = 0.0, note: String? = null)` mirroring `PaymentDto`. Old Firestore docs without `costs` deserialize to empty — **no migration required**.

**`core/data/mapper/OrderMapper.kt`:** round-trip `costs` both directions (same pattern as `payments`). Map `CostCategory` ↔ String defensively (unknown string → `OTHER`, matching existing enum-mapping style).

**`OrderRepository` (interface + `FirebaseOrderRepository`):** add
```kotlin
suspend fun updateCosts(
    userId: String,
    orderId: String,
    costs: List<OrderCost>,
): EmptyResult<DataError.Network>
```
A **targeted field write** of `costs` + `updatedAt` only, mirroring `recordPayment` / `updateNotes`. It must NOT touch `serverCreatedAt` / `createdAt`, keeping it inside the Lane-B immutability rules in `firestore.rules` (`serverCreatedAtProtectedOnUpdate()` / `activityCreatedAtStableOnUpdate()` on `match /orders/{orderId}`). No Firestore rules change is needed — costs are ordinary owner-scoped fields.

## Order-detail UI — `feature/order/presentation/detail/`

New composable **`components/OrderCostsCard.kt`**, rendered in `OrderDetailScreen` immediately after `OrderPaymentCard`. Follows the Payment card's construction (Surface + `radiusLg` + `SectionIconTile` header). Sienna-tinted icon tile to read as "private/business" vs the indigo Payment tile.

**Filled state:** one row per filled category (label + monospace ₦ amount, optional note as a small hint), a "＋ Add cost" affordance, a `HorizontalDivider`, **Total cost**, then a **Profit** band:
- Positive → success band (`success500` / `successDarkBg`+`successDarkText`), label "Profit", value `₦x` + margin %.
- Negative → error band (`error500` / `errorDarkBg`+`errorDarkText`), label "Loss", value `−₦x` + negative %.
- Footer caption with a lock glyph: "Private — never shown on receipts."

**Empty state (no costs yet):** quiet CTA — "Add what this order cost you — fabric, trims, labour — to see your real profit." + an "＋ Add costs" button + the same privacy caption.

**Editing — `components/CostsEditorSheet.kt`** (ModalBottomSheet, mirrors `RecordPaymentDialogV2` patterns incl. the [[feedback-ios-modal-bottom-sheet-timing]] delay): the 6 categories as ₦ amount fields with hint subtitles; blank = doesn't apply. "Save costs" dispatches to the ViewModel. Use `TextFieldValue` binding per [[feedback-compose-textfieldvalue-cursor]] for amount fields.

**MVI wiring:**
- `OrderDetailState`: costs already arrive on the observed `Order`; add editor-sheet visibility + in-progress edit buffer.
- `OrderDetailAction`: `OnEditCostsClick`, `OnCostsEditorDismiss`, `OnSaveCosts(costs: List<OrderCost>)` (+ field-change actions for the buffer, following existing form conventions).
- `OrderDetailViewModel`: on save, call `orderRepository.updateCosts(...)`, map failure to `UiText`, emit snackbar per [[feedback-notification-patterns]].
- All strings via `compose.resources` (positional args per [[feedback-compose-resources-positional-args]]); no backslash escapes per [[feedback-strings-no-backslash-escape]].

## Reports — `feature/reports/`

**`domain/KpiCalculator.kt`:** in each window, additionally accumulate over orders where `hasCosts`:
- `profit += order.profit` (i.e. `payableTotal − totalCost`)
- track `ordersWithCosts` count and total `ordersInWindow` count for coverage
- margin for the tile = `profit / revenueOfCostedOrders` (guard divide-by-zero).

**`domain/model/Kpi.kt` / `KpiSummary`:** add `profit: Kpi` and a coverage pair (`ordersWithCosts`, `ordersInWindow`). Sparkline for profit follows the existing per-bucket approach, summing `profit` over costed orders per bucket.

**`presentation/components/KpiGrid.kt` + `KpiTile.kt`:** add the Profit tile (success-tinted, may span full width per the approved mock). Show value, margin %, and a coverage caption: "On N of M orders with costs recorded." When `ordersWithCosts == 0`, show an inviting empty tile ("Add costs to an order to see profit") rather than ₦0. Gated by the existing Reports Pro paywall (`ReportsPaywallCard`) — **no new gating code**.

## Privacy guardrail (explicit)

Costs and profit appear **only** on `OrderCostsCard` and the Pro Reports tile. They must **never** be added to `core/sharing/ReceiptData.kt`, `ReceiptFormatter.kt`, `OrderReceiptSharer`, or any WhatsApp/share path. `ReceiptData` is an explicit field allow-list, so the guarantee holds by construction — the rule here is "do not add costs to it." A test asserts the receipt/share surface contains no cost/profit fields.

## Testing

- `OrderMapperTest`: round-trip an order with a full `costs` list (incl. note + `OTHER`), and an order with no `costs` (legacy doc → empty).
- `OrderTest` (new or existing): `totalCost`, `profit`, `profitMargin` (incl. `payableTotal == 0` → null margin), `hasCosts`, and the **Loss** case (costs > payable → negative profit).
- `KpiCalculatorTest`: profit + coverage over a **mixed** book (some orders with costs, some without) — asserts profit sums only costed orders and coverage counts are correct; divide-by-zero guard.
- `OrderDetailViewModel` test (Turbine): edit-costs actions → `updateCosts` called → state/snackbar on success and failure.
- Privacy test: `ReceiptFormatter` output exposes no cost/profit fields.
- Test conventions per [[reference-test-toolchain]] / [[feedback-kmp-backtick-test-names-jvm]]; gate on `compileTestKotlinIosSimulatorArm64`.

## Verification (end-to-end)

1. **iOS compile gate first** (per [[feedback-kmp-jvm-only-apis]] / [[feedback-kotlin-native-epoch-days]]): `./gradlew :composeApp:compileKotlinIosSimulatorArm64`.
2. Unit tests: `./gradlew :composeApp:testDebugUnitTest` and `:compileTestKotlinIosSimulatorArm64`.
3. Detekt: `./gradlew detekt` (previews may need `@file:Suppress("TooManyFunctions")` per [[feedback-detekt-toomanyfunctions-previews]]).
4. Run the app (Android + iOS sim). On an order: open **Costs & Profit** → add costs across categories → Save → verify the Profit band, the Loss case (enter costs > total), and the empty-state CTA. Confirm both light and dark.
5. Share a receipt for that same order → confirm **no** costs/profit appear anywhere on it.
6. Reports (as a Pro/Atelier account per [[reference-test-environment]]): confirm the Profit tile, margin %, and coverage line; confirm a Free account still sees the paywall.
7. Manual QA smoke steps written into the PR per [[feedback-qa-smoke-tests]].

## Out of scope (future slices)

Expenses ledger (costs not tied to an order), full P&L statement, materials/inventory, tax, notional own-labour valuation, and the "Help Me Price This" AI selling-price advisor (which will *consume* this cost history).
