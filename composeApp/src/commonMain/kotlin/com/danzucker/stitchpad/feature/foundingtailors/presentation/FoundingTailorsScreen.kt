package com.danzucker.stitchpad.feature.foundingtailors.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.StitchPadButton
import com.danzucker.stitchpad.ui.components.StitchPadButtonVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.founding_tailors_back_cd
import stitchpad.composeapp.generated.resources.founding_tailors_how_point1
import stitchpad.composeapp.generated.resources.founding_tailors_how_point2
import stitchpad.composeapp.generated.resources.founding_tailors_how_point3
import stitchpad.composeapp.generated.resources.founding_tailors_how_point4
import stitchpad.composeapp.generated.resources.founding_tailors_how_point5
import stitchpad.composeapp.generated.resources.founding_tailors_how_title
import stitchpad.composeapp.generated.resources.founding_tailors_share_cta
import stitchpad.composeapp.generated.resources.founding_tailors_subtitle
import stitchpad.composeapp.generated.resources.founding_tailors_title
import stitchpad.composeapp.generated.resources.founding_tailors_view_board

/**
 * Stateless Founding Tailors screen. Renders the program pitch, a "share my
 * invite link" CTA and a "view leaderboard" link-out. All state lives in
 * [FoundingTailorsViewModel]; this composable only reflects [state] and forwards
 * taps as [FoundingTailorsAction]s.
 *
 * [onNavigateBack] is defaulted so the two-arg (state, onAction) call form —
 * used by the previews — stays valid; the Root supplies the real back handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundingTailorsScreen(
    state: FoundingTailorsState,
    onAction: (FoundingTailorsAction) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.founding_tailors_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.founding_tailors_back_cd),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DesignTokens.space4),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.space4),
        ) {
            Spacer(Modifier.height(DesignTokens.space2))

            Text(
                text = stringResource(Res.string.founding_tailors_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Error slot — surfaces a failed link mint without blocking the retry
            // path (the share button re-enables once a link resolves).
            state.error?.let { error ->
                Text(
                    text = error.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(DesignTokens.space8),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingDots()
                }
            } else {
                StitchPadButton(
                    text = stringResource(Res.string.founding_tailors_share_cta),
                    onClick = { onAction(FoundingTailorsAction.ShareLink) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.referralUrl != null,
                    leadingIcon = Icons.Filled.Share,
                )
                StitchPadButton(
                    text = stringResource(Res.string.founding_tailors_view_board),
                    onClick = { onAction(FoundingTailorsAction.OpenLeaderboard) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = StitchPadButtonVariant.Secondary,
                    enabled = state.referralUrl != null,
                    leadingIcon = Icons.Filled.EmojiEvents,
                )
            }

            // "How points work" explainer — points are awarded only when a
            // referral reaches the server-verified `qualified` milestone, so set
            // expectations up front (no points for a bare install/signup).
            Spacer(Modifier.height(DesignTokens.space2))
            Text(
                text = stringResource(Res.string.founding_tailors_how_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                listOf(
                    Res.string.founding_tailors_how_point1,
                    Res.string.founding_tailors_how_point2,
                    Res.string.founding_tailors_how_point3,
                    Res.string.founding_tailors_how_point4,
                    Res.string.founding_tailors_how_point5,
                ).forEach { pointRes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(pointRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(DesignTokens.space2))
        }
    }
}

private val PREVIEW_STATE = FoundingTailorsState(
    isLoading = false,
    referralUrl = "https://link.getstitchpad.com/r/CODE0",
    error = null,
)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FoundingTailorsScreenLightPreview() {
    StitchPadTheme(darkTheme = false) {
        FoundingTailorsScreen(state = PREVIEW_STATE, onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun FoundingTailorsScreenDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        FoundingTailorsScreen(state = PREVIEW_STATE, onAction = {})
    }
}
