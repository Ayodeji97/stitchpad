package com.danzucker.stitchpad.feature.notebookimport.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.danzucker.stitchpad.core.domain.model.BodyProfileTemplate
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementField
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.measurement.presentation.measurementFieldLabel
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gender_female
import stitchpad.composeapp.generated.resources.gender_male
import stitchpad.composeapp.generated.resources.notebook_review_gender_unknown
import stitchpad.composeapp.generated.resources.notebook_review_unit_cm
import stitchpad.composeapp.generated.resources.notebook_review_unit_inches
import stitchpad.composeapp.generated.resources.notebook_review_unit_unknown

/** Minimum touch target for every tappable control on the review screens. */
internal val NotebookTouchTarget = DesignTokens.space12

/** The template field for [key], preferring the card's gender (male and female share some keys). */
internal fun templateField(key: String, gender: CustomerGender?): MeasurementField? {
    val order = listOfNotNull(gender) + CustomerGender.entries.filter { it != gender }
    return order.firstNotNullOfOrNull { g ->
        BodyProfileTemplate.sectionsFor(g).flatMap { it.fields }.find { it.key == key }
    }
}

/** The localized field name when the value is mapped to a field, else the label written in the notebook. */
@Composable
internal fun cardValueLabel(value: CardValue, gender: CustomerGender?): String {
    val field = value.fieldKey?.let { templateField(it, gender) } ?: return value.label
    return measurementFieldLabel(field.labelKey, field.label)
}

@Composable
internal fun genderText(gender: CustomerGender?): String = when (gender) {
    CustomerGender.FEMALE -> stringResource(Res.string.gender_female)
    CustomerGender.MALE -> stringResource(Res.string.gender_male)
    null -> stringResource(Res.string.notebook_review_gender_unknown)
}

@Composable
internal fun unitText(unit: MeasurementUnit?): String = when (unit) {
    MeasurementUnit.INCHES -> stringResource(Res.string.notebook_review_unit_inches)
    MeasurementUnit.CM -> stringResource(Res.string.notebook_review_unit_cm)
    null -> stringResource(Res.string.notebook_review_unit_unknown)
}

/** Warning colours from the design system, for unclear values. */
internal data class WarningColors(val background: Color, val content: Color, val accent: Color)

@Composable
internal fun warningColors(): WarningColors = if (LocalIsDarkTheme.current) {
    WarningColors(DesignTokens.warningDarkBg, DesignTokens.warningDarkText, DesignTokens.warningDarkText)
} else {
    WarningColors(DesignTokens.warning50, MaterialTheme.colorScheme.onSurface, DesignTokens.warning500)
}

/** "Waist 3?" in mono, the value highlighted in warning colours when unclear. */
@Composable
internal fun MonoValueChip(label: String, value: String, unclear: Boolean, modifier: Modifier = Modifier) {
    val warning = warningColors()
    val shape = RoundedCornerShape(DesignTokens.radiusSm)
    Text(
        text = "$label ${value.ifBlank { "?" }}",
        fontFamily = JetBrainsMonoFamily(),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (unclear) FontWeight.SemiBold else FontWeight.Normal,
        color = if (unclear) warning.content else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(if (unclear) warning.background else Color.Transparent, shape)
            .padding(horizontal = DesignTokens.space1),
    )
}
