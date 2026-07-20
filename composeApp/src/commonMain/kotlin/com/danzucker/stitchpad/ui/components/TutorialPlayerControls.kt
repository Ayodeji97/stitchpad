package com.danzucker.stitchpad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.JetBrainsMonoFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.tutorials_player_back_10
import stitchpad.composeapp.generated.resources.tutorials_player_forward_10
import stitchpad.composeapp.generated.resources.tutorials_player_pause
import stitchpad.composeapp.generated.resources.tutorials_player_play
import stitchpad.composeapp.generated.resources.tutorials_player_toggle_controls
import kotlin.math.abs

private const val AUTO_HIDE_DELAY_MS = 3_000L
private const val SKIP_SECONDS = 10.0

/**
 * Transport controls overlay for the tutorial video player: center play/pause with ±10s skip,
 * bottom scrubber with elapsed/total time. Tap anywhere to toggle visibility; auto-hides after
 * [AUTO_HIDE_DELAY_MS] while playing (never while paused or mid-drag). While the scrubber is
 * dragged the thumb follows the finger and player position updates are ignored; [onSeekTo]
 * fires once on release. Whenever playback stops ([isPlaying] flips false — clip end, system
 * pause) hidden controls reveal themselves so the replay/play affordance is always visible.
 * Until [durationSeconds] is known the scrubber and skips are disabled
 * and time labels show a placeholder. Deliberately white-on-scrim in both color modes — the
 * video surface behind it is always black.
 *
 * Stateless toward playback: the caller owns the player and feeds [isPlaying]/[positionSeconds].
 * Currently rendered by the iOS actual only; Android keeps Media3's native controller.
 */
@Composable
fun TutorialPlayerControls(
    isPlaying: Boolean,
    positionSeconds: Double,
    durationSeconds: Double?,
    onPlayPause: () -> Unit,
    onSeekBy: (Double) -> Unit,
    onSeekTo: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeDurationSeconds = sanitizeDuration(durationSeconds)
    var controlsVisible by remember { mutableStateOf(true) }
    var dragSeconds by remember { mutableStateOf<Double?>(null) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    val isDragging = dragSeconds != null

    LaunchedEffect(controlsVisible, isPlaying, isDragging, interactionNonce) {
        if (controlsVisible && isPlaying && !isDragging) {
            delay(AUTO_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // Clip end and system-initiated pauses (calls, Siri) stop playback while the controls may
    // be auto-hidden; reveal them so the replay/play affordance is never a hidden tap away.
    LaunchedEffect(isPlaying) {
        if (!isPlaying) controlsVisible = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(Res.string.tutorials_player_toggle_controls),
            ) { controlsVisible = !controlsVisible },
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                TransportButtons(
                    isPlaying = isPlaying,
                    seekEnabled = safeDurationSeconds != null,
                    onPlayPause = {
                        onPlayPause()
                        interactionNonce++
                    },
                    onBack = {
                        onSeekBy(-SKIP_SECONDS)
                        interactionNonce++
                    },
                    onForward = {
                        onSeekBy(SKIP_SECONDS)
                        interactionNonce++
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
                ScrubberRow(
                    positionSeconds = dragSeconds ?: positionSeconds,
                    durationSeconds = safeDurationSeconds,
                    onDrag = { dragSeconds = it },
                    onDragFinished = {
                        dragSeconds?.let(onSeekTo)
                        dragSeconds = null
                        interactionNonce++
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TransportButtons(
    isPlaying: Boolean,
    seekEnabled: Boolean,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.4f),
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        IconButton(onClick = onBack, enabled = seekEnabled, colors = colors, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Filled.Replay10,
                contentDescription = stringResource(Res.string.tutorials_player_back_10),
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onPlayPause, colors = colors, modifier = Modifier.size(72.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) Res.string.tutorials_player_pause else Res.string.tutorials_player_play,
                ),
                modifier = Modifier.size(52.dp),
            )
        }
        IconButton(onClick = onForward, enabled = seekEnabled, colors = colors, modifier = Modifier.size(56.dp)) {
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = stringResource(Res.string.tutorials_player_forward_10),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun ScrubberRow(
    positionSeconds: Double,
    durationSeconds: Double?,
    onDrag: (Double) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonoTimeLabel(formatPlaybackTime(if (durationSeconds != null) positionSeconds else null))
        Slider(
            value = durationSeconds
                ?.let { positionSeconds.toFloat().coerceIn(0f, it.toFloat()) }
                ?: 0f,
            onValueChange = { onDrag(it.toDouble()) },
            onValueChangeFinished = onDragFinished,
            valueRange = 0f..(durationSeconds?.toFloat() ?: 1f),
            enabled = durationSeconds != null,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.38f),
                disabledThumbColor = Color.White.copy(alpha = 0.24f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.24f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.24f),
            ),
            modifier = Modifier.weight(1f),
        )
        MonoTimeLabel(formatPlaybackTime(durationSeconds))
    }
}

@Composable
private fun MonoTimeLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = JetBrainsMonoFamily(),
    )
}

/**
 * Formats a playback position/duration as `m:ss` for the tutorial player's time labels.
 * Minutes roll past 59 (an hour-long clip shows `61:05`) — tutorial clips are short, an hour
 * segment would be noise. Unknown/invalid values (null, NaN, infinite, negative) render as
 * `--:--`, the "duration not yet reported" placeholder.
 */
internal fun formatPlaybackTime(seconds: Double?): String {
    if (seconds == null || !seconds.isFinite() || seconds < 0.0) return "--:--"
    val totalSeconds = seconds.toInt()
    val minutes = totalSeconds / 60
    val secs = totalSeconds % 60
    val paddedSecs = if (secs < 10) "0$secs" else secs.toString()
    return "$minutes:$paddedSecs"
}

/**
 * Normalizes a player-reported duration for use as the scrubber's `valueRange` upper bound.
 * A non-null but non-finite or non-positive value (a plausible transient from a native player)
 * would otherwise flow straight into Material3's Slider and crash it; treat it the same as
 * "duration not yet known" and collapse to null so the disabled-state UI covers it.
 */
internal fun sanitizeDuration(durationSeconds: Double?): Double? =
    durationSeconds?.takeIf { it.isFinite() && it > 0.0 }

private const val SEEK_SETTLE_TOLERANCE_SECONDS = 1.0
private const val SEEK_SETTLE_MAX_POLL_TICKS = 6

/**
 * Whether an in-flight seek can hand position reporting back to the player. Seeks are async:
 * until the player lands, it still reports the pre-seek position, and publishing that would
 * snap the scrubber thumb back. The seek is settled once the player is within
 * [SEEK_SETTLE_TOLERANCE_SECONDS] of the target — or after [SEEK_SETTLE_MAX_POLL_TICKS]
 * status-poll ticks, so a seek that never converges (dead stream) can't pin the UI to the
 * target forever.
 */
internal fun isSeekSettled(actualSeconds: Double, targetSeconds: Double, pollTicksElapsed: Int): Boolean =
    abs(actualSeconds - targetSeconds) <= SEEK_SETTLE_TOLERANCE_SECONDS ||
        pollTicksElapsed >= SEEK_SETTLE_MAX_POLL_TICKS

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TutorialPlayerControlsPlayingPreview() {
    StitchPadTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            TutorialPlayerControls(
                isPlaying = true,
                positionSeconds = 42.0,
                durationSeconds = 195.0,
                onPlayPause = {},
                onSeekBy = {},
                onSeekTo = {},
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun TutorialPlayerControlsPausedUnknownDurationPreview() {
    StitchPadTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            TutorialPlayerControls(
                isPlaying = false,
                positionSeconds = 0.0,
                durationSeconds = null,
                onPlayPause = {},
                onSeekBy = {},
                onSeekTo = {},
            )
        }
    }
}
