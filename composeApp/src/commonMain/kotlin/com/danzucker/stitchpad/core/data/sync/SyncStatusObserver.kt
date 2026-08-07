package com.danzucker.stitchpad.core.data.sync

import com.danzucker.stitchpad.core.domain.model.SyncStatus
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val USERS = "users"

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
            // Fail to invisible. Showing a possibly-false "Offline" is worse than
            // showing nothing, and hiding preserves today's behaviour exactly.
            .catch { emit(SyncStatus.SYNCED) }

    private companion object {
        const val OFFLINE_DEBOUNCE_MS = 2_000L
    }
}
