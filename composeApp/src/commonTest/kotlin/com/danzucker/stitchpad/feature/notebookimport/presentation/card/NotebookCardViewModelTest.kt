package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.FakeCurrentCountryProvider
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportJob
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportSaver
import com.danzucker.stitchpad.feature.notebookimport.domain.importCustomerId
import com.danzucker.stitchpad.feature.notebookimport.presentation.FakeReviewCustomers
import com.danzucker.stitchpad.feature.notebookimport.presentation.FakeReviewEntitlements
import com.danzucker.stitchpad.feature.notebookimport.presentation.FakeReviewRepository
import com.danzucker.stitchpad.feature.notebookimport.presentation.REVIEW_JOB
import com.danzucker.stitchpad.feature.notebookimport.presentation.REVIEW_OWNER
import com.danzucker.stitchpad.feature.notebookimport.presentation.customer
import com.danzucker.stitchpad.feature.notebookimport.presentation.donePage
import com.danzucker.stitchpad.feature.notebookimport.presentation.review.NotebookReviewSession
import com.danzucker.stitchpad.feature.notebookimport.presentation.reviewCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.danzucker.stitchpad.feature.notebookimport.domain.CheckBlocker
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookCardViewModelTest {

    private lateinit var repository: FakeReviewRepository
    private lateinit var customers: FakeReviewCustomers
    private lateinit var measurements: FakeMeasurementRepository
    private val reviewSession = NotebookReviewSession()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeReviewRepository()
        customers = FakeReviewCustomers()
        measurements = FakeMeasurementRepository()
        repository.pages.value = listOf(donePage("p1"), donePage("p2"))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(cardId: String = "c1"): NotebookCardViewModel {
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
        }
        val vm = NotebookCardViewModel(
            savedStateHandle = SavedStateHandle(mapOf("jobId" to REVIEW_JOB, "cardId" to cardId)),
            repository = repository,
            saver = NotebookImportSaver(customers, measurements, repository, clock),
            customerRepository = customers,
            activeWorkshop = FakeActiveWorkshopProvider(WorkshopSession.ownerOfSelf(REVIEW_OWNER)),
            entitlements = FakeReviewEntitlements(cap = 15),
            countryProvider = FakeCurrentCountryProvider(),
            reviewSession = reviewSession,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    @Test
    fun card_editValue_persists() = runTest {
        repository.cards.value = listOf(
            reviewCard(
                "c1",
                pageId = "p2",
                needsReview = true,
                values = listOf(CardValue("Bust", "38", "bust", false), CardValue("Waist", "3?", "waist", true)),
            ),
        )
        val vm = createViewModel()
        assertEquals(2, vm.state.value.pageNumber)
        assertFalse(vm.state.value.canMarkChecked)

        vm.onAction(NotebookCardAction.OnValueChange(1, "34"))
        assertEquals(CardValue("Waist", "34", "waist", false), vm.state.value.values[1])
        assertTrue(vm.state.value.canMarkChecked)

        vm.events.test {
            vm.onAction(NotebookCardAction.OnNavigateBack)
            assertEquals(NotebookCardEvent.NavigateBack, awaitItem())
        }
        val saved = assertNotNull(repository.lastUpdate("c1"))
        assertEquals(CardValue("Waist", "34", "waist", false), saved.values[1])
        // Leaving keeps the card's status: only "Looks right" checks it.
        assertEquals(CardStatus.NEW, saved.status)
    }

    @Test
    fun card_changeFieldKey_persists() = runTest {
        repository.cards.value = listOf(reviewCard("c1", values = listOf(CardValue("Rd arm", "13", null, false))))
        val vm = createViewModel()

        vm.onAction(NotebookCardAction.OnFieldPickerOpen(0))
        assertEquals(0, vm.state.value.fieldPickerIndex)
        vm.onAction(NotebookCardAction.OnFieldSelect(0, "arm_length"))

        assertNull(vm.state.value.fieldPickerIndex)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertEquals("arm_length", repository.lastUpdate("c1")?.values?.single()?.fieldKey)
    }

    @Test
    fun card_looksRight_requiresGender() = runTest {
        repository.cards.value = listOf(reviewCard("c1", gender = null, needsReview = true))
        val vm = createViewModel()
        assertFalse(vm.state.value.canMarkChecked)

        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertTrue(repository.updated.none { it.status == CardStatus.CHECKED })
        assertNotNull(vm.state.value.message)

        vm.onAction(NotebookCardAction.OnGenderSelect(CustomerGender.FEMALE))
        assertTrue(vm.state.value.canMarkChecked)
        vm.events.test {
            vm.onAction(NotebookCardAction.OnLooksRightClick)
            assertEquals(NotebookCardEvent.NavigateBack, awaitItem())
        }
        val checked = assertNotNull(repository.lastUpdate("c1"))
        assertEquals(CardStatus.CHECKED, checked.status)
        assertEquals(CustomerGender.FEMALE, checked.gender)
    }

    @Test
    fun card_looksRight_blockedByUnclearOrBlankValue() = runTest {
        repository.cards.value = listOf(
            reviewCard(
                "c1",
                values = listOf(CardValue("Waist", "3?", "waist", true), CardValue("Hip", "", "hip", false)),
            ),
        )
        val vm = createViewModel()
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertTrue(repository.updated.isEmpty())

        vm.onAction(NotebookCardAction.OnValueConfirm(0))
        assertFalse(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnValueRemove(1))
        assertTrue(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertEquals(listOf(CardValue("Waist", "3?", "waist", false)), repository.lastUpdate("c1")?.values)
    }

    @Test
    fun card_saveNow_savesOne() = runTest {
        repository.cards.value = listOf(reviewCard("c1"), reviewCard("c2", status = CardStatus.CHECKED))
        val vm = createViewModel()

        vm.events.test {
            vm.onAction(NotebookCardAction.OnSaveNowClick)
            assertEquals(NotebookCardEvent.NavigateBack, awaitItem())
        }

        assertEquals(listOf(importCustomerId(REVIEW_JOB, "c1")), customers.created.map { it.id })
        assertNotNull(measurements.lastCreatedMeasurement)
        val byId = repository.cards.value.associateBy { it.id }
        assertEquals(CardStatus.SAVED, byId.getValue("c1").status)
        assertEquals(CardStatus.CHECKED, byId.getValue("c2").status)
    }

    @Test
    fun card_saveNow_capReached_emitsCapEvent() = runTest {
        customers.capReached = true
        repository.cards.value = listOf(reviewCard("c1"))
        val vm = createViewModel()

        vm.onAction(NotebookCardAction.OnSaveNowClick)
        assertEquals(15, vm.state.value.capSheet?.customerCap)
        vm.onAction(NotebookCardAction.OnCapSheetDismiss)
        assertNull(vm.state.value.capSheet)
        assertEquals(CardStatus.CHECKED, repository.cards.value.single().status)
    }

    @Test
    fun card_skip_setsSkipped() = runTest {
        repository.cards.value = listOf(reviewCard("c1", gender = null))
        val vm = createViewModel()

        vm.events.test {
            vm.onAction(NotebookCardAction.OnSkipClick)
            assertEquals(NotebookCardEvent.NavigateBack, awaitItem())
        }
        assertEquals(CardStatus.SKIPPED, repository.lastUpdate("c1")?.status)
    }

    @Test
    fun card_matchedToggle_setsMatchedCustomerId() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.cards.value = listOf(reviewCard("c1", name = "Emeka Okafor"))
        val vm = createViewModel()

        val candidate = assertNotNull(vm.state.value.matchCandidate)
        assertEquals("Emeka Okafor", candidate.name)
        // Unresolved: neither option selected, and it blocks "Looks right".
        assertNull(vm.state.value.matchChoice)
        assertEquals(CheckBlocker.MATCH, vm.state.value.checkBlocker)
        assertFalse(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertTrue(repository.updated.isEmpty())

        vm.onAction(NotebookCardAction.OnMatchToggle(addToExisting = true))
        assertEquals(true, vm.state.value.matchChoice)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        val written = assertNotNull(repository.lastUpdate("c1"))
        assertEquals("cust-1", written.matchedCustomerId)
        assertFalse(written.keepAsNew)
    }

    @Test
    fun card_matchedToggle_offKeepsAsNew() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.cards.value = listOf(reviewCard("c1", name = "Emeka Okafor", matched = "cust-1"))
        val vm = createViewModel()
        assertEquals(true, vm.state.value.matchChoice)

        vm.onAction(NotebookCardAction.OnMatchToggle(addToExisting = false))
        assertEquals(false, vm.state.value.matchChoice)
        assertTrue(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        val written = assertNotNull(repository.lastUpdate("c1"))
        assertNull(written.matchedCustomerId)
        assertTrue(written.keepAsNew)
    }

    @Test
    fun card_ambiguousMatch_offersCandidatesAndBlocksUntilPicked() = runTest {
        customers.customers.value = listOf(
            customer("cust-1", "Bisi Ade", phone = "+2348031111111"),
            customer("cust-2", "Bisi Ade"),
        )
        repository.cards.value = listOf(reviewCard("c1", name = "Bisi Ade"))
        val vm = createViewModel()

        assertNull(vm.state.value.matchCandidate)
        assertEquals(
            listOf(ExistingCustomer("cust-1", "Bisi Ade", "+2348031111111"), ExistingCustomer("cust-2", "Bisi Ade", null)),
            vm.state.value.matchCandidates,
        )
        assertNull(vm.state.value.matchChoice)
        assertEquals(CheckBlocker.MATCH, vm.state.value.checkBlocker)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        vm.onAction(NotebookCardAction.OnSaveNowClick)
        assertTrue(repository.updated.isEmpty())
        assertTrue(customers.created.isEmpty())

        vm.onAction(NotebookCardAction.OnMatchCandidatePick("cust-2"))
        assertEquals(true, vm.state.value.matchChoice)
        assertTrue(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        val written = assertNotNull(repository.lastUpdate("c1"))
        assertEquals("cust-2", written.matchedCustomerId)
        assertFalse(written.keepAsNew)
    }

    @Test
    fun card_ambiguousMatch_saveAsNewCustomer() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Bisi Ade"), customer("cust-2", "Bisi Ade"))
        repository.cards.value = listOf(reviewCard("c1", name = "Bisi Ade"))
        val vm = createViewModel()

        vm.onAction(NotebookCardAction.OnMatchToggle(addToExisting = false))
        assertEquals(false, vm.state.value.matchChoice)
        assertTrue(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        val written = assertNotNull(repository.lastUpdate("c1"))
        assertNull(written.matchedCustomerId)
        assertTrue(written.keepAsNew)
    }

    @Test
    fun card_pickingSomeoneNotACandidate_isIgnored() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Bisi Ade"), customer("cust-2", "Bisi Ade"))
        repository.cards.value = listOf(reviewCard("c1", name = "Bisi Ade"))
        val vm = createViewModel()

        vm.onAction(NotebookCardAction.OnMatchCandidatePick("someone-else"))
        assertNull(vm.state.value.matchChoice)
    }

    @Test
    fun card_nullUnit_blocksUntilChosen() = runTest {
        repository.cards.value = listOf(reviewCard("c1", unit = null))
        val vm = createViewModel()

        assertNull(vm.state.value.unit)
        assertEquals(CheckBlocker.UNIT, vm.state.value.checkBlocker)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        vm.onAction(NotebookCardAction.OnSaveNowClick)
        assertTrue(repository.updated.none { it.status == CardStatus.CHECKED })
        assertTrue(customers.created.isEmpty())

        vm.onAction(NotebookCardAction.OnUnitSelect(MeasurementUnit.CM))
        assertTrue(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertEquals(MeasurementUnit.CM, repository.lastUpdate("c1")?.unit)
    }

    @Test
    fun card_staleMatch_isClearedAndNeverShowsTheCardsOwnName() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.cards.value = listOf(reviewCard("c1", name = "Bisi Ade", matched = "gone"))
        val vm = createViewModel()

        assertNull(vm.state.value.matchCandidate)
        assertNull(vm.state.value.draft?.matchedCustomerId)
        assertTrue(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertNull(repository.lastUpdate("c1")?.matchedCustomerId)
    }

    @Test
    fun card_staleMatch_offersTheRealSuggestionAgain() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.cards.value = listOf(reviewCard("c1", name = "Emeka Okafor", matched = "gone"))
        val vm = createViewModel()

        assertEquals("cust-1", vm.state.value.matchCandidate?.id)
        assertNull(vm.state.value.matchChoice)
        assertFalse(vm.state.value.canMarkChecked)
    }

    @Test
    fun card_pendingSamePerson_blocksLooksRightAndSaveNow() = runTest {
        repository.cards.value = listOf(reviewCard("c1", name = "Kemi Adebayo"), reviewCard("c2", name = "Kemi Adebayo"))
        val vm = createViewModel()

        assertEquals(CheckBlocker.DUPLICATE, vm.state.value.checkBlocker)
        assertFalse(vm.state.value.canMarkChecked)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        vm.onAction(NotebookCardAction.OnSaveNowClick)
        assertTrue(repository.updated.isEmpty())
        assertTrue(customers.created.isEmpty())

        // Renaming it so it no longer looks like the other card lifts the block.
        vm.onAction(NotebookCardAction.OnNameChange("Kemi Bello"))
        assertNull(vm.state.value.checkBlocker)
    }

    @Test
    fun card_samePersonKeptSeparateOnTheList_isNotBlocked() = runTest {
        reviewSession.keepSeparate(REVIEW_JOB, setOf("c1", "c2"))
        repository.cards.value = listOf(reviewCard("c1", name = "Kemi Adebayo"), reviewCard("c2", name = "Kemi Adebayo"))
        val vm = createViewModel()

        assertNull(vm.state.value.checkBlocker)
        assertTrue(vm.state.value.canMarkChecked)
    }

    @Test
    fun card_photo_loadsUrlUntilImagesDeleted() = runTest {
        repository.cards.value = listOf(reviewCard("c1"))
        val vm = createViewModel()
        assertEquals("https://example.test/page.jpg", vm.state.value.imageUrl)
        assertFalse(vm.state.value.photoUnavailable)

        repository.job.value = ImportJob(REVIEW_JOB, Instant.fromEpochSeconds(0), "saved", 1, Instant.fromEpochSeconds(5))
        assertNull(vm.state.value.imageUrl)
        assertTrue(vm.state.value.photoUnavailable)
    }
}
