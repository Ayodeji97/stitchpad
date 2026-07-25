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
 *
 * @param existingNotes The saved order's current per-category notes, keyed by [CostCategory].
 *                       The costs editor only ever drafts amounts (there's no note input in
 *                       that UI), so without carrying these forward, every save would rebuild
 *                       each [OrderCost] with `note = null` and silently wipe any note a cost
 *                       line already had (e.g. seeded/imported data, or a future note-entry
 *                       UI). Defaults to empty so existing call sites/tests are unaffected.
 */
internal fun orderCostsFromDraft(
    draft: Map<CostCategory, String>,
    existingNotes: Map<CostCategory, String?> = emptyMap(),
): List<OrderCost> =
    draft.mapNotNull { (category, digits) ->
        val amount = digits.filter(Char::isDigit).toDoubleOrNull() ?: return@mapNotNull null
        if (amount <= 0.0) null else OrderCost(category, amount, note = existingNotes[category])
    }
