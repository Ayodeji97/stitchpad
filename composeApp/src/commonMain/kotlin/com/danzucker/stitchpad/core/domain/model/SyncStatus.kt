package com.danzucker.stitchpad.core.domain.model

/**
 * Whether local data has reached the server yet.
 *
 * The app is offline-first by design ([com.danzucker.stitchpad.core.offline.OfflineWriteDispatcher]
 * deliberately does not await server acknowledgement, so forms never hang in airplane
 * mode). This enum exists so that choice is visible to the user rather than silent.
 */
enum class SyncStatus {
    /** Everything the app has is on the server. */
    SYNCED,

    /** Connected, but at least one local write has not been acknowledged yet. */
    SYNCING,

    /** Not reaching Firestore. Reads are served from cache; writes are queued locally. */
    OFFLINE,
}
