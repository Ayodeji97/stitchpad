package com.danzucker.stitchpad.feature.order.presentation.detail.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_detail_notes_cancel
import stitchpad.composeapp.generated.resources.order_detail_notes_empty_hint
import stitchpad.composeapp.generated.resources.order_detail_notes_save
import stitchpad.composeapp.generated.resources.order_detail_notes_section

@Composable
fun OrderNotesCard(
    notes: String?,
    isEditing: Boolean,
    draft: String,
    onCardClick: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val border = if (isEditing) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!isEditing) {
                    Modifier.clickable(onClick = onCardClick, role = Role.Button)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(DesignTokens.space4)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                ) {
                    SectionIconTile(
                        imageVector = Icons.AutoMirrored.Filled.StickyNote2,
                        contentDescription = null,
                    )
                    Text(
                        text = stringResource(Res.string.order_detail_notes_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Show pencil icon when populated and not editing
                if (!isEditing && !notes.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(DesignTokens.iconInline),
                    )
                }
            }

            Spacer(Modifier.height(DesignTokens.space3))

            if (isEditing) {
                // Editing state
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.order_detail_notes_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2, Alignment.End),
                ) {
                    TextButton(onClick = onCancelClick) {
                        Text(
                            text = stringResource(Res.string.order_detail_notes_cancel),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Button(
                        onClick = onSaveClick,
                        enabled = draft.trim() != notes?.trim().orEmpty(),
                    ) {
                        Text(
                            text = stringResource(Res.string.order_detail_notes_save),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else {
                // Read state
                if (notes.isNullOrBlank()) {
                    Text(
                        text = stringResource(Res.string.order_detail_notes_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    )
                } else {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionIconTile(
    imageVector: ImageVector,
    contentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// region — Previews

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderNotesCardEmptyCollapsedLightPreview() {
    StitchPadTheme {
        OrderNotesCard(
            notes = null,
            isEditing = false,
            draft = "",
            onCardClick = {},
            onDraftChange = {},
            onSaveClick = {},
            onCancelClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderNotesCardPopulatedCollapsedLightPreview() {
    StitchPadTheme {
        OrderNotesCard(
            notes = "Customer wants the agbada with wide sleeves. Lace fabric from Lagos Island.",
            isEditing = false,
            draft = "",
            onCardClick = {},
            onDraftChange = {},
            onSaveClick = {},
            onCancelClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderNotesCardEditingLightPreview() {
    StitchPadTheme {
        OrderNotesCard(
            notes = "Customer wants the agbada with wide sleeves.",
            isEditing = true,
            draft = "Customer wants the agbada with wide sleeves. Use gold thread.",
            onCardClick = {},
            onDraftChange = {},
            onSaveClick = {},
            onCancelClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OrderNotesCardPopulatedCollapsedDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        OrderNotesCard(
            notes = "Customer wants the agbada with wide sleeves. Lace fabric from Lagos Island.",
            isEditing = false,
            draft = "",
            onCardClick = {},
            onDraftChange = {},
            onSaveClick = {},
            onCancelClick = {},
        )
    }
}

// endregion
