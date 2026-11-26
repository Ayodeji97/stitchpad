package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.core.media.ImageCompressor
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportJob
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookImportRepository
import com.danzucker.stitchpad.feature.notebookimport.domain.NotebookPageUploader
import com.danzucker.stitchpad.feature.notebookimport.domain.PageStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.notebookimport.presentation.reviewCard
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.notebook_capture_photo_unreadable
import stitchpad.composeapp.generated.resources.notebook_capture_retry_limit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

internal fun unconfinedAppScope() = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

private const val OWNER = "owner-1"

internal class FakeCaptureRepository : NotebookImportRepository {
    val openJobs = MutableStateFlow<List<ImportJob>>(emptyList())
    var openJobsError: DataError.Network? = null

    /** Open-job lookups never answer while true (a lookup that times out). */
    var openJobsHang = false
    var openJobsCalls = 0
    val pages = MutableStateFlow<List<ImportPage>>(emptyList())

    /** Pages of jobs other than the one [pages] stands for. */
    val pagesByJob = mutableMapOf<String, MutableStateFlow<List<ImportPage>>>()
    val cardsByJob = mutableMapOf<String, MutableStateFlow<List<ReviewCard>>>()

    var createJobResult: Result<String, DataError.Network> = Result.Success("job-1")
    var uploadError: DataError.Network? = null
    var retryError: DataError.Network? = null
    var uploadGate: CompletableDeferred<Unit>? = null

    var createJobCalls = 0
    val uploaded = mutableListOf<Pair<String, ByteArray>>()
    val retried = mutableListOf<Pair<String, String>>()
    val events = mutableListOf<String>()
    private var counter = 0

    override suspend fun createJob(ownerUid: String): Result<String, DataError.Network> {
        createJobCalls++
        events += "create"
        return createJobResult
    }

    override suspend fun uploadPage(
        ownerUid: String,
        jobId: String,
        jpeg: ByteArray,
    ): Result<String, DataError.Network> {
        events += "upload"
        uploadGate?.await()
        uploadError?.let { return Result.Error(it) }
        val id = "p${(1_000_000_000_000L + counter++)}_abcd0000"
        uploaded += id to jpeg
        pages.value = pages.value + ImportPage(id, PageStatus.PROCESSING, 0, null, null)
        return Result.Success(id)
    }

    override fun observePages(ownerUid: String, jobId: String): Flow<Result<List<ImportPage>, DataError.Network>> =
        (pagesByJob[jobId] ?: pages).map { Result.Success(it) }

    override fun observeCards(ownerUid: String, jobId: String): Flow<Result<List<ReviewCard>, DataError.Network>> =
        (cardsByJob[jobId] ?: MutableStateFlow(emptyList())).map { Result.Success(it) }

    override fun observeOpenJobs(ownerUid: String): Flow<Result<List<ImportJob>, DataError.Network>> {
        openJobsCalls++
        if (openJobsHang) return flow { awaitCancellation() }
        val error = openJobsError
        return if (error != null) flowOf(Result.Error(error)) else openJobs.map { Result.Success(it) }
    }

    override fun observeJob(ownerUid: String, jobId: String): Flow<ImportJob?> = emptyFlow()

    override suspend fun pageImageUrl(ownerUid: String, jobId: String, pageId: String) =
        Result.Success("https://example.test/$pageId.jpg")

    override suspend fun updateCard(ownerUid: String, card: ReviewCard): EmptyResult<DataError.Network> =
        Result.Success(Unit)

    override suspend fun deleteCard(ownerUid: String, jobId: String, cardId: String): EmptyResult<DataError.Network> =
        Result.Success(Unit)

    override suspend fun retryPage(jobId: String, pageId: String): EmptyResult<DataError.Network> {
        retried += jobId to pageId
        return retryError?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

    override suspend fun finishJob(ownerUid: String, jobId: String, abandoned: Boolean): EmptyResult<DataError.Network> =
        Result.Success(Unit)

    fun setPage(id: String, status: PageStatus, personCount: Int = 0, error: String? = null) {
        pages.value = pages.value.map {
            if (it.id == id) ImportPage(id, status, personCount, error, null) else it
        }
    }
}

internal class RecordingCompressor : ImageCompressor {
    val calls = mutableListOf<Triple<Int, Int, Int>>()

    /** Simulates a photo the platform cannot decode. */
    var fail = false
    override suspend fun compress(bytes: ByteArray, maxEdgePx: Int, jpegQuality: Int): ByteArray? {
        calls += Triple(bytes.size, maxEdgePx, jpegQuality)
        return if (fail) null else byteArrayOf(9) + bytes
    }
}

/** Test "now": a fixed day plus the test scheduler's virtual time. */
internal const val CAPTURE_NOW = 1_800_000_000_000L
private const val MINUTE = 60_000L
private const val DAY = 24 * 60 * MINUTE

internal class FakeEntitlements : EntitlementsProvider {
    private val value = UserEntitlements(
        tier = SubscriptionTier.FREE,
        customerCap = 15,
        smartCoinAllowance = 5,
        isInWelcomeWindow = false,
        welcomeEndsAt = null,
        isWithinWelcomeEndingWarning = false,
        welcomeDaysLeft = null,
        canUseCustomMeasurements = false,
    )
    private val _flow = MutableStateFlow(value)
    override val flow: StateFlow<UserEntitlements> = _flow
    override fun current(): UserEntitlements = value
    override suspend fun awaitHydrated(): UserEntitlements = value
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookCaptureViewModelTest {

    private lateinit var repository: FakeCaptureRepository
    private lateinit var workshop: FakeActiveWorkshopProvider
    private lateinit var compressor: RecordingCompressor
    private lateinit var appScope: CoroutineScope

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeCaptureRepository()
        workshop = FakeActiveWorkshopProvider(WorkshopSession.ownerOfSelf(OWNER))
        compressor = RecordingCompressor()
        appScope = unconfinedAppScope()
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.now(): Long = CAPTURE_NOW + testScheduler.currentTime

    private fun TestScope.newUploader() =
        NotebookPageUploader(repository, compressor, workshop, FakeEntitlements(), appScope) { now() }

    private fun TestScope.createViewModel(uploader: NotebookPageUploader = newUploader()): NotebookCaptureViewModel {
        val vm = NotebookCaptureViewModel(
            repository = repository,
            activeWorkshop = workshop,
            entitlements = FakeEntitlements(),
            uploader = uploader,
            nowMillis = { now() },
        )
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        return vm
    }

    private fun job(
        id: String = "job-open",
        ageMillis: Long = 60 * MINUTE,
        imagesDeletedAt: Instant? = null,
    ) = ImportJob(id, Instant.fromEpochMilliseconds(CAPTURE_NOW - ageMillis), "open", 0, imagesDeletedAt)

    private fun page(id: String, status: PageStatus = PageStatus.DONE, count: Int = 1) =
        ImportPage(id, status, count, null, null)

    @Test
    fun firstPhoto_createsJobAndUploads() = runTest {
        val vm = createViewModel()
        val photo = byteArrayOf(1, 2, 3)

        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(photo)))

        assertEquals(1, repository.createJobCalls)
        assertEquals(listOf("create", "upload"), repository.events)
        assertContentEquals(byteArrayOf(9, 1, 2, 3), repository.uploaded.single().second)
        assertEquals(listOf(Triple(3, 2560, 90), Triple(4, 256, 80)), compressor.calls)
        val page = vm.state.value.pages.single()
        assertEquals(1, page.number)
        assertEquals(CapturePageStatus.READING, page.status)
        // After upload only the small thumbnail is kept.
        assertContentEquals(byteArrayOf(9, 9, 1, 2, 3), page.thumb)
    }

    @Test
    fun secondPhoto_reusesJob() = runTest {
        val vm = createViewModel()

        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2))))

        assertEquals(1, repository.createJobCalls)
        assertEquals(2, repository.uploaded.size)
        assertEquals(listOf(1, 2), vm.state.value.pages.map { it.number })
    }

    @Test
    fun resume_openJobPagesShown() = runTest {
        repository.openJobs.value = listOf(job())
        repository.pages.value = listOf(
            ImportPage("p1000000000002_aaaaaaaa", PageStatus.DONE, 3, null, null),
            ImportPage("p1000000000001_bbbbbbbb", PageStatus.PROCESSING, 0, null, null),
        )

        val vm = createViewModel()

        val pages = vm.state.value.pages
        assertEquals(listOf(1, 2), pages.map { it.number })
        // Sorted by page id, which is upload order.
        assertEquals(CapturePageStatus.READING, pages[0].status)
        assertEquals(CapturePageStatus.DONE, pages[1].status)
        assertNull(pages[0].thumb)
        assertTrue(vm.state.value.canReview)
        assertEquals(3, vm.state.value.reviewCount)

        // The resumed job is reused rather than a new one created.
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(0, repository.createJobCalls)
        assertEquals(3, vm.state.value.pages.size)
    }

    @Test
    fun resume_openJobWithNoPages_isFreshStart() = runTest {
        repository.openJobs.value = listOf(job())

        val vm = createViewModel()

        assertTrue(vm.state.value.pages.isEmpty())
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(1, repository.createJobCalls)
    }

    @Test
    fun uploadFailure_showsRetryAndReuploads() = runTest {
        val vm = createViewModel()
        repository.uploadError = DataError.Network.NO_INTERNET

        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))

        val failed = vm.state.value.pages.single()
        assertEquals(CapturePageStatus.UPLOAD_FAILED, failed.status)
        assertNotNull(vm.state.value.errorMessage)

        repository.uploadError = null
        vm.onAction(NotebookCaptureAction.OnRetryPage(failed.key))

        val page = vm.state.value.pages.single()
        assertEquals(CapturePageStatus.READING, page.status)
        assertEquals(1, repository.uploaded.size)
        assertContentEquals(byteArrayOf(9, 1), repository.uploaded.single().second)
        assertEquals(1, repository.createJobCalls)
        assertTrue(repository.retried.isEmpty())
    }

    @Test
    fun createJobFailure_marksFailedAndRetryCreatesJob() = runTest {
        val vm = createViewModel()
        repository.createJobResult = Result.Error(DataError.Network.NO_INTERNET)

        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))

        val failed = vm.state.value.pages.single()
        assertEquals(CapturePageStatus.UPLOAD_FAILED, failed.status)
        assertTrue(repository.uploaded.isEmpty())

        repository.createJobResult = Result.Success("job-2")
        vm.onAction(NotebookCaptureAction.OnRetryPage(failed.key))

        assertEquals(2, repository.createJobCalls)
        assertEquals(1, repository.uploaded.size)
    }

    @Test
    fun serverFailure_retryCallsRetryPage() = runTest {
        val vm = createViewModel()
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        val pageId = repository.uploaded.single().first
        repository.setPage(pageId, PageStatus.FAILED, error = "read_failed")

        val failed = vm.state.value.pages.single()
        assertEquals(CapturePageStatus.READ_FAILED, failed.status)
        // The thumbnail of a page captured this session survives the server update.
        assertNotNull(failed.thumb)

        vm.onAction(NotebookCaptureAction.OnRetryPage(failed.key))

        assertEquals(listOf("job-1" to pageId), repository.retried)
        assertEquals(1, repository.uploaded.size)
    }

    @Test
    fun serverFailure_retryFailureShowsError() = runTest {
        repository.openJobs.value = listOf(job())
        repository.pages.value = listOf(ImportPage("p1", PageStatus.FAILED, 0, "read_failed", null))
        val vm = createViewModel()
        repository.retryError = DataError.Network.NO_INTERNET

        vm.onAction(NotebookCaptureAction.OnRetryPage("p1"))

        assertNotNull(vm.state.value.errorMessage)
        vm.onAction(NotebookCaptureAction.OnErrorDismiss)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun capFailure_noRetry() = runTest {
        repository.openJobs.value = listOf(job())
        repository.pages.value = listOf(ImportPage("p1", PageStatus.FAILED, 0, "page_cap_reached", null))
        val vm = createViewModel()

        val page = vm.state.value.pages.single()
        assertEquals(CapturePageStatus.CAP_REACHED, page.status)

        vm.onAction(NotebookCaptureAction.OnRetryPage(page.key))

        assertTrue(repository.retried.isEmpty())
        assertEquals(0, repository.createJobCalls)
    }

    @Test
    fun reviewEnabled_afterFirstDone() = runTest {
        val vm = createViewModel()
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2))))
        assertFalse(vm.state.value.canReview)

        val first = repository.uploaded.first().first
        repository.setPage(first, PageStatus.DONE, personCount = 4)

        assertTrue(vm.state.value.canReview)
        assertEquals(4, vm.state.value.reviewCount)

        vm.events.test {
            vm.onAction(NotebookCaptureAction.OnReviewClick)
            assertEquals(NotebookCaptureEvent.NavigateToReview("job-1"), awaitItem())
        }
    }

    @Test
    fun reviewClick_withoutDonePage_doesNothing() = runTest {
        val vm = createViewModel()
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))

        vm.events.test {
            vm.onAction(NotebookCaptureAction.OnReviewClick)
            expectNoEvents()
        }
    }

    @Test
    fun staff_cannotStart() = runTest {
        workshop.setSession(
            WorkshopSession(
                authUid = "staff-1",
                workshopUid = OWNER,
                role = StaffRole.STAFF,
                membershipStatus = MembershipStatus.ACTIVE,
            )
        )
        val vm = NotebookCaptureViewModel(repository, workshop, FakeEntitlements(), newUploader()) { now() }

        vm.events.test {
            backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
            assertEquals(NotebookCaptureEvent.NavigateBack, awaitItem())
        }

        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(0, repository.createJobCalls)
        assertTrue(repository.uploaded.isEmpty())
    }

    @Test
    fun navigateBack_emitsEvent() = runTest {
        val vm = createViewModel()
        vm.events.test {
            vm.onAction(NotebookCaptureAction.OnNavigateBack)
            assertEquals(NotebookCaptureEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun recreatedViewModel_seesPagesQueuedByEarlierOne() = runTest {
        val uploader = newUploader()
        repository.uploadGate = CompletableDeferred()
        val first = createViewModel(uploader)
        first.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1), byteArrayOf(2))))

        // The tailor leaves and comes back while the first upload is still in flight.
        val second = createViewModel(uploader)
        assertEquals(2, second.state.value.pages.size)
        assertTrue(second.state.value.pages.all { it.status == CapturePageStatus.UPLOADING })

        repository.uploadGate!!.complete(Unit)

        assertEquals(2, repository.uploaded.size)
        assertEquals(1, repository.createJobCalls)
        assertEquals(listOf(1, 2), second.state.value.pages.map { it.number })
        assertTrue(second.state.value.pages.all { it.status == CapturePageStatus.READING })
    }

    // --- Open-job lookup (resume) ---

    @Test
    fun lookupTimeout_showsTryAgainAndNeverStartsANewJob() = runTest {
        repository.openJobsHang = true
        val vm = createViewModel()
        assertEquals(ResumeLookup.LOADING, vm.state.value.lookup)
        assertFalse(vm.state.value.canCapture)

        advanceTimeBy(3_001)

        assertEquals(ResumeLookup.FAILED, vm.state.value.lookup)
        assertFalse(vm.state.value.canCapture)
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(0, repository.createJobCalls)
        assertTrue(repository.uploaded.isEmpty())

        // Try again: the lookup now answers with a job that has pages, which is resumed.
        repository.openJobsHang = false
        repository.openJobs.value = listOf(job())
        repository.pages.value = listOf(page("p1000000000001_aaaaaaaa"))
        vm.onAction(NotebookCaptureAction.OnRetryLookup)

        assertEquals(ResumeLookup.READY, vm.state.value.lookup)
        assertTrue(vm.state.value.canCapture)
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(0, repository.createJobCalls)
        assertEquals(1, repository.uploaded.size)
    }

    @Test
    fun lookupError_showsTryAgainAndNeverStartsANewJob() = runTest {
        repository.openJobsError = DataError.Network.UNKNOWN
        val vm = createViewModel()

        assertEquals(ResumeLookup.FAILED, vm.state.value.lookup)
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(0, repository.createJobCalls)

        repository.openJobsError = null
        vm.onAction(NotebookCaptureAction.OnRetryLookup)
        assertEquals(ResumeLookup.READY, vm.state.value.lookup)
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(1, repository.createJobCalls)
    }

    @Test
    fun resume_skipsEmptyNewestJob_resumesNewestWithPages() = runTest {
        repository.openJobs.value = listOf(job("job-empty", ageMillis = MINUTE), job("job-pages", ageMillis = DAY))
        repository.pagesByJob["job-empty"] = MutableStateFlow(emptyList())
        repository.pagesByJob["job-pages"] = MutableStateFlow(listOf(page("p1000000000001_aaaaaaaa")))
        val vm = createViewModel()

        assertEquals(1, vm.state.value.pages.size)
        vm.events.test {
            vm.onAction(NotebookCaptureAction.OnReviewClick)
            assertEquals(NotebookCaptureEvent.NavigateToReview("job-pages"), awaitItem())
        }
    }

    @Test
    fun resume_neverAdoptsAJobWhoseImagesWereDeleted() = runTest {
        repository.openJobs.value = listOf(job("job-swept", imagesDeletedAt = Instant.fromEpochMilliseconds(CAPTURE_NOW)))
        repository.pagesByJob["job-swept"] = MutableStateFlow(listOf(page("p1000000000001_aaaaaaaa")))
        val vm = createViewModel()

        assertTrue(vm.state.value.pages.isEmpty())
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(1, repository.createJobCalls)
    }

    @Test
    fun resume_neverAdoptsAJob7DaysOld() = runTest {
        repository.openJobs.value = listOf(job("job-old", ageMillis = 7 * DAY))
        repository.pagesByJob["job-old"] = MutableStateFlow(listOf(page("p1000000000001_aaaaaaaa")))
        val vm = createViewModel()

        assertTrue(vm.state.value.pages.isEmpty())
        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1))))
        assertEquals(1, repository.createJobCalls)
    }

    // --- Banner to an earlier scan ---

    @Test
    fun banner_earlierJobWithUnsavedCards_linksToItsReview() = runTest {
        repository.openJobs.value = listOf(job("job-old", ageMillis = 8 * DAY))
        repository.pagesByJob["job-old"] = MutableStateFlow(listOf(page("p1000000000001_aaaaaaaa")))
        repository.cardsByJob["job-old"] = MutableStateFlow(
            listOf(reviewCard("c1", status = CardStatus.SAVED), reviewCard("c2", status = CardStatus.CHECKED)),
        )
        val vm = createViewModel()

        assertEquals("job-old", vm.state.value.earlierJobId)
        vm.events.test {
            vm.onAction(NotebookCaptureAction.OnEarlierScanReviewClick)
            assertEquals(NotebookCaptureEvent.NavigateToReview("job-old"), awaitItem())
        }

        // Once every card there is saved or skipped the banner goes.
        repository.cardsByJob.getValue("job-old").value =
            listOf(reviewCard("c1", status = CardStatus.SAVED), reviewCard("c2", status = CardStatus.SKIPPED))
        assertNull(vm.state.value.earlierJobId)
    }

    @Test
    fun banner_notShownForTheCurrentJob() = runTest {
        repository.openJobs.value = listOf(job("job-open"))
        repository.pagesByJob["job-open"] = MutableStateFlow(listOf(page("p1000000000001_aaaaaaaa")))
        repository.cardsByJob["job-open"] = MutableStateFlow(listOf(reviewCard("c1")))
        val vm = createViewModel()

        assertEquals(1, vm.state.value.pages.size)
        assertNull(vm.state.value.earlierJobId)
    }

    // --- Stuck pages and retry limit ---

    @Test
    fun stuckProcessing_over10Minutes_offersTryAgain() = runTest {
        repository.openJobs.value = listOf(job())
        repository.pages.value = listOf(
            ImportPage("p1", PageStatus.PROCESSING, 0, null, null, Instant.fromEpochMilliseconds(CAPTURE_NOW - 11 * MINUTE)),
        )
        val vm = createViewModel()

        val page = vm.state.value.pages.single()
        assertEquals(CapturePageStatus.READ_STUCK, page.status)
        vm.onAction(NotebookCaptureAction.OnRetryPage(page.key))
        assertEquals(listOf("job-open" to "p1"), repository.retried)
    }

    @Test
    fun processing_turnsStuckOnceTenMinutesPass() = runTest {
        repository.openJobs.value = listOf(job())
        repository.pages.value = listOf(
            ImportPage("p1", PageStatus.PROCESSING, 0, null, null, Instant.fromEpochMilliseconds(CAPTURE_NOW - 9 * MINUTE)),
        )
        val vm = createViewModel()
        assertEquals(CapturePageStatus.READING, vm.state.value.pages.single().status)
        vm.onAction(NotebookCaptureAction.OnRetryPage("p1"))
        assertTrue(repository.retried.isEmpty())

        advanceTimeBy(2 * MINUTE)

        assertEquals(CapturePageStatus.READ_STUCK, vm.state.value.pages.single().status)
    }

    @Test
    fun retryLimit_showsTryAClearerPhoto() = runTest {
        repository.openJobs.value = listOf(job())
        repository.pages.value = listOf(ImportPage("p1", PageStatus.FAILED, 0, "read_failed", null))
        repository.retryError = DataError.Network.TOO_MANY_REQUESTS
        val vm = createViewModel()

        vm.onAction(NotebookCaptureAction.OnRetryPage("p1"))

        val message = assertIs<UiText.StringResourceText>(vm.state.value.errorMessage)
        assertEquals(Res.string.notebook_capture_retry_limit, message.id)
    }

    // --- Compression ---

    @Test
    fun compressionFails_pageFailedAndRawPhotoNeverUploaded() = runTest {
        compressor.fail = true
        val vm = createViewModel()

        vm.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(byteArrayOf(1, 2, 3))))

        assertTrue(repository.uploaded.isEmpty())
        assertEquals(CapturePageStatus.UPLOAD_FAILED, vm.state.value.pages.single().status)
        val message = assertIs<UiText.StringResourceText>(vm.state.value.errorMessage)
        assertEquals(Res.string.notebook_capture_photo_unreadable, message.id)
    }
}
