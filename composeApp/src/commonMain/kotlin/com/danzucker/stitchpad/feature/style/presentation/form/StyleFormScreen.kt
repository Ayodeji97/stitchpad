package com.danzucker.stitchpad.feature.style.presentation.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.style_add_title
import stitchpad.composeapp.generated.resources.style_change_photo
import stitchpad.composeapp.generated.resources.style_description_label
import stitchpad.composeapp.generated.resources.style_description_placeholder
import stitchpad.composeapp.generated.resources.style_edit_title
import stitchpad.composeapp.generated.resources.style_folder_batch_over_cap
import stitchpad.composeapp.generated.resources.style_photos_selected
import stitchpad.composeapp.generated.resources.style_pick_photo
import stitchpad.composeapp.generated.resources.style_pick_photos
import stitchpad.composeapp.generated.resources.style_save_button

// MultiPhotoPreview lays thumbnails out 3 per row. The multi-pick batch size is
// driven by the folder's remaining capacity via state.maxPhotoSelection.
private const val MULTI_PREVIEW_COLUMNS = 3

@Composable
fun StyleFormRoot(onNavigateBack: () -> Unit) {
    val viewModel: StyleFormViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            StyleFormEvent.NavigateBack -> onNavigateBack()
            is StyleFormEvent.CapReached -> scope.launch {
                snackbarHostState.showSnackbar(
                    message = getString(Res.string.style_folder_batch_over_cap, event.cap),
                )
            }
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onAction(StyleFormAction.OnErrorDismiss)
        }
    }

    StyleFormScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        pickerScope = scope,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleFormScreen(
    state: StyleFormState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    pickerScope: CoroutineScope = rememberCoroutineScope(),
    onAction: (StyleFormAction) -> Unit
) {
    val title = if (state.isEditMode) {
        stringResource(Res.string.style_edit_title)
    } else {
        stringResource(Res.string.style_add_title)
    }

    val focusManager = LocalFocusManager.current

    // Key on maxPhotoSelection so the launcher is rebuilt once the VM resolves the
    // folder's remaining capacity (the remembered launcher won't otherwise pick up
    // a changed maxSelection).
    val imagePicker = key(state.allowMultiPhoto, state.maxPhotoSelection) {
        rememberImagePickerLauncher(
            selectionMode = if (state.allowMultiPhoto) {
                SelectionMode.Multiple(maxSelection = state.maxPhotoSelection)
            } else {
                SelectionMode.Single
            },
            scope = pickerScope,
            onResult = { byteArrays ->
                if (byteArrays.isNotEmpty()) onAction(StyleFormAction.OnPhotosPicked(byteArrays))
            }
        )
    }

    val canSave = state.description.trim().isNotBlank() &&
        (state.isEditMode || state.selectedPhotos.isNotEmpty()) &&
        !state.isSaving

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(StyleFormAction.OnNavigateBack) }) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space4),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space4)
            ) {
                PhotoSection(
                    state = state,
                    onPickClick = {
                        focusManager.clearFocus()
                        imagePicker.launch()
                    }
                )
                StyleDescriptionField(
                    value = state.description,
                    onValueChange = { onAction(StyleFormAction.OnDescriptionChange(it)) },
                    onDone = { focusManager.clearFocus() }
                )
                Spacer(Modifier.height(DesignTokens.space2))
                Button(
                    onClick = { onAction(StyleFormAction.OnSaveClick) },
                    enabled = canSave,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(Res.string.style_save_button),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoSection(
    state: StyleFormState,
    onPickClick: () -> Unit
) {
    if (state.selectedPhotos.size > 1) {
        MultiPhotoPreview(photos = state.selectedPhotos, onPickClick = onPickClick)
    } else {
        SinglePhotoPreview(state = state, onPickClick = onPickClick)
    }
}

@Composable
private fun SinglePhotoPreview(
    state: StyleFormState,
    onPickClick: () -> Unit
) {
    val model: Any? = when {
        state.selectedPhotos.isNotEmpty() -> state.selectedPhotos.first()
        state.existingStyle != null -> state.existingStyle.localPhotoPath ?: state.existingStyle.photoUrl
        else -> null
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                RoundedCornerShape(DesignTokens.radiusMd)
            )
            .clickable(onClick = onPickClick)
    ) {
        if (model != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = state.description.ifBlank { null },
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LoadingDots()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DesignTokens.space3)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(DesignTokens.radiusFull)
                        )
                        .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space1)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(Res.string.style_change_photo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(DesignTokens.space2))
                Text(
                    text = if (state.allowMultiPhoto) {
                        stringResource(Res.string.style_pick_photos)
                    } else {
                        stringResource(Res.string.style_pick_photo)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MultiPhotoPreview(
    photos: List<ByteArray>,
    onPickClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                RoundedCornerShape(DesignTokens.radiusMd)
            )
            .clickable(onClick = onPickClick)
            .padding(DesignTokens.space3)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1)
        ) {
            Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(Res.string.style_photos_selected, photos.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        // Manual grid (3 per row): nesting a lazy grid inside the form's vertical
        // scroll would conflict; the picked set is small and bounded.
        photos.chunked(MULTI_PREVIEW_COLUMNS).forEach { rowPhotos ->
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                rowPhotos.forEach { bytes ->
                    SubcomposeAsyncImage(
                        model = bytes,
                        contentDescription = null,
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
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(DesignTokens.radiusSm))
                    )
                }
                // Pad a short final row so thumbnails keep a consistent size.
                repeat(MULTI_PREVIEW_COLUMNS - rowPhotos.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleDescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )
    val interactionSource = remember { MutableInteractionSource() }
    val placeholder = stringResource(Res.string.style_description_placeholder)

    Column {
        Text(
            text = stringResource(Res.string.style_description_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = DesignTokens.space1)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = false,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = RoundedCornerShape(DesignTokens.radiusMd),
                            focusedBorderThickness = 1.dp,
                            unfocusedBorderThickness = 1.dp
                        )
                    }
                )
            }
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StyleFormScreenAddPreview() {
    StitchPadTheme {
        StyleFormScreen(state = StyleFormState(), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StyleFormScreenFilledPreview() {
    StitchPadTheme {
        StyleFormScreen(
            state = StyleFormState(
                description = "Red agbada with gold trim",
                selectedPhotos = listOf(ByteArray(0))
            ),
            onAction = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun StyleFormScreenMultiPhotoPreview() {
    StitchPadTheme {
        StyleFormScreen(
            state = StyleFormState(
                description = "Owambe inspiration",
                allowMultiPhoto = true,
                selectedPhotos = listOf(ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0))
            ),
            onAction = {}
        )
    }
}
