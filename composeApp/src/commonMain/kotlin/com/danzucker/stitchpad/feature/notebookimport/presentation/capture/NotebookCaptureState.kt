package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

import androidx.compose.runtime.Immutable
import com.danzucker.stitchpad.core.presentation.UiText

/** What one page tile shows. Upload failures are local; read failures come from the server. */
enum class CapturePageStatus { UPLOADING, READING, DONE, UPLOAD_FAILED, READ_FAILED, CAP_REACHED }

/**
 * One tile on the capture grid. [thumb] is only present for pages captured in this session
 * (the repository never returns images), so resumed pages show a placeholder.
 * Equality on [thumb] is by reference: the bytes are large and never mutated.
 */
@Immutable
class CapturePage(
    val key: String,
    val number: Int,
    val status: CapturePageStatus,
    val personCount: Int = 0,
    val thumb: ByteArray? = null,
) {
    fun numbered(n: Int) = CapturePage(key, n, status, personCount, thumb)

    override fun equals(other: Any?): Boolean =
        other is CapturePage && key == other.key && number == other.number && status == other.status &&
            personCount == other.personCount && thumb === other.thumb

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + number
        result = 31 * result + status.hashCode()
        result = 31 * result + personCount
        return result
    }
}

data class NotebookCaptureState(
    val pages: List<CapturePage> = emptyList(),
    val errorMessage: UiText? = null,
) {
    /** Enabled once at least one page has been read. */
    val canReview: Boolean get() = pages.any { it.status == CapturePageStatus.DONE }

    /** Customers found across all read pages. */
    val reviewCount: Int get() = pages.filter { it.status == CapturePageStatus.DONE }.sumOf { it.personCount }

    val hasFailedPage: Boolean
        get() = pages.any {
            it.status == CapturePageStatus.UPLOAD_FAILED || it.status == CapturePageStatus.READ_FAILED ||
                it.status == CapturePageStatus.CAP_REACHED
        }
}
