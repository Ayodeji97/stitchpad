package com.danzucker.stitchpad.feature.branding.presentation

/**
 * Shared transient state for the brand-logo upload tile. Drives the visual treatment
 * of the picker zone (empty/icon, loading-with-preview, uploaded preview, failed).
 *
 * `Uploaded.url` is the resolved Firebase Storage download URL — safe to feed Coil.
 * `Uploaded.path` is the Storage path, needed for delete on Skip / Remove.
 *
 * `Failed` carries the **original** picked bytes so the user can retry without re-opening the picker.
 */
sealed interface LogoUploadState {
    data object Empty : LogoUploadState
    data class Uploading(val previewBytes: ByteArray) : LogoUploadState {
        override fun equals(other: Any?): Boolean =
            other is Uploading && previewBytes.contentEquals(other.previewBytes)
        override fun hashCode(): Int = previewBytes.contentHashCode()
    }
    data class Uploaded(val url: String, val path: String) : LogoUploadState
    data class Failed(val previewBytes: ByteArray) : LogoUploadState {
        override fun equals(other: Any?): Boolean =
            other is Failed && previewBytes.contentEquals(other.previewBytes)
        override fun hashCode(): Int = previewBytes.contentHashCode()
    }
}
