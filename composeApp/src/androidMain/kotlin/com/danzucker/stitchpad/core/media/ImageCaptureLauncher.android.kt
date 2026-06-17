package com.danzucker.stitchpad.core.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Stable
actual class ImageCaptureLauncher internal constructor(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

@Composable
actual fun rememberImageCaptureLauncher(
    onResult: (ByteArray?) -> Unit
): ImageCaptureLauncher {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val captureScope = rememberCoroutineScope()
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null && file.exists()) {
            captureScope.launch {
                val bytes = withContext(Dispatchers.Default) {
                    try {
                        file.toDownscaledJpegBytes()
                    } finally {
                        file.delete()
                    }
                }
                currentOnResult(bytes)
            }
        } else {
            file?.delete()
            currentOnResult(null)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCapture(context, captureLauncher) { pendingFile = it }
        } else {
            currentOnResult(null)
        }
    }

    return remember {
        ImageCaptureLauncher {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                launchCapture(context, captureLauncher) { pendingFile = it }
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

private fun launchCapture(
    context: Context,
    launcher: ActivityResultLauncher<Uri>,
    setPending: (File) -> Unit
) {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    setPending(file)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    launcher.launch(uri)
}

private const val MAX_DIM = 1600
private const val JPEG_QUALITY = 80

// Single ownership point for recycling: the `finally` below recycles every
// bitmap this function allocated, each exactly once (recycleIfDistinct guards
// against double-recycling shared instances). Any RuntimeException from decode/
// rotate/scale propagates after cleanup runs.
@Suppress("ReturnCount")
private fun File.toDownscaledJpegBytes(): ByteArray? {
    val raw = runCatching { readBytes() }.getOrNull() ?: return null

    val dimensions = raw.imageDimensions() ?: return raw
    val decoded = BitmapFactory.decodeByteArray(
        raw, 0, raw.size,
        BitmapFactory.Options().apply { inSampleSize = dimensions.sampleSize() }
    ) ?: return raw

    val rotation = runCatching {
        ExifInterface(absolutePath).rotationDegrees()
    }.getOrDefault(0)

    var oriented: Bitmap? = null
    var scaled: Bitmap? = null
    try {
        oriented = if (rotation == 0) decoded else decoded.rotated(rotation)
        scaled = oriented.scaledToMaxDimension()
        return scaled.toJpegBytes()
    } finally {
        scaled?.recycleIfDistinct(oriented ?: decoded, decoded)
        oriented?.recycleIfDistinct(decoded)
        decoded.recycle()
    }
}

private data class ImageDimensions(
    val width: Int,
    val height: Int
) {
    fun sampleSize(): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= MAX_DIM || height / (sampleSize * 2) >= MAX_DIM) {
            sampleSize *= 2
        }
        return sampleSize
    }
}

private fun Bitmap.toJpegBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    return out.toByteArray()
}

private fun ByteArray.imageDimensions(): ImageDimensions? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        ImageDimensions(bounds.outWidth, bounds.outHeight)
    } else {
        null
    }
}

private fun Bitmap.scaledToMaxDimension(): Bitmap {
    val longEdge = maxOf(width, height).toFloat()
    val scale = longEdge / MAX_DIM
    return if (scale > 1f) {
        val newW = (width / scale).toInt().coerceAtLeast(1)
        val newH = (height / scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(this, newW, newH, true)
    } else {
        this
    }
}

private fun Bitmap.recycleIfDistinct(vararg others: Bitmap) {
    if (others.none { this === it }) {
        recycle()
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun ExifInterface.rotationDegrees(): Int = when (
    getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
) {
    ExifInterface.ORIENTATION_ROTATE_90 -> 90
    ExifInterface.ORIENTATION_ROTATE_180 -> 180
    ExifInterface.ORIENTATION_ROTATE_270 -> 270
    else -> 0
}
