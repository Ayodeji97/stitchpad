package com.danzucker.stitchpad.feature.referral.presentation.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.referral_code_apply
import stitchpad.composeapp.generated.resources.referral_code_field_label
import stitchpad.composeapp.generated.resources.referral_code_field_placeholder
import stitchpad.composeapp.generated.resources.referral_code_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralCodeScreen(
    state: ReferralCodeState,
    snackbarHostState: SnackbarHostState,
    onAction: (ReferralCodeAction) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.referral_code_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(ReferralCodeAction.OnBackClick) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.codeInput,
                onValueChange = { onAction(ReferralCodeAction.OnCodeChange(it)) },
                label = { Text(stringResource(Res.string.referral_code_field_label)) },
                placeholder = { Text(stringResource(Res.string.referral_code_field_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            StitchPadButton(
                text = stringResource(Res.string.referral_code_apply),
                onClick = { onAction(ReferralCodeAction.OnApplyClick) },
                enabled = state.canSubmit,
                isLoading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ReferralCodeScreenPreview() {
    StitchPadTheme {
        ReferralCodeScreen(
            state = ReferralCodeState(codeInput = "ABCD1234"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun ReferralCodeScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        ReferralCodeScreen(
            state = ReferralCodeState(codeInput = "ABCD1234"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
