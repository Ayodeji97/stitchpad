package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_footer_caption
import stitchpad.composeapp.generated.resources.order_detail_footer_caption_delivered

@Composable
fun OrderFooterCaption(
    orderId: String,
    referenceTimestamp: Long,
    isDelivered: Boolean,
    modifier: Modifier = Modifier,
) {
    val displayId = "ST-" + orderId.uppercase().take(6)
    val dateStr = formatShortDate(referenceTimestamp)
    val template = if (isDelivered) {
        stringResource(Res.string.order_detail_footer_caption_delivered, displayId, dateStr)
    } else {
        stringResource(Res.string.order_detail_footer_caption, displayId, dateStr)
    }
    Text(
        text = template,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = DesignTokens.space4),
    )
}

private fun formatShortDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    return "${date.dayOfMonth} $month ${date.year}"
}

// region Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderFooterCaptionCreatedPreview() {
    StitchPadTheme {
        OrderFooterCaption(
            orderId = "abcdef0123456",
            referenceTimestamp = 1_743_638_400_000L, // 3 Apr 2025
            isDelivered = false,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderFooterCaptionDeliveredDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderFooterCaption(
            orderId = "xyz789",
            referenceTimestamp = 1_746_316_800_000L, // 4 May 2025
            isDelivered = true,
        )
    }
}

// endregion
