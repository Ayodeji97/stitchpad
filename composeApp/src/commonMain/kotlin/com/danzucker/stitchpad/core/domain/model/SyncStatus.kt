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

    /**
     * Connected, but the watched user document ([com.danzucker.stitchpad.core.data.sync.SyncStatusObserver]
     * only observes `users/{workshopUid}`) has a local write that has not been
     * acknowledged yet.
     *
     * This is scoped to that single document, not the app's data as a whole:
     * `hasPendingWrites` on a `DocumentSnapshot` never reflects writes to
     * subcollections, so this fires for direct writes to the user doc itself
     * (branding, push token, workshop setup) and never for the tailor's
     * customer or order writes, which are the ones that matter day to day.
     * Per-record pending state for those is carried separately by
     * `isPendingSync` on [Customer] / [Order] and surfaced via the row badge,
     * not this status.
     */
    SYNCING,

    /** Not reaching Firestore. Reads are served from cache; writes are queued locally. */
    OFFLINE,
}
