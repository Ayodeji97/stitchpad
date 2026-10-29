package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.country_name_bf
import stitchpad.composeapp.generated.resources.country_name_bj
import stitchpad.composeapp.generated.resources.country_name_cd
import stitchpad.composeapp.generated.resources.country_name_cg
import stitchpad.composeapp.generated.resources.country_name_ci
import stitchpad.composeapp.generated.resources.country_name_cm
import stitchpad.composeapp.generated.resources.country_name_ga
import stitchpad.composeapp.generated.resources.country_name_gh
import stitchpad.composeapp.generated.resources.country_name_gn
import stitchpad.composeapp.generated.resources.country_name_ke
import stitchpad.composeapp.generated.resources.country_name_ml
import stitchpad.composeapp.generated.resources.country_name_ne
import stitchpad.composeapp.generated.resources.country_name_ng
import stitchpad.composeapp.generated.resources.country_name_sn
import stitchpad.composeapp.generated.resources.country_name_tg
import stitchpad.composeapp.generated.resources.country_picker_title
import stitchpad.composeapp.generated.resources.time_zone_kinshasa
import stitchpad.composeapp.generated.resources.time_zone_lubumbashi

/**
 * Bottom sheet listing the supported markets: flag, localized name and `+dialCode`, with a
 * check on [selected]. Stateless; the caller owns open/closed state and closes it on select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CountryPickerSheetContent(selected = selected, onSelect = onSelect)
    }
}

@Composable
private fun CountryPickerSheetContent(
    selected: Country,
    onSelect: (Country) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = DesignTokens.space5, vertical = DesignTokens.space2),
    ) {
        Text(
            text = stringResource(Res.string.country_picker_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = DesignTokens.space3),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(bottom = DesignTokens.space3),
        ) {
            items(Country.entries, key = { it.iso }) { country ->
                CountryRow(
                    country = country,
                    isSelected = country == selected,
                    onClick = { onSelect(country) },
                )
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: Country,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = DesignTokens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Text(text = country.flagEmoji, fontSize = 22.sp)
        Text(
            text = countryDisplayName(country),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "+${country.dialCode}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Fixed-size slot so the dial codes stay aligned whether or not the row is checked.
        Box(modifier = Modifier.size(DesignTokens.iconList)) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(DesignTokens.iconList),
                )
            }
        }
    }
}

/** Regional-indicator flag emoji built from the ISO code (e.g. "GH" → 🇬🇭). */
val Country.flagEmoji: String
    get() = buildString {
        iso.uppercase().forEach { letter ->
            // U+1F1E6 + (letter - 'A'), written as its UTF-16 surrogate pair.
            append(REGIONAL_INDICATOR_HIGH_SURROGATE)
            append((REGIONAL_INDICATOR_LOW_SURROGATE_A.code + (letter - 'A')).toChar())
        }
    }

private const val REGIONAL_INDICATOR_HIGH_SURROGATE = '\uD83C'
private const val REGIONAL_INDICATOR_LOW_SURROGATE_A = '\uDDE6'

private val Country.nameRes: StringResource
    get() = when (this) {
        Country.NG -> Res.string.country_name_ng
        Country.GH -> Res.string.country_name_gh
        Country.KE -> Res.string.country_name_ke
        Country.CI -> Res.string.country_name_ci
        Country.SN -> Res.string.country_name_sn
        Country.BJ -> Res.string.country_name_bj
        Country.TG -> Res.string.country_name_tg
        Country.ML -> Res.string.country_name_ml
        Country.BF -> Res.string.country_name_bf
        Country.NE -> Res.string.country_name_ne
        Country.CM -> Res.string.country_name_cm
        Country.GA -> Res.string.country_name_ga
        Country.CG -> Res.string.country_name_cg
        Country.GN -> Res.string.country_name_gn
        Country.CD -> Res.string.country_name_cd
    }

@Composable
fun countryDisplayName(country: Country): String = stringResource(country.nameRes)

/** Label for a zone the picker offers (DR Congo's two); falls back to the IANA id. */
@Composable
fun timeZoneDisplayName(timeZone: String): String = when (timeZone) {
    "Africa/Kinshasa" -> stringResource(Res.string.time_zone_kinshasa)
    "Africa/Lubumbashi" -> stringResource(Res.string.time_zone_lubumbashi)
    else -> timeZone
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun CountryPickerSheetContentPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            CountryPickerSheetContent(selected = Country.GH, onSelect = {})
        }
    }
}
