@file:Suppress("MatchingDeclarationName")

package com.danzucker.stitchpad.feature.order.presentation.detail

import com.danzucker.stitchpad.core.domain.model.CostCategory
import com.danzucker.stitchpad.core.domain.model.OrderCost

/**
 * Pure mapper from the costs editor's in-progress draft to persistable [OrderCost] lines.
 *
 * Intentionally free of any Order / Firestore / coroutine dependency so the invariants
 * (digits-only parsing, blank-drop, zero-exclusion) can be unit-tested in isolation
 * without building an expect-class sharer fake for commonTest — mirrors [capPaymentDigits]
 * in PaymentMath.kt.
 */
internal fun orderCostsFromDraft(draft: Map<CostCategory, String>): List<OrderCost> =
    draft.mapNotNull { (category, digits) ->
        val amount = digits.filter(Char::isDigit).toDoubleOrNull() ?: return@mapNotNull null
        if (amount <= 0.0) null else OrderCost(category, amount)
    }
