package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import com.danzucker.stitchpad.feature.notebookimport.domain.PageBox

/** Smallest crop side, as a fraction of the page, so a degenerate box still shows readable context. */
const val MIN_CROP_FRACTION = 0.1f

/** Context kept around the model's box on every side, as a fraction of the page. */
private const val CROP_PADDING = 0.02f

/** A crop of the page photo in normalised 0..1 coordinates, always inside the page. */
data class CropRegion(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * The page area to show for a card. The box comes from the model and is not validated on the server,
 * so it is treated as untrusted: non-finite, empty or fully off-page boxes give null (show the whole
 * page); anything else is padded, clamped to the page and grown to [MIN_CROP_FRACTION].
 */
@Suppress("ReturnCount")
fun cropRegion(box: PageBox?): CropRegion? {
    if (box == null) return null
    val parts = listOf(box.x, box.y, box.w, box.h)
    if (parts.any { !it.isFinite() } || box.w <= 0f || box.h <= 0f) return null
    val horizontal = span(box.x, box.w) ?: return null
    val vertical = span(box.y, box.h) ?: return null
    return CropRegion(horizontal.first, vertical.first, horizontal.second, vertical.second)
}

/** Start and length of one axis inside 0..1, or null when the box misses the page on this axis. */
private fun span(start: Float, length: Float): Pair<Float, Float>? {
    val end = start + length
    if (start >= 1f || end <= 0f) return null
    var from = (start - CROP_PADDING).coerceIn(0f, 1f)
    var to = (end + CROP_PADDING).coerceIn(0f, 1f)
    if (to - from < MIN_CROP_FRACTION) {
        val center = (from + to) / 2f
        from = (center - MIN_CROP_FRACTION / 2f).coerceIn(0f, 1f - MIN_CROP_FRACTION)
        to = from + MIN_CROP_FRACTION
    }
    return from to (to - from)
}
