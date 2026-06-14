package com.danzucker.stitchpad.feature.style.presentation.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.StitchPadFab
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.fab_add_style
import stitchpad.composeapp.generated.resources.style_action_copy
import stitchpad.composeapp.generated.resources.style_action_delete
import stitchpad.composeapp.generated.resources.style_action_move
import stitchpad.composeapp.generated.resources.style_copied_snackbar
import stitchpad.composeapp.generated.resources.style_delete_cancel
import stitchpad.composeapp.generated.resources.style_delete_confirm
import stitchpad.composeapp.generated.resources.style_delete_message
import stitchpad.composeapp.generated.resources.style_delete_title
import stitchpad.composeapp.generated.resources.style_empty_subtitle
import stitchpad.composeapp.generated.resources.style_empty_title
import stitchpad.composeapp.generated.resources.style_folder_full_action
import stitchpad.composeapp.generated.resources.style_folder_full_snackbar
import stitchpad.composeapp.generated.resources.style_gallery_title
import stitchpad.composeapp.generated.resources.style_inspiration_empty_subtitle
import stitchpad.composeapp.generated.resources.style_inspiration_empty_title
import stitchpad.composeapp.generated.resources.style_inspiration_title
import stitchpad.composeapp.generated.resources.style_moved_snackbar
import stitchpad.composeapp.generated.resources.style_transfer_copy_title
import stitchpad.composeapp.generated.resources.style_transfer_empty
import stitchpad.composeapp.generated.resources.style_transfer_move_title
import stitchpad.composeapp.generated.resources.style_transfer_view_cta

@Composable
fun StyleGalleryRoot(
    onNavigateBack: () -> Unit,
    onNavigateToAddStyle: (String?, String?) -> Unit,
    onNavigateToEditStyle: (String?, String?, String) -> Unit,
    onNavigateToStyleGallery: (String?) -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val viewModel: StyleGalleryViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val viewActionLabel = stringResource(Res.string.style_transfer_view_cta)
    val inspirationName = stringResource(Res.string.style_inspiration_title)
    val upgradeActionLabel = stringResource(Res.string.style_folder_full_action)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            StyleGalleryEvent.NavigateBack -> onNavigateBack()
            is StyleGalleryEvent.NavigateToAddStyle -> onNavigateToAddStyle(event.customerId, event.folderId)
            is StyleGalleryEvent.NavigateToEditStyle -> onNavigateToEditStyle(
                event.customerId,
                event.folderId,
                event.styleId
            )
            is StyleGalleryEvent.StyleTransferred -> scope.launch {
                val targetName = transferTargetName(event.target, inspirationName)
                val template = when (event.mode) {
                    StyleTransferMode.COPY -> Res.string.style_copied_snackbar
                    StyleTransferMode.MOVE -> Res.string.style_moved_snackbar
                }
                // Longer snackbar + a "View" action that jumps to the target
                // customer's closet so the user can confirm the transfer landed.
                val result = snackbarHostState.showSnackbar(
                    message = getString(template, targetName),
                    actionLabel = viewActionLabel,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val targetCustomerId = (event.target as? TransferTarget.Customer)?.customerId
                    onNavigateToStyleGallery(targetCustomerId)
                }
            }
            is StyleGalleryEvent.CapReached -> scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = getString(Res.string.style_folder_full_snackbar, event.cap),
                    actionLabel = upgradeActionLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onNavigateToUpgrade()
                }
            }
            StyleGalleryEvent.NavigateToUpgrade -> onNavigateToUpgrade()
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(StyleGalleryAction.OnErrorDismiss)
        }
    }

    StyleGalleryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleGalleryScreen(
    state: StyleGalleryState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (StyleGalleryAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (state.isInspirationGallery) {
                                Res.string.style_inspiration_title
                            } else {
                                Res.string.style_gallery_title
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(StyleGalleryAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            StitchPadFab(
                onClick = { onAction(StyleGalleryAction.OnAddClick) },
                contentDescription = stringResource(Res.string.fab_add_style)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            state.styles.isEmpty() -> {
                StyleGalleryEmptyState(
                    isInspirationGallery = state.isInspirationGallery,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(DesignTokens.space8)
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = DesignTokens.space4,
                        end = DesignTokens.space4,
                        top = DesignTokens.space4,
                        bottom = 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(items = state.styles, key = { it.id }) { style ->
                        StyleCard(
                            style = style,
                            onClick = { onAction(StyleGalleryAction.OnStyleClick(style)) },
                            onLongClick = { onAction(StyleGalleryAction.OnStyleLongPress(style)) }
                        )
                    }
                }
            }
        }
    }

    if (state.showDeleteDialog && state.styleToDelete != null) {
        AlertDialog(
            onDismissRequest = { onAction(StyleGalleryAction.OnDismissDeleteDialog) },
            title = {
                Text(
                    text = stringResource(Res.string.style_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.style_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(StyleGalleryAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd)
                ) {
                    Text(
                        text = stringResource(Res.string.style_delete_confirm),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(StyleGalleryAction.OnDismissDeleteDialog) }) {
                    Text(
                        text = stringResource(Res.string.style_delete_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    state.actionSheetStyle?.let { style ->
        StyleActionsSheet(
            onCopy = { onAction(StyleGalleryAction.OnCopyClick) },
            onMove = { onAction(StyleGalleryAction.OnMoveClick) },
            onDelete = { onAction(StyleGalleryAction.OnDeleteClick(style)) },
            onDismiss = { onAction(StyleGalleryAction.OnDismissActionSheet) }
        )
    }

    state.transfer?.let { transfer ->
        CustomerPickerSheet(
            transfer = transfer,
            onSelect = { onAction(StyleGalleryAction.OnTargetCustomerSelected(it)) },
            onDismiss = { onAction(StyleGalleryAction.OnDismissTransfer) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleActionsSheet(
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = DesignTokens.space6)) {
            SheetActionRow(
                icon = Icons.Default.ContentCopy,
                label = stringResource(Res.string.style_action_copy),
                onClick = onCopy
            )
            SheetActionRow(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                label = stringResource(Res.string.style_action_move),
                onClick = onMove
            )
            SheetActionRow(
                icon = Icons.Default.DeleteOutline,
                label = stringResource(Res.string.style_action_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space4),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.space5, vertical = DesignTokens.space4)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerPickerSheet(
    transfer: StyleTransfer,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = DesignTokens.space6)) {
            Text(
                text = stringResource(
                    when (transfer.mode) {
                        StyleTransferMode.COPY -> Res.string.style_transfer_copy_title
                        StyleTransferMode.MOVE -> Res.string.style_transfer_move_title
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.space5,
                    vertical = DesignTokens.space3
                )
            )
            if (transfer.targets.isEmpty()) {
                Text(
                    text = stringResource(Res.string.style_transfer_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.space5,
                        vertical = DesignTokens.space4
                    )
                )
            } else {
                transfer.targets.forEach { target ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(target.id) }
                            .padding(horizontal = DesignTokens.space5, vertical = DesignTokens.space3)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = transferTargetName(
                                target,
                                stringResource(Res.string.style_inspiration_title),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun transferTargetName(
    target: TransferTarget,
    inspirationName: String,
): String =
    when (target) {
        is TransferTarget.Customer -> target.name
        TransferTarget.Inspiration -> inspirationName
    }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StyleCard(
    style: Style,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            SubcomposeAsyncImage(
                model = style.localPhotoPath ?: style.photoUrl,
                contentDescription = style.description.ifBlank { null },
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LoadingDots()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (style.description.isNotBlank()) {
                Text(
                    text = style.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        horizontal = DesignTokens.space3,
                        vertical = DesignTokens.space2
                    )
                )
            }
        }
    }
}

@Composable
private fun StyleGalleryEmptyState(
    isInspirationGallery: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(DesignTokens.radiusXl)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(DesignTokens.space3))
        Text(
            text = stringResource(
                if (isInspirationGallery) {
                    Res.string.style_inspiration_empty_title
                } else {
                    Res.string.style_empty_title
                }
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(DesignTokens.space1))
        Text(
            text = stringResource(
                if (isInspirationGallery) {
                    Res.string.style_inspiration_empty_subtitle
                } else {
                    Res.string.style_empty_subtitle
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StyleGalleryScreenLoadingPreview() {
    StitchPadTheme {
        StyleGalleryScreen(state = StyleGalleryState(isLoading = true), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StyleGalleryScreenEmptyPreview() {
    StitchPadTheme {
        StyleGalleryScreen(
            state = StyleGalleryState(isLoading = false, styles = emptyList()),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StyleGalleryScreenFilledPreview() {
    StitchPadTheme {
        StyleGalleryScreen(
            state = StyleGalleryState(
                isLoading = false,
                styles = listOf(
                    Style(
                        id = "1",
                        customerId = "c1",
                        description = "Red agbada with gold trim",
                        photoUrl = "",
                        photoStoragePath = "",
                        createdAt = 0L,
                        updatedAt = 0L
                    ),
                    Style(
                        id = "2",
                        customerId = "c1",
                        description = "Blue senator kaftan",
                        photoUrl = "",
                        photoStoragePath = "",
                        createdAt = 0L,
                        updatedAt = 0L
                    )
                )
            ),
            onAction = {}
        )
    }
}
