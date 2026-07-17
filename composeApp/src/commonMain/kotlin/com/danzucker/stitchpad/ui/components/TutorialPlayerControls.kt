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
