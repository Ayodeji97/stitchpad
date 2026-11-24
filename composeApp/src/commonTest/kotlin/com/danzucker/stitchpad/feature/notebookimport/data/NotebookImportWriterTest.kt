package com.danzucker.stitchpad.feature.notebookimport.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class HangingRemote : NotebookImportRemote {
    val never = CompletableDeferred<Unit>()
    val statuses = mutableListOf<String>()
    var imagesDeleted = false
    var updateCalls = 0

    override suspend fun createJobDoc(ownerUid: String): String {
        never.await()
        return "unreachable"
    }

    override suspend fun updateCardDoc(ownerUid: String, card: ReviewCard) {
        updateCalls++
        never.await()
    }

    override suspend fun setJobStatus(ownerUid: String, jobId: String, status: String) {
        statuses += status
    }

    override suspend fun deleteJobImages(ownerUid: String, jobId: String) {
        never.await()
        imagesDeleted = true
    }

    override suspend fun deleteCardDoc(ownerUid: String, jobId: String, cardId: String) {
        never.await()
    }
}

class NotebookImportWriterTest {
    private fun TestScope.appScope() = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())

    private val card = ReviewCard(
        id = "c", jobId = "j", pageId = "p", name = "n", phone = null, gender = CustomerGender.MALE,
        unit = MeasurementUnit.CM, dateWritten = null, values = emptyList(), box = null,
        needsReview = false, status = CardStatus.CHECKED, matchedCustomerId = null, savedCustomerId = null,
    )

    @Test
    fun createJob_timeoutIsNoInternet() = runTest {
        val writer = NotebookImportWriter(HangingRemote(), OfflineWriteDispatcher(appScope()))
        assertEquals(Result.Error(DataError.Network.NO_INTERNET), writer.createJob("u"))
    }

    @Test
    fun updateCard_returnsPromptlyWhileRemoteHangs() = runTest {
        val remote = HangingRemote()
        val writer = NotebookImportWriter(remote, OfflineWriteDispatcher(appScope()))
        assertEquals(Result.Success(Unit), writer.updateCard("u", card))
        advanceUntilIdle()
        assertEquals(1, remote.updateCalls)
    }

    @Test
    fun finishJob_abandoned_cleanupTimesOutButStatusStillWritten() = runTest {
        val remote = HangingRemote()
        val writer = NotebookImportWriter(remote, OfflineWriteDispatcher(appScope()))
        assertEquals(Result.Success(Unit), writer.finishJob("u", "j", abandoned = true))
        advanceUntilIdle()
        assertEquals(listOf("abandoned"), remote.statuses)
        assertTrue(!remote.imagesDeleted)
    }

    @Test
    fun finishJob_saved_skipsImageCleanup() = runTest {
        val remote = HangingRemote()
        val writer = NotebookImportWriter(remote, OfflineWriteDispatcher(appScope()))
        writer.finishJob("u", "j", abandoned = false)
        advanceUntilIdle()
        assertEquals(listOf("saved"), remote.statuses)
    }
}
