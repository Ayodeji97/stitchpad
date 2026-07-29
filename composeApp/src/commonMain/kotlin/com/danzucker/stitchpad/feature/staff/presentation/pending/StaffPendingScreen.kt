package com.danzucker.stitchpad.feature.staff.presentation.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.staff.presentation.components.AnimatedHourglass
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.staff_pending_chip_sub
import stitchpad.composeapp.generated.resources.staff_pending_leave
import stitchpad.composeapp.generated.resources.staff_pending_pill
import stitchpad.composeapp.generated.resources.staff_pending_step1_sub
import stitchpad.composeapp.generated.resources.staff_pending_step1_title
import stitchpad.composeapp.generated.resources.staff_pending_step2_sub
import stitchpad.composeapp.generated.resources.staff_pending_step2_title
import stitchpad.composeapp.generated.resources.staff_pending_step3_sub
import stitchpad.composeapp.generated.resources.staff_pending_step3_title
import stitchpad.composeapp.generated.resources.staff_pending_subtitle
import stitchpad.composeapp.generated.resources.staff_pending_title
import stitchpad.composeapp.generated.resources.staff_pending_workshop_fallback
import stitchpad.composeapp.generated.resources.staff_sign_out

// Success green — approval "done" step. (Matches the design system's success accent.)
private val SuccessGreen = Color(0xFF2D9E6B)

@Composable
fun StaffPendingRoot(
    workshopName: String?,
    onNavigateToHome: () -> Unit,
    onNavigateToRedeem: (declined: Boolean) -> Unit,
    onSignedOut: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: StaffPendingViewModel = koinViewModel { parametersOf(workshopName.orEmpty()) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            StaffPendingEvent.NavigateToHome -> onNavigateToHome()
            is StaffPendingEvent.NavigateToRedeem -> onNavigateToRedeem(event.declined)
            StaffPendingEvent.SignedOut -> onSignedOut()
            is StaffPendingEvent.ShowError -> scope.launch {
                val message = when (val text = event.message) {
                    is UiText.DynamicString -> text.value
                    is UiText.StringResourceText -> getString(text.id)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    StaffPendingScreen(state = state, snackbarHostState = snackbarHostState, onAction = viewModel::onAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPendingScreen(
    state: StaffPendingState,
    snackbarHostState: SnackbarHostState,
    onAction: (StaffPendingAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    TextButton(onClick = { onAction(StaffPendingAction.OnSignOutClick) }) {
                        Text(stringResource(Res.string.staff_sign_out))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedHourglass(modifier = Modifier.padding(top = DesignTokens.space4))

            PendingPill(modifier = Modifier.padding(top = DesignTokens.space5))

            Text(
                text = stringResource(Res.string.staff_pending_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = DesignTokens.space5),
            )
            Text(
                text = stringResource(Res.string.staff_pending_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = DesignTokens.space2),
            )

            WorkshopChip(
                name = state.workshopName ?: stringResource(Res.string.staff_pending_workshop_fallback),
                modifier = Modifier.padding(top = DesignTokens.space6),
            )

            StepTracker(modifier = Modifier.padding(top = DesignTokens.space6))

            Spacer(Modifier.height(DesignTokens.space8))
            TextButton(
                onClick = { onAction(StaffPendingAction.OnLeaveClick) },
                enabled = !state.isLeaving,
            ) {
                Text(
                    stringResource(Res.string.staff_pending_leave),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(DesignTokens.space4))
        }
    }
}

@Composable
private fun PendingPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(DesignTokens.saffron500.copy(alpha = 0.14f), RoundedCornerShape(DesignTokens.radiusFull))
            .border(1.dp, DesignTokens.saffron500.copy(alpha = 0.3f), RoundedCornerShape(DesignTokens.radiusFull))
            .padding(horizontal = DesignTokens.space3, vertical = DesignTokens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(DesignTokens.saffron500, RoundedCornerShape(DesignTokens.radiusFull)),
        )
        Text(
            text = stringResource(Res.string.staff_pending_pill).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = DesignTokens.saffron500,
        )
    }
}

@Composable
private fun WorkshopChip(name: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(DesignTokens.radiusXl))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(DesignTokens.radiusXl))
            .padding(DesignTokens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(listOf(DesignTokens.sienna300, DesignTokens.sienna500)),
                    RoundedCornerShape(DesignTokens.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.staff_pending_chip_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class StepStatus { DONE, CURRENT, TODO }

@Composable
private fun StepTracker(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        StepRow(
            status = StepStatus.DONE,
            index = 1,
            title = stringResource(Res.string.staff_pending_step1_title),
            subtitle = stringResource(Res.string.staff_pending_step1_sub),
            showLine = true,
        )
        StepRow(
            status = StepStatus.CURRENT,
            index = 2,
            title = stringResource(Res.string.staff_pending_step2_title),
            subtitle = stringResource(Res.string.staff_pending_step2_sub),
            showLine = true,
        )
        StepRow(
            status = StepStatus.TODO,
            index = 3,
            title = stringResource(Res.string.staff_pending_step3_title),
            subtitle = stringResource(Res.string.staff_pending_step3_sub),
            showLine = false,
        )
    }
}

@Composable
private fun StepRow(status: StepStatus, index: Int, title: String, subtitle: String, showLine: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepIcon(status = status, index = index)
            if (showLine) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .width(2.dp)
                        .height(22.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Column(modifier = Modifier.padding(bottom = DesignTokens.space3)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (status == StepStatus.TODO) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepIcon(status: StepStatus, index: Int) {
    val bg = when (status) {
        StepStatus.DONE -> SuccessGreen
        StepStatus.CURRENT -> MaterialTheme.colorScheme.primary
        StepStatus.TODO -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier.size(30.dp).background(bg, RoundedCornerShape(DesignTokens.radiusFull)),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            StepStatus.DONE -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            StepStatus.CURRENT ->
                Icon(Icons.Outlined.HourglassEmpty, null, tint = Color.White, modifier = Modifier.size(16.dp))
            StepStatus.TODO -> Text(
                index.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StaffPendingPreview() {
    StitchPadTheme {
        StaffPendingScreen(
            state = StaffPendingState(workshopName = "Ade Fashions"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StaffPendingPreviewDark() {
    StitchPadTheme(darkTheme = true) {
        StaffPendingScreen(
            state = StaffPendingState(workshopName = "Ade Fashions"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
