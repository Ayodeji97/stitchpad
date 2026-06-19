package com.danzucker.stitchpad.core.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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

private const val MAX_DIM = 1920
private const val JPEG_QUALITY = 85

// Reads the captured JPEG and runs it through the same downscale used for gallery
// picks (see AndroidImageCompressor). Falls back to the original bytes if the file
// can't be decoded; null only when it can't be read at all.
private fun File.toDownscaledJpegBytes(): ByteArray? {
    val raw = runCatching { readBytes() }.getOrNull() ?: return null
    return downscaleJpegBytes(raw, MAX_DIM, JPEG_QUALITY) ?: raw
}
