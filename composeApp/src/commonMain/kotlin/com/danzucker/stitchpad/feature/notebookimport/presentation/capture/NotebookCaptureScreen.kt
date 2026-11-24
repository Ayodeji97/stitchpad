@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.core.media.rememberImageCaptureLauncher
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadButtonVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.notebook_capture_back_cd
import stitchpad.composeapp.generated.resources.notebook_capture_cap
import stitchpad.composeapp.generated.resources.notebook_capture_failed
import stitchpad.composeapp.generated.resources.notebook_capture_footnote
import stitchpad.composeapp.generated.resources.notebook_capture_from_gallery
import stitchpad.composeapp.generated.resources.notebook_capture_intro
import stitchpad.composeapp.generated.resources.notebook_capture_no_people
import stitchpad.composeapp.generated.resources.notebook_capture_page_found
import stitchpad.composeapp.generated.resources.notebook_capture_page_not_read
import stitchpad.composeapp.generated.resources.notebook_capture_page_not_uploaded
import stitchpad.composeapp.generated.resources.notebook_capture_page_uploading
import stitchpad.composeapp.generated.resources.notebook_capture_pages_count
import stitchpad.composeapp.generated.resources.notebook_capture_pages_count_one
import stitchpad.composeapp.generated.resources.notebook_capture_pages_title
import stitchpad.composeapp.generated.resources.notebook_capture_reading
import stitchpad.composeapp.generated.resources.notebook_capture_retry
import stitchpad.composeapp.generated.resources.notebook_capture_review
import stitchpad.composeapp.generated.resources.notebook_capture_take_photo
import stitchpad.composeapp.generated.resources.notebook_capture_title
import stitchpad.composeapp.generated.resources.notebook_capture_upload_failed

private const val GRID_COLUMNS = 3
private const val GALLERY_MAX_SELECTION = 20
private const val TILE_ASPECT = 0.8f
private const val SCRIM_ALPHA = 0.72f
private val SPINNER_SIZE = 22.dp
private val MIN_TOUCH_TARGET = 48.dp

@Composable
fun NotebookCaptureRoot(
    onNavigateBack: () -> Unit,
    onNavigateToReview: (jobId: String) -> Unit,
) {
    val viewModel: NotebookCaptureViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotebookCaptureEvent.NavigateBack -> onNavigateBack()
            is NotebookCaptureEvent.NavigateToReview -> onNavigateToReview(event.jobId)
        }
    }

    val errorMessage = state.errorMessage?.asString()
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            viewModel.onAction(NotebookCaptureAction.OnErrorDismiss)
            scope.launch { snackbarHostState.showSnackbar(errorMessage) }
        }
    }

    val captureLauncher = rememberImageCaptureLauncher { bytes ->
        if (bytes != null) viewModel.onAction(NotebookCaptureAction.OnPhotosPicked(listOf(bytes)))
    }
    val galleryPicker = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Multiple(maxSelection = GALLERY_MAX_SELECTION),
        scope = scope,
        onResult = { photos ->
            if (photos.isNotEmpty()) viewModel.onAction(NotebookCaptureAction.OnPhotosPicked(photos))
        },
    )

    NotebookCaptureScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onTakePhoto = captureLauncher::launch,
        onPickFromGallery = galleryPicker::launch,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookCaptureScreen(
    state: NotebookCaptureState,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onAction: (NotebookCaptureAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.notebook_capture_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(NotebookCaptureAction.OnNavigateBack) },
                        modifier = Modifier.size(MIN_TOUCH_TARGET),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.notebook_capture_back_cd),
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
        bottomBar = {
            ReviewBar(
                state = state,
                onReviewClick = { onAction(NotebookCaptureAction.OnReviewClick) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        CaptureGrid(
            state = state,
            onTakePhoto = onTakePhoto,
            onPickFromGallery = onPickFromGallery,
            onRetry = { key -> onAction(NotebookCaptureAction.OnRetryPage(key)) },
            contentPadding = paddingValues,
        )
    }
}

@Composable
private fun CaptureGrid(
    state: NotebookCaptureState,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRetry: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val fullWidth: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) =
        { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = DesignTokens.space5,
            end = DesignTokens.space5,
            top = contentPadding.calculateTopPadding() + DesignTokens.space1,
            bottom = contentPadding.calculateBottomPadding() + DesignTokens.space4,
        ),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        item(span = fullWidth) {
            Text(
                text = stringResource(Res.string.notebook_capture_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item(span = fullWidth) {
            CaptureButtons(onTakePhoto = onTakePhoto, onPickFromGallery = onPickFromGallery)
        }
        if (state.pages.isNotEmpty()) {
            item(span = fullWidth) { PagesHeader(count = state.pages.size) }
            items(items = state.pages, key = { it.key }) { page -> PageTile(page = page) }
            state.pages.filter { it.needsAttention() }.forEach { page ->
                item(key = "banner-${page.key}", span = fullWidth) {
                    FailedPageBanner(page = page, onRetry = { onRetry(page.key) })
                }
            }
        }
        item(span = fullWidth) {
            Text(
                text = stringResource(Res.string.notebook_capture_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun CapturePage.needsAttention() =
    status == CapturePageStatus.UPLOAD_FAILED ||
        status == CapturePageStatus.READ_FAILED ||
        status == CapturePageStatus.CAP_REACHED

@Composable
private fun CaptureButtons(onTakePhoto: () -> Unit, onPickFromGallery: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
        StitchPadButton(
            text = stringResource(Res.string.notebook_capture_take_photo),
            onClick = onTakePhoto,
            leadingIcon = Icons.Default.AddAPhoto,
            modifier = Modifier.weight(1f),
        )
        StitchPadButton(
            text = stringResource(Res.string.notebook_capture_from_gallery),
            onClick = onPickFromGallery,
            variant = StitchPadButtonVariant.Secondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PagesHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(Res.string.notebook_capture_pages_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (count == 1) {
                stringResource(Res.string.notebook_capture_pages_count_one)
            } else {
                stringResource(Res.string.notebook_capture_pages_count, count)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageTile(page: CapturePage) {
    val busy = page.status == CapturePageStatus.UPLOADING || page.status == CapturePageStatus.READING
    val shape = RoundedCornerShape(DesignTokens.radiusMd)
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space1)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(TILE_ASPECT)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape),
        ) {
            if (page.thumb != null) {
                SubcomposeAsyncImage(
                    model = page.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(DesignTokens.space8),
                )
            }
            if (busy) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA)),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SPINNER_SIZE),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        PageLabel(page = page)
    }
}

@Composable
private fun PageLabel(page: CapturePage) {
    val text = when (page.status) {
        CapturePageStatus.UPLOADING -> stringResource(Res.string.notebook_capture_page_uploading, page.number)
        CapturePageStatus.READING -> stringResource(Res.string.notebook_capture_reading, page.number)
        CapturePageStatus.DONE ->
            if (page.personCount == 0) {
                stringResource(Res.string.notebook_capture_no_people)
            } else {
                stringResource(Res.string.notebook_capture_page_found, page.number, page.personCount)
            }
        CapturePageStatus.UPLOAD_FAILED -> stringResource(Res.string.notebook_capture_page_not_uploaded, page.number)
        CapturePageStatus.READ_FAILED, CapturePageStatus.CAP_REACHED ->
            stringResource(Res.string.notebook_capture_page_not_read, page.number)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (page.status == CapturePageStatus.DONE && page.personCount > 0) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = successColor(),
                modifier = Modifier.size(DesignTokens.iconInline),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (page.needsAttention()) FontWeight.SemiBold else FontWeight.Medium,
            color = if (page.needsAttention()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun successColor() =
    if (LocalIsDarkTheme.current) DesignTokens.successDarkText else DesignTokens.success500

@Composable
private fun FailedPageBanner(page: CapturePage, onRetry: () -> Unit) {
    val isDark = LocalIsDarkTheme.current
    val title = when (page.status) {
        CapturePageStatus.CAP_REACHED -> stringResource(Res.string.notebook_capture_cap)
        CapturePageStatus.UPLOAD_FAILED -> stringResource(Res.string.notebook_capture_upload_failed)
        else -> stringResource(Res.string.notebook_capture_failed)
    }
    val pageLabel = if (page.status == CapturePageStatus.UPLOAD_FAILED) {
        stringResource(Res.string.notebook_capture_page_not_uploaded, page.number)
    } else {
        stringResource(Res.string.notebook_capture_page_not_read, page.number)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(if (isDark) DesignTokens.warningDarkBg else DesignTokens.warning50)
            .padding(start = DesignTokens.space3, top = DesignTokens.space2, bottom = DesignTokens.space2),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = DesignTokens.warning500,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = pageLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // The page cap cannot be fixed by retrying, so that banner carries no action.
        if (page.status != CapturePageStatus.CAP_REACHED) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
            ) {
                Text(
                    text = stringResource(Res.string.notebook_capture_retry),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Spacer(Modifier.size(DesignTokens.space3))
        }
    }
}

@Composable
private fun ReviewBar(state: NotebookCaptureState, onReviewClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            StitchPadButton(
                text = stringResource(Res.string.notebook_capture_review, state.reviewCount),
                onClick = onReviewClick,
                enabled = state.canReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.space5, vertical = DesignTokens.space3),
            )
        }
    }
}

private val previewThumb: ByteArray? = null

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookCaptureScreenEmptyPreview() {
    StitchPadTheme {
        NotebookCaptureScreen(
            state = NotebookCaptureState(),
            onTakePhoto = {},
            onPickFromGallery = {},
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookCaptureScreenUploadingPreview() {
    StitchPadTheme {
        NotebookCaptureScreen(
            state = NotebookCaptureState(
                pages = listOf(
                    CapturePage("p1", 1, CapturePageStatus.DONE, personCount = 6, thumb = previewThumb),
                    CapturePage("p2", 2, CapturePageStatus.READING, thumb = previewThumb),
                    CapturePage("local-3", 3, CapturePageStatus.UPLOADING, thumb = previewThumb),
                ),
            ),
            onTakePhoto = {},
            onPickFromGallery = {},
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookCaptureScreenFailedPreview() {
    StitchPadTheme {
        NotebookCaptureScreen(
            state = NotebookCaptureState(
                pages = listOf(
                    CapturePage("p1", 1, CapturePageStatus.DONE, personCount = 6),
                    CapturePage("p2", 2, CapturePageStatus.DONE, personCount = 7),
                    CapturePage("p3", 3, CapturePageStatus.READ_FAILED),
                    CapturePage("p4", 4, CapturePageStatus.CAP_REACHED),
                    CapturePage("local-5", 5, CapturePageStatus.UPLOAD_FAILED),
                ),
            ),
            onTakePhoto = {},
            onPickFromGallery = {},
            onAction = {},
        )
    }
}
