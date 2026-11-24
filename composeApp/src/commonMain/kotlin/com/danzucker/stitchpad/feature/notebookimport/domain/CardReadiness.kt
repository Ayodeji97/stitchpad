package com.danzucker.stitchpad.feature.notebookimport.domain

/**
 * A card can be marked checked (and saved) only when nothing about it is still unknown: a gender
 * was chosen, it has a name, and no value is unclear or blank.
 */
fun ReviewCard.isReadyToCheck(): Boolean = checkBlocker() == null

/** What still stops a card from being checked, in the order the tailor should fix it. */
enum class CheckBlocker { GENDER, NAME, VALUES, DUPLICATE, MATCH }

fun ReviewCard.checkBlocker(): CheckBlocker? = when {
    gender == null -> CheckBlocker.GENDER
    name.isBlank() -> CheckBlocker.NAME
    values.any { it.flagged || it.value.isBlank() } -> CheckBlocker.VALUES
    else -> null
}

/** Saved or skipped: nothing more to do with this card. */
val ReviewCard.isDone: Boolean
    get() = status == CardStatus.SAVED || status == CardStatus.SKIPPED
