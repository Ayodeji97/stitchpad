package com.danzucker.stitchpad.feature.dashboard.domain

import com.danzucker.stitchpad.feature.dashboard.domain.model.PipelineStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PipelineStageTest {

    @Test
    fun previousStepsBackwardAndStopsAtPending() {
        assertEquals(PipelineStage.FITTING, PipelineStage.READY.previous())
        assertNull(PipelineStage.PENDING.previous())
    }
}
