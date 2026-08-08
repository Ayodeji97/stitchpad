package com.danzucker.stitchpad.feature.customer.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerSlotState
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.workshopUidOrNull
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.core.util.WhatsAppMessageBuilder
import com.danzucker.stitchpad.feature.customer.presentation.toCustomerUiText
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryDestination
import com.danzucker.stitchpad.feature.measurement.presentation.entry.MeasurementEntryResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.customer_delete_orders_load_failed
import stitchpad.composeapp.generated.resources.customer_delete_pending_orders_load

private const val SHEET_DISMISS_DELAY_MS = 450L

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerListViewModel(
    private val customerRepository: CustomerRepository,
    private val orderRepository: OrderRepository,
    private val activeWorkshopProvider: ActiveWorkshopProvider,
    private val freemiumRepository: FreemiumRepository,
    private val measurementEntryResolver: MeasurementEntryResolver,
) : ViewModel() {

    /** Cached count of non-delivered orders per customer id, maintained by [observeOrders]. */
    private var activeOrderCountByCustomerId: Map<String, Int> = emptyMap()

    private var hasLoadedInitialData = false
    private var allCustomers: List<Customer> = emptyList()
    private var allLockedCustomers: List<Customer> = emptyList()

    // Tracks the workshopUid each listener last subscribed under, so a genuine
    // A -> B switch (not the first-ever subscribe, and not a transition to/from
    // null — already handled by the `workshopUid == null` branch below) can be
    // told apart from a cold start. See the reset note in observeCustomers.
    private var lastCustomersWorkshopUid: String? = null
    private var lastOrdersWorkshopUid: String? = null

    private val _state = MutableStateFlow(CustomerListState())

    private val _events = Channel<CustomerListEvent>()
    val events = _events.receiveAsFlow()

    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                hasLoadedInitialData = true
                observeCustomers()
                observeOrders()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CustomerListState()
        )

    init {
        // Slice 6c: reflect active-staff into state so rows/sheet hide contact
        // (phone + WhatsApp) and the WhatsApp handler early-returns.
        viewModelScope.launch {
            activeWorkshopProvider.flow.collect { session ->
                _state.update { it.copy(isActiveStaff = session.isActiveStaff) }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun onAction(action: CustomerListAction) {
        when (action) {
            is CustomerListAction.OnSearchQueryChange -> {
                _state.update {
                    it.copy(
                        searchQuery = action.query,
                        customers = filterCustomers(allCustomers, action.query),
                        lockedCustomers = filterCustomers(allLockedCustomers, action.query),
                    )
                }
            }
            is CustomerListAction.OnCustomerClick -> {
                viewModelScope.launch {
                    _events.send(CustomerListEvent.NavigateToCustomerDetail(action.customer.id))
                }
            }
            CustomerListAction.OnAddCustomerClick -> {
                // Slice 6c: staff can't create customers. Defense-in-depth — the FAB
                // is hidden, and the empty-state add button no-ops here.
                if (_state.value.isActiveStaff) return
                viewModelScope.launch {
                    _events.send(CustomerListEvent.NavigateToAddCustomer)
                }
            }
            is CustomerListAction.OnDeleteCustomerClick -> {
                // Slice 6c: staff can't delete customers. Defense-in-depth — the
                // sheet hides the Delete row (canMutate = false).
                if (_state.value.isActiveStaff) return
                val activeCount = activeOrderCountByCustomerId[action.customer.id] ?: 0
                _state.update {
                    it.copy(
                        // Sheet may be open (delete tapped from CustomerActionsSheet) — close it
                        // before the dialog appears so the user only sees one modal at a time.
                        // Dialog is Compose-native, not UIKit, so no 450ms timing race needed.
                        actionsSheetForId = null,
                        showDeleteDialog = true,
                        customerToDelete = action.customer,
                        customerToDeleteActiveOrderCount = activeCount
                    )
                }
            }
            CustomerListAction.OnConfirmDelete -> {
                // Slice 6c: guard the confirm/execute too (cold-start race — see
                // OnDeleteCustomerClick). Server rules also deny staff delete.
                if (_state.value.isActiveStaff) return
                deleteCustomer()
            }
            CustomerListAction.OnDismissDeleteDialog -> {
                _state.update {
                    it.copy(
                        showDeleteDialog = false,
                        customerToDelete = null,
                        customerToDeleteActiveOrderCount = 0
                    )
                }
            }
            CustomerListAction.OnErrorDismiss -> {
                _state.update { it.copy(errorMessage = null) }
            }
            is CustomerListAction.OpenSwapSheetFor -> {
                _state.update { it.copy(swapSheetForId = action.lockedCustomerId) }
            }
            CustomerListAction.DismissSwapSheet -> {
                _state.update { it.copy(swapSheetForId = null) }
            }
            is CustomerListAction.ConfirmSwap -> {
                viewModelScope.launch {
                    val swapResult = freemiumRepository.swapCustomerSlot(
                        promote = action.lockedCustomerId,
                        demote = action.activeCustomerIdToDemote,
                    )
                    when (swapResult) {
                        is Result.Success -> {
                            val firstName = _state.value.lockedCustomers
                                .firstOrNull { it.id == action.lockedCustomerId }
                                ?.name
                                ?.substringBefore(" ")
                                ?: ""
                            _events.send(CustomerListEvent.SwapSucceeded(firstName))
                        }
                        is Result.Error -> _events.send(CustomerListEvent.SwapFailed)
                    }
                    _state.update { it.copy(swapSheetForId = null) }
                }
            }
            is CustomerListAction.OnOverflowClick -> {
                _state.update { it.copy(actionsSheetForId = action.customer.id) }
            }
            CustomerListAction.DismissActionsSheet -> {
                _state.update { it.copy(actionsSheetForId = null) }
            }
            is CustomerListAction.OnViewCustomerFromSheet -> {
                navigateFromSheet { CustomerListEvent.NavigateToCustomerDetail(action.customerId) }
            }
            is CustomerListAction.OnEditCustomerFromRow -> {
                // Slice 6c: staff can't edit customers / create measurements or orders.
                // Defense-in-depth — the sheet hides these rows (canMutate = false).
                if (_state.value.isActiveStaff) return
                navigateFromSheet { CustomerListEvent.NavigateToEditCustomer(action.customerId) }
            }
            is CustomerListAction.OnAddMeasurementFromRow -> {
                if (_state.value.isActiveStaff) return
                navigateFromSheet { CustomerListEvent.NavigateToAddMeasurement(action.customerId) }
            }
            is CustomerListAction.OnViewMeasurementsFromRow -> viewMeasurementsFromSheet(action.customerId)
            is CustomerListAction.OnNewOrderFromRow -> {
                if (_state.value.isActiveStaff) return
                navigateFromSheet { CustomerListEvent.NavigateToOrderForm(action.customerId) }
            }
            is CustomerListAction.OnMessageWhatsApp -> messageOnWhatsApp(action.customer)
        }
    }

    // PTSP-32: dismiss the sheet, then (after the same 450ms UIKit-timing delay
    // navigateFromSheet uses — WhatsApp launch is openURL on iOS) emit the launch
    // event with a generic customer greeting. Guarded on a usable number.
    private fun messageOnWhatsApp(customer: Customer) {
        // Slice 6c: staff must never trigger a customer contact action.
        // Defense-in-depth alongside the sheet hiding the WhatsApp row.
        if (_state.value.isActiveStaff) return
        if (customer.phone.isBlank()) return
        _state.update { it.copy(actionsSheetForId = null) }
        viewModelScope.launch {
            delay(SHEET_DISMISS_DELAY_MS)
            val message = WhatsAppMessageBuilder.buildForCustomer(customer)
            _events.send(CustomerListEvent.LaunchWhatsApp(customer.phone, message))
        }
    }

    // Kill-switch / staff-revocation must bite mid-session, not on next restart
    // (StitchPad exploration, 2026-08-08): re-subscribe on every `workshopUid`
    // change instead of pinning the listener to a one-shot read. flatMapLatest
    // cancels the previous Firestore listener before starting the new one.
    private fun observeCustomers() {
        viewModelScope.launch {
            activeWorkshopProvider.flow
                .map { it.workshopUid.takeIf { uid -> uid.isNotBlank() } }
                .distinctUntilChanged()
                .flatMapLatest { workshopUid ->
                    // A -> B (both non-null, different uids): flatMapLatest cancels A's
                    // listener and starts B's, but B's first snapshot can take a beat to
                    // arrive — without this, `allCustomers`/state keep showing A's rows in
                    // the meantime, which is only masked today by the role-change nav
                    // redirect. Emit the same null sentinel the sign-out branch already
                    // uses so the reset logic isn't duplicated. Skipped on cold start
                    // (lastCustomersWorkshopUid == null) so first load doesn't flash empty.
                    val isWorkshopSwitch = lastCustomersWorkshopUid != null &&
                        workshopUid != null &&
                        workshopUid != lastCustomersWorkshopUid
                    lastCustomersWorkshopUid = workshopUid
                    val liveFlow: Flow<Result<List<Customer>, DataError.Network>?> =
                        if (workshopUid == null) flowOf(null) else customerRepository.observeCustomers(workshopUid)
                    if (isWorkshopSwitch) liveFlow.onStart { emit(null) } else liveFlow
                }
                .collect { result ->
                    if (result == null) {
                        allCustomers = emptyList()
                        allLockedCustomers = emptyList()
                        _state.update { state ->
                            state.copy(
                                customers = emptyList(),
                                lockedCustomers = emptyList(),
                                isLoading = false,
                            )
                        }
                        return@collect
                    }
                    when (result) {
                        is Result.Success -> {
                            val (active, locked) = result.data.partition {
                                it.slotState == CustomerSlotState.ACTIVE
                            }
                            allCustomers = active
                            allLockedCustomers = locked
                            _state.update { state ->
                                state.copy(
                                    customers = filterCustomers(active, state.searchQuery),
                                    lockedCustomers = filterCustomers(locked, state.searchQuery),
                                    isLoading = false
                                )
                            }
                        }
                        is Result.Error -> {
                            _state.update { state ->
                                state.copy(isLoading = false, errorMessage = result.error.toCustomerUiText())
                            }
                        }
                    }
                }
        }
    }

    @Suppress("ReturnCount")
    private fun deleteCustomer() {
        val current = _state.value
        val customer = current.customerToDelete ?: return

        // Race guard #1: we don't yet have a trustworthy active-order count for this customer
        // (orders flow hasn't emitted Success). Refuse with a snackbar so we don't orphan
        // non-delivered orders by deleting on a stale empty count. The two failure modes get
        // distinct copy: still-loading vs. load-failed actionable for the user.
        if (!current.ordersLoaded) {
            val message = if (current.ordersLoadFailed) {
                Res.string.customer_delete_orders_load_failed
            } else {
                Res.string.customer_delete_pending_orders_load
            }
            _state.update {
                it.copy(errorMessage = UiText.StringResourceText(message))
            }
            return
        }

        // Race guard #2: the screen gates the confirm button on customerToDeleteActiveOrderCount,
        // but the cache may have updated since the dialog opened (orders flow re-emitted with
        // newly-created orders). Morph the dialog into the "blocked" variant by writing the
        // current count back into state — the screen already renders that branch.
        val activeCount = activeOrderCountByCustomerId[customer.id] ?: 0
        if (activeCount > 0) {
            _state.update {
                it.copy(customerToDeleteActiveOrderCount = activeCount)
            }
            return
        }

        _state.update {
            it.copy(
                showDeleteDialog = false,
                customerToDelete = null,
                customerToDeleteActiveOrderCount = 0
            )
        }
        viewModelScope.launch {
            val userId = activeWorkshopProvider.workshopUidOrNull() ?: return@launch
            val result = customerRepository.deleteCustomer(userId, customer.id)
            if (result is Result.Error) {
                _state.update { it.copy(errorMessage = result.error.toCustomerUiText()) }
            }
        }
    }

    // Same re-subscription fix as observeCustomers — this listener backs the
    // delete-guard's active-order count, so pinning it to a one-shot uid would
    // let a post-kill-switch delete run against the WRONG tree's stale counts.
    private fun observeOrders() {
        viewModelScope.launch {
            activeWorkshopProvider.flow
                .map { it.workshopUid.takeIf { uid -> uid.isNotBlank() } }
                .distinctUntilChanged()
                .flatMapLatest { workshopUid ->
                    // Same workshop-switch reset as observeCustomers above, mirrored for
                    // the delete-guard's order-count cache.
                    val isWorkshopSwitch = lastOrdersWorkshopUid != null &&
                        workshopUid != null &&
                        workshopUid != lastOrdersWorkshopUid
                    lastOrdersWorkshopUid = workshopUid
                    val liveFlow: Flow<Result<List<Order>, DataError.Network>?> =
                        if (workshopUid == null) flowOf(null) else orderRepository.observeOrders(workshopUid)
                    if (isWorkshopSwitch) liveFlow.onStart { emit(null) } else liveFlow
                }
                .collect { result ->
                    if (result == null) {
                        // No addressable tree (signed out / workshop switch in flight): drop the
                        // cached counts and require a fresh Success under the new tree before the
                        // delete guard trusts them again — mirrors the Result.Error caution below.
                        activeOrderCountByCustomerId = emptyMap()
                        _state.update { it.copy(ordersLoaded = false) }
                        return@collect
                    }
                    when (result) {
                        is Result.Success -> {
                            activeOrderCountByCustomerId = result.data
                                .filter { it.status != OrderStatus.DELIVERED }
                                .groupingBy { it.customerId }
                                .eachCount()
                            _state.update { it.copy(ordersLoaded = true, ordersLoadFailed = false) }
                        }
                        is Result.Error -> {
                            // Don't flip ordersLoaded — `activeOrderCountByCustomerId` is still
                            // empty/stale, and FirebaseCustomerRepository.deleteCustomer is a single-
                            // doc delete with no cascade, so allowing deletion here would orphan any
                            // active orders on the customer. ordersLoadFailed surfaces a specific
                            // snackbar so the user understands why delete is blocked, distinct from
                            // the "still loading" first-emission case.
                            _state.update { it.copy(ordersLoadFailed = true) }
                        }
                    }
                }
        }
    }

    private fun filterCustomers(
        customers: List<Customer>,
        query: String,
    ): List<Customer> {
        if (query.isBlank()) return customers
        val q = query.lowercase().trim()
        return customers.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
    }

    /**
     * Common path for the four "from row" nav actions: close the sheet, wait
     * ~450ms for the Compose dismissal to fully settle (per
     * `feedback_ios_modal_bottom_sheet_timing` — UIKit-backed nav after a
     * Compose sheet dismiss can silently no-op on iOS), then emit the nav
     * event.
     */
    private fun navigateFromSheet(event: () -> CustomerListEvent) {
        _state.update { it.copy(actionsSheetForId = null) }
        viewModelScope.launch {
            delay(SHEET_DISMISS_DELAY_MS)
            _events.send(event())
        }
    }

    /**
     * "View measurements" from the actions sheet: resolve during the dismiss
     * animation (exactly one measurement -> its detail; confirmed zero -> the
     * detail empty state; several or unknown -> customer detail), then honor
     * the same 450ms dismiss-delay contract as [navigateFromSheet] before
     * emitting.
     */
    private fun viewMeasurementsFromSheet(customerId: String) {
        _state.update { it.copy(actionsSheetForId = null) }
        viewModelScope.launch {
            val destination = measurementEntryResolver.resolve(customerId)
            delay(SHEET_DISMISS_DELAY_MS)
            val event = when (destination) {
                is MeasurementEntryDestination.Detail ->
                    CustomerListEvent.NavigateToMeasurementDetail(destination.customerId, destination.measurementId)
                is MeasurementEntryDestination.CustomerDetail ->
                    CustomerListEvent.NavigateToCustomerDetail(destination.customerId)
            }
            _events.send(event)
        }
    }
}
