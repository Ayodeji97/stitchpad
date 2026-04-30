package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.feature.dashboard.domain.model.Buckets
import com.danzucker.stitchpad.feature.dashboard.domain.model.DashboardOrderRow
import com.danzucker.stitchpad.feature.dashboard.presentation.model.DashboardUiState
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
import com.danzucker.stitchpad.feature.dashboard.presentation.model.ReconnectCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FocusResolverTest {

    private val emptyBuckets = Buckets(
        overdue = emptyList(),
        dueToday = emptyList(),
        ready = emptyList(),
        outstandingAmount = 0.0,
        outstandingOrderCount = 0,
        pipelineInProgress = emptyList(),
        pipelineInProgressTotal = 0,
        pipelinePending = emptyList(),
        pipelinePendingTotal = 0
    )

    private fun row(id: String = "o", name: String = "Customer"): DashboardOrderRow =
        DashboardOrderRow(orderId = id, customerName = name, primaryLabel = "Agbada")

    private fun customer(name: String = "Test", id: String = "c1"): Customer =
        Customer(id = id, userId = "u", name = name, phone = "08011112222")

    private fun order(): Order = Order(
        id = "o1",
        userId = "u",
        customerId = "c1",
        customerName = "Test",
        items = listOf(OrderItem(id = "i1", garmentType = GarmentType.AGBADA, description = "", price = 0.0)),
        status = OrderStatus.PENDING,
        priority = OrderPriority.NORMAL,
        statusHistory = emptyList(),
        totalPrice = 0.0,
        depositPaid = 0.0,
        balanceRemaining = 0.0,
        deadline = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun nba(type: NextBestActionType, customer: String = "Topcat"): NextBestAction =
        NextBestAction(
            type = type,
            orderId = "o-$type",
            customerId = "c1",
            customerName = customer,
            customerPhone = "08011112222",
            garmentLabel = "Agbada",
            balanceAmount = 0.0,
            daysCount = 0
        )

    @Test
    fun emptyOrdersAndCustomersResolvesToBrandNew() {
        val state = FocusResolver.resolveUiState(
            buckets = emptyBuckets,
            nextBestActions = emptyList(),
            orders = emptyList(),
            customers = emptyList()
        )
        assertEquals(DashboardUiState.BrandNew, state)
    }

    @Test
    fun customersWithoutOrdersResolvesToFirstCustomer() {
        val state = FocusResolver.resolveUiState(
            buckets = emptyBuckets,
            nextBestActions = emptyList(),
            orders = emptyList(),
            customers = listOf(customer())
        )
        assertEquals(DashboardUiState.FirstCustomer, state)
    }

    @Test
    fun overdueResolvesToBusyDay() {
        val state = FocusResolver.resolveUiState(
            buckets = emptyBuckets.copy(overdue = listOf(row())),
            nextBestActions = emptyList(),
            orders = listOf(order()),
            customers = listOf(customer())
        )
        assertEquals(DashboardUiState.BusyDay, state)
    }

    @Test
    fun dueTodayResolvesToBusyDay() {
        val state = FocusResolver.resolveUiState(
            buckets = emptyBuckets.copy(dueToday = listOf(row())),
            nextBestActions = emptyList(),
            orders = listOf(order()),
            customers = listOf(customer())
        )
        assertEquals(DashboardUiState.BusyDay, state)
    }

    @Test
    fun readyOnlyResolvesToReadyForPickupNotBusyDay() {
        val state = FocusResolver.resolveUiState(
            buckets = emptyBuckets.copy(ready = listOf(row())),
            nextBestActions = emptyList(),
            orders = listOf(order()),
            customers = listOf(customer())
        )
        assertEquals(DashboardUiState.ReadyForPickup, state)
    }

    @Test
    fun nextBestActionsWithoutTriageResolvesToNbaActive() {
        val state = FocusResolver.resolveUiState(
            buckets = emptyBuckets,
            nextBestActions = listOf(nba(NextBestActionType.CollectDeposit)),
            orders = listOf(order()),
            customers = listOf(customer())
        )
        assertEquals(DashboardUiState.NbaActive, state)
    }

    @Test
    fun pipelineWithoutTriageOrNbaResolvesToPipelineSteady() {
        val state = FocusResolver.resolveUiState(
            buckets = emptyBuckets.copy(pipelineInProgressTotal = 2),
            nextBestActions = emptyList(),
            orders = listOf(order()),
            customers = listOf(customer())
        )
        assertEquals(DashboardUiState.PipelineSteady, state)
    }

    @Test
    fun nothingTriagedOrPipelinedResolvesToQuietDay() {
        val state = FocusResolver.resolveUiState(
            buckets = emptyBuckets,
            nextBestActions = emptyList(),
            orders = listOf(order()),
            customers = listOf(customer())
        )
        assertEquals(DashboardUiState.QuietDay, state)
    }

    @Test
    fun firstCustomerFocusUsesFirstOrderVariant() {
        val focus = FocusResolver.resolveFocus(
            uiState = DashboardUiState.FirstCustomer,
            buckets = emptyBuckets,
            nextBestActions = emptyList(),
            customers = listOf(customer(name = "Bola")),
            reconnect = emptyList()
        )
        assertEquals(FocusVariant.FirstOrder, focus.variant)
        assertNotNull(focus.ctaLabel)
        assertNull(focus.supporting)
    }

    @Test
    fun busyDayFocusUsesFocusVariant() {
        val focus = FocusResolver.resolveFocus(
            uiState = DashboardUiState.BusyDay,
            buckets = emptyBuckets.copy(overdue = listOf(row(name = "Tunde"))),
            nextBestActions = emptyList(),
            customers = listOf(customer()),
            reconnect = emptyList()
        )
        assertEquals(FocusVariant.Focus, focus.variant)
        assertNotNull(focus.supporting)
        assertNotNull(focus.ctaLabel)
    }

    @Test
    fun readyForPickupFocusUsesPickupVariant() {
        val focus = FocusResolver.resolveFocus(
            uiState = DashboardUiState.ReadyForPickup,
            buckets = emptyBuckets.copy(ready = listOf(row(name = "Aisha"))),
            nextBestActions = emptyList(),
            customers = listOf(customer()),
            reconnect = emptyList()
        )
        assertEquals(FocusVariant.Pickup, focus.variant)
    }

    @Test
    fun nbaActiveFocusUsesEarnVariant() {
        val focus = FocusResolver.resolveFocus(
            uiState = DashboardUiState.NbaActive,
            buckets = emptyBuckets,
            nextBestActions = listOf(nba(NextBestActionType.CollectDeposit, customer = "Femi")),
            customers = listOf(customer()),
            reconnect = emptyList()
        )
        assertEquals(FocusVariant.Earn, focus.variant)
    }

    @Test
    fun pipelineSteadyFocusUsesSteadyVariant() {
        val focus = FocusResolver.resolveFocus(
            uiState = DashboardUiState.PipelineSteady,
            buckets = emptyBuckets.copy(pipelineInProgressTotal = 3, pipelinePendingTotal = 1),
            nextBestActions = emptyList(),
            customers = listOf(customer()),
            reconnect = emptyList()
        )
        assertEquals(FocusVariant.Steady, focus.variant)
    }

    @Test
    fun quietDayFocusUsesQuietVariantWithoutReconnect() {
        val focus = FocusResolver.resolveFocus(
            uiState = DashboardUiState.QuietDay,
            buckets = emptyBuckets,
            nextBestActions = emptyList(),
            customers = listOf(customer()),
            reconnect = emptyList()
        )
        assertEquals(FocusVariant.Quiet, focus.variant)
        assertNotNull(focus.ctaLabel)
    }

    @Test
    fun quietDayFocusReferencesTopReconnectCandidateWhenPresent() {
        val focus = FocusResolver.resolveFocus(
            uiState = DashboardUiState.QuietDay,
            buckets = emptyBuckets,
            nextBestActions = emptyList(),
            customers = listOf(customer()),
            reconnect = listOf(
                ReconnectCandidate(
                    customerId = "c1",
                    customerName = "Old Customer",
                    customerPhone = "08011112222",
                    daysSinceLastInteraction = 30,
                    hasOrderHistory = true
                )
            )
        )
        assertEquals(FocusVariant.Quiet, focus.variant)
        assertNotNull(focus.ctaLabel)
    }

    @Test
    fun loadingAndBrandNewReturnPlaceholderQuiet() {
        val loading = FocusResolver.resolveFocus(
            uiState = DashboardUiState.Loading,
            buckets = emptyBuckets,
            nextBestActions = emptyList(),
            customers = emptyList(),
            reconnect = emptyList()
        )
        val brandNew = FocusResolver.resolveFocus(
            uiState = DashboardUiState.BrandNew,
            buckets = emptyBuckets,
            nextBestActions = emptyList(),
            customers = emptyList(),
            reconnect = emptyList()
        )
        assertEquals(FocusVariant.Quiet, loading.variant)
        assertNull(loading.ctaLabel)
        assertEquals(FocusVariant.Quiet, brandNew.variant)
        assertNull(brandNew.ctaLabel)
    }
}
