@file:Suppress("MatchingDeclarationName")

package com.danzucker.stitchpad.feature.notebookimport.domain

/** Localized labels for details of the other card that are kept as notes when they clash. */
data class MergeNoteLabels(val date: String, val phone: String)

/**
 * Combines two cards the tailor said are the same person into [keep]. Nothing from [other] is silently
 * dropped: a value is skipped only when [keep] already has the very same reading. Every conflict ends as a
 * flagged value (flags are what the review screen and the "Looks right" rule act on), so the merged card
 * must be looked at again:
 * - the same field with a different reading: both values are kept and flagged;
 * - a different unit: [other]'s values are kept and flagged;
 * - a different written date or phone: [keep]'s stays, [other]'s is kept as a flagged note;
 * - a different gender: gender is cleared, so the tailor must choose again;
 * - different existing-customer decisions: both are cleared, so the tailor must choose again.
 * Missing phone, gender, unit, date or match decision are taken from [other].
 */
fun mergeCards(keep: ReviewCard, other: ReviewCard, labels: MergeNoteLabels): ReviewCard {
    val unitConflict = conflicts(keep.unit, other.unit)
    val merged = mergeValues(keep.values, other.values, unitConflict)
    val notes = clashNotes(keep, other, labels)
    val genderConflict = conflicts(keep.gender, other.gender)
    val decision = mergedDecision(keep, other)
    val conflict = unitConflict || merged.conflict || notes.isNotEmpty() || genderConflict || decision.clash
    return keep.copy(
        phone = mergedPhone(keep.phone, other.phone),
        gender = if (genderConflict) null else keep.gender ?: other.gender,
        unit = keep.unit ?: other.unit,
        dateWritten = keep.dateWritten ?: other.dateWritten,
        values = merged.values + notes,
        needsReview = keep.needsReview || other.needsReview || conflict,
        status = mergedStatus(keep.status, other.status, conflict),
        matchedCustomerId = decision.matchedCustomerId,
        keepAsNew = decision.keepAsNew,
    )
}

/** [other]'s written date and phone, as flagged notes, when they differ from [keep]'s. */
private fun clashNotes(keep: ReviewCard, other: ReviewCard, labels: MergeNoteLabels): List<CardValue> = buildList {
    if (conflicts(keep.dateWritten, other.dateWritten)) {
        add(CardValue(labels.date, other.dateWritten.toString(), fieldKey = null, flagged = true))
    }
    val keepDigits = keep.phone.orEmpty().filter { it.isDigit() }
    val otherDigits = other.phone.orEmpty().filter { it.isDigit() }
    if (keepDigits.isNotEmpty() && otherDigits.isNotEmpty() && keepDigits != otherDigits) {
        add(CardValue(labels.phone, other.phone.orEmpty().trim(), fieldKey = null, flagged = true))
    }
}

private class MatchDecision(val matchedCustomerId: String?, val keepAsNew: Boolean, val clash: Boolean)

/** One decision when the cards agree or only one decided; none (ask again) when they disagree. */
private fun mergedDecision(keep: ReviewCard, other: ReviewCard): MatchDecision {
    val matches = listOfNotNull(keep.matchedCustomerId, other.matchedCustomerId).distinct()
    val keptNew = keep.keepAsNew || other.keepAsNew
    val clash = matches.size > 1 || (matches.isNotEmpty() && keptNew)
    return when {
        clash -> MatchDecision(null, keepAsNew = false, clash = true)
        matches.isNotEmpty() -> MatchDecision(matches.single(), keepAsNew = false, clash = false)
        else -> MatchDecision(null, keepAsNew = keptNew, clash = false)
    }
}

private fun mergedPhone(keep: String?, other: String?): String? = keep?.takeIf { it.isNotBlank() } ?: other

/** Stays checked only when both were checked and nothing clashed. */
private fun mergedStatus(keep: CardStatus, other: CardStatus, conflict: Boolean): CardStatus =
    if (keep == CardStatus.CHECKED && other == CardStatus.CHECKED && !conflict) CardStatus.CHECKED else CardStatus.NEW

private fun <T> conflicts(a: T?, b: T?): Boolean = a != null && b != null && a != b

private class MergedValues(val values: List<CardValue>, val conflict: Boolean)

/** [incoming] appended to [base]; clashing readings of one field are both kept and flagged. */
private fun mergeValues(base: List<CardValue>, incoming: List<CardValue>, flagAll: Boolean): MergedValues {
    val values = base.toMutableList()
    var conflict = false
    for (value in incoming) {
        val clashAt = value.fieldKey?.let { key ->
            values.indexOfFirst { it.fieldKey == key && it.value.trim() != value.value.trim() }
        } ?: -1
        when {
            !flagAll && values.any { it.isSameReading(value) } -> Unit
            clashAt >= 0 -> {
                values[clashAt] = values[clashAt].copy(flagged = true)
                values += value.copy(flagged = true)
                conflict = true
            }
            else -> values += if (flagAll) value.copy(flagged = true) else value
        }
    }
    return MergedValues(values, conflict)
}

/** Same field (or, for notes, same label) with the same written value. */
private fun CardValue.isSameReading(other: CardValue): Boolean {
    val sameSlot = if (fieldKey != null) {
        fieldKey == other.fieldKey
    } else {
        other.fieldKey == null && label.equals(other.label, ignoreCase = true)
    }
    return sameSlot && value.trim() == other.value.trim()
}
