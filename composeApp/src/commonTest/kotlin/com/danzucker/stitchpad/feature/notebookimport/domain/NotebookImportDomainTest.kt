package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotebookImportDomainTest {
    private val entitlements = UserEntitlements(
        tier = SubscriptionTier.FREE,
        customerCap = Int.MAX_VALUE,
        smartCoinAllowance = 5,
        isInWelcomeWindow = false,
        welcomeEndsAt = null,
        isWithinWelcomeEndingWarning = false,
        welcomeDaysLeft = null,
        canUseCustomMeasurements = false,
    )

    private fun card(
        id: String = "p1_0",
        name: String = "Bisi",
        phone: String? = null,
        gender: CustomerGender? = CustomerGender.FEMALE,
        unit: MeasurementUnit? = MeasurementUnit.INCHES,
        date: LocalDate? = LocalDate(2026, 3, 1),
        values: List<CardValue> = emptyList(),
    ) = ReviewCard(
        id = id, jobId = "job1", pageId = "p1", name = name, phone = phone, gender = gender,
        unit = unit, dateWritten = date, values = values, box = null, needsReview = false,
        status = CardStatus.NEW, matchedCustomerId = null, savedCustomerId = null,
    )

    private fun customer(id: String, name: String, phone: String) = Customer(id = id, userId = "u", name = name, phone = phone)

    @Test
    fun access_ownerTrue_staffFalse() {
        val owner = WorkshopSession.ownerOfSelf("o")
        val staff = WorkshopSession("s", "o", StaffRole.STAFF, MembershipStatus.ACTIVE)
        assertEquals(true, canStartNotebookImport(owner, entitlements))
        assertEquals(false, canStartNotebookImport(staff, entitlements))
    }

    @Test
    fun normaliseName_stripsTitlesAccentsCase() {
        assertEquals("adebayo bisi", DuplicateMatcher.normaliseName("  Mrs. Adébáyọ̀  Bisi"))
        // precomposed and combining forms agree
        assertEquals("adebayo", DuplicateMatcher.normaliseName("Adébáyọ̀"))
        assertEquals("amina", DuplicateMatcher.normaliseName("Alhaji Amina"))
    }

    @Test
    fun findExisting_phoneBeatsName() {
        val byName = customer("a", "Bisi Ade", "2348000000001")
        val byPhone = customer("b", "Someone Else", "2348031234567")
        val found = DuplicateMatcher.findExisting(
            card(name = "Bisi Ade", phone = "0803 123 4567"), listOf(byName, byPhone), Country.NG,
        )
        assertEquals("b", found?.id)
    }

    @Test
    fun findExisting_nameOnly() {
        val c = customer("a", "Bisi  Ade", "")
        assertEquals("a", DuplicateMatcher.findExisting(card(name = "mrs bisi ade"), listOf(c), Country.NG)?.id)
    }

    @Test
    fun findExisting_none() {
        assertNull(DuplicateMatcher.findExisting(card(name = "Zed", phone = ""), listOf(customer("a", "Bisi", "")), Country.NG))
    }

    @Test
    fun groupWithinImport_samePersonTwoPages() {
        val groups = DuplicateMatcher.groupWithinImport(
            listOf(card("a", name = "Bisi Ade", phone = "08031234567"), card("b", name = "bisi ade"), card("c", name = "Tola")),
        )
        assertEquals(listOf(listOf("a", "b")), groups)
    }

    @Test
    fun groupWithinImport_conflictingPhonesNotGrouped() {
        val groups = DuplicateMatcher.groupWithinImport(
            listOf(card("a", name = "Bisi", phone = "08031234567"), card("b", name = "Bisi", phone = "08099999999")),
        )
        assertEquals(emptyList(), groups)
    }

    @Test
    fun mapper_newCustomerDeterministicIds() {
        val r = cardToRecords(card(name = " Bisi ", phone = "0803 123 4567"), "u", null, LocalDate(2026, 5, 5), MeasurementUnit.CM, Country.NG)
        assertEquals("nb_job1_p1_0", r.customer?.id)
        assertEquals("u", r.customer?.userId)
        assertEquals("Bisi", r.customer?.name)
        assertEquals("2348031234567", r.customer?.phone)
        assertEquals("nbm_job1_p1_0", r.measurement.id)
        assertEquals("nb_job1_p1_0", r.measurement.customerId)
        assertEquals(CustomerGender.FEMALE, r.measurement.gender)
    }

    @Test
    fun mapper_existingCustomer_noCustomerRecord() {
        val r = cardToRecords(card(), "u", "existing", LocalDate(2026, 5, 5), MeasurementUnit.CM)
        assertNull(r.customer)
        assertEquals("existing", r.measurement.customerId)
    }

    @Test
    fun mapper_unmatchedToNotes() {
        val r = cardToRecords(
            card(values = listOf(CardValue("Chest", "36", "chest", false), CardValue("Odd", "5", null, true), CardValue("Weird", "7", null, false))),
            "u", null, LocalDate(2026, 5, 5), MeasurementUnit.CM,
        )
        assertEquals(mapOf("chest" to "36"), r.measurement.fields)
        assertEquals("Odd: 5\nWeird: 7", r.measurement.notes)
    }

    @Test
    fun mapper_duplicateKeyToNotes() {
        val r = cardToRecords(
            card(values = listOf(CardValue("Chest", "36", "chest", false), CardValue("Chest 2", "38", "chest", false))),
            "u", null, LocalDate(2026, 5, 5), MeasurementUnit.CM,
        )
        assertEquals(mapOf("chest" to "36"), r.measurement.fields)
        assertEquals("Chest 2: 38", r.measurement.notes)
    }

    @Test
    fun mapper_dateFallsBackToImportDate() {
        val import = LocalDate(2026, 5, 5)
        val withDate = cardToRecords(card(date = LocalDate(2026, 3, 1)), "u", null, import, MeasurementUnit.CM)
        val without = cardToRecords(card(date = null), "u", null, import, MeasurementUnit.CM)
        assertEquals(LocalDate(2026, 3, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), withDate.measurement.dateTaken)
        assertEquals(import.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), without.measurement.dateTaken)
        assertNull(without.measurement.notes)
        assertNotNull(without.measurement)
    }

    @Test
    fun mapper_unitFallsBackToDefault() {
        val r = cardToRecords(card(unit = null), "u", null, LocalDate(2026, 5, 5), MeasurementUnit.CM)
        assertEquals(MeasurementUnit.CM, r.measurement.unit)
        assertEquals(MeasurementUnit.INCHES, cardToRecords(card(), "u", null, LocalDate(2026, 5, 5), MeasurementUnit.CM).measurement.unit)
    }
}
