package com.danzucker.stitchpad.feature.auth.domain

/** No-op [RemoteSyncGate] for tests that don't assert on stream gating. */
class NoOpRemoteSyncGate : RemoteSyncGate {
    override suspend fun quiesce() = Unit
    override suspend fun resume() = Unit
}
