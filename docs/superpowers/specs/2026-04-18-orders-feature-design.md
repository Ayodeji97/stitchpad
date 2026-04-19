# Orders Feature Design Spec

## Context

StitchPad is a KMP + Compose Multiplatform app for Nigerian tailors. Customers, measurements, and styles are already implemented (Sprints 1-2). The Orders feature is the Sprint 3 priority — it lets tailors create, track, and manage sewing orders tied to their customers.

This covers all 6 Sprint 3 backlog items:
- Create order form (P0, 5h)
- Order list with status filters (P0, 4h)
- Order detail screen with full info and status history (P0, 3h)
- Edit/delete order with confirmation (P1, 2h)
- Deposit and balance tracking per order (P1, 2h)
- Overdue order highlighting (P1, 2h)

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Order items | Embedded list in Order document | Typical tailoring orders have 1-3 items. No need for subcollections. |
| Status flow | Pending -> In Progress -> Ready -> Delivered | Start simple, expand later if tailors need granular stages. |
| Payments | Total price + deposit + balance in V1 | Currency is Naira. No payment gateway — just manual tracking. |
| Data linking | Customer + optional style(s) + optional measurement per item | Full traceability of what's being made with which measurements. |
| Firestore path | `users/{userId}/orders/{orderId}` | Top-level for easy cross-customer queries in the Orders tab. |
| Architecture | Flat Order model with embedded items + status history | Single document read per order. Simple and fast. |
| Order form UX | 3-step wizard (Customer -> Items -> Details) | Less overwhelming than one long form. Matches tailor mental model. |

---

## Domain Model

### `core/domain/model/Order.kt`

```kotlin
enum class OrderStatus {
    PENDING,        // Accepted, not started
    IN_PROGRESS,    // Tailor is working on it
    READY,          // Finished, awaiting pickup/delivery
    DELIVERED       // Handed to customer
}

enum class OrderPriority {
    NORMAL,         // Standard turnaround
    URGENT,         // Needs attention soon
    RUSH            // Top priority, e.g., event tomorrow
}

data class OrderItem(
    val id: String,
    val garmentType: GarmentType,      // Reuses existing enum
    val description: String,
    val price: Double,                  // Price in Naira
    val styleId: String? = null,
    val measurementId: String? = null
)

data class StatusChange(
    val status: OrderStatus,
    val changedAt: Long                 // Epoch millis
)

data class Order(
    val id: String,
    val userId: String,
    val customerId: String,
    val customerName: String,           // Denormalized for list display
    val items: List<OrderItem>,
    val status: OrderStatus,
    val priority: OrderPriority,
    val statusHistory: List<StatusChange>,
    val totalPrice: Double,
    val depositPaid: Double,
    val balanceRemaining: Double,       // totalPrice - depositPaid
    val deadline: Long?,                // Nullable — not all orders have deadlines
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

---

## Data Layer

### Firestore Document Structure

Path: `users/{userId}/orders/{orderId}`

```
orderId/
  customerId: String
  customerName: String
  status: String              // "PENDING" | "IN_PROGRESS" | "READY" | "DELIVERED"
  priority: String            // "NORMAL" | "URGENT" | "RUSH"
  totalPrice: Double
  depositPaid: Double
  balanceRemaining: Double
  deadline: Long?
  notes: String?
  createdAt: Long
  updatedAt: Long
  items: [                    // Embedded array
    {
      id: String
      garmentType: String
      description: String
      price: Double
      styleId: String?
      measurementId: String?
    }
  ]
  statusHistory: [            // Embedded array
    {
      status: String
      changedAt: Long
    }
  ]
```

### DTOs — `core/data/dto/OrderDto.kt`

```kotlin
@Serializable
data class OrderDto(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val status: String = "PENDING",
    val priority: String = "NORMAL",
    val totalPrice: Double = 0.0,
    val depositPaid: Double = 0.0,
    val balanceRemaining: Double = 0.0,
    val deadline: Long? = null,
    val notes: String? = null,
    val items: List<OrderItemDto> = emptyList(),
    val statusHistory: List<StatusChangeDto> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class OrderItemDto(
    val id: String = "",
    val garmentType: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val styleId: String? = null,
    val measurementId: String? = null
)

@Serializable
data class StatusChangeDto(
    val status: String = "PENDING",
    val changedAt: Long = 0L
)
```

### Mapper — `core/data/mapper/OrderMapper.kt`

Extension functions following existing pattern:
- `OrderDto.toOrder(): Order`
- `Order.toOrderDto(): OrderDto`
- `OrderItemDto.toOrderItem(): OrderItem`
- `OrderItem.toOrderItemDto(): OrderItemDto`
- `StatusChangeDto.toStatusChange(): StatusChange`
- `StatusChange.toStatusChangeDto(): StatusChangeDto`

Enum parsing uses `runCatching { valueOf() }.getOrDefault(default)` (same as CustomerMapper).

### Repository Interface — `core/domain/repository/OrderRepository.kt`

```kotlin
interface OrderRepository {
    fun observeOrders(userId: String): Flow<Result<List<Order>, DataError.Network>>
    suspend fun getOrder(userId: String, orderId: String): Result<Order, DataError.Network>
    suspend fun createOrder(userId: String, order: Order): EmptyResult<DataError.Network>
    suspend fun updateOrder(userId: String, order: Order): EmptyResult<DataError.Network>
    suspend fun updateOrderStatus(userId: String, orderId: String, newStatus: OrderStatus): EmptyResult<DataError.Network>
    suspend fun deleteOrder(userId: String, orderId: String): EmptyResult<DataError.Network>
}
```

### Implementation — `feature/order/data/FirebaseOrderRepository.kt`

- Collection: `firestore.collection("users").document(userId).collection("orders")`
- `observeOrders`: snapshots() sorted by createdAt descending, mapped via DTO
- `updateOrderStatus`: reads current order, appends StatusChange to history, updates status + updatedAt
- Same error handling pattern as FirebaseCustomerRepository

### DI — `di/OrderModule.kt`

```kotlin
val orderDataModule = module {
    singleOf(::FirebaseOrderRepository) bind OrderRepository::class
}

val orderPresentationModule = module {
    viewModelOf(::OrderListViewModel)
    viewModelOf(::OrderFormViewModel)
    viewModelOf(::OrderDetailViewModel)
}
```

---

## Presentation Layer (MVI)

### Screen 1: Order List — `feature/order/presentation/list/`

Replaces `OrdersPlaceholderRoute` in the bottom nav Orders tab.

**State:**
```kotlin
data class OrderListState(
    val orders: List<Order> = emptyList(),
    val statusFilter: OrderStatus? = null,     // null = "All"
    val showOverdueOnly: Boolean = false,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val orderToDelete: Order? = null,
    val errorMessage: UiText? = null
)
```

**Actions:** `OnStatusFilterChange(status)`, `OnOrderClick(order)`, `OnAddOrderClick`, `OnDeleteClick(order)`, `OnConfirmDelete`, `OnDismissDelete`

**Events:** `NavigateToOrderForm`, `NavigateToOrderDetail(orderId)`

**UI Details:**
- Filter chips row: All | Pending | In Progress | Ready | Delivered | Overdue
- Order cards show: customer name, item count + garment types, status badge (colored), priority indicator (icon/color for Urgent/Rush), deadline (with overdue highlighting in red), total price
- Overdue = deadline is in the past AND status is not DELIVERED
- Sort: overdue first, then by deadline (soonest), then by priority
- FAB to create new order
- Swipe-to-delete with confirmation dialog
- Empty state: icon + "No orders yet. Tap + to create one."

### Screen 2: Order Form — `feature/order/presentation/form/`

3-step wizard for creating and editing orders.

**State:**
```kotlin
data class OrderFormState(
    val currentStep: Int = 1,
    val isEditMode: Boolean = false,
    // Step 1 - Customer
    val customers: List<Customer> = emptyList(),
    val customerSearchQuery: String = "",
    val selectedCustomer: Customer? = null,
    // Step 2 - Items
    val items: List<OrderItemFormState> = listOf(OrderItemFormState()),
    val availableStyles: List<Style> = emptyList(),
    val availableMeasurements: List<Measurement> = emptyList(),
    // Step 3 - Details
    val deadline: Long? = null,
    val priority: OrderPriority = OrderPriority.NORMAL,
    val depositPaid: String = "",
    val notes: String = "",
    // General
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: UiText? = null
)

data class OrderItemFormState(
    val id: String = generatedUUID,
    val garmentType: GarmentType? = null,
    val description: String = "",
    val price: String = "",
    val styleId: String? = null,
    val measurementId: String? = null
)
```

**Actions:** `OnNextStep`, `OnPreviousStep`, `OnSelectCustomer(customer)`, `OnCustomerSearchChange(query)`, `OnAddItem`, `OnRemoveItem(itemId)`, `OnItemGarmentTypeChange(itemId, type)`, `OnItemDescriptionChange(itemId, text)`, `OnItemPriceChange(itemId, price)`, `OnItemStyleChange(itemId, styleId)`, `OnItemMeasurementChange(itemId, measurementId)`, `OnDeadlineChange(deadline)`, `OnPriorityChange(priority)`, `OnDepositChange(deposit)`, `OnNotesChange(notes)`, `OnSave`

**Events:** `NavigateBack`, `OrderSaved`

**Wizard Steps:**

1. **Customer Selection** — Searchable list of existing customers. Tap to select. "Next" enabled only when a customer is selected. In edit mode, customer is pre-selected and read-only.

2. **Line Items** — Card per item with: garment type dropdown (GarmentType enum values), description text field, price number field (Naira). Optional: style picker (loads from selected customer's styles), measurement picker (loads from selected customer's measurements). "Add Item" button below. Remove button per item (if > 1 item). "Next" enabled when all items have garment type + valid price.

3. **Details** — Date picker for deadline (optional), priority chips (Normal/Urgent/Rush), deposit paid number field, notes text area. "Create Order" / "Save Changes" button.

**On selecting a customer (Step 1):** Pre-load that customer's styles and measurements so they're available in Step 2 dropdowns.

### Screen 3: Order Detail — `feature/order/presentation/detail/`

**State:**
```kotlin
data class OrderDetailState(
    val order: Order? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val showStatusUpdateDialog: Boolean = false,
    val errorMessage: UiText? = null
)
```

**Actions:** `OnEditClick`, `OnDeleteClick`, `OnConfirmDelete`, `OnDismissDelete`, `OnUpdateStatusClick`, `OnConfirmStatusUpdate`, `OnDismissStatusUpdate`, `OnCustomerClick`, `OnBackClick`

**Events:** `NavigateToOrderForm(orderId)`, `NavigateToCustomerDetail(customerId)`, `NavigateBack`, `OrderDeleted`

**UI Layout:**
- Top bar: back arrow, "Order Details" title, edit icon
- Customer section: name + phone (tappable -> navigates to customer detail)
- Items section: list of items with garment type, description, price each
- Financial section: total price, deposit paid, balance remaining
- Status section: current status badge + "Update Status" button (advances to next status). Status history timeline below showing all transitions with timestamps.
- Details section: deadline (with overdue warning if applicable), priority badge, notes
- Delete button at bottom (with confirmation dialog)

---

## Navigation

### New Routes — update `navigation/Routes.kt`

```kotlin
@Serializable
data object OrderListRoute                              // Replaces OrdersPlaceholderRoute

@Serializable
data class OrderFormRoute(val orderId: String? = null)   // null = create, non-null = edit

@Serializable
data class OrderDetailRoute(val orderId: String)
```

### Navigation Graph Updates

- `MainScreen.kt`: Replace `OrdersPlaceholderRoute` composable with `OrderListRoot`
- `BottomNavItem.Orders`: Change route to `OrderListRoute`
- Add `OrderFormRoute` and `OrderDetailRoute` composables in nav graph
- Cross-feature: OrderDetail -> CustomerDetail navigation via callback

---

## Error Handling

### `feature/order/domain/OrderError.kt`

No feature-specific errors needed beyond `DataError.Network`. Use existing error mapping pattern:

```kotlin
fun DataError.Network.toOrderUiText(): UiText { ... }
```

Maps network errors to user-facing strings (same as customer/measurement error mappers).

---

## String Resources

Add to `composeResources/values/strings.xml`:
- Order list: title, filter labels, empty state, delete confirmation
- Order form: step titles, field labels, validation messages, save button labels
- Order detail: section headers, status labels, status update confirmation
- Priority labels: Normal, Urgent, Rush
- Status labels: Pending, In Progress, Ready, Delivered
- Overdue indicator text

---

## Files to Create/Modify

### New Files (~18 files)
```
core/domain/model/Order.kt                              — Domain model + enums
core/data/dto/OrderDto.kt                                — Serializable DTOs
core/data/mapper/OrderMapper.kt                          — DTO <-> domain mappers
core/domain/repository/OrderRepository.kt                — Repository interface
feature/order/domain/OrderError.kt                       — Error -> UiText mapping
feature/order/data/FirebaseOrderRepository.kt            — Firestore implementation
di/OrderModule.kt                                        — Koin data + presentation modules
feature/order/presentation/list/OrderListState.kt
feature/order/presentation/list/OrderListAction.kt
feature/order/presentation/list/OrderListEvent.kt
feature/order/presentation/list/OrderListViewModel.kt
feature/order/presentation/list/OrderListScreen.kt       — Root + Screen composables
feature/order/presentation/form/OrderFormState.kt
feature/order/presentation/form/OrderFormAction.kt
feature/order/presentation/form/OrderFormEvent.kt
feature/order/presentation/form/OrderFormViewModel.kt
feature/order/presentation/form/OrderFormScreen.kt       — Root + Screen composables
feature/order/presentation/detail/OrderDetailState.kt
feature/order/presentation/detail/OrderDetailAction.kt
feature/order/presentation/detail/OrderDetailEvent.kt
feature/order/presentation/detail/OrderDetailViewModel.kt
feature/order/presentation/detail/OrderDetailScreen.kt   — Root + Screen composables
```

### Modified Files
```
navigation/Routes.kt                — Add Order routes, remove placeholder
feature/main/presentation/MainScreen.kt — Wire OrderListRoot into nav graph
di/CoreModule.kt (or App.kt)        — Register order modules
composeResources/values/strings.xml  — Add order string resources
```

---

## Verification Plan

1. **Build**: `./gradlew :composeApp:assembleDebug` — compiles without errors
2. **Order List**: Launch app, tap Orders tab. Shows empty state. Create an order via FAB. Order appears in list. Filter chips work.
3. **Order Form Wizard**: Step through all 3 steps. Validate customer selection required, at least 1 item with price required. Create order successfully.
4. **Order Detail**: Tap order in list. All info displays. Update status works (Pending -> In Progress). Status history updates.
5. **Edit/Delete**: Edit order from detail screen. Delete order with confirmation.
6. **Overdue**: Create order with past deadline. Verify red/warning indicator shows in list.
7. **Deposit tracking**: Create order with deposit. Verify balance = total - deposit displays correctly.
8. **iOS**: Open in Xcode, build and run on simulator. Verify all screens render correctly.
