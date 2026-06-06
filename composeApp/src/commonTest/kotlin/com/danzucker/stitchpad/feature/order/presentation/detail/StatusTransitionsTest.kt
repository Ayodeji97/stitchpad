package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTransitionsTest {

    private val allStages = listOf(
        StatusTransition(OrderStatus.PENDING, null),
        StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
        StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
        StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
        StatusTransition(OrderStatus.READY, null),
        StatusTransition(OrderStatus.DELIVERED, null),
    )

    @Test
    fun pendingShowsEveryOtherStageInForwardOrder() {
        val moves = allStatusTransitions(OrderStatus.PENDING, null)
        assertEquals(allStages.drop(1), moves)
    }

    @Test
    fun cuttingShowsAllStagesExceptCutting() {
        val moves = allStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING)
        assertEquals(allStages.filterNot { it.toSubStatus == OrderSubStatus.CUTTING }, moves)
        // Includes the back-move to Pending and forward to Delivered.
        assertEquals(true, moves.contains(StatusTransition(OrderStatus.PENDING, null)))
        assertEquals(true, moves.contains(StatusTransition(OrderStatus.DELIVERED, null)))
    }

    @Test
    fun fittingShowsAllStagesExceptFitting() {
        val moves = allStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING)
        assertEquals(allStages.filterNot { it.toSubStatus == OrderSubStatus.FITTING }, moves)
    }

    @Test
    fun readyShowsAllStagesExceptReady() {
        val moves = allStatusTransitions(OrderStatus.READY, null)
        assertEquals(allStages.filterNot { it == StatusTransition(OrderStatus.READY, null) }, moves)
    }

    @Test
    fun deliveredStillShowsEveryEarlierStage() {
        // PTSP-28: a delivered order must still be able to move back, so the
        // sheet is non-empty (it was empty under the old next-only picker).
        val moves = allStatusTransitions(OrderStatus.DELIVERED, null)
        assertEquals(allStages.dropLast(1), moves)
    }

    @Test
    fun inProgressWithNullSubStatusBehavesAsCutting() {
        // Legacy data: status = IN_PROGRESS but no subStatus persisted.
        // Treat as CUTTING so the current stage is excluded correctly.
        val moves = allStatusTransitions(OrderStatus.IN_PROGRESS, null)
        assertEquals(
            allStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
            moves,
        )
    }
}
