package com.danzucker.stitchpad.core.data.sync

import com.danzucker.stitchpad.core.domain.model.SyncStatus
import com.danzucker.stitchpad.core.logging.AppLogger
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
            // Survive listener failures rather than dying on them. This flow is
            // collected once, for the life of the main screen, and never resubscribed
            // — so a bounded retry would mean any outage outlasting the attempt
            // budget silently killed the banner for the rest of the session, which is
            // the very failure this feature exists to make visible. Each failure
            // emits SYNCED (fail to invisible: a possibly-false "Offline" is worse
            // than showing nothing) and then resubscribes after a capped backoff.
            //
            // Confirmed permanent case: staff accounts can never read
            // users/{ownerUid} (firestore.rules:146 is `allow read: if isOwner(uid)`),
            // so this fires for every staff user. It settles into one quiet retry per
            // MAX_RETRY_BACKOFF_MS and stays correctly invisible — logged, so it is
            // discoverable rather than silent if it ever starts affecting owners too.
            .retryWithFallback(
                fallback = SyncStatus.SYNCED,
                initialBackoffMs = INITIAL_RETRY_BACKOFF_MS,
                maxBackoffMs = MAX_RETRY_BACKOFF_MS,
            ) { cause, attempt ->
                AppLogger.w(tag = TAG, throwable = cause) {
                    "observe(userId=$userId) failed; hiding banner and retrying (attempt ${attempt + 1})"
                }
            }
            // Safety net only — retryWithFallback never completes exceptionally, so
            // this covers anything thrown outside the retried chain.
            .catch { throwable ->
                AppLogger.e(tag = TAG, throwable = throwable) {
                    "observe(userId=$userId) failed outside the retry path; hiding banner"
                }
                emit(SyncStatus.SYNCED)
            }

    private companion object {
        const val OFFLINE_DEBOUNCE_MS = 2_000L
        const val INITIAL_RETRY_BACKOFF_MS = 500L
        const val MAX_RETRY_BACKOFF_MS = 60_000L
    }
}
