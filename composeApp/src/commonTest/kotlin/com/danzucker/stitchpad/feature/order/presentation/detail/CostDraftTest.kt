package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.OrderCost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CostDraftTest {

    @Test
    fun validAmountsAreMappedToOrderCosts() {
        val draft = mapOf(
            CostCategory.FABRIC to "25000",
            CostCategory.LABOUR to "7000",
        )
        val result = orderCostsFromDraft(draft)
        assertEquals(
            setOf(
                OrderCost(CostCategory.FABRIC, 25_000.0),
                OrderCost(CostCategory.LABOUR, 7_000.0),
            ),
            result.toSet(),
        )
    }

    @Test
    fun blankStringIsDropped() {
        val draft = mapOf(
            CostCategory.FABRIC to "25000",
            CostCategory.OTHER to "",
        )
        val result = orderCostsFromDraft(draft)
        assertEquals(listOf(OrderCost(CostCategory.FABRIC, 25_000.0)), result)
    }

    @Test
    fun zeroAmountIsExcluded() {
        val draft = mapOf(
            CostCategory.FABRIC to "25000",
            CostCategory.OTHER to "0",
        )
        val result = orderCostsFromDraft(draft)
        assertEquals(listOf(OrderCost(CostCategory.FABRIC, 25_000.0)), result)
    }

    @Test
    fun nonDigitCharactersAreStrippedBeforeParsing() {
        val draft = mapOf(CostCategory.FABRIC to "₦25,000")
        val result = orderCostsFromDraft(draft)
        assertEquals(listOf(OrderCost(CostCategory.FABRIC, 25_000.0)), result)
    }

    @Test
    fun emptyMapProducesEmptyList() {
        val result = orderCostsFromDraft(emptyMap())
        assertTrue(result.isEmpty())
    }

    // Regression: the costs editor only drafts amounts (no note input), so saving must
    // carry forward each category's existing note rather than defaulting it to null and
    // silently wiping it. See orderCostsFromDraft KDoc / OrderDetailViewModel.saveCosts().
    @Test
    fun existingNoteIsPreservedForMatchingCategory() {
        val draft = mapOf(CostCategory.FABRIC to "30000")
        val existingNotes = mapOf(CostCategory.FABRIC to "Ankara from Balogun market")

        val result = orderCostsFromDraft(draft, existingNotes)

        assertEquals(
            listOf(OrderCost(CostCategory.FABRIC, 30_000.0, note = "Ankara from Balogun market")),
            result,
        )
    }

    @Test
    fun categoryWithNoExistingNoteStaysNull() {
        val draft = mapOf(CostCategory.LABOUR to "7000")
        val existingNotes = mapOf(CostCategory.FABRIC to "Ankara from Balogun market")

        val result = orderCostsFromDraft(draft, existingNotes)

        assertEquals(listOf(OrderCost(CostCategory.LABOUR, 7_000.0, note = null)), result)
    }
}
