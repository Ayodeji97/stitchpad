package com.danzucker.stitchpad.core.media

import com.preat.peekaboo.image.picker.ResizeOptions

/**
 * Single compression target for ALL order photos (style + fabric, order form + order-detail
 * inline), gallery side. Peekaboo resizes a picked image to fit within [width]x[height] and
 * re-encodes at [ResizeOptions.compressionQuality], but only when the source exceeds its
 * `resizeThresholdBytes` (left at peekaboo's 1 MB default) — so small images pass through
 * untouched and are never needlessly re-encoded. The camera path mirrors this target via the
 * MAX_DIM / JPEG_QUALITY constants in ImageCaptureLauncher.{android,ios}.kt.
 */
val orderPhotoResizeOptions: ResizeOptions = ResizeOptions(
    width = 1600,
    height = 1600,
    compressionQuality = 0.8,
)
