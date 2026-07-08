package com.danzucker.stitchpad.core.sharing

/**
 * Renders a measurement card and hands it to the platform share sheet.
 * Same contract as [OrderReceiptSharer]: suspend, Unit, throws on failure.
 * Interface (not expect class) so the ViewModel can inject it and commonTest
 * can fake it. [shareAsText] is the WhatsApp fallback when the customer has
 * no phone — plain text via the generic share sheet.
 */
interface MeasurementSharer {
    suspend fun shareAsImage(data: MeasurementShareData)
    suspend fun shareAsPdf(data: MeasurementShareData)
    suspend fun shareAsText(text: String)
}
