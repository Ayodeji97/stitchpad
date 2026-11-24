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
import com.danzucker.stitchpad.feature.notebookimport.domain.isReadyToCheck
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
) {
    val name: String get() = draft?.name.orEmpty()
    val phone: String get() = draft?.phone.orEmpty()
    val gender: CustomerGender? get() = draft?.gender
    val unit: MeasurementUnit get() = draft?.unit ?: defaultUnit
    val dateWritten: LocalDate? get() = draft?.dateWritten
    val values: List<CardValue> get() = draft?.values.orEmpty()
    val status: CardStatus? get() = draft?.status
    val addToExisting: Boolean get() = draft?.matchedCustomerId != null
    val isReadOnly: Boolean get() = draft?.status == CardStatus.SAVED

    /** Why "Looks right" is disabled, or null when it is enabled. */
    val checkBlocker: CheckBlocker? get() = draft?.checkBlocker()

    /** "Looks right" needs a gender, a name, and no unclear or blank value. */
    val canMarkChecked: Boolean get() = draft?.isReadyToCheck() == true && !isSaving
}
