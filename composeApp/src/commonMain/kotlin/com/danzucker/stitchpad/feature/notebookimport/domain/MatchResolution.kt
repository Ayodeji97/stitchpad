package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer

/**
 * Where a card stands against the tailor's existing customers.
 * [card] is the card to show and save: a confirmed match whose customer no longer exists is cleared
 * from it, so nothing is ever saved under a missing id. [customer] is the confirmed customer, or the
 * matcher's suggestion. [unresolved] is true while a suggestion waits for the tailor's choice.
 */
data class MatchResolution(
    val card: ReviewCard,
    val customer: Customer?,
    val confirmed: Boolean,
) {
    val unresolved: Boolean get() = customer != null && !confirmed && !card.keepAsNew
}

/** [customers] must be the loaded list: an empty placeholder would make every confirmed match look stale. */
fun resolveMatch(card: ReviewCard, customers: List<Customer>, country: Country): MatchResolution {
    val confirmedId = card.matchedCustomerId
    val confirmed = confirmedId?.let { id -> customers.find { it.id == id } }
    if (confirmed != null) return MatchResolution(card, confirmed, confirmed = true)
    val cleared = if (confirmedId != null) card.copy(matchedCustomerId = null) else card
    return MatchResolution(cleared, DuplicateMatcher.findExisting(cleared, customers, country), confirmed = false)
}
