package com.danzucker.stitchpad.feature.goals.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.ui.components.NairaAmountField
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.goals_setup_amount_label
import stitchpad.composeapp.generated.resources.goals_setup_amount_placeholder
import stitchpad.composeapp.generated.resources.goals_setup_back_cd
import stitchpad.composeapp.generated.resources.goals_setup_quick_picks
import stitchpad.composeapp.generated.resources.goals_setup_save
import stitchpad.composeapp.generated.resources.goals_setup_subtitle
import stitchpad.composeapp.generated.resources.goals_setup_title

private val QUICK_PICKS = listOf(100_000L, 300_000L, 500_000L, 1_000_000L)

@Composable
fun GoalSetupRoot(
    onNavigateBack: () -> Unit,
    viewModel: GoalSetupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            GoalSetupEvent.NavigateBack -> onNavigateBack()
        }
    }

    val errorText = state.errorMessage?.asString()
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.onAction(GoalSetupAction.OnErrorDismiss)
        }
    }

    GoalSetupScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSetupScreen(
    state: GoalSetupState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (GoalSetupAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.goals_setup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(GoalSetupAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.goals_setup_back_cd)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (state.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                CircularProgressIndicator()
            }
        } else {
            GoalSetupContent(
                state = state,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(DesignTokens.space4)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GoalSetupContent(
    state: GoalSetupState,
    onAction: (GoalSetupAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
        modifier = modifier
    ) {
        Text(
            text = stringResource(Res.string.goals_setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        NairaAmountField(
            value = state.targetAmountInput,
            onValueChange = { onAction(GoalSetupAction.OnTargetAmountChange(it)) },
            label = { Text(stringResource(Res.string.goals_setup_amount_label)) },
            placeholder = { Text(stringResource(Res.string.goals_setup_amount_placeholder)) },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(Res.string.goals_setup_quick_picks).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)
        ) {
            QUICK_PICKS.forEach { amount ->
                val selected = state.targetAmountInput == amount.toString()
                FilterChip(
                    selected = selected,
                    onClick = { onAction(GoalSetupAction.OnQuickPickClick(amount)) },
                    label = { Text(text = "₦${formatThousands(amount)}") }
                )
            }
        }

        Spacer(Modifier.height(DesignTokens.space2))

        Button(
            onClick = { onAction(GoalSetupAction.OnSaveClick) },
            enabled = state.canSave,
            shape = RoundedCornerShape(DesignTokens.radiusMd),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(Res.string.goals_setup_save),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatThousands(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun GoalSetupScreenPreview() {
    StitchPadTheme {
        GoalSetupScreen(
            state = GoalSetupState(targetAmountInput = "300000", isLoading = false),
            onAction = {}
        )
    }
}
