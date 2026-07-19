# iOS Tutorial Player Controls Overlay — Design

**Date:** 2026-07-17
**Status:** Approved
**Context:** PR #277 fixed the black-screen bug by replacing `AVPlayerViewController` with a bare
`AVPlayerLayer`, which has no built-in transport controls. The documented trade-off was "custom
controls can follow" — this is that follow-up. iOS-only: Android's Media3 `PlayerView` keeps its
stock controller.

## Goal

Restore playback controls on the iOS tutorial player: play/pause, ±10s skip, scrubber with
elapsed/total time. Tap the video to show/hide controls; auto-hide while playing.

## Scope

- **In:** new shared controls composable (commonMain), iOS wiring, removal of the now-dead
  native tap path.
- **Out:** Android player changes, the common `expect` signature, `TutorialPlayerScreen`,
  ViewModel, Close/error/retry UI, captions, mute, fullscreen.

## Components

### 1. `TutorialPlayerControls` (new, commonMain `ui/components/`)

Stateless, previewable overlay composable. No platform types.

**API:**

```kotlin
@Composable
fun TutorialPlayerControls(
    isPlaying: Boolean,
    positionSeconds: Double,
    durationSeconds: Double?,   // null until the player item reports a finite duration
    onPlayPause: () -> Unit,
    onSeekBy: (Double) -> Unit, // ±10.0 from the skip buttons
    onSeekTo: (Double) -> Unit, // absolute seconds from scrubber release
    modifier: Modifier = Modifier,
)
```

**Layout** (full-size overlay Box):

```
┌─────────────────────────────┐
│                             │
│    ⏪10   ▶/⏸   10⏩         │  center row, Icons.Filled.{Replay10, PlayArrow/Pause, Forward10}
│                             │
│ 0:42 ━━━━━●────── 3:15      │  bottom row: elapsed · Slider · total
└─────────────────────────────┘
```

- ~35% black scrim behind controls when visible.
- White-on-scrim in BOTH light and dark mode — deliberate (video surface is always black),
  matching the existing Close button on this screen.
- Time labels in JetBrains Mono (app's numeric font) so ticking elapsed time doesn't jitter.
- Unknown duration: labels show `--:--`, scrubber and skip buttons disabled.
- All labels/content descriptions from compose.resources strings (no hardcoded text; `&apos;`
  not `\'` if an apostrophe is ever needed).

**Internal transient state** (Compose-internal category, like `LazyListState` — allowed outside
the ViewModel):

- `controlsVisible` — starts `true`; tap on the surface toggles it.
- Auto-hide: 3s after playback is running with no interaction; any control interaction resets
  the timer; while paused, never auto-hides.
- Scrub drag: while dragging, the thumb follows the finger (local drag value) and player
  position updates do not yank it; `onSeekTo` fires once on release (same local-override
  pattern as the TextFieldValue cursor rule).

**`formatPlaybackTime(seconds: Double?): String`** — small pure util beside the composable.
`m:ss` (hours roll into minutes: 61:05 for an hour-long clip), `--:--` for null/NaN/negative.
No `String.format` (JVM-only; breaks the iOS link) — manual zero-padding.

### 2. iOS wiring (`TutorialVideoPlayer.ios.kt`)

- `TutorialPlayback` gains:
  - `isPlaying(): Boolean` (`player.rate > 0f`)
  - `positionSeconds(): Double` (`CMTimeGetSeconds(player.currentTime())`)
  - `durationSeconds(): Double?` (`CMTimeGetSeconds(currentItem.duration)`, NaN/indefinite → null)
  - `seekTo(seconds: Double)` — clamped to `[0, duration]`, `CMTimeMakeWithSeconds(_, 600)`
- The existing 500 ms status poll additionally publishes a `mutableStateOf` snapshot
  (isPlaying/position/duration) that drives the overlay. No new loops, no KVO.
- Composable body becomes `Box { UIKitView(surface); TutorialPlayerControls(...) }` — the
  Compose overlay sits above the interop view and owns all gestures.
- End-of-clip behavior unchanged: rewind to frame 0 paused; center button shows ▶ and replays.
- Foreground auto-resume and loading-dots plumbing unchanged.

### 3. `PlayerLayerContainerView.ios.kt` cleanup

Remove the `onTap` constructor param and `touchesEnded` override — added in #277 solely for
the tutorial tap-to-toggle, now superseded by the Compose overlay. `VideoBackground` never
passed it.

## Edge cases

- **Duration not yet known** (remote clip still resolving): scrubber/skips disabled, `--:--`.
- **Seek while buffering:** allowed; the existing poll already mirrors waiting state into the
  caller's loading dots.
- **URI change** (`key(playback)` rebuild): overlay remembers per-playback; visibility resets
  to visible — correct for a fresh clip.
- **Backgrounding:** system pause → on return, foreground observer resumes; controls unchanged.

## Testing

- **commonTest:** `formatPlaybackTime` cases — 0:00, 0:59, 1:00, 10:42, hour-long (61:05),
  null/NaN → `--:--`. kotlin.test; test names letters/digits/spaces/hyphens only (iOS gate).
- **Previews:** playing + paused states (`@file:Suppress` for Detekt TooManyFunctions if needed).
- **Gates:** `compileKotlinIosSimulatorArm64`, `:composeApp:testDebugUnitTest`, `detekt`.
- **Manual QA (Daniel, Pro Max sim):** play, pause via button, ±10s, scrub, tap-to-hide/show,
  auto-hide after 3s, replay after clip end, background/foreground resume. Claude
  builds/installs/screenshots; Daniel taps.

## Non-goals / follow-ups

- Unifying Android onto this overlay (possible later: the component is commonMain and
  platform-free by design; only Android wiring would change).
- Mute, captions, fullscreen, playback speed.
