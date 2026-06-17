package com.danzucker.stitchpad.feature.gift.presentation.redeem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gift_redeem_code_label
import stitchpad.composeapp.generated.resources.gift_redeem_cta
import stitchpad.composeapp.generated.resources.gift_redeem_heading
import stitchpad.composeapp.generated.resources.gift_redeem_subtitle
import stitchpad.composeapp.generated.resources.gift_redeem_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemGiftScreen(
    state: RedeemGiftState,
    snackbarHostState: SnackbarHostState,
    onAction: (RedeemGiftAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.gift_redeem_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(RedeemGiftAction.OnBack) }) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        RedeemGiftContent(
            state = state,
            onAction = onAction,
            padding = padding,
        )
    }

    if (state.showAcceptSheet) {
        AcceptGiftSheet(
            accountEmail = state.accountEmail,
            isRedeeming = state.isRedeeming,
            onConfirm = { onAction(RedeemGiftAction.OnConfirmAccept) },
            onDismiss = { onAction(RedeemGiftAction.OnDismissSheet) },
        )
    }
}

@Composable
private fun RedeemGiftContent(
    state: RedeemGiftState,
    onAction: (RedeemGiftAction) -> Unit,
    padding: PaddingValues,
) {
    // Local TextFieldValue (forwarding only .text) so VM-driven state changes never
    // land the cursor mid-string — see the Compose TextFieldValue cursor-desync note.
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.code)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Text(
            text = stringResource(Res.string.gift_redeem_heading),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.gift_redeem_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onAction(RedeemGiftAction.OnCodeChange(it.text))
            },
            label = { Text(stringResource(Res.string.gift_redeem_code_label)) },
            singleLine = true,
            enabled = !state.isRedeeming,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onAction(RedeemGiftAction.OnRedeemClick) },
            enabled = state.canRedeem,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = stringResource(Res.string.gift_redeem_cta),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RedeemGiftScreenPreview() {
    StitchPadTheme {
        RedeemGiftScreen(
            state = RedeemGiftState(code = "ABC234"),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun RedeemGiftScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        RedeemGiftScreen(
            state = RedeemGiftState(),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
