package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer

/**
 * Where a card stands against the tailor's existing customers.
 * [card] is the card to show and save: a confirmed match whose customer no longer exists is cleared
 * from it, so nothing is ever saved under a missing id. [customer] is the confirmed customer, or the
 * matcher's single suggestion. [candidates] are the same-name customers when the matcher could not tell
 * which one it is. [unresolved] is true while a suggestion or a choice of candidates waits for the tailor.
 */
data class MatchResolution(
    val card: ReviewCard,
    val customer: Customer?,
    val confirmed: Boolean,
    val candidates: List<Customer> = emptyList(),
) {
    val unresolved: Boolean get() = (customer != null || candidates.isNotEmpty()) && !confirmed && !card.keepAsNew

    /** Several possible customers; the tailor must pick one or save as new. */
    val ambiguous: Boolean get() = candidates.isNotEmpty() && !confirmed
}

/** [customers] must be the loaded list: an empty placeholder would make every confirmed match look stale. */
fun resolveMatch(card: ReviewCard, customers: List<Customer>, country: Country): MatchResolution {
    val confirmedId = card.matchedCustomerId
    val confirmed = confirmedId?.let { id -> customers.find { it.id == id } }
    if (confirmed != null) return MatchResolution(card, confirmed, confirmed = true)
    val cleared = if (confirmedId != null) card.copy(matchedCustomerId = null) else card
    return when (val found = DuplicateMatcher.findExisting(cleared, customers, country)) {
        ExistingMatch.None -> MatchResolution(cleared, null, confirmed = false)
        is ExistingMatch.Match -> MatchResolution(cleared, found.customer, confirmed = false)
        is ExistingMatch.Ambiguous -> MatchResolution(cleared, null, confirmed = false, candidates = found.candidates)
    }
}
