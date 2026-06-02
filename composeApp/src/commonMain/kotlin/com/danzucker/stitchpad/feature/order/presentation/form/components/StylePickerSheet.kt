package com.danzucker.stitchpad.feature.order.presentation.form.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_form_style_picker_empty
import stitchpad.composeapp.generated.resources.order_form_style_picker_title
import stitchpad.composeapp.generated.resources.style_picker_already_added

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylePickerSheet(
    styles: List<Style>,
    alreadySelectedStyleIds: Set<String>,
    remainingCapacity: Int,
    onSelect: (Style) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
            Text(
                text = stringResource(Res.string.order_form_style_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space4,
                    vertical = DesignTokens.space3,
                ),
            )
            if (styles.isEmpty()) {
                Text(
                    text = stringResource(Res.string.order_form_style_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.space4,
                        vertical = DesignTokens.space4,
                    ),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(items = styles, key = { it.id }) { style ->
                        val alreadyPicked = style.id in alreadySelectedStyleIds
                        val outOfCapacity = remainingCapacity <= 0
                        val disabled = alreadyPicked || outOfCapacity
                        StylePickerRow(
                            style = style,
                            disabled = disabled,
                            statusLabel = when {
                                alreadyPicked -> stringResource(Res.string.style_picker_already_added)
                                else -> null
                            },
                            onClick = { if (!disabled) onSelect(style) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePickerRow(
    style: Style,
    disabled: Boolean,
    statusLabel: String?,
    onClick: () -> Unit,
) {
    val rowAlpha = if (disabled) 0.5f else 1f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clickable(role = Role.Button, enabled = !disabled, onClick = onClick)
            .padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space2,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(DesignTokens.radiusMd)),
        ) {
            SubcomposeAsyncImage(
                model = style.localPhotoPath ?: style.photoUrl,
                contentDescription = null,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LoadingDots(dotSize = 4.dp)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = style.description.ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
