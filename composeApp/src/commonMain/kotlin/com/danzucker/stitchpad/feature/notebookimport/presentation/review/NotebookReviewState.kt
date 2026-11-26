package com.danzucker.stitchpad.feature.notebookimport.presentation.review

import androidx.compose.runtime.Immutable
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.presentation.CapSheet

/**
 * An existing customer this card may belong to. [confirmed] is true only once the tailor said so
 * (the card's matchedCustomerId is set); until then it is a suggestion and saving creates a new customer.
 */
@Immutable
data class MatchSuggestion(val customerId: String, val customerName: String, val confirmed: Boolean)

/** Cards in this import that look like the same person. Shown on the first card of the group. */
@Immutable
data class DuplicatePrompt(val cardIds: List<String>, val otherName: String, val pageNumbers: List<Int>)

@Immutable
data class ReviewRow(
    val cardId: String,
    val name: String,
    val pageNumber: Int,
    val gender: CustomerGender?,
    val unit: MeasurementUnit?,
    val values: List<CardValue>,
    val unclearCount: Int,
    val match: MatchSuggestion? = null,
    val duplicate: DuplicatePrompt? = null,
    /** Several existing customers share this name; the tailor picks one on the card. */
    val ambiguousMatch: Boolean = false,
)

data class NotebookReviewState(
    val isLoading: Boolean = true,
    val needLook: List<ReviewRow> = emptyList(),
    val ready: List<ReviewRow> = emptyList(),
    val saved: List<ReviewRow> = emptyList(),
    val skipped: List<ReviewRow> = emptyList(),
    /** Every card read from the notebook, whatever its status. */
    val foundCount: Int = 0,
    val needLookCount: Int = 0,
    /** Pages still uploading or being read; more cards may arrive. */
    val pagesReading: Int = 0,
    val isSaving: Boolean = false,
    val showDiscardDialog: Boolean = false,
    /** Every card is saved or skipped and the job was closed. */
    val isFinished: Boolean = false,
    val message: UiText? = null,
    /** Pro price for the cap sheet, in the workshop's currency. */
    val upgradeCtaPrice: String? = null,
    /** Existing-customer matches can be judged only once the customer list is in. */
    val customersLoaded: Boolean = false,
    /** The customer-cap sheet, open after saving stopped at the Free cap. */
    val capSheet: CapSheet? = null,
) {
    val canSaveAll: Boolean get() = ready.isNotEmpty() && !isSaving && customersLoaded
}
