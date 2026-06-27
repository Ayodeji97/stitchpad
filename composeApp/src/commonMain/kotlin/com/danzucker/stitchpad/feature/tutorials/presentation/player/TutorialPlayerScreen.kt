package com.danzucker.stitchpad.feature.tutorials.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.components.LoadingDots
import com.danzucker.stitchpad.ui.components.TutorialVideoPlayer
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.tutorials_player_close
import stitchpad.composeapp.generated.resources.tutorials_player_error
import stitchpad.composeapp.generated.resources.tutorials_player_retry

@Composable
fun TutorialPlayerScreen(
    state: TutorialPlayerState,
    onAction: (TutorialPlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.playableUri != null -> {
                // The player buffers the (often remote) clip before the first frame; show the
                // branded loading indicator over the black surface until it reports ready.
                TutorialVideoPlayer(
                    uri = state.playableUri,
                    modifier = Modifier.fillMaxSize(),
                    onLoadingChanged = { onAction(TutorialPlayerAction.OnBufferingChanged(it)) },
                )
                if (state.isBuffering) {
                    LoadingDots(color = Color.White)
                }
            }

            state.hasError -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.tutorials_player_error),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { onAction(TutorialPlayerAction.OnRetry) }) {
                        Text(stringResource(Res.string.tutorials_player_retry))
                    }
                }
            }

            else -> {
                LoadingDots(color = Color.White)
            }
        }

        IconButton(
            onClick = { onAction(TutorialPlayerAction.OnClose) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(8.dp)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.tutorials_player_close),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TutorialPlayerLoadingPreview() {
    StitchPadTheme {
        TutorialPlayerScreen(state = TutorialPlayerState(isLoading = true), onAction = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TutorialPlayerErrorPreview() {
    StitchPadTheme {
        TutorialPlayerScreen(
            state = TutorialPlayerState(isLoading = false, hasError = true),
            onAction = {},
        )
    }
}
