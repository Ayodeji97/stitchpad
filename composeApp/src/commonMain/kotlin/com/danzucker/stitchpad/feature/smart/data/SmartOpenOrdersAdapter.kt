package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.GarmentType
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderItem
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.repository.OrderRepository
import com.danzucker.stitchpad.core.presentation.localizedMonthNameAsync
import com.danzucker.stitchpad.core.presentation.localizedWeekdayShortAsync
import com.danzucker.stitchpad.core.sharing.formatMoney
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.order.presentation.garmentDisplayNameAsync
import com.danzucker.stitchpad.feature.smart.domain.model.OrderSummary
import com.danzucker.stitchpad.feature.smart.presentation.draft.OpenOrdersProvider
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.smart_order_garment_fallback
import stitchpad.composeapp.generated.resources.smart_order_no_deadline

/**
 * Pre-localized copy for [OrderSummary] rows. Defaults are the English strings
 * the picker has always shown (and an empty garment map, which falls back to the
 * enum-derived English name) so tests stay resource-free.
 */
internal data class OpenOrderSummaryLabels(
    val noDeadline: String = "No deadline set",
    val garmentFallback: String = "Garment",
    val garmentNames: Map<GarmentType, String> = emptyMap(),
)

internal class SmartOpenOrdersAdapter(
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
    private val resolveLabels: suspend () -> OpenOrderSummaryLabels = ::resolveOpenOrderSummaryLabels,
) : OpenOrdersProvider {

    override suspend fun openOrdersFor(customerId: String): List<OrderSummary> {
        val userId = authRepository.getCurrentUser()?.id ?: return emptyList()
        return when (val result = orderRepository.observeOrders(userId).first()) {
            is Result.Success -> {
                val open = result.data.filter {
                    it.customerId == customerId &&
                        it.archivedAt == null &&
                        it.status != OrderStatus.DELIVERED
                }
                if (open.isEmpty()) {
                    emptyList()
                } else {
                    val labels = resolveLabels()
                    open.map { it.toSummary(labels) }
                }
            }
            is Result.Error -> emptyList()
        }
    }

    private suspend fun Order.toSummary(labels: OpenOrderSummaryLabels): OrderSummary = OrderSummary(
        id = id,
        customerId = customerId,
        garmentLabel = items.firstOrNull()?.description?.takeIf { it.isNotBlank() }
            ?: items.firstOrNull()?.garmentLabel(labels)
            ?: labels.garmentFallback,
        balanceFormatted = formatMoney(balanceRemaining, currency),
        deadlineFormatted = deadline?.let { formatDeadline(it) } ?: labels.noDeadline,
    )

    private fun OrderItem.garmentLabel(labels: OpenOrderSummaryLabels): String {
        val custom = customGarmentName
        if (garmentType == GarmentType.OTHER && !custom.isNullOrBlank()) return custom
        return labels.garmentNames[garmentType]
            ?: garmentType.name.lowercase().replaceFirstChar { it.titlecase() }
    }

    private suspend fun formatDeadline(epochMillis: Long): String {
        val dt = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        // Resource-backed so a French device reads "Ven, nov. 6" rather than "Fri, Nov 6".
        val day = localizedWeekdayShortAsync(dt.dayOfWeek)
        val month = localizedMonthNameAsync(dt.monthNumber, short = true)
        return "$day, $month ${dt.dayOfMonth}"
    }
}

private suspend fun resolveOpenOrderSummaryLabels(): OpenOrderSummaryLabels = OpenOrderSummaryLabels(
    noDeadline = getString(Res.string.smart_order_no_deadline),
    garmentFallback = getString(Res.string.smart_order_garment_fallback),
    garmentNames = GarmentType.entries.associateWith { garmentDisplayNameAsync(it) },
)
