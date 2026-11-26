package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MergeCardsTest {

    @Suppress("LongParameterList")
    private fun card(
        id: String,
        phone: String? = null,
        gender: CustomerGender? = CustomerGender.FEMALE,
        unit: MeasurementUnit? = MeasurementUnit.INCHES,
        date: LocalDate? = null,
        values: List<CardValue> = emptyList(),
        status: CardStatus = CardStatus.NEW,
        matched: String? = null,
    ) = ReviewCard(
        id = id, jobId = "j", pageId = "p_$id", name = "Kemi", phone = phone, gender = gender, unit = unit,
        dateWritten = date, values = values, box = null, needsReview = false, status = status,
        matchedCustomerId = matched, savedCustomerId = null,
    )

    private val labels = MergeNoteLabels(date = "Date", phone = "Phone")

    private fun v(label: String, value: String, key: String?, flagged: Boolean = false) =
        CardValue(label, value, key, flagged)

    @Test
    fun appendsNewValues_dropsExactDuplicates_flagsConflicts() {
        val merged = mergeCards(
            card("a", values = listOf(v("Bust", "38", "bust"), v("Hip", "44", "hip"))),
            card("b", values = listOf(v("Bust", "38", "bust"), v("Hip", "45", "hip"), v("Rd arm", "13", null))),
            labels = labels,
        )
        assertEquals(
            listOf(v("Bust", "38", "bust"), v("Hip", "44", "hip", true), v("Hip", "45", "hip", true), v("Rd arm", "13", null)),
            merged.values,
        )
        assertEquals("a", merged.id)
        assertEquals(CardStatus.NEW, merged.status)
    }

    @Test
    fun fillsMissingDetailsFromTheOther() {
        val merged = mergeCards(
            card("a", gender = null, unit = null, matched = null),
            card("b", phone = "0803", gender = CustomerGender.MALE, unit = MeasurementUnit.CM, matched = "cust"),
            labels = labels,
        )
        assertEquals("0803", merged.phone)
        assertEquals(CustomerGender.MALE, merged.gender)
        assertEquals(MeasurementUnit.CM, merged.unit)
        assertEquals("cust", merged.matchedCustomerId)
    }

    @Test
    fun conflictingGender_isClearedSoTheTailorMustChoose() {
        val merged = mergeCards(card("a"), card("b", gender = CustomerGender.MALE), labels = labels)
        assertNull(merged.gender)
    }

    @Test
    fun conflictingUnit_flagsTheOthersValues() {
        val merged = mergeCards(
            card("a", values = listOf(v("Bust", "38", "bust"))),
            card("b", unit = MeasurementUnit.CM, values = listOf(v("Hip", "112", "hip"))),
            labels = labels,
        )
        assertEquals(listOf(v("Bust", "38", "bust"), v("Hip", "112", "hip", true)), merged.values)
        assertEquals(MeasurementUnit.INCHES, merged.unit)
    }

    @Test
    fun conflictingDate_keepsTheOtherDateAsAFlaggedNote() {
        val merged = mergeCards(
            card("a", date = LocalDate(2026, 1, 2)),
            card("b", date = LocalDate(2026, 3, 4)),
            labels = labels,
        )
        assertEquals(LocalDate(2026, 1, 2), merged.dateWritten)
        assertEquals(listOf(v("Date", "2026-03-04", null, true)), merged.values)
    }

    @Test
    fun differingPhone_isKeptAsAFlaggedNote() {
        val merged = mergeCards(card("a", phone = "0803 111 2222"), card("b", phone = "0812 333 4444"), labels = labels)
        assertEquals("0803 111 2222", merged.phone)
        assertEquals(listOf(v("Phone", "0812 333 4444", null, true)), merged.values)
        assertEquals(CardStatus.NEW, merged.status)
    }

    @Test
    fun samePhoneWrittenDifferently_isNotAConflict() {
        val merged = mergeCards(card("a", phone = "0803 111 2222"), card("b", phone = "08031112222"), labels = labels)
        assertEquals(emptyList(), merged.values)
    }

    @Test
    fun conflictingMatchDecisions_areClearedToAskAgain() {
        val merged = mergeCards(card("a", matched = "cust"), card("b").copy(keepAsNew = true), labels = labels)
        assertNull(merged.matchedCustomerId)
        assertFalse(merged.keepAsNew)
    }

    @Test
    fun twoCheckedCardsWithoutConflict_stayChecked() {
        val merged = mergeCards(
            card("a", status = CardStatus.CHECKED, values = listOf(v("Bust", "38", "bust"))),
            card("b", status = CardStatus.CHECKED, values = listOf(v("Hip", "44", "hip"))),
            labels = labels,
        )
        assertEquals(CardStatus.CHECKED, merged.status)
    }
}
