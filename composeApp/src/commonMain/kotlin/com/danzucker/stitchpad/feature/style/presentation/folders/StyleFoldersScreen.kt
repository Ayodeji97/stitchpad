@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.style.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.StitchPadFab
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.style_delete_cancel
import stitchpad.composeapp.generated.resources.style_delete_confirm
import stitchpad.composeapp.generated.resources.style_folder_delete_message
import stitchpad.composeapp.generated.resources.style_folder_delete_title
import stitchpad.composeapp.generated.resources.style_folder_name_placeholder
import stitchpad.composeapp.generated.resources.style_folders_create_cd
import stitchpad.composeapp.generated.resources.style_folders_create_title
import stitchpad.composeapp.generated.resources.style_folders_default_name
import stitchpad.composeapp.generated.resources.style_folders_rename_title
import stitchpad.composeapp.generated.resources.style_inspiration_title

@Composable
fun StyleFoldersRoot(
    onNavigateBack: () -> Unit,
    onNavigateToFolder: (customerId: String?, folderId: String?) -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val viewModel: StyleFoldersViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            StyleFoldersEvent.NavigateBack -> onNavigateBack()
            is StyleFoldersEvent.NavigateToFolder -> onNavigateToFolder(event.customerId, event.folderId)
            StyleFoldersEvent.NavigateToUpgrade -> onNavigateToUpgrade()
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(StyleFoldersAction.OnErrorDismiss)
        }
    }

    StyleFoldersScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleFoldersScreen(
    state: StyleFoldersState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (StyleFoldersAction) -> Unit,
) {
    val inspirationTitle = stringResource(Res.string.style_inspiration_title)
    val defaultFolderName = stringResource(Res.string.style_folders_default_name)

    val screenTitle = if (state.isInspiration) {
        inspirationTitle
    } else {
        state.customerName?.let { "$it’s Closet" } ?: defaultFolderName
    }

    val folderCountLabel = if (state.limits.foldersEnabled) {
        "${state.folders.size + 1} / ${state.limits.maxFolders}"
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = screenTitle.uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (folderCountLabel != null) {
                            Spacer(Modifier.width(DesignTokens.space3))
                            Text(
                                text = folderCountLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(StyleFoldersAction.OnNavigateBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            StitchPadFab(
                onClick = { onAction(StyleFoldersAction.OnCreateClick) },
                contentDescription = stringResource(Res.string.style_folders_create_cd),
                icon = Icons.Default.CreateNewFolder,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = DesignTokens.space4,
                    end = DesignTokens.space4,
                    top = DesignTokens.space4,
                    bottom = 80.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Default "My styles" card — always pinned first, folderId = null
                item(key = "__default__") {
                    FolderCard(
                        folder = null,
                        defaultName = defaultFolderName,
                        isDefault = true,
                        onClick = { onAction(StyleFoldersAction.OnFolderClick(null)) },
                    )
                }
                items(items = state.folders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        defaultName = defaultFolderName,
                        isDefault = false,
                        onClick = { onAction(StyleFoldersAction.OnFolderClick(folder.id)) },
                    )
                }
            }
        }
    }

    // Create folder sheet
    if (state.showCreateSheet) {
        FolderNameSheet(
            title = stringResource(Res.string.style_folders_create_title),
            initialName = "",
            onConfirm = { name -> onAction(StyleFoldersAction.OnConfirmCreate(name)) },
            onDismiss = { onAction(StyleFoldersAction.OnDismissSheet) },
        )
    }

    // Rename folder sheet
    if (state.renameTarget != null) {
        FolderNameSheet(
            title = stringResource(Res.string.style_folders_rename_title),
            initialName = state.renameTarget.name,
            onConfirm = { name -> onAction(StyleFoldersAction.OnConfirmRename(name)) },
            onDismiss = { onAction(StyleFoldersAction.OnDismissSheet) },
        )
    }

    // Delete folder dialog
    if (state.deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { onAction(StyleFoldersAction.OnDismissSheet) },
            title = {
                Text(
                    text = stringResource(Res.string.style_folder_delete_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.style_folder_delete_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(StyleFoldersAction.OnConfirmDelete) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                ) {
                    Text(
                        text = stringResource(Res.string.style_delete_confirm),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(StyleFoldersAction.OnDismissSheet) }) {
                    Text(
                        text = stringResource(Res.string.style_delete_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            shape = RoundedCornerShape(DesignTokens.radiusXl),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderNameSheet(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Local TextFieldValue to avoid cursor desync when VM owns the string.
    var textValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName))
    }
    val placeholder = stringResource(Res.string.style_folder_name_placeholder)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space5)
                .padding(bottom = DesignTokens.space8),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DesignTokens.space4))
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val name = textValue.text.trim()
                        if (name.isNotBlank()) onConfirm(name)
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            )
            Spacer(Modifier.height(DesignTokens.space4))
            Button(
                onClick = {
                    val name = textValue.text.trim()
                    if (name.isNotBlank()) onConfirm(name)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(DesignTokens.radiusMd),
            ) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: StyleFolder?,
    defaultName: String,
    isDefault: Boolean,
    onClick: () -> Unit,
) {
    val heritageAccent = LocalStitchPadColors.current.heritageAccent
    val coverUrl = folder?.coverStyleId // null = no cover → placeholder

    Card(
        shape = RoundedCornerShape(DesignTokens.radiusMd),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .clickable(onClick = onClick),
    ) {
        Column {
            // Cover image / placeholder
            Box(
                contentAlignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                if (coverUrl != null) {
                    SubcomposeAsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LoadingDots()
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    // Empty folder — surfaceVariant placeholder with icon
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
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                // Count badge (top-right)
                val count = folder?.styleCount ?: 0
                if (count > 0 || folder != null) {
                    val monoFamily = JetBrainsMonoFamily()
                    Text(
                        text = count.toString(),
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
                if (isDefault) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = heritageAccent,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = folder?.name ?: defaultName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StyleFoldersScreenLoadingPreview() {
    StitchPadTheme {
        StyleFoldersScreen(
            state = StyleFoldersState(isLoading = true, isInspiration = true),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StyleFoldersScreenPopulatedPreview() {
    StitchPadTheme {
        StyleFoldersScreen(
            state = StyleFoldersState(
                isLoading = false,
                isInspiration = true,
                folders = listOf(
                    StyleFolder(id = "f1", name = "Corset", styleCount = 3, createdAt = 0L, updatedAt = 0L),
                    StyleFolder(id = "f2", name = "Wedding looks", styleCount = 7, createdAt = 0L, updatedAt = 0L),
                ),
                limits = StyleCollectionLimits.forInspiration(SubscriptionTier.PRO),
            ),
            onAction = {},
        )
    }
}
