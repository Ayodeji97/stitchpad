package com.danzucker.stitchpad.core.data.mapper

import com.danzucker.stitchpad.core.data.dto.NotificationDto
import com.danzucker.stitchpad.core.domain.model.NotificationType
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationMapperTest {

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
