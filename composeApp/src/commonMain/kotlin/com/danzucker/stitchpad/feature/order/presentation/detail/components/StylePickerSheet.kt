package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.style_picker_already_added
import stitchpad.composeapp.generated.resources.style_picker_create_new
import stitchpad.composeapp.generated.resources.style_picker_created_caption
import stitchpad.composeapp.generated.resources.style_picker_empty
import stitchpad.composeapp.generated.resources.style_picker_title
import stitchpad.composeapp.generated.resources.style_untitled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylePickerSheet(
    styles: List<Style>,
    alreadySelectedStyleIds: Set<String>,
    remainingCapacity: Int,
    onSelectStyle: (styleId: String) -> Unit,
    onCreateNewClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        StylePickerSheetContent(
            styles = styles,
            alreadySelectedStyleIds = alreadySelectedStyleIds,
            remainingCapacity = remainingCapacity,
            onSelectStyle = onSelectStyle,
            onCreateNewClick = onCreateNewClick,
        )
    }
}

@Composable
private fun StylePickerSheetContent(
    styles: List<Style>,
    alreadySelectedStyleIds: Set<String>,
    remainingCapacity: Int,
    onSelectStyle: (styleId: String) -> Unit,
    onCreateNewClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space2)) {
        Text(
            text = stringResource(Res.string.style_picker_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(DesignTokens.space3))

        if (styles.isEmpty()) {
            Text(
                text = stringResource(Res.string.style_picker_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            styles.forEach { style ->
                val alreadyPicked = style.id in alreadySelectedStyleIds
                val outOfCapacity = remainingCapacity <= 0
                val disabled = alreadyPicked || outOfCapacity
                StyleRow(
                    style = style,
                    disabled = disabled,
                    statusLabel = if (alreadyPicked) stringResource(Res.string.style_picker_already_added) else null,
                    onClick = { if (!disabled) onSelectStyle(style.id) },
                )
            }
        }

        Spacer(Modifier.height(DesignTokens.space3))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(DesignTokens.space2))

        TextButton(
            onClick = onCreateNewClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(DesignTokens.iconInline),
            )
            Spacer(Modifier.size(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.style_picker_create_new),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(DesignTokens.space2))
    }
}

@Composable
private fun StyleRow(
    style: Style,
    disabled: Boolean,
    statusLabel: String?,
    onClick: () -> Unit,
) {
    val rowAlpha = if (disabled) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clickable(role = Role.Button, enabled = !disabled, onClick = onClick)
            .padding(vertical = DesignTokens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.description.ifBlank { stringResource(Res.string.style_untitled) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    Res.string.style_picker_created_caption,
                    formatStyleShortDate(style.createdAt),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (statusLabel != null) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatStyleShortDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.lowercase().replaceFirstChar(Char::uppercase).take(3)
    return "${date.dayOfMonth} $month ${date.year}"
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StylePickerSheetPopulatedLightPreview() {
    StitchPadTheme {
        StylePickerSheetContent(
            styles = listOf(
                Style(
                    id = "s1",
                    customerId = "c1",
                    description = "Royal blue agbada with gold embroidery",
                    photoUrl = "https://example.com/s1.jpg",
                    photoStoragePath = "",
                    createdAt = 1_746_316_800_000L,
                    updatedAt = 1_746_316_800_000L,
                ),
                Style(
                    id = "s2",
                    customerId = "c1",
                    description = "White kaftan with lace detail",
                    photoUrl = "https://example.com/s2.jpg",
                    photoStoragePath = "",
                    createdAt = 1_743_638_400_000L,
                    updatedAt = 1_743_638_400_000L,
                ),
            ),
            alreadySelectedStyleIds = setOf("s1"),
            remainingCapacity = 2,
            onSelectStyle = {},
            onCreateNewClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StylePickerSheetEmptyDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        StylePickerSheetContent(
            styles = emptyList(),
            alreadySelectedStyleIds = emptySet(),
            remainingCapacity = 3,
            onSelectStyle = {},
            onCreateNewClick = {},
        )
    }
}
