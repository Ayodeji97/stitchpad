package com.danzucker.stitchpad.core.data.sync

import com.danzucker.stitchpad.core.domain.model.SyncStatus
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

private const val USERS = "users"
private const val TAG = "SyncStatusObserver"

/**
 * Emits the workshop's [SyncStatus] by watching snapshot metadata on the user document.
 *
 * Deliberately reuses the user doc rather than opening a dedicated listener: that
 * document is already observed for every signed-in user, so this costs no extra
 * Firestore connection. `includeMetadataChanges = true` is what makes metadata-only
 * transitions (a queued write being acknowledged) arrive at all.
 */
class SyncStatusObserver(
    private val firestore: FirebaseFirestore,
) {

    fun observe(userId: String): Flow<SyncStatus> =
        firestore.collection(USERS).document(userId)
            .snapshots(includeMetadataChanges = true)
            .map { snapshot ->
                syncStatusOf(
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites,
                )
            }
            // distinctUntilChanged BEFORE debounceOffline: debounceOffline restarts its
            // delay window on every OFFLINE element it receives (it's built on
            // transformLatest), not just the first of a run. With
            // includeMetadataChanges = true, Firestore can emit repeated OFFLINE
            // snapshots while disconnected; collapsing duplicates first ensures the
            // delay timer starts once per genuine transition instead of being
            // perpetually restarted and never firing.
            .distinctUntilChanged()
            .debounceOffline(OFFLINE_DEBOUNCE_MS)
            // A transient listener error (token refresh, a rules deploy, a quota
            // blip) should not permanently freeze the banner: retryWhen resubscribes
            // to the whole chain above with a short, capped backoff before we give
            // up. Confirmed non-transient case: staff accounts can never read
            // users/{ownerUid} (firestore.rules:146 is `allow read: if isOwner(uid)`),
            // so this fires on every collection for every staff user — logged below
            // so that is visible instead of silent.
            .retryWhen { cause, attempt ->
                val shouldRetry = attempt < MAX_RETRY_ATTEMPTS
                AppLogger.w(tag = TAG, throwable = cause) {
                    "observe(userId=$userId) failed" +
                        if (shouldRetry) {
                            ", retrying (attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS)"
                        } else {
                            ", giving up after $MAX_RETRY_ATTEMPTS attempts"
                        }
                }
                if (shouldRetry) {
                    delay(RETRY_BACKOFF_MS * (attempt + 1))
                }
                shouldRetry
            }
            // Fail to invisible. Showing a possibly-false "Offline" is worse than
            // showing nothing, and hiding preserves today's behaviour exactly.
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) {
                    "observe(userId=$userId) exhausted retries; hiding banner"
                }
                emit(SyncStatus.SYNCED)
            }

    private companion object {
        const val OFFLINE_DEBOUNCE_MS = 2_000L
        const val MAX_RETRY_ATTEMPTS = 3L
        const val RETRY_BACKOFF_MS = 500L
    }
}
