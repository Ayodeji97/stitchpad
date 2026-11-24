package com.danzucker.stitchpad.feature.notebookimport.domain

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.FakeCaptureRepository
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.RecordingCompressor
import com.danzucker.stitchpad.feature.notebookimport.presentation.capture.unconfinedAppScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeCaptureRepository()
        scope = unconfinedAppScope()
    }

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
        val uploader = NotebookPageUploader(repository, RecordingCompressor(), scope)

        uploader.submit("owner", listOf(byteArrayOf(1)))
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
        val uploader = NotebookPageUploader(repository, RecordingCompressor(), scope)
        uploader.submit("owner", listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)))

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
        val uploader = NotebookPageUploader(repository, RecordingCompressor(), scope)

        uploader.errors.test {
            uploader.submit("owner", listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3)))
            assertEquals(DataError.Network.NO_INTERNET, awaitItem())
            expectNoEvents()
        }
        assertEquals(3, uploader.pages.value.count { it.phase == UploadPhase.FAILED })
    }
}
