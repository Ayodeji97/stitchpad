package com.danzucker.stitchpad.feature.dashboard.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineSummaryRowTest {

    @Test
    fun `both totals positive shows both segments in order`() {
        assertEquals(
            listOf(PipelineSummarySegment.InProgress, PipelineSummarySegment.NotStarted),
            pipelineSummarySegments(inProgressTotal = 3, notStartedTotal = 2),
        )
    }

    @Test
    fun `only in-progress shows only in-progress segment`() {
        assertEquals(
            listOf(PipelineSummarySegment.InProgress),
            pipelineSummarySegments(inProgressTotal = 4, notStartedTotal = 0),
        )
    }

    @Test
    fun `only not-started shows only not-started segment`() {
        assertEquals(
            listOf(PipelineSummarySegment.NotStarted),
            pipelineSummarySegments(inProgressTotal = 0, notStartedTotal = 5),
        )
    }

    @Test
    fun `both zero shows no segments`() {
        assertEquals(
            emptyList(),
            pipelineSummarySegments(inProgressTotal = 0, notStartedTotal = 0),
        )
    }
}
