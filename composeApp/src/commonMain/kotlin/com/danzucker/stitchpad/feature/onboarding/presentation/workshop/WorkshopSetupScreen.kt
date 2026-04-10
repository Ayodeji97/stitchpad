package com.danzucker.stitchpad.feature.onboarding.presentation.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.feature.onboarding.presentation.components.StitchPadLogo
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WorkshopSetupRoot(
    onNavigateToHome: () -> Unit,
    viewModel: WorkshopSetupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WorkshopSetupEvent.NavigateToHome -> onNavigateToHome()
            is WorkshopSetupEvent.ShowError -> {
                val message = event.message.let {
                    if (it is com.danzucker.stitchpad.core.presentation.UiText.DynamicString) {
                        it.value
                    } else {
                        it.toString()
                    }
                }
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    WorkshopSetupScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@Composable
fun WorkshopSetupScreen(
    state: WorkshopSetupState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (WorkshopSetupAction) -> Unit
) {
    val inputColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(DesignTokens.primary500),
                contentAlignment = Alignment.Center
            ) {
                StitchPadLogo(size = 64.dp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-24).dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = DesignTokens.space4, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set up your workshop",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Personalise StitchPad for your brand",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(28.dp))

                LabeledField(label = "Business name") {
                    OutlinedTextField(
                        value = state.businessName,
                        onValueChange = { if (it.length <= 50) onAction(WorkshopSetupAction.OnBusinessNameChange(it)) },
                        placeholder = { Text("e.g. Ade Fashions") },
                        isError = state.businessNameError != null,
                        supportingText = {
                            if (state.businessNameError != null) {
                                Text(
                                    text = state.businessNameError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = "Shown on your dashboard. You can change this later.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = inputColors,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(DesignTokens.space3))

                LabeledField(label = "Phone number") {
                    OutlinedTextField(
                        value = state.phone,
                        onValueChange = { onAction(WorkshopSetupAction.OnPhoneChange(it)) },
                        placeholder = { Text("+234 801 234 5678") },
                        isError = state.phoneError != null,
                        supportingText = {
                            if (state.phoneError != null) {
                                Text(
                                    text = state.phoneError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = "For your profile, not shared with customers.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = inputColors,
                        shape = RoundedCornerShape(DesignTokens.radiusMd),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { onAction(WorkshopSetupAction.OnContinueClick) },
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Continue")
                    }
                }
                Spacer(modifier = Modifier.height(DesignTokens.space4))

                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onAction(WorkshopSetupAction.OnSkipClick) }
                        .padding(DesignTokens.space2)
                )
                Spacer(modifier = Modifier.height(DesignTokens.space10))
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun WorkshopSetupScreenPreview() {
    StitchPadTheme {
        WorkshopSetupScreen(state = WorkshopSetupState(), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun WorkshopSetupScreenFilledPreview() {
    StitchPadTheme {
        WorkshopSetupScreen(
            state = WorkshopSetupState(
                businessName = "Ade Fashions",
                phone = "+2348012345678"
            ),
            onAction = {}
        )
    }
}
