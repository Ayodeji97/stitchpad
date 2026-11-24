package com.danzucker.stitchpad.feature.notebookimport.data

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.PageBox
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotebookImportMapperTest {
    private val full = ReviewCard(
        id = "p001_ab_0", jobId = "job1", pageId = "p001_ab", name = "Bisi", phone = "0803",
        gender = CustomerGender.MALE, unit = MeasurementUnit.CM, dateWritten = LocalDate(2026, 3, 1),
        values = listOf(CardValue("Bust", "36", "bust", true), CardValue("Odd", "5", null, false)),
        box = PageBox(0.1f, 0.2f, 0.3f, 0.4f), needsReview = true,
        status = CardStatus.CHECKED, matchedCustomerId = "c9", savedCustomerId = null,
    )

    private fun dtoFrom(card: ReviewCard, fields: Map<String, Any?>) = NotebookCardDto(
        name = fields["name"] as String,
        phone = fields["phone"] as String?,
        gender = fields["gender"] as String,
        unit = fields["unit"] as String,
        dateWritten = fields["dateWritten"] as String?,
        values = (fields["values"] as List<*>).map {
            @Suppress("UNCHECKED_CAST")
            val m = it as Map<String, Any?>
            CardValueDto(m["label"] as String, m["value"] as String, m["fieldKey"] as String?, m["flagged"] as Boolean)
        },
        box = card.box?.let { PageBoxDto(it.x, it.y, it.w, it.h) },
        needsReview = card.needsReview,
        pageId = card.pageId,
        status = fields["status"] as String,
        matchedCustomerId = fields["matchedCustomerId"] as String?,
        savedCustomerId = fields["savedCustomerId"] as String?,
    )

    @Test
    fun card_roundTrip() {
        val back = dtoFrom(full, full.toUpdateFields()).toReviewCard(full.id, full.jobId)
        assertEquals(full, back)
    }

    @Test
    fun card_roundTrip_nullBoxAndNullGender() {
        val card = full.copy(box = null, gender = null, unit = null, dateWritten = null, phone = null)
        val fields = card.toUpdateFields()
        assertEquals("unknown", fields["gender"])
        val back = dtoFrom(card, fields).toReviewCard(card.id, card.jobId)
        assertEquals(card, back)
        assertNull(back.box)
        assertNull(back.gender)
    }

    @Test
    fun updateFields_onlyRuleAllowListedKeys() {
        val allowed = setOf(
            "name", "phone", "gender", "unit", "dateWritten", "values",
            "status", "matchedCustomerId", "savedCustomerId", "updatedAt",
        )
        assertTrue(allowed.containsAll(full.toUpdateFields().keys))
    }

    @Test
    fun cardStatus_wireValuesMatchRules() {
        val wire: List<String> = CardStatus.entries.map { it.toWire() }
        assertEquals(listOf("new", "checked", "saved", "skipped"), wire)
    }

    @Test
    fun serverCard_unknownEnumsAndBadDateDegradeSafely() {
        val card = NotebookCardDto(gender = "weird", unit = "weird", dateWritten = "not-a-date", status = "weird")
            .toReviewCard("c", "j")
        assertNull(card.gender)
        assertNull(card.unit)
        assertNull(card.dateWritten)
        assertEquals(CardStatus.NEW, card.status)
    }

    @Test
    fun page_statusMapping_andNullThumb() {
        assertEquals(PageStatus.UPLOADING, NotebookPageDto().toImportPage("p").status)
        assertEquals(PageStatus.PROCESSING, NotebookPageDto("processing").toImportPage("p").status)
        assertEquals(3, NotebookPageDto("done", 3).toImportPage("p").personCount)
        val failed = NotebookPageDto("failed", 0, "read_failed").toImportPage("p")
        assertEquals(PageStatus.FAILED, failed.status)
        assertEquals("read_failed", failed.error)
        assertNull(failed.localThumb)
    }

    @Test
    fun pageId_format() {
        val id = pageIdFor(7, byteArrayOf(0x0a, 0xFF.toByte(), 0x00, 0x1c))
        assertEquals("p007_0aff001c", id)
        assertFalse(id.contains(' '))
    }
}
