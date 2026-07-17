# iOS Tutorial Player Controls Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore playback controls (play/pause, ±10s skip, scrubber) on the iOS tutorial player via a shared Compose overlay, replacing the controls lost when PR #277 swapped `AVPlayerViewController` for a bare `AVPlayerLayer`.

**Architecture:** A stateless `TutorialPlayerControls` composable in commonMain (previewable, no platform types) rendered only by the iOS actual, layered in a Box above the `UIKitView` player surface. The existing 500 ms status poll in `TutorialVideoPlayer.ios.kt` publishes a position/duration/isPlaying snapshot that drives the overlay. Android is untouched.

**Tech Stack:** Compose Multiplatform 1.11.1, Material3, materialIconsExtended (already a dependency), AVFoundation via Kotlin/Native cinterop, kotlin.test for commonTest.

**Spec:** `docs/2026-07-17-ios-tutorial-player-controls-design.md`

## Global Constraints

- Branch: `feat/ios-tutorial-player-controls` (already created; spec committed on it). Never push to main.
- NO `String.format` or any JVM-only API in commonMain — breaks the iOS link. Manual zero-padding.
- Test names: letters/digits/spaces/hyphens only — NO backticks (iOS test compile gate). Repo convention is `camelCase_underscore` names.
- All user-visible strings via compose.resources (`strings.xml`); apostrophes as `&apos;`, never `\'`.
- Controls are white-on-scrim in BOTH light and dark mode — deliberate, video surface is always black.
- Transient UI state (visibility, drag) lives in the component via `remember` — allowed under the "Compose-internal state" exception; everything else stays out of composables.
- Gradle: never pipe gradle output through `tail`/`head` without capturing the real exit code (`PIPESTATUS`); prefer redirecting to a file and checking `$?`.
- iOS gate is `./gradlew :composeApp:compileKotlinIosSimulatorArm64` — Android-only compiles prove nothing for iosMain.
- Commit messages end with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: `formatPlaybackTime` util (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/TutorialPlayerControls.kt` (util only in this task; composable added in Task 2)
- Test: `composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/ui/components/PlaybackTimeFormatTest.kt`

**Interfaces:**
- Produces: `internal fun formatPlaybackTime(seconds: Double?): String` — `m:ss` (minutes roll past 59, e.g. `61:05`); `--:--` for null/NaN/infinite/negative.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.danzucker.stitchpad.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackTimeFormatTest {

    @Test
    fun zeroSeconds_formatsAsZeroZero() {
        assertEquals("0:00", formatPlaybackTime(0.0))
    }

    @Test
    fun subMinute_formatsWithoutRounding() {
        // 59.9 must not round up to 1:00 — truncate, don't round.
        assertEquals("0:59", formatPlaybackTime(59.9))
    }

    @Test
    fun exactMinute_rollsToMinutes() {
        assertEquals("1:00", formatPlaybackTime(60.0))
    }

    @Test
    fun tenMinutes_formatsMinutesAndPaddedSeconds() {
        assertEquals("10:42", formatPlaybackTime(642.0))
    }

    @Test
    fun overAnHour_rollsIntoMinutes() {
        // No hour segment by design: 1h 1m 5s shows as 61:05.
        assertEquals("61:05", formatPlaybackTime(3665.0))
    }

    @Test
    fun nullSeconds_showsPlaceholder() {
        assertEquals("--:--", formatPlaybackTime(null))
    }

    @Test
    fun nanSeconds_showsPlaceholder() {
        assertEquals("--:--", formatPlaybackTime(Double.NaN))
    }

    @Test
    fun negativeSeconds_showsPlaceholder() {
        assertEquals("--:--", formatPlaybackTime(-1.0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.ui.components.PlaybackTimeFormatTest" > /tmp/t1.log 2>&1; echo "exit=$?"; tail -20 /tmp/t1.log`
Expected: compilation FAILS with unresolved reference `formatPlaybackTime`.

- [ ] **Step 3: Write minimal implementation**

Create `TutorialPlayerControls.kt` containing only:

```kotlin
package com.danzucker.stitchpad.ui.components

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.danzucker.stitchpad.ui.components.PlaybackTimeFormatTest" > /tmp/t1.log 2>&1; echo "exit=$?"; tail -5 /tmp/t1.log`
Expected: `exit=0`, BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/TutorialPlayerControls.kt composeApp/src/commonTest/kotlin/com/danzucker/stitchpad/ui/components/PlaybackTimeFormatTest.kt
git commit -m "feat(tutorials): playback time formatter for player controls

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: `TutorialPlayerControls` composable + strings + previews

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/TutorialPlayerControls.kt` (add composable above the Task 1 util)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` (next to the existing `tutorials_player_*` strings at ~line 1714)

**Interfaces:**
- Consumes: `formatPlaybackTime(Double?): String` from Task 1; `JetBrainsMonoFamily()` from `ui.theme.Type`; `StitchPadTheme` for previews.
- Produces (Task 3 calls this exact signature):

```kotlin
@Composable
fun TutorialPlayerControls(
    isPlaying: Boolean,
    positionSeconds: Double,
    durationSeconds: Double?,
    onPlayPause: () -> Unit,
    onSeekBy: (Double) -> Unit,
    onSeekTo: (Double) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Add string resources**

In `strings.xml`, directly after `tutorials_player_close`:

```xml
    <string name="tutorials_player_play">Play</string>
    <string name="tutorials_player_pause">Pause</string>
    <string name="tutorials_player_back_10">Back 10 seconds</string>
    <string name="tutorials_player_forward_10">Forward 10 seconds</string>
```

- [ ] **Step 2: Write the composable**

Replace the full content of `TutorialPlayerControls.kt` with (keeps the Task 1 util at the bottom):

```kotlin
@file:Suppress("TooManyFunctions")

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

private const val AUTO_HIDE_DELAY_MS = 3_000L
private const val SKIP_SECONDS = 10.0

/**
 * Transport controls overlay for the tutorial video player: center play/pause with ±10s skip,
 * bottom scrubber with elapsed/total time. Tap anywhere to toggle visibility; auto-hides after
 * [AUTO_HIDE_DELAY_MS] while playing (never while paused or mid-drag). While the scrubber is
 * dragged the thumb follows the finger and player position updates are ignored; [onSeekTo]
 * fires once on release. Until [durationSeconds] is known the scrubber and skips are disabled
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
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
                    seekEnabled = durationSeconds != null,
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
                    durationSeconds = durationSeconds,
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
```

Notes for the implementer:
- `androidx.compose.ui.tooling.preview.Preview` in commonMain is correct in this repo — see `TutorialPlayerScreen.kt:25` for precedent.
- If detekt does NOT flag TooManyFunctions, drop the `@file:Suppress` line (only add suppressions detekt actually requires; check the detekt run in Step 3).

- [ ] **Step 3: Verify it compiles on both targets + tests still pass + detekt**

Run: `./gradlew :composeApp:testDebugUnitTest :composeApp:compileKotlinIosSimulatorArm64 detekt > /tmp/t2.log 2>&1; echo "exit=$?"; tail -15 /tmp/t2.log`
Expected: `exit=0`. If detekt flags anything, fix it (do not blanket-suppress beyond what's flagged).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/TutorialPlayerControls.kt composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(tutorials): shared TutorialPlayerControls overlay composable

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: iOS wiring + native tap-path cleanup

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/ui/components/TutorialVideoPlayer.ios.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/ui/components/PlayerLayerContainerView.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/TutorialVideoPlayer.kt` (KDoc only)

**Interfaces:**
- Consumes: `TutorialPlayerControls(isPlaying, positionSeconds, durationSeconds, onPlayPause, onSeekBy, onSeekTo, modifier)` from Task 2.
- Produces: no new public API; `TutorialVideoPlayer` expect signature unchanged.

- [ ] **Step 1: Extend `TutorialPlayback`** (in `TutorialVideoPlayer.ios.kt`)

Add these members to the class (after `isWaitingToPlay()`), plus imports `platform.AVFoundation.currentTime`, `platform.AVFoundation.duration`, `platform.CoreMedia.CMTimeGetSeconds`, `platform.CoreMedia.CMTimeMakeWithSeconds`:

```kotlin
    fun isPlaying(): Boolean = player.rate > 0f

    fun positionSeconds(): Double = CMTimeGetSeconds(player.currentTime())

    /** Item duration in seconds, or null while unknown (still resolving / indefinite). */
    fun durationSeconds(): Double? {
        val item = player.currentItem ?: return null
        val seconds = CMTimeGetSeconds(item.duration)
        return seconds.takeIf { it.isFinite() && it > 0.0 }
    }

    /** Seeks to [seconds] clamped to the playable range; returns the clamped target. */
    fun seekTo(seconds: Double): Double {
        val upperBound = durationSeconds()
        val clamped = if (upperBound != null) {
            seconds.coerceIn(0.0, upperBound)
        } else {
            seconds.coerceAtLeast(0.0)
        }
        player.seekToTime(CMTimeMakeWithSeconds(clamped, preferredTimescale = 600))
        return clamped
    }

    fun seekBy(deltaSeconds: Double): Double = seekTo(positionSeconds() + deltaSeconds)
```

If the named argument `preferredTimescale` doesn't resolve, use the positional form `CMTimeMakeWithSeconds(clamped, 600)`.

- [ ] **Step 2: Rework the composable body**

In the same file:

1. Add a private snapshot holder near the top of the file (after the constants):

```kotlin
/** UI-facing playback snapshot published by the status poll for the controls overlay. */
private data class PlayerSnapshot(
    val isPlaying: Boolean = false,
    val positionSeconds: Double = 0.0,
    val durationSeconds: Double? = null,
)
```

2. In `TutorialVideoPlayer`, add snapshot state after the `playback` remember (imports: `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`):

```kotlin
    var snapshot by remember(playback) { mutableStateOf(PlayerSnapshot()) }
```

3. In the existing poll `LaunchedEffect`, publish the snapshot every tick — after the `currentOnLoadingChanged(...)` line, before `delay(STATUS_POLL_MS)`:

```kotlin
            snapshot = PlayerSnapshot(
                isPlaying = playback.isPlaying(),
                positionSeconds = playback.positionSeconds(),
                durationSeconds = playback.durationSeconds(),
            )
```

4. Replace the `key(playback) { UIKitView(...) }` block with a Box hosting surface + overlay. Seeks update the snapshot position optimistically with the clamped value `seekTo`/`seekBy` return — `seekToTime` is async, so re-reading the player right after would yank the scrubber thumb back until the next poll tick. (Imports: `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.fillMaxSize`.)

```kotlin
    // key(playback): the interop factory runs once per node and captures the layer, so a new
    // uri (hence new playback) must rebuild the node or the old player would stay on screen.
    key(playback) {
        Box(modifier = modifier) {
            UIKitView(
                factory = { PlayerLayerContainerView(playback.playerLayer) },
                modifier = Modifier.fillMaxSize(),
            )
            TutorialPlayerControls(
                isPlaying = snapshot.isPlaying,
                positionSeconds = snapshot.positionSeconds,
                durationSeconds = snapshot.durationSeconds,
                onPlayPause = {
                    playback.togglePlayPause()
                    snapshot = snapshot.copy(isPlaying = playback.isPlaying())
                },
                onSeekBy = { delta ->
                    snapshot = snapshot.copy(positionSeconds = playback.seekBy(delta))
                },
                onSeekTo = { seconds ->
                    snapshot = snapshot.copy(positionSeconds = playback.seekTo(seconds))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
```

Interop hit-testing note: the Compose overlay sits above the interop view and consumes all touches it hits; the native view no longer needs (or receives) taps. If taps do NOT reach the overlay on the simulator during Task 4 QA, add `properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false)` to the `UIKitView` call (import `androidx.compose.ui.viewinterop.UIKitInteropProperties`).

5. Update the file-header KDoc of the iOS actual: replace the sentence "tapping the video toggles play/pause; a finished clip rewinds to the start so a tap replays it" with "a [TutorialPlayerControls] overlay provides play/pause, ±10s skip, and a scrubber; a finished clip rewinds to the start (paused) so play replays it", and replace "The cost is no native scrubber/caption controls; custom Compose controls are the follow-up if needed." with "Transport controls are the shared [TutorialPlayerControls] Compose overlay layered above the interop surface."

- [ ] **Step 3: Remove the dead native tap path**

`PlayerLayerContainerView.ios.kt` — remove the `onTap` constructor param, the `touchesEnded` override, the `UIEvent` import, and the `[onTap]` KDoc paragraph. Resulting class:

```kotlin
@OptIn(ExperimentalForeignApi::class)
internal class PlayerLayerContainerView(
    private val playerLayer: AVPlayerLayer,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.setFrame(bounds)
        CATransaction.commit()
    }
}
```

Verify `VideoBackground.ios.kt` constructs it without `onTap` (it should — the param was tutorial-only); if it passes `onTap`, stop and re-read that file before proceeding.

- [ ] **Step 4: Update the common expect KDoc**

In `TutorialVideoPlayer.kt` (commonMain), change the first KDoc sentence from "Full-bleed how-to video player WITH native transport controls (play/pause, scrubber, mute, fullscreen, and captions where the source provides them)." to "Full-bleed how-to video player WITH transport controls — Media3's native controller on Android; the shared [TutorialPlayerControls] Compose overlay on iOS (play/pause, ±10s skip, scrubber)."

- [ ] **Step 5: Verify iOS gate + full checks**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:testDebugUnitTest detekt > /tmp/t3.log 2>&1; echo "exit=$?"; tail -15 /tmp/t3.log`
Expected: `exit=0`.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/ui/components/TutorialVideoPlayer.ios.kt composeApp/src/iosMain/kotlin/com/danzucker/stitchpad/ui/components/PlayerLayerContainerView.ios.kt composeApp/src/commonMain/kotlin/com/danzucker/stitchpad/ui/components/TutorialVideoPlayer.kt
git commit -m "feat(ios): wire TutorialPlayerControls overlay into tutorial player

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Simulator verification, PR, reviews

**Files:** none (verification + PR only)

- [ ] **Step 1: Build and install on the QA simulator** (iPhone 17 Pro Max, UDID `BBBE5A8A-23F1-4853-9B88-1B9CEE66D862` — booted, Fola signed in)

```bash
cd iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,id=BBBE5A8A-23F1-4853-9B88-1B9CEE66D862' \
  -derivedDataPath build/DerivedData build > /tmp/xcodebuild.log 2>&1; echo "exit=$?"
xcrun simctl install BBBE5A8A-23F1-4853-9B88-1B9CEE66D862 iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/StitchPad.app
xcrun simctl launch BBBE5A8A-23F1-4853-9B88-1B9CEE66D862 com.danzucker.stitchpad
```

Expected: `exit=0`; app launches. Reinstall preserves Fola's sign-in.

- [ ] **Step 2: Manual QA with Daniel** (Claude screenshots via `xcrun simctl io <udid> screenshot`, Daniel taps — simctl cannot synthesize taps)

Smoke checklist: open a tutorial → controls visible over video → auto-hide after ~3s → tap shows them → pause (icon flips, no auto-hide while paused) → ⏪10 / 10⏩ jump correctly → scrub without thumb yanking → let clip end → play replays from start → background then foreground → playback resumes.

- [ ] **Step 3: Push and open PR**

```bash
git push -u origin feat/ios-tutorial-player-controls
gh pr create --title "feat(ios): tutorial player controls overlay — play/pause, ±10s, scrubber" --body "$(cat <<'EOF'
## Summary
- Restores playback controls on the iOS tutorial player, lost in #277 when AVPlayerViewController (black-screen bug) was replaced by a bare AVPlayerLayer
- New shared `TutorialPlayerControls` overlay (commonMain, previewable): center play/pause + ±10s skips, bottom scrubber with mono time labels, tap-to-toggle with 3s auto-hide
- iOS actual publishes a position/duration/isPlaying snapshot from the existing 500ms poll; seeks are optimistic to avoid scrubber snap-back
- Removes the now-dead native tap path from PlayerLayerContainerView
- Android (Media3 native controller), expect signature, screen, and ViewModel untouched

Spec: docs/2026-07-17-ios-tutorial-player-controls-design.md

## Manual smoke test (iOS sim or device)
1. Settings → Tutorials → open any tutorial video
2. Controls appear over the video; they fade out after ~3s of playback
3. Tap the video — controls reappear; tap again — they hide
4. Pause via center button — icon flips to play; controls stay while paused
5. ⏪10 / 10⏩ jump back/forward 10s; elapsed label follows
6. Drag the scrubber — thumb follows finger, releases to that position, no snap-back
7. Let the clip end — it rewinds to the first frame paused; play replays it
8. Background the app mid-playback, return — playback resumes
9. Android quick check: tutorial player unchanged (stock controller)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Reviews (both, per review rotation)**

- Cursor Bugbot: runs on the PR automatically — read and address its findings.
- Codex: `codex review -c model=gpt-5.5` (default model blocks — see feedback_codex_review_model). Address findings.

Expected: both reviews addressed (fix or explicit justification) before merge. Do NOT merge without Daniel's QA sign-off (Step 2).
