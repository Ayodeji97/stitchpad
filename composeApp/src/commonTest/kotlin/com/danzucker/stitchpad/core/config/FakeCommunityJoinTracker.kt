package com.danzucker.stitchpad.core.config

import com.danzucker.stitchpad.core.config.domain.CommunityJoinTracker

class FakeCommunityJoinTracker : CommunityJoinTracker {
    var tapCount: Int = 0
        private set

    override suspend fun trackJoinTapped() {
        tapCount++
    }
}
