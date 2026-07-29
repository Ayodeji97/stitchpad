package com.danzucker.stitchpad.feature.staff.presentation.redeem

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import com.danzucker.stitchpad.util.ObserveAsEvents
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.staff_join_code_helper
import stitchpad.composeapp.generated.resources.staff_join_code_label
import stitchpad.composeapp.generated.resources.staff_join_code_valid
import stitchpad.composeapp.generated.resources.staff_join_cta
import stitchpad.composeapp.generated.resources.staff_join_no_code
import stitchpad.composeapp.generated.resources.staff_join_note
import stitchpad.composeapp.generated.resources.staff_join_subtitle
import stitchpad.composeapp.generated.resources.staff_join_subtitle_prefilled
import stitchpad.composeapp.generated.resources.staff_join_title
import stitchpad.composeapp.generated.resources.staff_pending_declined
import stitchpad.composeapp.generated.resources.staff_sign_out

@Composable
fun RedeemInviteRoot(
    onNavigateToPending: (workshopName: String) -> Unit,
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    declined: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: RedeemInviteViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Arriving back here because the owner declined a prior request — explain why.
    LaunchedEffect(Unit) {
        if (declined) snackbarHostState.showSnackbar(getString(Res.string.staff_pending_declined))
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RedeemInviteEvent.NavigateToPending -> onNavigateToPending(event.workshopName)
            RedeemInviteEvent.NavigateBack -> onNavigateBack()
            RedeemInviteEvent.SignedOut -> onSignedOut()
            is RedeemInviteEvent.ShowError -> scope.launch {
                val message = when (val text = event.message) {
                    is UiText.DynamicString -> text.value
                    is UiText.StringResourceText -> getString(text.id)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    RedeemInviteScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemInviteScreen(
    state: RedeemInviteState,
    snackbarHostState: SnackbarHostState,
    onAction: (RedeemInviteAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(onClick = { onAction(RedeemInviteAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(RedeemInviteAction.OnSignOutClick) }) {
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
            StaffCrest(modifier = Modifier.padding(top = DesignTokens.space4))

            Text(
                text = stringResource(Res.string.staff_join_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = DesignTokens.space3),
            )
            Text(
                text = stringResource(
                    if (state.prefilled) {
                        Res.string.staff_join_subtitle_prefilled
                    } else {
                        Res.string.staff_join_subtitle
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = DesignTokens.space2),
            )

            OutlinedTextField(
                value = formatInviteCode(state.code),
                onValueChange = { onAction(RedeemInviteAction.OnCodeChange(it)) },
                label = { Text(stringResource(Res.string.staff_join_code_label)) },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                singleLine = true,
                isError = state.codeError != null,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                supportingText = {
                    when {
                        state.codeError != null -> HelperRow(
                            icon = { Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error) },
                            text = state.codeError.asString(),
                            color = MaterialTheme.colorScheme.error,
                        )
                        state.canSubmit -> HelperRow(
                            icon = {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            text = stringResource(Res.string.staff_join_code_valid),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> Text(stringResource(Res.string.staff_join_code_helper))
                    }
                },
                shape = RoundedCornerShape(DesignTokens.radiusLg),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DesignTokens.space6),
            )

            StitchPadButton(
                text = stringResource(Res.string.staff_join_cta),
                onClick = { onAction(RedeemInviteAction.OnJoinClick) },
                enabled = state.canSubmit,
                isLoading = state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DesignTokens.space5),
            )

            InfoNote(
                text = stringResource(Res.string.staff_join_note),
                modifier = Modifier.padding(top = DesignTokens.space5),
            )

            Spacer(Modifier.height(DesignTokens.space4))
            TextButton(onClick = { }) {
                Text(stringResource(Res.string.staff_join_no_code))
            }
            Spacer(Modifier.height(DesignTokens.space4))
        }
    }
}

/** "XXXX-XXXX" from a normalised code for readability; a short raw code is shown as-is. */
private fun formatInviteCode(code: String): String =
    if (code.length > 4) "${code.take(4)}-${code.drop(4)}" else code

@Composable
private fun StaffCrest(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                    ),
                    shape = RoundedCornerShape(DesignTokens.radiusXl),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp),
            )
        }
        // Saffron heritage tick.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(26.dp)
                .background(DesignTokens.saffron500, RoundedCornerShape(DesignTokens.radiusFull)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, null, tint = DesignTokens.inkDark, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun HelperRow(icon: @Composable () -> Unit, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                RoundedCornerShape(DesignTokens.radiusLg),
            )
            .padding(DesignTokens.space4),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RedeemInvitePreview() {
    StitchPadTheme {
        RedeemInviteScreen(
            state = RedeemInviteState(code = "K7QP3RM9"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RedeemInviteErrorPreviewDark() {
    StitchPadTheme(darkTheme = true) {
        RedeemInviteScreen(
            state = RedeemInviteState(
                code = "K7QP0000",
                codeError = UiText.DynamicString("That code is invalid or has expired."),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
