package com.danzucker.stitchpad.core.util

import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.whatsapp_message_customer_greeting
import stitchpad.composeapp.generated.resources.whatsapp_message_delivered
import stitchpad.composeapp.generated.resources.whatsapp_message_in_progress
import stitchpad.composeapp.generated.resources.whatsapp_message_pending
import stitchpad.composeapp.generated.resources.whatsapp_message_ready

object WhatsAppMessageBuilder {
    suspend fun buildForOrder(order: Order, customer: Customer): String {
        val resource = when (order.status) {
            OrderStatus.PENDING -> Res.string.whatsapp_message_pending
            OrderStatus.IN_PROGRESS -> Res.string.whatsapp_message_in_progress
            OrderStatus.READY -> Res.string.whatsapp_message_ready
            OrderStatus.DELIVERED -> Res.string.whatsapp_message_delivered
        }
        return getString(resource, customer.firstName())
    }

    // Customer-level greeting with no order context (PTSP-32/33) — used when the
    // tailor messages a customer from the customer card or detail screen rather
    // than from a specific order.
    suspend fun buildForCustomer(customer: Customer): String =
        getString(Res.string.whatsapp_message_customer_greeting, customer.firstName())

    private fun Customer.firstName(): String =
        name.substringBefore(' ').ifBlank { name }
}
