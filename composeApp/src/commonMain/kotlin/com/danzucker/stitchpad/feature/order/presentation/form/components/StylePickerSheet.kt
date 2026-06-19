@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.order.presentation.form.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.feature.order.presentation.form.StylePickerSource
import com.danzucker.stitchpad.feature.style.domain.StylePickerFolder
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_form_style_picker_title
import stitchpad.composeapp.generated.resources.order_style_closet_empty
import stitchpad.composeapp.generated.resources.order_style_folder_back
import stitchpad.composeapp.generated.resources.order_style_inspiration_empty
import stitchpad.composeapp.generated.resources.order_style_source_closet
import stitchpad.composeapp.generated.resources.order_style_source_inspiration
import stitchpad.composeapp.generated.resources.style_folders_default_name
import stitchpad.composeapp.generated.resources.style_picker_already_added

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylePickerSheet(
    closetFolders: List<StylePickerFolder>,
    inspirationFolders: List<StylePickerFolder>,
    selectedSource: StylePickerSource,
    onSourceChange: (StylePickerSource) -> Unit,
    pickerOpenFolderKey: String?,
    onFolderOpen: (StylePickerFolder) -> Unit,
    onFolderBack: () -> Unit,
    alreadySelectedStyleIds: Set<String>,
    remainingCapacity: Int,
    onSelect: (Style) -> Unit,
    onDismiss: () -> Unit,
) {
    val folders = if (selectedSource == StylePickerSource.CLOSET) closetFolders else inspirationFolders
    val namedFolders = folders.filter { it.folderId != null }
    // Resolve the LIVE folder from the current list by key (not a stale snapshot), so a
    // folder opened before its styles loaded reflects later updates. Falls back to the
    // grid if the folder no longer exists (deleted, or source switched).
    val pickerOpenFolder = pickerOpenFolderKey?.let { key -> folders.firstOrNull { it.key == key } }
    val defaultFolderName = stringResource(Res.string.style_folders_default_name)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.space3)) {
            // --- Title row (with optional back arrow when drilled in) ---
            if (pickerOpenFolder != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = DesignTokens.space2,
                            end = DesignTokens.space4,
                            top = DesignTokens.space2,
                        ),
                ) {
                    IconButton(onClick = onFolderBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.order_style_folder_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = pickerOpenFolder.name ?: defaultFolderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
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
            }

            // --- Source toggle (always visible) ---
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.space4)
                    .padding(bottom = DesignTokens.space2),
            ) {
                SegmentedButton(
                    selected = selectedSource == StylePickerSource.CLOSET,
                    onClick = { onSourceChange(StylePickerSource.CLOSET) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(Res.string.order_style_source_closet)) },
                )
                SegmentedButton(
                    selected = selectedSource == StylePickerSource.INSPIRATION,
                    onClick = { onSourceChange(StylePickerSource.INSPIRATION) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(Res.string.order_style_source_inspiration)) },
                )
            }

            // --- Body ---
            when {
                // Drilled into a folder: show its styles as 2-col grid.
                pickerOpenFolder != null -> {
                    StyleGrid(
                        styles = pickerOpenFolder.styles,
                        alreadySelectedStyleIds = alreadySelectedStyleIds,
                        remainingCapacity = remainingCapacity,
                        emptyText = if (selectedSource == StylePickerSource.INSPIRATION) {
                            stringResource(Res.string.order_style_inspiration_empty)
                        } else {
                            stringResource(Res.string.order_style_closet_empty)
                        },
                        onSelect = onSelect,
                    )
                }

                // No named folders: go straight to the default folder's styles.
                namedFolders.isEmpty() -> {
                    val defaultStyles = folders.firstOrNull()?.styles ?: emptyList()
                    StyleGrid(
                        styles = defaultStyles,
                        alreadySelectedStyleIds = alreadySelectedStyleIds,
                        remainingCapacity = remainingCapacity,
                        emptyText = if (selectedSource == StylePickerSource.INSPIRATION) {
                            stringResource(Res.string.order_style_inspiration_empty)
                        } else {
                            stringResource(Res.string.order_style_closet_empty)
                        },
                        onSelect = onSelect,
                    )
                }

                // Named folders exist: show the folder grid.
                else -> {
                    FolderGrid(
                        folders = folders,
                        defaultFolderName = defaultFolderName,
                        onFolderClick = onFolderOpen,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Folder grid (2-col)
// ---------------------------------------------------------------------------

@Composable
private fun FolderGrid(
    folders: List<StylePickerFolder>,
    defaultFolderName: String,
    onFolderClick: (StylePickerFolder) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = DesignTokens.space4,
            end = DesignTokens.space4,
            top = DesignTokens.space2,
            bottom = DesignTokens.space8,
        ),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items = folders, key = { it.folderId ?: "__default__" }) { folder ->
            PickerFolderCard(
                folder = folder,
                defaultName = defaultFolderName,
                onClick = { onFolderClick(folder) },
            )
        }
    }
}

@Composable
private fun PickerFolderCard(
    folder: StylePickerFolder,
    defaultName: String,
    onClick: () -> Unit,
) {
    val heritageAccent = LocalStitchPadColors.current.heritageAccent
    val isDefault = folder.folderId == null

    Card(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column {
            // Cover image or placeholder
            Box(
                contentAlignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                if (folder.coverUrl != null) {
                    SubcomposeAsyncImage(
                        model = folder.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) { LoadingDots() }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                // Count badge (top-right)
                if (folder.styles.isNotEmpty() || !isDefault) {
                    val monoFamily = JetBrainsMonoFamily()
                    Text(
                        text = folder.styles.size.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = monoFamily),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(DesignTokens.space2)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(DesignTokens.radiusSm),
                            )
                            .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
                    )
                }
            }

            // Label strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = DesignTokens.space3,
                        vertical = DesignTokens.space2,
                    ),
            ) {
                Icon(
                    imageVector = if (isDefault) Icons.Default.Star else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isDefault) heritageAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = folder.name ?: defaultName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Style grid (2-col)
// ---------------------------------------------------------------------------

@Composable
private fun StyleGrid(
    styles: List<Style>,
    alreadySelectedStyleIds: Set<String>,
    remainingCapacity: Int,
    emptyText: String,
    onSelect: (Style) -> Unit,
) {
    if (styles.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = DesignTokens.space4,
                vertical = DesignTokens.space4,
            ),
        )
        return
    }
    val alreadyAddedLabel = stringResource(Res.string.style_picker_already_added)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = DesignTokens.space4,
            end = DesignTokens.space4,
            top = DesignTokens.space2,
            bottom = DesignTokens.space8,
        ),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items = styles, key = { it.id }) { style ->
            val alreadyPicked = style.id in alreadySelectedStyleIds
            val outOfCapacity = remainingCapacity <= 0
            val disabled = alreadyPicked || outOfCapacity
            StylePickerCard(
                style = style,
                disabled = disabled,
                statusLabel = if (alreadyPicked) alreadyAddedLabel else null,
                onClick = { if (!disabled) onSelect(style) },
            )
        }
    }
}

@Composable
private fun StylePickerCard(
    style: Style,
    disabled: Boolean,
    statusLabel: String?,
    onClick: () -> Unit,
) {
    val cardAlpha = if (disabled) 0.5f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .clickable(role = Role.Button, enabled = !disabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(DesignTokens.radiusMd)),
        ) {
            SubcomposeAsyncImage(
                model = style.localPhotoPath ?: style.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) { LoadingDots(dotSize = 4.dp) }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(DesignTokens.radiusMd)),
            )
        }
        if (statusLabel != null) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space2,
                    vertical = DesignTokens.space1,
                ),
            )
        } else if (style.description.isNotBlank()) {
            Text(
                text = style.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space2,
                    vertical = DesignTokens.space1,
                ),
            )
        }
    }
}
