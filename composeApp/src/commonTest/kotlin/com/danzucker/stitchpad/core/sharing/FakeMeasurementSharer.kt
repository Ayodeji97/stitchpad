package com.danzucker.stitchpad.core.sharing

/** Records calls for assertions in ViewModel tests. Mirrors the fake-repository pattern used elsewhere in commonTest. */
class FakeMeasurementSharer : MeasurementSharer {
    var lastImageData: MeasurementShareData? = null
    var lastPdfData: MeasurementShareData? = null
    var throwOnShare: Boolean = false

    override suspend fun shareAsImage(data: MeasurementShareData) {
        if (throwOnShare) error("boom")
        lastImageData = data
    }

    override suspend fun shareAsPdf(data: MeasurementShareData) {
        if (throwOnShare) error("boom")
        lastPdfData = data
    }
}
