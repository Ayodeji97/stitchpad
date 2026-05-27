package com.danzucker.stitchpad.core.sharing

import coil3.BitmapImage
import coil3.Image
import org.jetbrains.skia.EncodedImageFormat

// On iOS, coil3.Bitmap is a typealias for org.jetbrains.skia.Bitmap (verified in coil-core 3.4.0
// sources). We convert to a Skia Image to access encodeToData(PNG), which is only available on
// the Skia Image type, not on Skia Bitmap directly.
actual fun Image.toPngBytes(): ByteArray? {
    val skiaBitmap = (this as? BitmapImage)?.bitmap ?: return null
    val skiaImage = org.jetbrains.skia.Image.makeFromBitmap(skiaBitmap)
    return skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
}
