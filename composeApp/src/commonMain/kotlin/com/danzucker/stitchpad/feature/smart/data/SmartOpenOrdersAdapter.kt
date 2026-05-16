package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
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

    private fun Order.toSummary(): OrderSummary = OrderSummary(
        id = id,
        customerId = customerId,
        garmentLabel = items.firstOrNull()?.description?.takeIf { it.isNotBlank() }
            ?: items.firstOrNull()?.garmentType?.name?.lowercase()
                ?.replaceFirstChar { it.titlecase() }
            ?: "Garment",
        balanceFormatted = formatNaira(balanceRemaining),
        deadlineFormatted = deadline?.let { formatDeadline(it) } ?: "No deadline set",
    )

    private fun formatNaira(amount: Double): String {
        val whole = amount.toLong().toString()
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        return "₦$grouped"
    }

    private fun formatDeadline(epochMillis: Long): String {
        val dt = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val day = dt.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }.take(3)
        val month = dt.month.name.lowercase().replaceFirstChar { it.titlecase() }.take(3)
        return "$day, $month ${dt.dayOfMonth}"
    }
}
