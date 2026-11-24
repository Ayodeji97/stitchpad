package com.danzucker.stitchpad.feature.notebookimport.presentation.review

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.danzucker.stitchpad.core.data.repository.FakeMeasurementRepository
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.FakeCurrentCountryProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.core.media.ImageCompressor
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportSaver
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookPageUploader
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.importCustomerId
import com.danzucker.stitchpad.feature.notebookimport.presentation.FakeReviewCustomers
import com.danzucker.stitchpad.feature.notebookimport.presentation.FakeReviewEntitlements
import com.danzucker.stitchpad.feature.notebookimport.presentation.FakeReviewRepository
import com.danzucker.stitchpad.feature.notebookimport.presentation.FakeUnitStore
import com.danzucker.stitchpad.feature.notebookimport.presentation.FinishCall
import com.danzucker.stitchpad.feature.notebookimport.presentation.REVIEW_JOB
import com.danzucker.stitchpad.feature.notebookimport.presentation.REVIEW_OWNER
import com.danzucker.stitchpad.feature.notebookimport.presentation.customer
import com.danzucker.stitchpad.feature.notebookimport.presentation.donePage
import com.danzucker.stitchpad.feature.notebookimport.presentation.reviewCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.notebookimport.domain.MergeNoteLabels
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.notebook_review_saved_before_cap
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

private class PassThroughCompressor : ImageCompressor {
    override suspend fun compress(bytes: ByteArray, maxEdgePx: Int, jpegQuality: Int): ByteArray = bytes
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookReviewViewModelTest {

    private lateinit var repository: FakeReviewRepository
    private lateinit var customers: FakeReviewCustomers
    private lateinit var measurements: FakeMeasurementRepository
    private lateinit var workshop: FakeActiveWorkshopProvider
    private lateinit var appScope: CoroutineScope
    private lateinit var uploader: NotebookPageUploader
    private val reviewSession = NotebookReviewSession()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeReviewRepository()
        customers = FakeReviewCustomers()
        measurements = FakeMeasurementRepository()
        workshop = FakeActiveWorkshopProvider(WorkshopSession.ownerOfSelf(REVIEW_OWNER))
        appScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        uploader = NotebookPageUploader(repository, PassThroughCompressor(), workshop, FakeReviewEntitlements(), appScope)
        uploader.adoptJob(REVIEW_JOB)
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): NotebookReviewViewModel {
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)
        }
        val vm = NotebookReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("jobId" to REVIEW_JOB)),
            repository = repository,
            saver = NotebookImportSaver(customers, measurements, repository, clock),
            customerRepository = customers,
            activeWorkshop = workshop,
            entitlements = FakeReviewEntitlements(cap = 15),
            countryProvider = FakeCurrentCountryProvider(),
            unitStore = FakeUnitStore(),
            uploader = uploader,
            reviewSession = reviewSession,
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private val labels = MergeNoteLabels(date = "Date", phone = "Phone")

    private fun flagged(label: String, value: String, key: String?) = CardValue(label, value, key, true)

    @Test
    fun list_countsAndSections() = runTest {
        repository.pages.value = listOf(donePage("p1"), donePage("p2"))
        repository.cards.value = listOf(
            reviewCard("c1", pageId = "p1", needsReview = true, values = listOf(flagged("Waist", "3?", "waist"))),
            reviewCard("c2", pageId = "p1"),
            reviewCard("c3", pageId = "p2", status = CardStatus.CHECKED, needsReview = true),
            reviewCard("c4", pageId = "p2", status = CardStatus.SAVED),
            reviewCard("c5", pageId = "p2", status = CardStatus.SKIPPED),
            // Clean on the server but with no gender: cannot be saved, so it needs a look.
            reviewCard("c6", pageId = "p2", gender = null),
        )

        val vm = createViewModel()
        val state = vm.state.value

        assertEquals(6, state.foundCount)
        assertEquals(2, state.needLookCount)
        assertEquals(listOf("c1", "c6"), state.needLook.map { it.cardId })
        assertEquals(listOf("c2", "c3"), state.ready.map { it.cardId })
        assertEquals(listOf("c4"), state.saved.map { it.cardId })
        assertEquals(listOf("c5"), state.skipped.map { it.cardId })
        val c1 = state.needLook.first()
        assertEquals(1, c1.unclearCount)
        assertEquals(1, c1.pageNumber)
        assertEquals(2, state.ready.last().pageNumber)
    }

    @Test
    fun list_saveAllChecked_onlyChecked() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(
            reviewCard("c1", needsReview = true, values = listOf(flagged("Waist", "3?", "waist"))),
            reviewCard("c2", status = CardStatus.CHECKED),
            reviewCard("c3", status = CardStatus.SKIPPED),
            // Same name as an existing customer, tailor chose "Save as a new customer".
            reviewCard("c4", name = "Emeka Okafor", status = CardStatus.CHECKED, keepAsNew = true),
        )
        val vm = createViewModel()
        assertNull(vm.state.value.ready.single { it.cardId == "c4" }.match)

        vm.onAction(NotebookReviewAction.OnSaveAllClick)

        assertEquals(
            listOf(importCustomerId(REVIEW_JOB, "c2"), importCustomerId(REVIEW_JOB, "c4")),
            customers.created.map { it.id },
        )
        val byId = repository.cards.value.associateBy { it.id }
        assertEquals(CardStatus.NEW, byId.getValue("c1").status)
        assertEquals(CardStatus.SAVED, byId.getValue("c2").status)
        assertEquals(CardStatus.SKIPPED, byId.getValue("c3").status)
        assertEquals(CardStatus.SAVED, byId.getValue("c4").status)
        assertNull(byId.getValue("c4").matchedCustomerId)
        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.isSaving)
    }

    @Test
    fun list_capOutcome_emitsCapEvent() = runTest {
        customers.capReached = true
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(
            reviewCard("c1", status = CardStatus.CHECKED),
            reviewCard("c2", status = CardStatus.CHECKED),
        )
        val vm = createViewModel()

        vm.onAction(NotebookReviewAction.OnSaveAllClick)

        val sheet = assertNotNull(vm.state.value.capSheet)
        assertEquals(15, sheet.customerCap)
        val message = assertIs<UiText.StringResourceText>(vm.state.value.message)
        assertEquals(Res.string.notebook_review_saved_before_cap, message.id)
        assertEquals(0, message.args.single())
        vm.onAction(NotebookReviewAction.OnCapSheetDismiss)
        assertNull(vm.state.value.capSheet)
        // Nothing was saved and both cards stay checked for after the upgrade.
        assertTrue(repository.cards.value.all { it.status == CardStatus.CHECKED })
        assertEquals(listOf("c1", "c2"), vm.state.value.ready.map { it.cardId })
        assertTrue(repository.finished.isEmpty())
    }

    @Test
    fun list_merge_combinesValuesDeletesOther() = runTest {
        repository.pages.value = listOf(donePage("p1"), donePage("p2"))
        repository.cards.value = listOf(
            reviewCard(
                "c1",
                name = "Kemi Adebayo",
                pageId = "p1",
                values = listOf(CardValue("Bust", "38", "bust", false)),
            ),
            reviewCard(
                "c2",
                name = "Kemi Adebayo",
                pageId = "p2",
                phone = "08031234567",
                values = listOf(CardValue("Hip", "44", "hip", false), CardValue("Bust", "39", "bust", false)),
            ),
        )
        val vm = createViewModel()
        // A pending same-person prompt keeps both cards out of Ready (and out of Save all).
        assertEquals(listOf("c1", "c2"), vm.state.value.needLook.map { it.cardId })
        assertTrue(vm.state.value.ready.isEmpty())
        val prompt = assertNotNull(vm.state.value.needLook.first { it.cardId == "c1" }.duplicate)
        assertEquals(listOf("c1", "c2"), prompt.cardIds)
        assertEquals(listOf(1, 2), prompt.pageNumbers)

        vm.onAction(NotebookReviewAction.OnMergeClick(prompt.cardIds, labels))

        val merged = assertNotNull(repository.lastUpdate("c1"))
        assertEquals(
            listOf(
                CardValue("Bust", "38", "bust", true),
                CardValue("Hip", "44", "hip", false),
                CardValue("Bust", "39", "bust", true),
            ),
            merged.values,
        )
        assertEquals("08031234567", merged.phone)
        assertEquals(CardStatus.NEW, merged.status)
        assertEquals(listOf("c2"), repository.deleted)
        // The conflicting bust values send the merged card back to "Need a look".
        assertEquals(listOf("c1"), vm.state.value.needLook.map { it.cardId })
        assertNull(vm.state.value.needLook.single().duplicate)
    }

    @Test
    fun list_keepSeparate_hidesPrompt() = runTest {
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(
            reviewCard("c1", name = "Kemi Adebayo"),
            reviewCard("c2", name = "Kemi Adebayo"),
        )
        val vm = createViewModel()

        assertTrue(vm.state.value.ready.isEmpty())

        vm.onAction(NotebookReviewAction.OnKeepSeparateClick(listOf("c1", "c2")))

        assertEquals(listOf("c1", "c2"), vm.state.value.ready.map { it.cardId })
        assertTrue(vm.state.value.ready.all { it.duplicate == null })
        assertTrue(repository.deleted.isEmpty())
        // The card editor reads the same decision.
        assertTrue(reviewSession.isKeptSeparate(REVIEW_JOB, listOf("c1", "c2")))
    }

    @Test
    fun list_merge_whenDeleteFails_skipsTheOtherCard() = runTest {
        repository.deleteError = DataError.Network.NO_INTERNET
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(
            reviewCard("c1", name = "Kemi Adebayo"),
            reviewCard("c2", name = "Kemi Adebayo", values = listOf(CardValue("Hip", "44", "hip", false))),
        )
        val vm = createViewModel()

        vm.onAction(NotebookReviewAction.OnMergeClick(listOf("c1", "c2"), labels))

        assertEquals(2, repository.lastUpdate("c1")?.values?.size)
        // The other card can never be saved a second time.
        assertEquals(CardStatus.SKIPPED, repository.lastUpdate("c2")?.status)
        assertEquals(listOf("c1"), (vm.state.value.needLook + vm.state.value.ready).map { it.cardId })
    }

    @Test
    fun list_saveAll_neverSavesUnresolvedSuggestion() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(
            reviewCard("c1", name = "Emeka Okafor", status = CardStatus.CHECKED),
            reviewCard("c2", status = CardStatus.CHECKED),
        )
        val vm = createViewModel()
        assertEquals(listOf("c1"), vm.state.value.needLook.map { it.cardId })

        vm.onAction(NotebookReviewAction.OnSaveAllClick)

        assertEquals(listOf(importCustomerId(REVIEW_JOB, "c2")), customers.created.map { it.id })
        assertEquals(importCustomerId(REVIEW_JOB, "c2"), measurements.lastCreatedMeasurement?.customerId)
        assertEquals(CardStatus.CHECKED, repository.cards.value.first { it.id == "c1" }.status)
    }

    @Test
    fun list_staleConfirmedMatch_isNeverSavedUnderTheMissingId() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(
            // Confirmed earlier, but that customer was deleted since; no other match for this name.
            reviewCard("c1", name = "Bisi Ade", status = CardStatus.CHECKED, matched = "gone"),
            // Confirmed to a deleted customer, and the name now matches someone else: ask again.
            reviewCard("c2", name = "Emeka Okafor", status = CardStatus.CHECKED, matched = "gone"),
        )
        val vm = createViewModel()
        assertEquals(listOf("c2"), vm.state.value.needLook.map { it.cardId })
        val again = assertNotNull(vm.state.value.needLook.single().match)
        assertEquals("cust-1", again.customerId)
        assertFalse(again.confirmed)
        assertNull(vm.state.value.ready.single().match)

        vm.onAction(NotebookReviewAction.OnSaveAllClick)

        assertEquals(listOf(importCustomerId(REVIEW_JOB, "c1")), customers.created.map { it.id })
        assertEquals(importCustomerId(REVIEW_JOB, "c1"), measurements.lastCreatedMeasurement?.customerId)
    }

    @Test
    fun list_matchSuggestion_writesOnlyOnConfirm() = runTest {
        customers.customers.value = listOf(customer("cust-1", "Emeka Okafor"))
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(reviewCard("c1", name = "Emeka Okafor"))
        val vm = createViewModel()

        // Unresolved suggestion: needs a look, nothing written.
        assertTrue(vm.state.value.ready.isEmpty())
        val match = assertNotNull(vm.state.value.needLook.single().match)
        assertEquals("Emeka Okafor", match.customerName)
        assertFalse(match.confirmed)
        assertTrue(repository.updated.isEmpty())

        vm.onAction(NotebookReviewAction.OnConfirmMatch("c1"))
        assertEquals("cust-1", repository.lastUpdate("c1")?.matchedCustomerId)
        assertEquals(false, repository.lastUpdate("c1")?.keepAsNew)
        assertTrue(vm.state.value.ready.single().match!!.confirmed)

        vm.onAction(NotebookReviewAction.OnRejectMatch("c1"))
        assertNull(repository.lastUpdate("c1")?.matchedCustomerId)
        assertEquals(true, repository.lastUpdate("c1")?.keepAsNew)
        assertNull(vm.state.value.ready.single().match)
    }

    @Test
    fun list_allDone_finishesJob() = runTest {
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(
            reviewCard("c1", status = CardStatus.SAVED),
            reviewCard("c2", status = CardStatus.SKIPPED),
        )
        val vm = createViewModel()

        assertEquals(listOf(FinishCall(REVIEW_JOB, abandoned = false)), repository.finished)
        assertNull(uploader.currentJobId())
        assertTrue(vm.state.value.isFinished)
        // A later snapshot does not finish it twice.
        repository.cards.value = repository.cards.value.toList()
        assertEquals(1, repository.finished.size)
    }

    @Test
    fun list_allDone_waitsWhilePagesAreStillReading() = runTest {
        repository.pages.value = listOf(donePage("p1"), ImportPage("p2", PageStatus.PROCESSING, 0, null, null))
        repository.cards.value = listOf(reviewCard("c1", status = CardStatus.SAVED))
        val vm = createViewModel()

        assertTrue(repository.finished.isEmpty())
        assertEquals(1, vm.state.value.pagesReading)

        repository.pages.value = listOf(donePage("p1"), donePage("p2"))
        assertEquals(listOf(FinishCall(REVIEW_JOB, abandoned = false)), repository.finished)
    }

    @Test
    fun list_discard_abandons() = runTest {
        repository.pages.value = listOf(donePage("p1"))
        repository.cards.value = listOf(reviewCard("c1"))
        val vm = createViewModel()

        vm.onAction(NotebookReviewAction.OnDiscardClick)
        assertTrue(vm.state.value.showDiscardDialog)
        assertTrue(repository.finished.isEmpty())

        vm.onAction(NotebookReviewAction.OnDiscardDismiss)
        assertFalse(vm.state.value.showDiscardDialog)
        assertTrue(repository.finished.isEmpty())

        vm.events.test {
            vm.onAction(NotebookReviewAction.OnDiscardClick)
            vm.onAction(NotebookReviewAction.OnDiscardConfirm)
            assertEquals(NotebookReviewEvent.NavigateBack, awaitItem())
        }
        assertEquals(listOf(FinishCall(REVIEW_JOB, abandoned = true)), repository.finished)
        assertNull(uploader.currentJobId())
        assertFalse(vm.state.value.showDiscardDialog)
    }

    @Test
    fun list_cardClick_navigatesToCard() = runTest {
        repository.cards.value = listOf(reviewCard("c1", gender = CustomerGender.MALE))
        val vm = createViewModel()

        vm.events.test {
            vm.onAction(NotebookReviewAction.OnCardClick("c1"))
            assertEquals(NotebookReviewEvent.NavigateToCard(REVIEW_JOB, "c1"), awaitItem())
        }
    }

    @Test
    fun list_staff_navigatesBack() = runTest {
        workshop.setSession(
            WorkshopSession(
                authUid = "staff-1",
                workshopUid = REVIEW_OWNER,
                role = StaffRole.STAFF,
                membershipStatus = MembershipStatus.ACTIVE,
            ),
        )
        repository.cards.value = listOf(reviewCard("c1"))
        val vm = NotebookReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("jobId" to REVIEW_JOB)),
            repository = repository,
            saver = NotebookImportSaver(customers, measurements, repository, Clock.System),
            customerRepository = customers,
            activeWorkshop = workshop,
            entitlements = FakeReviewEntitlements(),
            countryProvider = FakeCurrentCountryProvider(),
            unitStore = FakeUnitStore(),
            uploader = uploader,
            reviewSession = reviewSession,
        )
        vm.events.test {
            backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
            assertEquals(NotebookReviewEvent.NavigateBack, awaitItem())
        }
        assertTrue(vm.state.value.ready.isEmpty())
    }
}
