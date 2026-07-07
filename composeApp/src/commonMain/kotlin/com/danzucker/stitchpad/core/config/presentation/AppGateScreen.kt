package com.danzucker.stitchpad.core.config.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danzucker.stitchpad.core.config.domain.AppGateDecision
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadMark
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.app_gate_maintenance_body
import stitchpad.composeapp.generated.resources.app_gate_maintenance_title
import stitchpad.composeapp.generated.resources.app_gate_update_body
import stitchpad.composeapp.generated.resources.app_gate_update_button
import stitchpad.composeapp.generated.resources.app_gate_update_title

/**
 * Break-glass app gate. Renders [content] normally, but replaces the whole app with
 * a blocking screen when the remote config forces an update or flips on maintenance
 * mode. Wrap the nav host with this inside the theme.
 */
@Composable
fun AppGateRoot(
    viewModel: AppGateViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    when (val decision = state.decision) {
        AppGateDecision.Allowed -> content()

        is AppGateDecision.ForceUpdate -> AppGateBlockingScreen(
            decision = decision,
            onUpdateClick = {
                decision.updateUrl?.let { url -> runCatching { uriHandler.openUri(url) } }
            },
        )

        is AppGateDecision.Maintenance -> AppGateBlockingScreen(
            decision = decision,
            onUpdateClick = {},
        )
    }
}

/**
 * Full-screen, stateless blocking surface for the force-update / maintenance
 * states. Previewable in isolation; [AppGateDecision.Allowed] renders nothing
 * (the Root never routes it here).
 */
@Composable
fun AppGateBlockingScreen(
    decision: AppGateDecision,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title: String
    val body: String
    val buttonLabel: String?
    when (decision) {
        is AppGateDecision.ForceUpdate -> {
            title = stringResource(Res.string.app_gate_update_title)
            body = decision.message ?: stringResource(Res.string.app_gate_update_body)
            // Hide the CTA when we have no store URL to send the user to.
            buttonLabel = decision.updateUrl?.let { stringResource(Res.string.app_gate_update_button) }
        }

        is AppGateDecision.Maintenance -> {
            title = stringResource(Res.string.app_gate_maintenance_title)
            body = decision.message ?: stringResource(Res.string.app_gate_maintenance_body)
            buttonLabel = null
        }

        AppGateDecision.Allowed -> return
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(DesignTokens.space6),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space5),
            ) {
                StitchPadMark(size = 72.dp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (buttonLabel != null) {
                    StitchPadButton(
                        text = buttonLabel,
                        onClick = onUpdateClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = DesignTokens.space2),
                    )
                }
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun AppGateForceUpdatePreview() {
    StitchPadTheme {
        AppGateBlockingScreen(
            decision = AppGateDecision.ForceUpdate(message = null, updateUrl = "https://example.com"),
            onUpdateClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun AppGateForceUpdateNoUrlDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        AppGateBlockingScreen(
            decision = AppGateDecision.ForceUpdate(
                message = "This version is no longer supported. Please update from your store.",
                updateUrl = null,
            ),
            onUpdateClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun AppGateMaintenancePreview() {
    StitchPadTheme {
        AppGateBlockingScreen(
            decision = AppGateDecision.Maintenance(message = null),
            onUpdateClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun AppGateMaintenanceDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        AppGateBlockingScreen(
            decision = AppGateDecision.Maintenance(message = "Back in ~10 minutes."),
            onUpdateClick = {},
        )
    }
}
