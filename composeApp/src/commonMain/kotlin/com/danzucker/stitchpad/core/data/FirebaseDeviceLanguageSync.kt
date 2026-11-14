package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.core.sharing.deviceLanguageCode
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

@Serializable
private data class MessageLocaleDoc(val locale: String? = null)

/** Sync only the signed-in person's message language. Never creates a profile or touches country. */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseDeviceLanguageSync(
    auth: FirebaseAuth,
    firestore: FirebaseFirestore,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            auth.authStateChanged.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
                if (uid == null) flowOf(null) else firestore.collection("users").document(uid).snapshots
            }.retryWhen { _, _ ->
                delay(5_000L)
                true
            }.collect { snapshot ->
                if (snapshot == null || !snapshot.exists) return@collect
                val language = if (deviceLanguageCode().startsWith("fr", ignoreCase = true)) "fr" else "en"
                try {
                    if (snapshot.data<MessageLocaleDoc>().locale != language) {
                        snapshot.reference.update("locale" to language)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    AppLogger.w(tag = "DeviceLanguage", throwable = error) { "Message language sync failed" }
                }
            }
        }
    }

    fun ensureRunning() = Unit
}
