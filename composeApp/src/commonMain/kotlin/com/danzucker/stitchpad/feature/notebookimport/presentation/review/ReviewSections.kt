package com.danzucker.stitchpad.feature.notebookimport.presentation.review

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.DuplicateMatcher
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.MatchResolution
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import com.danzucker.stitchpad.feature.notebookimport.domain.isDone
import com.danzucker.stitchpad.feature.notebookimport.domain.isReadyToCheck
import com.danzucker.stitchpad.feature.notebookimport.domain.resolveMatch

/** Same-person groups the tailor kept separate on this screen (in memory only). */
data class ReviewDismissals(val groups: Set<Set<String>> = emptySet())

data class ReviewSections(
    val needLook: List<ReviewRow>,
    val ready: List<ReviewRow>,
    val saved: List<ReviewRow>,
    val skipped: List<ReviewRow>,
    /**
     * Cards behind [ready], in the same order: what "Save all checked" saves. A confirmed match whose
     * customer no longer exists is already cleared from them.
     */
    val readyCards: List<ReviewCard>,
)

/** 1-based page number in upload order (page ids sort in upload order); 0 when the page is unknown. */
fun pageNumbers(pages: List<ImportPage>): Map<String, Int> =
    pages.map { it.id }.sorted().withIndex().associate { (index, id) -> id to index + 1 }

/**
 * Splits the cards into the review sections.
 * - "Need a look": not saved or skipped, and any of: still NEW with needsReview set; not ready to check
 *   (no gender, no name, an unclear or blank value); an existing-customer suggestion the tailor has not
 *   answered; part of a same-person group still waiting for Combine or Keep separate.
 * - "Ready": the other active cards (CHECKED, or NEW cards the server read cleanly). Their values are
 *   shown in full in the list, which is their review.
 * [customers] is null until the customer list has loaded; matches are not judged before then (and the
 * screen does not allow saving).
 */
fun buildReviewSections(
    cards: List<ReviewCard>,
    pageNumbers: Map<String, Int>,
    customers: List<Customer>?,
    country: Country,
    dismissals: ReviewDismissals,
): ReviewSections {
    val active = cards.filterNot { it.isDone }
    val prompts = duplicatePrompts(active, pageNumbers, dismissals)
    val inPendingGroup = prompts.values.flatMap { it.cardIds }.toSet()
    val resolutions = active.associate { card ->
        card.id to (customers?.let { resolveMatch(card, it, country) } ?: MatchResolution(card, null, false))
    }
    fun row(card: ReviewCard): ReviewRow {
        val resolution = resolutions[card.id]
        return ReviewRow(
            cardId = card.id,
            name = card.name,
            pageNumber = pageNumbers[card.pageId] ?: 0,
            gender = card.gender,
            unit = card.unit,
            values = card.values,
            unclearCount = card.values.count { it.flagged || it.value.isBlank() },
            match = resolution?.toSuggestion(),
            duplicate = prompts[card.id],
        )
    }
    val (needLookCards, readyCards) = active.partition { card ->
        card.needsLook() || resolutions.getValue(card.id).unresolved || card.id in inPendingGroup
    }
    return ReviewSections(
        needLook = needLookCards.map(::row),
        ready = readyCards.map(::row),
        saved = cards.filter { it.status == CardStatus.SAVED }.map(::row),
        skipped = cards.filter { it.status == CardStatus.SKIPPED }.map(::row),
        readyCards = readyCards.map { resolutions.getValue(it.id).card },
    )
}

private fun ReviewCard.needsLook(): Boolean =
    (status == CardStatus.NEW && needsReview) || !isReadyToCheck()

/** Shown when confirmed, or while the suggestion waits for an answer; hidden once kept as new. */
private fun MatchResolution.toSuggestion(): MatchSuggestion? {
    val customer = customer ?: return null
    return when {
        confirmed -> MatchSuggestion(customer.id, customer.name, confirmed = true)
        unresolved -> MatchSuggestion(customer.id, customer.name, confirmed = false)
        else -> null
    }
}

/** One prompt per same-person group, on the group's first card. */
private fun duplicatePrompts(
    active: List<ReviewCard>,
    pageNumbers: Map<String, Int>,
    dismissals: ReviewDismissals,
): Map<String, DuplicatePrompt> {
    val byId = active.associateBy { it.id }
    return DuplicateMatcher.groupWithinImport(active)
        .filter { it.toSet() !in dismissals.groups }
        .associate { ids ->
            val group = ids.mapNotNull { byId[it] }
            ids.first() to DuplicatePrompt(
                cardIds = ids,
                otherName = group.getOrNull(1)?.name.orEmpty(),
                pageNumbers = group.map { pageNumbers[it.pageId] ?: 0 },
            )
        }
}
