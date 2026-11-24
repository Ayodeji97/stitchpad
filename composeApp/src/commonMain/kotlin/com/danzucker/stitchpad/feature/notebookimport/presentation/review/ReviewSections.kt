package com.danzucker.stitchpad.feature.notebookimport.presentation.review

import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.core.domain.model.Customer
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.DuplicateMatcher
import com.danzucker.stitchpad.feature.notebookimport.domain.ImportPage
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import com.danzucker.stitchpad.feature.notebookimport.domain.isDone
import com.danzucker.stitchpad.feature.notebookimport.domain.isReadyToCheck

/** What the tailor dismissed on this screen. Kept in memory only: a suggestion is never written anywhere. */
data class ReviewDismissals(
    val matches: Set<String> = emptySet(),
    val groups: Set<Set<String>> = emptySet(),
)

data class ReviewSections(
    val needLook: List<ReviewRow>,
    val ready: List<ReviewRow>,
    val saved: List<ReviewRow>,
    val skipped: List<ReviewRow>,
    /** Cards behind [ready], in the same order: what "Save all checked" saves. */
    val readyCards: List<ReviewCard>,
)

/** 1-based page number in upload order (page ids sort in upload order); 0 when the page is unknown. */
fun pageNumbers(pages: List<ImportPage>): Map<String, Int> =
    pages.map { it.id }.sorted().withIndex().associate { (index, id) -> id to index + 1 }

/**
 * Splits the cards into the review sections.
 * - "Need a look": not saved or skipped, and either still NEW with needsReview set, or not ready to
 *   check (no gender, no name, an unclear or blank value).
 * - "Ready": CHECKED cards, plus NEW cards the server read cleanly (needsReview false) and that are
 *   ready to check. Their values are shown in full in the list, which is their review.
 * Match suggestions come from [DuplicateMatcher.findExisting] and stay suggestions until confirmed.
 */
fun buildReviewSections(
    cards: List<ReviewCard>,
    pageNumbers: Map<String, Int>,
    customers: List<Customer>,
    country: Country,
    dismissals: ReviewDismissals,
): ReviewSections {
    val active = cards.filterNot { it.isDone }
    val prompts = duplicatePrompts(active, pageNumbers, dismissals)
    fun row(card: ReviewCard) = ReviewRow(
        cardId = card.id,
        name = card.name,
        pageNumber = pageNumbers[card.pageId] ?: 0,
        gender = card.gender,
        unit = card.unit,
        values = card.values,
        unclearCount = card.values.count { it.flagged || it.value.isBlank() },
        match = if (card.isDone) null else matchFor(card, customers, country, dismissals),
        duplicate = prompts[card.id],
    )
    val (needLookCards, readyCards) = active.partition { it.needsLook() }
    return ReviewSections(
        needLook = needLookCards.map(::row),
        ready = readyCards.map(::row),
        saved = cards.filter { it.status == CardStatus.SAVED }.map(::row),
        skipped = cards.filter { it.status == CardStatus.SKIPPED }.map(::row),
        readyCards = readyCards,
    )
}

private fun ReviewCard.needsLook(): Boolean =
    (status == CardStatus.NEW && needsReview) || !isReadyToCheck()

@Suppress("ReturnCount")
private fun matchFor(
    card: ReviewCard,
    customers: List<Customer>,
    country: Country,
    dismissals: ReviewDismissals,
): MatchSuggestion? {
    val confirmedId = card.matchedCustomerId
    if (confirmedId != null) {
        val name = customers.find { it.id == confirmedId }?.name ?: card.name
        return MatchSuggestion(confirmedId, name, confirmed = true)
    }
    if (card.id in dismissals.matches) return null
    val hit = DuplicateMatcher.findExisting(card, customers, country) ?: return null
    return MatchSuggestion(hit.id, hit.name, confirmed = false)
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
