@file:Suppress("MatchingDeclarationName") // file holds CountryChip + TimeZoneRow and their shared colours

package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.core.domain.model.Country
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.country_picker_chip_content_description
import stitchpad.composeapp.generated.resources.country_time_zone_title

/**
 * Colours for the country chip and zone row. The defaults match the always-dark Workshop Setup
 * form; screens on the themed surface pass [MaterialTheme]-derived colours instead.
 */
data class CountryFieldColors(
    val container: Color = DesignTokens.darkSurface2,
    val border: Color = DesignTokens.darkBorder,
    val primaryText: Color = DesignTokens.neutral100,
    val secondaryText: Color = DesignTokens.darkTextSecondary,
)

/** Tappable flag + dial code that opens the country picker. */
@Composable
fun CountryChip(
    country: Country,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CountryFieldColors = CountryFieldColors(),
) {
    val description = stringResource(
        Res.string.country_picker_chip_content_description,
        countryDisplayName(country),
        country.dialCode,
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(colors.container)
            .border(1.5.dp, colors.border, RoundedCornerShape(DesignTokens.radiusMd))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = DesignTokens.space3, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = country.flagEmoji, fontSize = 18.sp)
        Text(
            text = "+${country.dialCode}",
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
            ),
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(DesignTokens.iconInline),
        )
    }
}

/** Zone choice for countries spanning more than one time zone. */
@Composable
fun TimeZoneRow(
    timeZones: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: CountryFieldColors = CountryFieldColors(),
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.country_time_zone_title),
            style = TextStyle(
                fontSize = DesignTokens.labelMd,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
            timeZones.forEach { zone ->
                val isSelected = zone == selected
                val accent = LocalStitchPadColors.current.brandAccent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(DesignTokens.radiusMd))
                        .background(if (isSelected) accent.copy(alpha = 0.18f) else colors.container)
                        .border(
                            1.5.dp,
                            if (isSelected) accent else colors.border,
                            RoundedCornerShape(DesignTokens.radiusMd),
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(zone) }
                        .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space3),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = timeZoneDisplayName(zone),
                        style = TextStyle(
                            fontSize = DesignTokens.bodySm,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) colors.primaryText else colors.secondaryText,
                        ),
                    )
                }
            }
        }
    }
}
