package com.danzucker.stitchpad.core.sharing

import coil3.Image

/**
 * Converts a decoded Coil image to PNG-encoded bytes for synchronous receipt rendering.
 * Returns null if conversion fails or the image type is not supported on this platform.
 *
 * Used at receipt-build time to pre-fetch the user's brand logo so the Android/iOS
 * renderers can draw it without awaiting Coil during Canvas/CGContext drawing.
 */
expect fun Image.toPngBytes(): ByteArray?
