package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.OrderStatus
import com.danzucker.stitchpad.core.domain.model.OrderSubStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTransitionsTest {

    @Test
    fun pendingOffersAllForwardStages() {
        val moves = nextStatusTransitions(OrderStatus.PENDING, null)
        assertEquals(
            listOf(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
            ),
            moves,
        )
    }

    @Test
    fun cuttingOffersForwardPlusBackToPending() {
        val moves = nextStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING)
        assertEquals(
            listOf(
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.PENDING, null),
            ),
            moves,
        )
    }

    @Test
    fun fittingOffersReadyDeliveredAndBackToSewing() {
        val moves = nextStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING)
        assertEquals(
            listOf(
                StatusTransition(OrderStatus.READY, null),
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.SEWING),
            ),
            moves,
        )
    }

    @Test
    fun readyOffersDeliveredPlusBackToFitting() {
        val moves = nextStatusTransitions(OrderStatus.READY, null)
        assertEquals(
            listOf(
                StatusTransition(OrderStatus.DELIVERED, null),
                StatusTransition(OrderStatus.IN_PROGRESS, OrderSubStatus.FITTING),
            ),
            moves,
        )
    }

    @Test
    fun deliveredOffersNoMoves() {
        val moves = nextStatusTransitions(OrderStatus.DELIVERED, null)
        assertEquals(emptyList(), moves)
    }

    @Test
    fun inProgressWithNullSubStatusBehavesAsCutting() {
        // Legacy data: status = IN_PROGRESS but no subStatus persisted.
        // Treat as CUTTING for the picker so the user can move forward.
        val moves = nextStatusTransitions(OrderStatus.IN_PROGRESS, null)
        assertEquals(
            nextStatusTransitions(OrderStatus.IN_PROGRESS, OrderSubStatus.CUTTING),
            moves,
        )
    }
}
