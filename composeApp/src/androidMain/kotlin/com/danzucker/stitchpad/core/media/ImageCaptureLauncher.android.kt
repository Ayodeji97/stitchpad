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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null && file.exists()) {
            val bytes = file.toDownscaledJpegBytes()
            file.delete()
            currentOnResult(bytes)
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

private const val MAX_DIM = 1920
private const val JPEG_QUALITY = 85

@Suppress("ReturnCount")
private fun File.toDownscaledJpegBytes(): ByteArray? {
    val raw = runCatching { readBytes() }.getOrNull() ?: return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return raw

    var sampleSize = 1
    while (w / (sampleSize * 2) >= MAX_DIM || h / (sampleSize * 2) >= MAX_DIM) {
        sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        raw, 0, raw.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize }
    ) ?: return raw

    val rotation = runCatching { ExifInterface(absolutePath).rotationDegrees() }.getOrDefault(0)
    val oriented = if (rotation == 0) decoded else decoded.rotated(rotation)

    val longEdge = maxOf(oriented.width, oriented.height).toFloat()
    val scale = longEdge / MAX_DIM
    val finalBitmap = if (scale > 1f) {
        val newW = (oriented.width / scale).toInt().coerceAtLeast(1)
        val newH = (oriented.height / scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(oriented, newW, newH, true)
    } else {
        oriented
    }

    val out = ByteArrayOutputStream()
    finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    return out.toByteArray()
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
