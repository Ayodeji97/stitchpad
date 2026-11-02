package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.NotificationDto
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.domain.model.NotificationType
import com.danzucker.stitchpad.core.sharing.formatMoney
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationMapperTest {

    @Test
    fun legacyAndUnknownInboxCurrenciesDefaultToNgn() {
        listOf(null, "", "ZZZ").forEach { code ->
            val notification = NotificationDto(amount = 1200.5, currency = code).toNotification("legacy")
            assertEquals(Currency.NGN, notification.currency)
            assertEquals("₦1,200.50", formatMoney(notification.amount!!, notification.currency))
        }
    }

    @Test
    fun stampedCurrencySurvivesMappingAndPreservesMinorUnits() {
        val notification = NotificationDto(amount = 1200.5, currency = "ghs").toNotification("ghana-order")
        assertEquals(Currency.GHS, notification.currency)
        assertEquals("GH₵1,200.50", formatMoney(notification.amount!!, notification.currency))
    }

    @Test
    fun francNotificationUsesZeroDecimalDisplayWithoutChangingStoredAmount() {
        val notification = NotificationDto(amount = 1200.5, currency = "XOF").toNotification("senegal-order")
        assertEquals(1200.5, notification.amount)
        assertEquals("F CFA 1,201", formatMoney(notification.amount!!, notification.currency))
    }


    @Test
    fun toNotification_knownType_mapsToCorrectNotificationType() {
        val dto = NotificationDto(type = "OVERDUE")
        val notification = dto.toNotification("doc-1")
        assertEquals(NotificationType.OVERDUE, notification.type)
    }

    @Test
    fun toNotification_unknownType_mapsToUnknown() {
        val dto = NotificationDto(type = "SOME_FUTURE_TYPE")
        val notification = dto.toNotification("doc-2")
        assertEquals(NotificationType.UNKNOWN, notification.type)
    }

    @Test
    fun toNotification_usesDocIdNotDtoId() {
        // The DTO may carry a stale or blank `id` field — the Firestore document ID must win.
        val dto = NotificationDto(id = "dto-id-should-be-ignored")
        val notification = dto.toNotification("firestore-doc-id")
        assertEquals("firestore-doc-id", notification.id)
    }
}
