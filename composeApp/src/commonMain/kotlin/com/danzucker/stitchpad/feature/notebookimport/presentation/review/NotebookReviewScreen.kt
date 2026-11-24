@file:Suppress("TooManyFunctions")

package com.danzucker.stitchpad.feature.notebookimport.presentation.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.domain.model.CustomerGender
import com.danzucker.stitchpad.core.domain.model.MeasurementUnit
import com.danzucker.stitchpad.feature.freemium.presentation.cap.CustomerCapReachedSheet
import com.danzucker.stitchpad.feature.notebookimport.domain.CardValue
import com.danzucker.stitchpad.feature.notebookimport.domain.MergeNoteLabels
import com.danzucker.stitchpad.feature.notebookimport.presentation.MonoValueChip
import com.danzucker.stitchpad.feature.notebookimport.presentation.NotebookTouchTarget
import com.danzucker.stitchpad.feature.notebookimport.presentation.cardValueLabel
import com.danzucker.stitchpad.feature.notebookimport.presentation.genderText
import com.danzucker.stitchpad.feature.notebookimport.presentation.unitText
import com.danzucker.stitchpad.feature.notebookimport.presentation.warningColors
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.common_cancel
import stitchpad.composeapp.generated.resources.notebook_merge_date_label
import stitchpad.composeapp.generated.resources.notebook_merge_phone_label
import stitchpad.composeapp.generated.resources.notebook_review_all_done
import stitchpad.composeapp.generated.resources.notebook_review_already_customer
import stitchpad.composeapp.generated.resources.notebook_review_back_cd
import stitchpad.composeapp.generated.resources.notebook_review_combine
import stitchpad.composeapp.generated.resources.notebook_review_discard
import stitchpad.composeapp.generated.resources.notebook_review_discard_body
import stitchpad.composeapp.generated.resources.notebook_review_discard_confirm
import stitchpad.composeapp.generated.resources.notebook_review_discard_title
import stitchpad.composeapp.generated.resources.notebook_review_empty
import stitchpad.composeapp.generated.resources.notebook_review_keep_separate
import stitchpad.composeapp.generated.resources.notebook_review_match_body
import stitchpad.composeapp.generated.resources.notebook_review_match_confirm
import stitchpad.composeapp.generated.resources.notebook_review_match_confirmed
import stitchpad.composeapp.generated.resources.notebook_review_match_new
import stitchpad.composeapp.generated.resources.notebook_review_matches
import stitchpad.composeapp.generated.resources.notebook_review_no_name
import stitchpad.composeapp.generated.resources.notebook_review_no_values
import stitchpad.composeapp.generated.resources.notebook_review_open_card_cd
import stitchpad.composeapp.generated.resources.notebook_review_row_meta
import stitchpad.composeapp.generated.resources.notebook_review_same_person
import stitchpad.composeapp.generated.resources.notebook_review_same_person_body
import stitchpad.composeapp.generated.resources.notebook_review_save_checked
import stitchpad.composeapp.generated.resources.notebook_review_section_need_look
import stitchpad.composeapp.generated.resources.notebook_review_section_ready
import stitchpad.composeapp.generated.resources.notebook_review_section_saved
import stitchpad.composeapp.generated.resources.notebook_review_section_skipped
import stitchpad.composeapp.generated.resources.notebook_review_still_reading
import stitchpad.composeapp.generated.resources.notebook_review_summary
import stitchpad.composeapp.generated.resources.notebook_review_title
import stitchpad.composeapp.generated.resources.notebook_review_unclear

/**
 * Root of the review list. Navigation targets are callbacks wired by the nav graph:
 * [onNavigateToUpgrade] and [onNavigateToCustomers] are the cap sheet's upgrade and swap paths.
 */
@Composable
fun NotebookReviewRoot(
    onNavigateBack: () -> Unit,
    onOpenCard: (jobId: String, cardId: String) -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToCustomers: () -> Unit,
) {
    val viewModel: NotebookReviewViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            NotebookReviewEvent.NavigateBack -> onNavigateBack()
            is NotebookReviewEvent.NavigateToCard -> onOpenCard(event.jobId, event.cardId)
        }
    }

    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            viewModel.onAction(NotebookReviewAction.OnMessageShown)
            snackbarHostState.showSnackbar(message)
        }
    }

    NotebookReviewScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)

    state.capSheet?.let { pending ->
        CustomerCapReachedSheet(
            activeCount = pending.activeCount,
            customerCap = pending.customerCap,
            proMonthlyPrice = state.upgradeCtaPrice,
            onUpgradeClick = {
                viewModel.onAction(NotebookReviewAction.OnCapSheetDismiss)
                onNavigateToUpgrade()
            },
            onSwapClick = {
                viewModel.onAction(NotebookReviewAction.OnCapSheetDismiss)
                onNavigateToCustomers()
            },
            onDismiss = { viewModel.onAction(NotebookReviewAction.OnCapSheetDismiss) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookReviewScreen(
    state: NotebookReviewState,
    onAction: (NotebookReviewAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val mergeLabels = MergeNoteLabels(
        date = stringResource(Res.string.notebook_merge_date_label),
        phone = stringResource(Res.string.notebook_merge_phone_label),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.notebook_review_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onAction(NotebookReviewAction.OnNavigateBack) },
                        modifier = Modifier.size(NotebookTouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.notebook_review_back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (!state.isFinished && !state.isLoading) {
                ReviewBottomBar(
                    state = state,
                    onSave = { onAction(NotebookReviewAction.OnSaveAllClick) },
                    onDiscard = { onAction(NotebookReviewAction.OnDiscardClick) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { LoadingDots() }
        } else {
            val callbacks = RowCallbacks(
                onOpen = { onAction(NotebookReviewAction.OnCardClick(it)) },
                onConfirmMatch = { onAction(NotebookReviewAction.OnConfirmMatch(it)) },
                onRejectMatch = { onAction(NotebookReviewAction.OnRejectMatch(it)) },
                onMerge = { onAction(NotebookReviewAction.OnMergeClick(it, mergeLabels)) },
                onKeepSeparate = { onAction(NotebookReviewAction.OnKeepSeparateClick(it)) },
            )
            ReviewList(state = state, callbacks = callbacks, contentPadding = padding)
        }
    }

    if (state.showDiscardDialog) {
        DiscardDialog(
            onConfirm = { onAction(NotebookReviewAction.OnDiscardConfirm) },
            onDismiss = { onAction(NotebookReviewAction.OnDiscardDismiss) },
        )
    }
}

private class RowCallbacks(
    val onOpen: (String) -> Unit,
    val onConfirmMatch: (String) -> Unit,
    val onRejectMatch: (String) -> Unit,
    val onMerge: (List<String>) -> Unit,
    val onKeepSeparate: (List<String>) -> Unit,
)

@Composable
private fun ReviewList(state: NotebookReviewState, callbacks: RowCallbacks, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = DesignTokens.space5,
            end = DesignTokens.space5,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + DesignTokens.space4,
        ),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        item(key = "summary") {
            Text(
                text = stringResource(Res.string.notebook_review_summary, state.foundCount, state.needLookCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isFinished) {
            item(
                key = "done"
            ) { InfoBanner(text = stringResource(Res.string.notebook_review_all_done), success = true) }
        } else if (state.pagesReading > 0) {
            item(key = "reading") {
                InfoBanner(text = stringResource(Res.string.notebook_review_still_reading, state.pagesReading))
            }
        }
        if (state.foundCount == 0) {
            item(key = "empty") {
                Text(
                    text = stringResource(Res.string.notebook_review_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = DesignTokens.space8),
                )
            }
        }
        section("need", Res.string.notebook_review_section_need_look, state.needLook, callbacks)
        section("ready", Res.string.notebook_review_section_ready, state.ready, callbacks)
        section("saved", Res.string.notebook_review_section_saved, state.saved, callbacks, interactive = false)
        section("skipped", Res.string.notebook_review_section_skipped, state.skipped, callbacks, interactive = false)
    }
}

private fun LazyListScope.section(
    key: String,
    title: org.jetbrains.compose.resources.StringResource,
    rows: List<ReviewRow>,
    callbacks: RowCallbacks,
    interactive: Boolean = true,
) {
    if (rows.isEmpty()) return
    item(key = "header-$key") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = DesignTokens.space3),
        )
    }
    items(items = rows, key = { "$key-${it.cardId}" }) { row ->
        ReviewRowCard(row = row, callbacks = callbacks, interactive = interactive)
    }
}

@Composable
private fun InfoBanner(text: String, success: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.radiusMd))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(DesignTokens.space3),
    ) {
        Icon(
            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (success) successColor() else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun successColor() = if (LocalIsDarkTheme.current) DesignTokens.successDarkText else DesignTokens.success500

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewRowCard(row: ReviewRow, callbacks: RowCallbacks, interactive: Boolean) {
    val shape = RoundedCornerShape(DesignTokens.radiusLg)
    val name = row.name.ifBlank { stringResource(Res.string.notebook_review_no_name) }
    val openCd = stringResource(Res.string.notebook_review_open_card_cd, name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NotebookTouchTarget)
                .clickable(enabled = interactive, role = Role.Button, onClickLabel = openCd) {
                    callbacks.onOpen(row.cardId)
                }
                .padding(DesignTokens.space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.unclearCount > 0 && interactive) UnclearChip(row.unclearCount)
            }
            if (row.values.isEmpty()) {
                Text(
                    text = stringResource(Res.string.notebook_review_no_values),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
                ) {
                    row.values.forEach { value ->
                        MonoValueChip(
                            label = cardValueLabel(value, row.gender),
                            value = value.value,
                            unclear = interactive && (value.flagged || value.value.isBlank()),
                        )
                    }
                }
            }
            Text(
                text = stringResource(
                    Res.string.notebook_review_row_meta,
                    row.pageNumber,
                    genderText(row.gender),
                    unitText(row.unit),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (interactive) {
            row.duplicate?.let { DuplicatePanel(it, row.name, callbacks) }
            row.match?.let { MatchPanel(row.cardId, it, callbacks) }
        }
    }
}

@Composable
private fun UnclearChip(count: Int) {
    val warning = warningColors()
    Text(
        text = stringResource(Res.string.notebook_review_unclear, count),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = warning.content,
        modifier = Modifier
            .background(warning.background, RoundedCornerShape(DesignTokens.radiusFull))
            .padding(horizontal = DesignTokens.space2, vertical = 2.dp),
    )
}

@Composable
private fun PanelBox(content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space3),
    ) { content() }
}

@Composable
private fun DuplicatePanel(prompt: DuplicatePrompt, name: String, callbacks: RowCallbacks) {
    PanelBox {
        Text(
            text = stringResource(Res.string.notebook_review_same_person, prompt.otherName.ifBlank { name }),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (prompt.pageNumbers.size >= 2) {
            Text(
                text = stringResource(
                    Res.string.notebook_review_same_person_body,
                    prompt.pageNumbers[0],
                    prompt.pageNumbers[1],
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PanelActions(
            primary = stringResource(Res.string.notebook_review_combine),
            onPrimary = { callbacks.onMerge(prompt.cardIds) },
            secondary = stringResource(Res.string.notebook_review_keep_separate),
            onSecondary = { callbacks.onKeepSeparate(prompt.cardIds) },
        )
    }
}

@Composable
private fun MatchPanel(cardId: String, match: MatchSuggestion, callbacks: RowCallbacks) {
    PanelBox {
        if (match.confirmed) {
            Text(
                text = stringResource(Res.string.notebook_review_match_confirmed, match.customerName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.notebook_review_match_body, match.customerName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { callbacks.onRejectMatch(cardId) },
                modifier = Modifier.heightIn(min = NotebookTouchTarget),
            ) { Text(stringResource(Res.string.notebook_review_match_new), fontWeight = FontWeight.SemiBold) }
        } else {
            Text(
                text = stringResource(Res.string.notebook_review_already_customer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.notebook_review_matches, match.customerName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.notebook_review_match_body, match.customerName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PanelActions(
                primary = stringResource(Res.string.notebook_review_match_confirm, match.customerName),
                onPrimary = { callbacks.onConfirmMatch(cardId) },
                secondary = stringResource(Res.string.notebook_review_match_new),
                onSecondary = { callbacks.onRejectMatch(cardId) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelActions(primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space1),
    ) {
        Button(
            onClick = onPrimary,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            modifier = Modifier.heightIn(min = NotebookTouchTarget),
        ) { Text(primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        TextButton(
            onClick = onSecondary,
            modifier = Modifier.heightIn(min = NotebookTouchTarget),
        ) { Text(secondary, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ReviewBottomBar(state: NotebookReviewState, onSave: () -> Unit, onDiscard: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.navigationBarsPadding(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            StitchPadButton(
                text = stringResource(Res.string.notebook_review_save_checked, state.ready.size),
                onClick = onSave,
                enabled = state.canSaveAll,
                isLoading = state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DesignTokens.space5, end = DesignTokens.space5, top = DesignTokens.space3),
            )
            TextButton(
                onClick = onDiscard,
                enabled = !state.isSaving,
                modifier = Modifier.heightIn(min = NotebookTouchTarget),
            ) {
                Text(
                    text = stringResource(Res.string.notebook_review_discard),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DiscardDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.notebook_review_discard_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.notebook_review_discard_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(Res.string.notebook_review_discard_confirm), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel), fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

private val previewRows = listOf(
    ReviewRow(
        cardId = "c1",
        name = "Fatima Bello",
        pageNumber = 4,
        gender = CustomerGender.FEMALE,
        unit = MeasurementUnit.INCHES,
        values = listOf(
            CardValue("Waist", "3?", "waist", flagged = true),
            CardValue("Hip", "44", "hip_circumference", flagged = false),
            CardValue("Bust", "38", "bust_circumference", flagged = false),
        ),
        unclearCount = 1,
    ),
    ReviewRow(
        cardId = "c2",
        name = "Kemi Adebayo",
        pageNumber = 3,
        gender = CustomerGender.FEMALE,
        unit = MeasurementUnit.INCHES,
        values = listOf(CardValue("Bust", "36", "bust_circumference", flagged = false)),
        unclearCount = 0,
        duplicate = DuplicatePrompt(listOf("c2", "c4"), "Kemi Adebayo", listOf(3, 7)),
    ),
    ReviewRow(
        cardId = "c3",
        name = "Mr Okafor",
        pageNumber = 5,
        gender = CustomerGender.MALE,
        unit = MeasurementUnit.INCHES,
        values = listOf(CardValue("Chest", "42", "chest", flagged = false)),
        unclearCount = 0,
        match = MatchSuggestion("cust-1", "Emeka Okafor", confirmed = false),
    ),
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookReviewScreenPreview() {
    StitchPadTheme {
        NotebookReviewScreen(
            state = NotebookReviewState(
                isLoading = false,
                needLook = previewRows.take(1),
                ready = previewRows.drop(1),
                saved = listOf(previewRows[1].copy(cardId = "c9", name = "Bisi Ade", duplicate = null)),
                foundCount = 4,
                needLookCount = 1,
                pagesReading = 2,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookReviewScreenFinishedPreview() {
    StitchPadTheme(darkTheme = true) {
        NotebookReviewScreen(
            state = NotebookReviewState(
                isLoading = false,
                saved = previewRows.map { it.copy(match = null, duplicate = null, unclearCount = 0) },
                foundCount = 3,
                isFinished = true,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookReviewScreenDiscardPreview() {
    StitchPadTheme {
        NotebookReviewScreen(
            state = NotebookReviewState(
                isLoading = false,
                ready = previewRows.drop(2),
                foundCount = 1,
                showDiscardDialog = true,
            ),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun NotebookReviewScreenLoadingPreview() {
    StitchPadTheme {
        NotebookReviewScreen(state = NotebookReviewState(), onAction = {})
    }
}
