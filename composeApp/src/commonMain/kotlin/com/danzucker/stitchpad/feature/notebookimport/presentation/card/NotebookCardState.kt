package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import androidx.compose.runtime.Immutable
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementField
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.notebookimport.domain.CardStatus
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.CheckBlocker
import com.danzucker.stitchpad.feature.notebookimport.domain.ReviewCard
import com.danzucker.stitchpad.feature.notebookimport.domain.checkBlocker
import com.danzucker.stitchpad.feature.notebookimport.presentation.CapSheet
import kotlinx.datetime.LocalDate

/** An existing customer the card looks like. A suggestion only, until the tailor picks "Add to". */
@Immutable
data class ExistingCustomer(val id: String, val name: String)

data class NotebookCardState(
    val isLoading: Boolean = true,
    /** The card is gone (merged or deleted elsewhere). */
    val notFound: Boolean = false,
    /** The edited card. Null until loaded. */
    val draft: ReviewCard? = null,
    val pageNumber: Int = 0,
    /** Download URL of the page photo; null while loading or once the photo is gone. */
    val imageUrl: String? = null,
    val photoUnavailable: Boolean = false,
    val crop: CropRegion? = null,
    val matchCandidate: ExistingCustomer? = null,
    /** Template fields the value labels can be mapped to. */
    val fieldOptions: List<MeasurementField> = emptyList(),
    val fieldPickerIndex: Int? = null,
    val showDatePicker: Boolean = false,
    val isSaving: Boolean = false,
    val message: UiText? = null,
    val upgradeCtaPrice: String? = null,
    /** The workshop's unit, used when the notebook did not say. */
    val defaultUnit: MeasurementUnit = MeasurementUnit.INCHES,
    /** Existing-customer matches can be judged only once the customer list is in. */
    val customersLoaded: Boolean = false,
    /** The customer-cap sheet, open after "Save now" stopped at the Free cap. */
    val capSheet: CapSheet? = null,
    /** In a same-person group still waiting for Combine or Keep separate on the review list. */
    val inPendingDuplicate: Boolean = false,
) {
    val name: String get() = draft?.name.orEmpty()
    val phone: String get() = draft?.phone.orEmpty()
    val gender: CustomerGender? get() = draft?.gender
    val unit: MeasurementUnit get() = draft?.unit ?: defaultUnit
    val dateWritten: LocalDate? get() = draft?.dateWritten
    val values: List<CardValue> get() = draft?.values.orEmpty()
    val status: CardStatus? get() = draft?.status
    val addToExisting: Boolean get() = matchChoice == true
    val isReadOnly: Boolean get() = draft?.status == CardStatus.SAVED

    /**
     * The tailor's answer to [matchCandidate]: true "Add to", false "Create new customer", null not
     * answered yet (neither option selected).
     */
    val matchChoice: Boolean?
        get() = when {
            draft?.matchedCustomerId != null -> true
            draft?.keepAsNew == true -> false
            else -> null
        }

    /** Why "Looks right" is disabled, or null when it is enabled. */
    val checkBlocker: CheckBlocker?
        get() = draft?.checkBlocker()
            ?: CheckBlocker.DUPLICATE.takeIf { inPendingDuplicate }
            ?: CheckBlocker.MATCH.takeIf { matchCandidate != null && matchChoice == null }

    /**
     * "Looks right" needs a gender, a name, no unclear or blank value, no pending same-person prompt,
     * and an answer to any existing-customer suggestion (so the customer list must have loaded).
     */
    val canMarkChecked: Boolean get() = draft != null && checkBlocker == null && customersLoaded && !isSaving
}
