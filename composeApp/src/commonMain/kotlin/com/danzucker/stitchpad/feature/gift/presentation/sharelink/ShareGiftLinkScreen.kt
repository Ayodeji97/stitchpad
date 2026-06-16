package com.danzucker.stitchpad.feature.gift.presentation.sharelink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.gift.domain.GiftLink
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.gift_share_copy
import stitchpad.composeapp.generated.resources.gift_share_error
import stitchpad.composeapp.generated.resources.gift_share_retry
import stitchpad.composeapp.generated.resources.gift_share_share
import stitchpad.composeapp.generated.resources.gift_share_sheet_body
import stitchpad.composeapp.generated.resources.gift_share_sheet_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareGiftLinkScreen(
    state: ShareGiftLinkState,
    snackbarHostState: SnackbarHostState,
    onAction: (ShareGiftLinkAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.gift_share_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(ShareGiftLinkAction.OnBack) }) {
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
        when {
            state.isLoading -> LoadingState(padding)
            state.hasError || state.link == null -> ErrorState(padding, onAction)
            else -> LoadedState(link = state.link, padding = padding, onAction = onAction)
        }
    }
}

@Composable
private fun LoadingState(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorState(padding: PaddingValues, onAction: (ShareGiftLinkAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(DesignTokens.space4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.gift_share_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { onAction(ShareGiftLinkAction.OnRetry) }) {
            Text(stringResource(Res.string.gift_share_retry))
        }
    }
}

@Composable
private fun LoadedState(
    link: GiftLink,
    padding: PaddingValues,
    onAction: (ShareGiftLinkAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = DesignTokens.space4, vertical = DesignTokens.space4),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
    ) {
        Text(
            text = stringResource(Res.string.gift_share_sheet_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = link.url,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = JetBrainsMonoFamily(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(DesignTokens.radiusMd),
                )
                .padding(DesignTokens.space3),
        )

        Button(
            onClick = { onAction(ShareGiftLinkAction.OnShareClick) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(Res.string.gift_share_share),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = DesignTokens.space2),
            )
        }

        OutlinedButton(
            onClick = { onAction(ShareGiftLinkAction.OnCopyClick) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(DesignTokens.radiusLg),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(Res.string.gift_share_copy),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = DesignTokens.space2),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ShareGiftLinkScreenPreview() {
    StitchPadTheme {
        ShareGiftLinkScreen(
            state = ShareGiftLinkState(
                isLoading = false,
                link = GiftLink(token = "ABC", url = "https://getstitchpad.com/gift/ABC234XYZ"),
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ShareGiftLinkScreenErrorDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        ShareGiftLinkScreen(
            state = ShareGiftLinkState(isLoading = false, hasError = true),
            snackbarHostState = SnackbarHostState(),
            onAction = {},
        )
    }
}
