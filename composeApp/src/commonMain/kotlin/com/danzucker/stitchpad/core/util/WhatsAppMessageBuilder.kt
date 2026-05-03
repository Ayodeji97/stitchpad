package com.danzucker.stitchpad.core.util

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.whatsapp_message_delivered
import stitchpad.composeapp.generated.resources.whatsapp_message_in_progress
import stitchpad.composeapp.generated.resources.whatsapp_message_pending
import stitchpad.composeapp.generated.resources.whatsapp_message_ready

object WhatsAppMessageBuilder {
    suspend fun buildForOrder(order: Order, customer: Customer): String {
        val firstName = customer.name.substringBefore(' ').ifBlank { customer.name }
        val resource = when (order.status) {
            OrderStatus.PENDING -> Res.string.whatsapp_message_pending
            OrderStatus.IN_PROGRESS -> Res.string.whatsapp_message_in_progress
            OrderStatus.READY -> Res.string.whatsapp_message_ready
            OrderStatus.DELIVERED -> Res.string.whatsapp_message_delivered
        }
        return getString(resource, firstName)
    }
}
