package com.danzucker.stitchpad.core.config.data

import com.danzucker.stitchpad.core.config.domain.CommunityJoinTracker
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val TAG = "CommunityJoinTracker"

@OptIn(ExperimentalTime::class)
class FirebaseCommunityJoinTracker(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : CommunityJoinTracker {

    override suspend fun trackJoinTapped() {
        val uid = auth.currentUser?.uid ?: return
        runCatching {
            firestore.collection("users").document(uid).update(
                "communityJoinTappedAt" to nowMillis(),
                "communityJoinTapCount" to FieldValue.increment(1),
            )
        }.onFailure { throwable ->
            AppLogger.e(tag = TAG, throwable = throwable) { "trackJoinTapped failed" }
        }
    }
}
