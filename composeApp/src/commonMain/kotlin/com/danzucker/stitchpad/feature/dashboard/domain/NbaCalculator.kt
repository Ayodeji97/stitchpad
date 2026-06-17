package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.displayGarmentName
import com.danzucker.stitchpad.feature.dashboard.domain.internal.simpleLabel
import com.danzucker.stitchpad.feature.dashboard.domain.internal.toLocalDate
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestAction
import com.danzucker.stitchpad.feature.dashboard.presentation.model.NextBestActionType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

private const val NBA_LIMIT = 5
private const val FINISH_STALE_DAYS = 7
private const val DELIVER_STALE_DAYS = 3
private const val START_SOON_DAYS = 7

/**
 * Derives the dashboard's "next best action" prompts — revenue-driving nudges
 * the tailor should take right now, ranked by [NextBestActionType] priority and
 * (for StartSoon ties) deadline urgency, then by outstanding balance.
 *
 * Skips DELIVERED orders, customers without a phone, and customers we don't
 * know about (orphan orders). Final list is capped at [NBA_LIMIT] entries.
 *
 * Each branch in [compute] maps a single business rule:
 *   - CollectOverdue   — past-deadline order with balance still owed
 *   - CollectOnReady   — finished and waiting for pickup with balance owed
 *   - FinishStale      — IN_PROGRESS untouched for [FINISH_STALE_DAYS]
 *   - DeliverStale     — READY untouched for [DELIVER_STALE_DAYS]
 *   - CollectDeposit   — PENDING with no deposit booked yet
 *   - StartSoon        — PENDING due within [START_SOON_DAYS]
 */
object NbaCalculator {

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun compute(
        orders: List<Order>,
        customersById: Map<String, Customer>,
        today: LocalDate,
        timeZone: TimeZone
    ): List<NextBestAction> {
        val candidates = mutableListOf<NextBestAction>()

        orders.forEach { order ->
            if (order.status == OrderStatus.DELIVERED) return@forEach
            val customer = customersById[order.customerId] ?: return@forEach
            if (customer.phone.isBlank()) return@forEach
            val garment = order.items.firstOrNull()?.displayGarmentName { it.simpleLabel() }.orEmpty()
            val deadlineDate = order.deadline?.toLocalDate(timeZone)
            val daysUntilDeadline = deadlineDate?.let { today.daysUntil(it) }
            // Computed once per order — only READY / IN_PROGRESS branches consume it. Avoids the
            // earlier per-branch double-scan of statusHistory inside the when expression.
            val transitionDays = when (order.status) {
                OrderStatus.READY,
                OrderStatus.IN_PROGRESS ->
                    daysSinceLastTransitionTo(order, order.status, today, timeZone)
                else -> 0
            }

            val action = when {
                deadlineDate != null && deadlineDate < today && order.balanceRemaining > 0.0 ->
                    buildAction(
                        type = NextBestActionType.CollectOverdue,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.balanceRemaining,
                        days = today.daysUntil(deadlineDate).let { -it }
                    )
                order.status == OrderStatus.READY && order.balanceRemaining > 0.0 ->
                    buildAction(
                        type = NextBestActionType.CollectOnReady,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.balanceRemaining,
                        days = transitionDays
                    )
                order.status == OrderStatus.IN_PROGRESS && transitionDays > FINISH_STALE_DAYS ->
                    buildAction(
                        type = NextBestActionType.FinishStale,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.balanceRemaining,
                        days = transitionDays
                    )
                order.status == OrderStatus.READY && transitionDays > DELIVER_STALE_DAYS ->
                    buildAction(
                        type = NextBestActionType.DeliverStale,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.balanceRemaining,
                        days = transitionDays
                    )
                order.status == OrderStatus.PENDING &&
                    order.depositPaid == 0.0 &&
                    order.payableTotal > 0.0 ->
                    buildAction(
                        type = NextBestActionType.CollectDeposit,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = order.payableTotal,
                        days = daysSinceCreation(order, today, timeZone)
                    )
                order.status == OrderStatus.PENDING &&
                    daysUntilDeadline != null && daysUntilDeadline in 0..START_SOON_DAYS ->
                    buildAction(
                        type = NextBestActionType.StartSoon,
                        order = order,
                        customer = customer,
                        garment = garment,
                        balance = 0.0,
                        days = daysUntilDeadline
                    )
                else -> null
            }
            if (action != null) candidates += action
        }

        return candidates
            .sortedWith(
                compareBy<NextBestAction> { it.type.ordinal }
                    .thenBy { if (it.type == NextBestActionType.StartSoon) it.daysCount else 0 }
                    .thenByDescending { it.balanceAmount }
            )
            .take(NBA_LIMIT)
    }

    private fun buildAction(
        type: NextBestActionType,
        order: Order,
        customer: Customer,
        garment: String,
        balance: Double,
        days: Int
    ): NextBestAction = NextBestAction(
        type = type,
        orderId = order.id,
        customerId = customer.id,
        customerName = order.customerName.ifBlank { customer.name },
        customerPhone = customer.phone,
        garmentLabel = garment,
        balanceAmount = balance,
        daysCount = days
    )

    private fun daysSinceLastTransitionTo(
        order: Order,
        target: OrderStatus,
        today: LocalDate,
        timeZone: TimeZone
    ): Int {
        val lastTransition = order.statusHistory.lastOrNull { it.status == target }
        val anchorMillis = lastTransition?.changedAt ?: order.updatedAt
        if (anchorMillis == 0L) return 0
        return anchorMillis.toLocalDate(timeZone).daysUntil(today)
    }

    private fun daysSinceCreation(order: Order, today: LocalDate, timeZone: TimeZone): Int {
        if (order.createdAt == 0L) return 0
        return order.createdAt.toLocalDate(timeZone).daysUntil(today)
    }
}
