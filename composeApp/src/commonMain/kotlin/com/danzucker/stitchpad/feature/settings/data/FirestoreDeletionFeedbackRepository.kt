package com.danzucker.stitchpad.feature.settings.data

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.settings.data.dto.DeletionFeedbackDto
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedback
import com.danzucker.stitchpad.feature.settings.domain.DeletionFeedbackRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TAG = "DeletionFeedbackRepo"
private const val COLLECTION = "account_deletion_feedback"

@OptIn(ExperimentalTime::class)
private fun defaultNowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

class FirestoreDeletionFeedbackRepository(
    private val firestore: FirebaseFirestore,
    private val nowEpochMs: () -> Long = ::defaultNowEpochMs,
) : DeletionFeedbackRepository {

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun submitFeedback(feedback: DeletionFeedback): EmptyResult<DataError.Network> {
        return try {
            val dto = DeletionFeedbackDto(
                reason = feedback.reason.analyticsKey,
                additionalNotes = feedback.additionalNotes?.takeIf { it.isNotBlank() },
                plan = feedback.plan,
                daysActive = feedback.daysActive,
                platform = feedback.platform,
                appVersion = feedback.appVersion,
                locale = feedback.locale,
                createdAtEpochMs = nowEpochMs(),
            )
            val docRef = firestore.collection(COLLECTION).document
            docRef.set(dto)
            Result.Success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "submitFeedback failed reason=${feedback.reason}" }
            Result.Error(DataError.Network.UNKNOWN)
        }
    }
}
