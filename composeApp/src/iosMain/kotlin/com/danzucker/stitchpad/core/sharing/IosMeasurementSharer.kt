package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.platform.activeKeyWindow
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSKernAttributeName
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.drawAtPoint
import platform.UIKit.popoverPresentationController
import platform.UIKit.sizeWithAttributes

/**
 * Paper-light measurement card renderer + iOS share sheet. Structurally mirrors
 * [OrderReceiptSharer]'s iOS actual, but the card only ever renders in the light
 * Adire Atelier palette (image AND PDF) — there's no dark variant, and unlike the
 * receipt there is no post-render crop step, so [layoutHeight] / [layoutHeightPdf]
 * must sum the exact same advances the draw closures use.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("TooManyFunctions", "LargeClass")
class IosMeasurementSharer : MeasurementSharer {

    override suspend fun shareAsImage(data: MeasurementShareData) {
        val fileUrl = withContext(Dispatchers.Default) {
            val image = renderCardImage(data)
            val pngData = UIImagePNGRepresentation(image)
                ?: error("Failed to encode measurement card image as PNG")
            val url = tempFileUrl("png")
            if (!pngData.writeToURL(url, atomically = true)) {
                error("Failed to write measurement card PNG to $url")
            }
            url
        }
        shareUrl(fileUrl)
    }

    override suspend fun shareAsPdf(data: MeasurementShareData) {
        val fileUrl = withContext(Dispatchers.Default) {
            renderCardPdf(data)
        }
        shareUrl(fileUrl)
    }

    override suspend fun shareAsText(text: String) {
        // Give the Compose ModalBottomSheet time to finish its dismiss animation before we
        // present a UIKit modal on top — same rationale as shareUrl below.
        delay(SHARE_PRESENT_DELAY_MS)
        withContext(Dispatchers.Main) {
            val rootVC = activeKeyWindow()?.rootViewController ?: return@withContext
            val presenter = topmostPresenter(rootVC)
            val activityVC = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null
            )
            activityVC.popoverPresentationController?.apply {
                sourceView = presenter.view
                presenter.view.bounds.useContents {
                    sourceRect = CGRectMake(
                        origin.x + size.width / 2.0,
                        origin.y + size.height / 2.0,
                        0.0,
                        0.0
                    )
                }
            }
            presenter.presentViewController(activityVC, animated = true, completion = null)
        }
    }

    // region Card Image Rendering (paper light)

    /**
     * Sums the exact same y-advances [renderCardImage] draws with. Unlike the Android
     * bitmap (which crops to actual content afterward), the iOS image renderer has no
     * post-crop step, so this height must match the draw closure precisely.
     */
    private fun layoutHeight(data: MeasurementShareData): Double {
        val width = 800.0
        val padding = 40.0
        var y = padding + 26.0 // header baseline
        y += 24.0 // header-to-divider
        y += 30.0 // divider-to-customer
        y += 34.0 // customer-to-meta
        data.sections.forEach { section ->
            y += 28.0 + 28.0 + 44.0 * section.rows.size
        }
        val wrappedNotes = data.notes?.let { wrapText(it, italicFont(22.0), width - 2 * padding) }.orEmpty()
        if (wrappedNotes.isNotEmpty()) {
            y += 28.0 + 28.0 + 34.0 * wrappedNotes.size
        }
        y += 32.0 + 32.0 // footer gaps
        y += padding // bottom breathing room
        return y
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderCardImage(data: MeasurementShareData): UIImage {
        val width = 800.0
        val padding = 40.0
        val height = layoutHeight(data)

        val size = CGSizeMake(width, height)
        val format = UIGraphicsImageRendererFormat().apply { opaque = true }
        val renderer = UIGraphicsImageRenderer(size = size, format = format)

        return renderer.imageWithActions { context ->
            context ?: return@imageWithActions

            cardColor("#FAF6EC").setFill()
            platform.UIKit.UIRectFill(CGRectMake(0.0, 0.0, width, height))

            var y = padding + 26.0
            drawText("StitchPad", padding, y, boldFont(34.0), cardColor("#2C3E7C"))
            drawTextRight(
                "MEASUREMENT CARD",
                width - padding,
                y - 6.0,
                regularFont(18.0),
                cardColor("#7D7970"),
                kern = 18.0 * HEADER_LABEL_KERN_EM
            )
            y += 24.0
            drawDivider(padding, y, width - padding, cardColor("#E5E3DF"))
            y += 30.0

            drawText(data.customerName, padding, y, boldFont(44.0), cardColor("#14110E"))
            y += 34.0

            drawText(metaLine(data), padding, y, regularFont(22.0), cardColor("#57534C"))

            data.sections.forEach { section ->
                y += 28.0
                drawDivider(padding, y, width - padding, cardColor("#E5E3DF"))
                y += 28.0
                drawText(
                    section.title.uppercase(),
                    padding,
                    y,
                    boldFont(20.0),
                    cardColor("#8E4524"),
                    kern = 20.0 * SECTION_TITLE_KERN_EM
                )
                section.rows.forEach { row ->
                    y += 44.0
                    drawText(row.label, padding, y, regularFont(24.0), cardColor("#57534C"))
                    drawTextRight(
                        "${row.value}${data.unitSuffix}",
                        width - padding,
                        y,
                        monoBoldFont(24.0),
                        cardColor("#14110E")
                    )
                }
            }

            val wrappedNotes = data.notes?.let { wrapText(it, italicFont(22.0), width - 2 * padding) }.orEmpty()
            if (wrappedNotes.isNotEmpty()) {
                y += 28.0
                drawDivider(padding, y, width - padding, cardColor("#E5E3DF"))
                y += 28.0
                wrappedNotes.forEach { line ->
                    y += 34.0
                    drawText(line, padding, y, italicFont(22.0), cardColor("#57534C"))
                }
            }

            y += 32.0
            drawDivider(padding, y, width - padding, cardColor("#E5E3DF"))
            y += 32.0
            drawCentered(FOOTER_TEXT, y = y, width = width, font = regularFont(18.0), color = cardColor("#7D7970"))
            // Paranoid check: y here should land within a few px of layoutHeight(data) —
            // the renderer above was sized to exactly that height with no post-crop step,
            // unlike the Android bitmap renderer which crops to actual content.
        }
    }

    // endregion

    // region Card PDF Rendering (paper light)

    /** Mirrors [renderCardPdf]'s exact y-advances at half the image's scale. */
    private fun layoutHeightPdf(data: MeasurementShareData): Double {
        val pageWidth = 420.0
        val padding = 30.0
        var y = padding + 13.0
        y += 12.0
        y += 15.0
        y += 17.0
        data.sections.forEach { section ->
            y += 14.0 + 14.0 + 22.0 * section.rows.size
        }
        val wrappedNotes = data.notes?.let { wrapText(it, italicFont(11.0), pageWidth - 2 * padding) }.orEmpty()
        if (wrappedNotes.isNotEmpty()) {
            y += 14.0 + 14.0 + 17.0 * wrappedNotes.size
        }
        y += 16.0 + 16.0
        y += padding
        return y
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderCardPdf(data: MeasurementShareData): NSURL {
        val pageWidth = 420.0
        val padding = 30.0
        val pageHeight = maxOf(595.0, layoutHeightPdf(data))

        val fileUrl = tempFileUrl("pdf")
        val format = UIGraphicsPDFRendererFormat()
        val bounds = CGRectMake(0.0, 0.0, pageWidth, pageHeight)
        val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

        val pdfData = renderer.PDFDataWithActions { context ->
            val ctx = context ?: return@PDFDataWithActions
            ctx.beginPage()

            cardColor("#FAF6EC").setFill()
            platform.UIKit.UIRectFill(CGRectMake(0.0, 0.0, pageWidth, pageHeight))

            var y = padding + 13.0
            drawText("StitchPad", padding, y, boldFont(17.0), cardColor("#2C3E7C"))
            drawTextRight(
                "MEASUREMENT CARD",
                pageWidth - padding,
                y - 3.0,
                regularFont(9.0),
                cardColor("#7D7970"),
                kern = 9.0 * HEADER_LABEL_KERN_EM
            )
            y += 12.0
            drawDivider(padding, y, pageWidth - padding, cardColor("#E5E3DF"))
            y += 15.0

            drawText(data.customerName, padding, y, boldFont(22.0), cardColor("#14110E"))
            y += 17.0

            drawText(metaLine(data), padding, y, regularFont(11.0), cardColor("#57534C"))

            data.sections.forEach { section ->
                y += 14.0
                drawDivider(padding, y, pageWidth - padding, cardColor("#E5E3DF"))
                y += 14.0
                drawText(
                    section.title.uppercase(),
                    padding,
                    y,
                    boldFont(10.0),
                    cardColor("#8E4524"),
                    kern = 10.0 * SECTION_TITLE_KERN_EM
                )
                section.rows.forEach { row ->
                    y += 22.0
                    drawText(row.label, padding, y, regularFont(12.0), cardColor("#57534C"))
                    drawTextRight(
                        "${row.value}${data.unitSuffix}",
                        pageWidth - padding,
                        y,
                        monoBoldFont(12.0),
                        cardColor("#14110E")
                    )
                }
            }

            val wrappedNotes = data.notes?.let { wrapText(it, italicFont(11.0), pageWidth - 2 * padding) }.orEmpty()
            if (wrappedNotes.isNotEmpty()) {
                y += 14.0
                drawDivider(padding, y, pageWidth - padding, cardColor("#E5E3DF"))
                y += 14.0
                wrappedNotes.forEach { line ->
                    y += 17.0
                    drawText(line, padding, y, italicFont(11.0), cardColor("#57534C"))
                }
            }

            y += 16.0
            drawDivider(padding, y, pageWidth - padding, cardColor("#E5E3DF"))
            y += 16.0
            drawCentered(FOOTER_TEXT, y = y, width = pageWidth, font = regularFont(9.0), color = cardColor("#7D7970"))
        }

        if (!pdfData.writeToURL(fileUrl, atomically = true)) {
            error("Failed to write measurement card PDF to $fileUrl")
        }
        return fileUrl
    }

    // endregion

    // region Shared Layout Helpers

    private fun metaLine(data: MeasurementShareData): String {
        val parts = buildList {
            add(data.measurementName)
            add(data.genderLabel)
            add(data.unitLabel)
            data.dateFormatted?.let { add("Taken $it") }
            data.businessName?.let { add(it) }
        }
        return parts.joinToString(" · ")
    }

    /** Greedily wraps [text] into lines that fit within [maxWidth] under [font]'s metrics. */
    private fun wrapText(text: String, font: UIFont, maxWidth: Double): List<String> {
        val attrs = mapOf<Any?, Any?>(NSFontAttributeName to font)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            val candidateWidth = NSString.create(string = candidate)
                .sizeWithAttributes(attrs)
                .useContents { width }
            if (candidateWidth > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    // endregion

    // region Drawing Helpers

    private fun buildAttrs(font: UIFont, color: UIColor, kern: Double?): Map<Any?, Any?> {
        val attrs = mutableMapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color,
        )
        if (kern != null) attrs[NSKernAttributeName] = kern
        return attrs
    }

    // y is a BASELINE (Android Canvas.drawText semantics) so both platforms share
    // one layout table; NSString.drawAtPoint is top-origin, hence the ascender shift.
    private fun drawText(text: String, x: Double, y: Double, font: UIFont, color: UIColor, kern: Double? = null) {
        val nsText = NSString.create(string = text)
        val attrs = buildAttrs(font, color, kern)
        nsText.drawAtPoint(CGPointMake(x, y - font.ascender), withAttributes = attrs)
    }

    // y is a BASELINE (Android Canvas.drawText semantics) so both platforms share
    // one layout table; NSString.drawAtPoint is top-origin, hence the ascender shift.
    private fun drawTextRight(
        text: String,
        rightX: Double,
        y: Double,
        font: UIFont,
        color: UIColor,
        kern: Double? = null,
    ) {
        val nsText = NSString.create(string = text)
        val attrs = buildAttrs(font, color, kern)
        val size = nsText.sizeWithAttributes(attrs)
        size.useContents {
            nsText.drawAtPoint(CGPointMake(rightX - this.width, y - font.ascender), withAttributes = attrs)
        }
    }

    // y is a BASELINE (Android Canvas.drawText semantics) so both platforms share
    // one layout table; NSString.drawAtPoint is top-origin, hence the ascender shift.
    private fun drawCentered(text: String, y: Double, width: Double, font: UIFont, color: UIColor) {
        val nsText = NSString.create(string = text)
        val attrs = buildAttrs(font, color, kern = null)
        val size = nsText.sizeWithAttributes(attrs)
        size.useContents {
            nsText.drawAtPoint(CGPointMake((width - this.width) / 2.0, y - font.ascender), withAttributes = attrs)
        }
    }

    private fun drawDivider(x1: Double, y: Double, x2: Double, color: UIColor) {
        color.setFill()
        platform.UIKit.UIRectFill(CGRectMake(x1, y, x2 - x1, 1.0))
    }

    private fun cardColor(hex: String): UIColor {
        val cleaned = hex.removePrefix("#")
        val r = cleaned.substring(0, 2).toInt(16) / 255.0
        val g = cleaned.substring(2, 4).toInt(16) / 255.0
        val b = cleaned.substring(4, 6).toInt(16) / 255.0
        return UIColor.colorWithRed(r, green = g, blue = b, alpha = 1.0)
    }

    private fun regularFont(size: Double) = UIFont.systemFontOfSize(size)
    private fun boldFont(size: Double) = UIFont.boldSystemFontOfSize(size)
    private fun italicFont(size: Double) = UIFont.italicSystemFontOfSize(size)
    private fun monoBoldFont(size: Double) = UIFont.monospacedSystemFontOfSize(size, weight = UIFontWeightBold)

    // endregion

    // region File & Share

    private fun tempFileUrl(extension: String): NSURL {
        val dir = NSTemporaryDirectory()
        val name = "measurement_${NSUUID().UUIDString}.$extension"
        return NSURL.fileURLWithPath("$dir$name")
    }

    private suspend fun shareUrl(url: NSURL) {
        // Give the Compose ModalBottomSheet time to finish its dismiss animation before we
        // present a UIKit modal on top. UIKit silently refuses to present while another VC
        // is mid-transition.
        delay(SHARE_PRESENT_DELAY_MS)
        withContext(Dispatchers.Main) {
            val rootVC = activeKeyWindow()?.rootViewController ?: return@withContext
            val presenter = topmostPresenter(rootVC)
            val activityVC = UIActivityViewController(
                activityItems = listOf(url),
                applicationActivities = null
            )
            // iPad: UIActivityViewController must have a popover source or it fails to present.
            activityVC.popoverPresentationController?.apply {
                sourceView = presenter.view
                presenter.view.bounds.useContents {
                    sourceRect = CGRectMake(
                        origin.x + size.width / 2.0,
                        origin.y + size.height / 2.0,
                        0.0,
                        0.0
                    )
                }
            }
            presenter.presentViewController(activityVC, animated = true, completion = null)
        }
    }

    private fun topmostPresenter(root: UIViewController): UIViewController {
        var vc: UIViewController = root
        while (true) {
            val next = vc.presentedViewController ?: return vc
            if (next.isBeingDismissed()) return vc
            vc = next
        }
    }

    private companion object {
        const val SHARE_PRESENT_DELAY_MS = 450L
        const val HEADER_LABEL_KERN_EM = 0.15
        const val SECTION_TITLE_KERN_EM = 0.12
        const val FOOTER_TEXT = "Made with StitchPad — the smart work pad for tailors · getstitchpad.com"
    }

    // endregion
}
