package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.danzucker.stitchpad.core.sharing.formatPrice
import com.danzucker.stitchpad.ui.theme.DesignTokens

/** True when a struck-through gross price should be shown next to the net total. */
fun shouldShowStruckGross(discount: Double): Boolean = discount > 0.0

/**
 * Renders [netPrice] as the headline figure. When [discount] > 0 it also shows the
 * struck-through [grossPrice] (in a muted labelSmall) so the discount is visible.
 * [stacked] = true puts the struck gross ABOVE the net (end-aligned column, for tight
 * list rows); false puts them inline in a row (for the hero total line). When there is
 * no discount the output is a single net Text, byte-for-byte like the pre-feature UI.
 */
@Composable
fun StrikethroughPrice(
    grossPrice: Double,
    netPrice: Double,
    discount: Double,
    netStyle: TextStyle,
    netColor: Color,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val net: @Composable () -> Unit = {
        Text(text = "₦${formatPrice(netPrice)}", style = netStyle, color = netColor)
    }
    if (!shouldShowStruckGross(discount)) {
        Row(modifier = modifier) { net() }
        return
    }
    val struck: @Composable () -> Unit = {
        // Match the net total's size (just lighter weight) so the strikethrough is
        // clearly legible on dark surfaces — labelSmall was too faint to read.
        Text(
            text = "₦${formatPrice(grossPrice)}",
            style = netStyle.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
        )
    }
    if (stacked) {
        Column(modifier = modifier, horizontalAlignment = Alignment.End) {
            struck()
            net()
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            struck()
            net()
        }
    }
}
