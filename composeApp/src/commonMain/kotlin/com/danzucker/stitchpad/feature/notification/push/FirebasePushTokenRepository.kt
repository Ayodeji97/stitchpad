package com.danzucker.stitchpad.feature.notification.push

import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore

class FirebasePushTokenRepository(
    private val firestore: FirebaseFirestore,
) : PushTokenRepository {

    private fun tokens(userId: String) =
        firestore.collection("users").document(userId).collection("notificationTokens")

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun registerToken(userId: String, token: String, platform: String) {
        // Fire-and-forget shape (call from a background scope) — GitLive set() awaits the
        // server ACK, so do NOT block UI on it.
        val data = mapOf(
            "platform" to platform,
            "updatedAt" to FieldValue.serverTimestamp,
        )
        tokens(userId).document(token).set(data, merge = true)
    }

    @Suppress("INLINE_FROM_HIGHER_PLATFORM")
    override suspend fun unregisterToken(userId: String, token: String) {
        tokens(userId).document(token).delete()
    }
}
