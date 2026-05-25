package com.danzucker.stitchpad.feature.smart.data.mapper

import com.danzucker.stitchpad.feature.smart.data.dto.DraftMessageResponseDto
import com.danzucker.stitchpad.feature.smart.domain.model.DraftIntent
import com.danzucker.stitchpad.core.smartinfra.domain.language.DraftLanguage
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftMessageMappersTest {

    @Test
    fun `request to DTO maps wire names and omits empty notes`() {
        val req = DraftMessageRequest(
            customerId = "cust-1",
            orderId = "order-1",
            intent = DraftIntent.BalanceReminder,
            language = DraftLanguage.English,
            customNotes = null,
        )
        val dto = req.toDto()
        assertEquals("balance_reminder", dto.intentType)
        assertEquals("cust-1", dto.customerId)
        assertEquals("order-1", dto.orderId)
        assertEquals("en", dto.language)
        assertNull(dto.customNotes)
    }

    @Test
    fun `request to DTO maps Pidgin and custom notes`() {
        val req = DraftMessageRequest(
            customerId = "c",
            orderId = "o",
            intent = DraftIntent.CustomNote,
            language = DraftLanguage.Pidgin,
            customNotes = "Apologise for delay",
        )
        val dto = req.toDto()
        assertEquals("custom_note", dto.intentType)
        assertEquals("pcm", dto.language)
        assertEquals("Apologise for delay", dto.customNotes)
    }

    @Test
    fun `request to DTO trims and nulls blank custom notes`() {
        val req = DraftMessageRequest(
            customerId = "c",
            orderId = "o",
            intent = DraftIntent.FollowUp,
            language = DraftLanguage.English,
            customNotes = "   ",
        )
        val dto = req.toDto()
        assertNull(dto.customNotes)
    }

    @Test
    fun `response DTO to domain copies fields including null quota for premium`() {
        val dto = DraftMessageResponseDto(
            draftText = "Hi Folake!",
            remainingFreeQuota = null,
        )
        val result = dto.toDomain()
        assertEquals("Hi Folake!", result.draftText)
        assertNull(result.remainingFreeQuota)
    }

    @Test
    fun `response DTO to domain preserves remainingFreeQuota for free tier`() {
        val dto = DraftMessageResponseDto(draftText = "x", remainingFreeQuota = 4)
        assertEquals(4, dto.toDomain().remainingFreeQuota)
    }
}
