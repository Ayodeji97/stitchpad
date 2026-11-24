package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.model.CustomerGender
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
import com.danzucker.stitchpad.feature.notebookimport.presentation.FakeUnitStore
import com.danzucker.stitchpad.feature.notebookimport.presentation.REVIEW_JOB
import com.danzucker.stitchpad.feature.notebookimport.presentation.REVIEW_OWNER
import com.danzucker.stitchpad.feature.notebookimport.presentation.customer
import com.danzucker.stitchpad.feature.notebookimport.presentation.donePage
import com.danzucker.stitchpad.feature.notebookimport.presentation.reviewCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
            unitStore = FakeUnitStore(),
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

        vm.events.test {
            vm.onAction(NotebookCardAction.OnSaveNowClick)
            assertIs<NotebookCardEvent.ShowCapReachedSheet>(awaitItem())
        }
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
        assertFalse(vm.state.value.addToExisting)

        vm.onAction(NotebookCardAction.OnMatchToggle(addToExisting = true))
        assertTrue(vm.state.value.addToExisting)
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertEquals("cust-1", repository.lastUpdate("c1")?.matchedCustomerId)
    }

    @Test
    fun card_matchedToggle_offClearsMatchedCustomerId() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.cards.value = listOf(reviewCard("c1", name = "Emeka Okafor", matched = "cust-1"))
        val vm = createViewModel()
        assertTrue(vm.state.value.addToExisting)

        vm.onAction(NotebookCardAction.OnMatchToggle(addToExisting = false))
        vm.onAction(NotebookCardAction.OnLooksRightClick)
        assertNull(repository.lastUpdate("c1")?.matchedCustomerId)
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
