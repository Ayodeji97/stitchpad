package com.danzucker.stitchpad.feature.measurement.presentation.form

import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.presentation.UiText
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.custom_field_added
import stitchpad.composeapp.generated.resources.custom_field_added_female
import stitchpad.composeapp.generated.resources.custom_field_added_male

/**
 * Confirmation shown after a custom field is created. If the field shows on the
 * current gender (or on both), the tailor sees it appear immediately, so a plain
 * "added" confirmation is enough. If it is scoped to the OTHER gender only, the
 * field will not appear on this measurement — so the message says which set it
 * landed in (informational; no gender-switch action — that field is for the
 * tailor's other customers).
 */
fun customFieldAddedMessage(
    label: String,
    currentGender: CustomerGender?,
    genders: Set<CustomerGender>,
): UiText {
    val shownHere = currentGender == null || currentGender in genders
    if (shownHere) {
        return UiText.StringResourceText(Res.string.custom_field_added, arrayOf(label))
    }
    // Not shown here → genders excludes the current gender. With only two genders,
    // the field is scoped to exactly the opposite one.
    val resource = if (CustomerGender.FEMALE in genders) {
        Res.string.custom_field_added_female
    } else {
        Res.string.custom_field_added_male
    }
    return UiText.StringResourceText(resource, arrayOf(label))
}
