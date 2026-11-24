package com.danzucker.stitchpad.feature.notebookimport.presentation.capture

import androidx.compose.runtime.Immutable
import com.danzucker.stitchpad.core.presentation.UiText

/**
 * What one page tile shows. Upload failures are local; read failures come from the server. READ_STUCK is a
 * page the server has been reading for over 10 minutes: it can be retried.
 */
enum class CapturePageStatus { UPLOADING, READING, READ_STUCK, DONE, UPLOAD_FAILED, READ_FAILED, CAP_REACHED }

/** The look-up of an open job to resume. Capture stays disabled until it succeeds. */
enum class ResumeLookup { LOADING, FAILED, READY }

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
    val lookup: ResumeLookup = ResumeLookup.LOADING,
    /** An earlier open scan (not the current one) with customers still to save; shown as a banner. */
    val earlierJobId: String? = null,
) {
    /** Photos can be taken only once we know whether to resume a job, so no job is ever orphaned. */
    val canCapture: Boolean get() = lookup == ResumeLookup.READY

    /** Enabled once at least one page has been read. */
    val canReview: Boolean get() = pages.any { it.status == CapturePageStatus.DONE }

    /** Customers found across all read pages. */
    val reviewCount: Int get() = pages.filter { it.status == CapturePageStatus.DONE }.sumOf { it.personCount }

    val hasFailedPage: Boolean
        get() = pages.any {
            it.status == CapturePageStatus.UPLOAD_FAILED || it.status == CapturePageStatus.READ_FAILED ||
                it.status == CapturePageStatus.READ_STUCK || it.status == CapturePageStatus.CAP_REACHED
        }
}
