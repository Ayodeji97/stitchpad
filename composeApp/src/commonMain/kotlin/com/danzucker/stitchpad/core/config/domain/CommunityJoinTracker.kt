package com.danzucker.stitchpad.core.config.domain

/**
 * Records that the signed-in user tapped "Join community". Implementations
 * MUST be safe to fire-and-forget — callers launch this without awaiting and
 * never block UI on it (the underlying Firestore write suspends until ACK).
 */
interface CommunityJoinTracker {
    suspend fun trackJoinTapped()
}
