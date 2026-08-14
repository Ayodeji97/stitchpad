package com.danzucker.stitchpad.feature.tutorials.data

import com.danzucker.stitchpad.core.logging.AppLogger
import com.danzucker.stitchpad.feature.tutorials.domain.TutorialUriResolver
import com.danzucker.stitchpad.feature.tutorials.domain.model.Tutorial
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "TutorialMediaResolver"

/**
 * Turns a [Tutorial] into a playable URI: a cached local file when available, otherwise the
 * Firebase Storage download URL resolved on demand (storage paths are stored in Firestore, not
 * token URLs, because download tokens rotate). On a cache miss it streams the remote URL AND
 * kicks off a background download so the next watch is offline. Returns null only if the URL
 * can't be resolved (caller shows an error + retry).
 */
class TutorialMediaResolver(
    private val storage: FirebaseStorage,
    private val cache: TutorialVideoCache,
    private val downloadScope: CoroutineScope,
) : TutorialUriResolver {
    override suspend fun resolvePlayableUri(tutorial: Tutorial): String? {
        cache.cachedUri(tutorial.id)?.let { return it }

        // try/catch with a cancellation rethrow, not runCatching: this suspend runs
        // in the player's viewModelScope, so closing the player mid-resolve must
        // cancel cleanly instead of masquerading as "URL resolution failed"
        // (codex, PR #360). The runCatching below stays — downloadScope is
        // app-lifetime, where cancellation only means process death.
        val remoteUrl = try {
            storage.reference.child(tutorial.storagePath).getDownloadUrl()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            AppLogger.e(tag = TAG, throwable = e) { "resolve url failed id=${tutorial.id}" }
            null
        }

        if (remoteUrl != null) {
            downloadScope.launch { runCatching { cache.download(tutorial.id, remoteUrl) } }
        }
        return remoteUrl
    }
}
