package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.presentation.localizedMonthNameAsync
import com.danzucker.stitchpad.core.presentation.localizedWeekdayShortAsync
import com.danzucker.stitchpad.core.sharing.formatMoney
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.feature.smart.presentation.draft.OpenOrdersProvider
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class SmartOpenOrdersAdapter(
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
) : OpenOrdersProvider {

    override suspend fun openOrdersFor(customerId: String): List<OrderSummary> {
        val userId = authRepository.getCurrentUser()?.id ?: return emptyList()
        return when (val result = orderRepository.observeOrders(userId).first()) {
            is Result.Success ->
                result.data
                    .filter {
                        it.customerId == customerId &&
                            it.archivedAt == null &&
                            it.status != OrderStatus.DELIVERED
                    }
                    .map { it.toSummary() }
            is Result.Error -> emptyList()
        }
    }

    private suspend fun Order.toSummary(): OrderSummary = OrderSummary(
        id = id,
        customerId = customerId,
        garmentLabel = items.firstOrNull()?.description?.takeIf { it.isNotBlank() }
            ?: items.firstOrNull()?.garmentType?.name?.lowercase()
                ?.replaceFirstChar { it.titlecase() }
            ?: "Garment",
        balanceFormatted = formatMoney(balanceRemaining, currency),
        deadlineFormatted = deadline?.let { formatDeadline(it) } ?: "No deadline set",
    )

    private suspend fun formatDeadline(epochMillis: Long): String {
        val dt = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        // Resource-backed so a French device reads "Ven, nov. 6" rather than "Fri, Nov 6".
        val day = localizedWeekdayShortAsync(dt.dayOfWeek)
        val month = localizedMonthNameAsync(dt.monthNumber, short = true)
        return "$day, $month ${dt.dayOfMonth}"
    }
}
