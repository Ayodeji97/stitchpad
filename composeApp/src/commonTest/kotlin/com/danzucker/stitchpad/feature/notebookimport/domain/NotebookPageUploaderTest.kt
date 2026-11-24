package com.danzucker.stitchpad.feature.notebookimport.domain

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.session.FakeActiveWorkshopProvider
import com.danzucker.stitchpad.core.domain.session.MembershipStatus
import com.danzucker.stitchpad.core.domain.session.StaffRole
import com.danzucker.stitchpad.core.domain.session.WorkshopSession
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.FakeCaptureRepository
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.FakeEntitlements
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.RecordingCompressor
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.unconfinedAppScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotebookPageUploaderTest {

    private lateinit var repository: FakeCaptureRepository
    private lateinit var scope: CoroutineScope
    private lateinit var workshop: FakeActiveWorkshopProvider

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeCaptureRepository()
        scope = unconfinedAppScope()
        workshop = FakeActiveWorkshopProvider(WorkshopSession.ownerOfSelf("owner"))
    }

    private fun newUploader() =
        NotebookPageUploader(repository, RecordingCompressor(), workshop, FakeEntitlements(), scope)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun upload_completesWithNoObserver() = runTest {
        // No ViewModel exists: the uploader alone finishes the work (the screen was left).
        val gate = CompletableDeferred<Unit>()
        repository.uploadGate = gate
        val uploader = newUploader()

        uploader.submit(listOf(byteArrayOf(1)))
        assertEquals(UploadPhase.UPLOADING, uploader.pages.value.single().phase)
        gate.complete(Unit)

        val page = uploader.pages.value.single()
        assertEquals(UploadPhase.UPLOADED, page.phase)
        assertNull(page.bytes)
        assertEquals(1, repository.uploaded.size)
    }

    @Test
    fun clear_dropsQueuedPagesOfFinishedJob() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.uploadGate = gate
        val uploader = newUploader()
        uploader.submit(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)))

        uploader.clear("job-1")

        assertTrue(uploader.pages.value.isEmpty())
        assertNull(uploader.jobId.value)
        gate.complete(Unit)
        // Only the page already in flight when the job finished was sent; queued ones were dropped.
        assertEquals(1, repository.uploaded.size)
    }

    @Test
    fun manyFailuresWithSameError_reportedOnce() = runTest {
        repository.uploadError = DataError.Network.NO_INTERNET
        val uploader = newUploader()

        uploader.errors.test {
            uploader.submit(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)))
            assertEquals(DataError.Network.NO_INTERNET, awaitItem())
            expectNoEvents()
        }
        assertEquals(3, uploader.pages.value.count { it.phase == UploadPhase.FAILED })
    }

    @Test
    fun signOut_midQueue_dropsRemainingAndClearsState() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.uploadGate = gate
        val uploader = newUploader()
        uploader.submit(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)))

        workshop.setSession(WorkshopSession.signedOut())
        gate.complete(Unit)

        // Only the upload already in flight when the user signed out could finish.
        assertEquals(1, repository.uploaded.size)
        assertTrue(uploader.pages.value.isEmpty())
        assertNull(uploader.jobId.value)
        assertNull(uploader.currentJobId())
    }

    @Test
    fun otherUser_seesNothingOfPreviousUser_andCannotRetryTheirBytes() = runTest {
        repository.uploadError = DataError.Network.NO_INTERNET
        val uploader = newUploader()
        uploader.submit(listOf(byteArrayOf(1)))
        assertEquals(UploadPhase.FAILED, uploader.pages.value.single().phase)
        val failedId = uploader.pages.value.single().id

        workshop.setSession(WorkshopSession.ownerOfSelf("other"))
        repository.uploadError = null

        assertTrue(uploader.pages.value.isEmpty())
        assertNull(uploader.currentJobId())
        uploader.retry(failedId)
        assertTrue(repository.uploaded.isEmpty())
        assertTrue(uploader.pages.value.isEmpty())
    }

    @Test
    fun gateFlipsFalse_midQueue_noFurtherUploads() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.uploadGate = gate
        val uploader = newUploader()
        uploader.submit(listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)))

        // Same uid, but no longer the owner.
        workshop.setSession(
            WorkshopSession("staff-1", "owner", StaffRole.STAFF, MembershipStatus.ACTIVE)
        )
        gate.complete(Unit)

        assertEquals(1, repository.uploaded.size)
        assertTrue(uploader.pages.value.isEmpty())
    }

    @Test
    fun concurrentSubmits_produceUniqueIds() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.uploadGate = gate
        val uploader = newUploader()

        coroutineScope {
            repeat(32) { launch(Dispatchers.Default) { uploader.submit(listOf(byteArrayOf(it.toByte()))) } }
        }

        val ids = uploader.pages.value.map { it.id }
        assertEquals(32, ids.size)
        assertEquals(32, ids.toSet().size)
        gate.complete(Unit)
    }
}
