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
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIColor
import platform.UIKit.UIFont
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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("TooManyFunctions")
actual class OrderReceiptSharer {

    actual suspend fun shareReceiptAsImage(receiptData: ReceiptData) {
        val image = renderDarkImage(receiptData)
        val pngData = UIImagePNGRepresentation(image)
            ?: error("Failed to encode receipt image as PNG")
        val fileUrl = tempFileUrl("png")
        if (!pngData.writeToURL(fileUrl, atomically = true)) {
            error("Failed to write receipt PNG to $fileUrl")
        }
        shareUrl(fileUrl)
    }

    actual suspend fun shareReceiptAsPdf(receiptData: ReceiptData) {
        val fileUrl = renderLightPdf(receiptData)
        shareUrl(fileUrl)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderDarkImage(data: ReceiptData): UIImage {
        val width = 800.0
        val padding = 40.0
        val headerHeight = if (data.businessPhone != null) 90.0 else 70.0
        val lineSpacing = 28.0
        var estimatedHeight = headerHeight + padding * 2
        estimatedHeight += 60.0 + 20.0 // customer row
        estimatedHeight += 30.0 + data.items.size * lineSpacing + 20.0 // items
        estimatedHeight += lineSpacing * 3 + 30.0 // payment
        estimatedHeight += 60.0 // status
        if (data.priorityLabel != null) estimatedHeight += 30.0
        estimatedHeight += 50.0 // footer

        val size = CGSizeMake(width, estimatedHeight)
        val format = UIGraphicsImageRendererFormat().apply { opaque = true }
        val renderer = UIGraphicsImageRenderer(size = size, format = format)

        return renderer.imageWithActions { context ->
            val ctx = context ?: return@imageWithActions

            // Background
            darkColor("#121110").setFill()
            platform.UIKit.UIRectFill(CGRectMake(0.0, 0.0, width, estimatedHeight))

            // Header band
            darkColor("#E8A800").setFill()
            platform.UIKit.UIRectFill(CGRectMake(0.0, 0.0, width, headerHeight))

            drawCentered(
                data.businessName,
                y = headerHeight / 2.0 - 14.0,
                width = width,
                font = boldFont(22.0),
                color = darkColor("#121110")
            )
            if (data.businessPhone != null) {
                drawCentered(
                    data.businessPhone,
                    y = headerHeight / 2.0 + 8.0,
                    width = width,
                    font = regularFont(13.0),
                    color = darkColor("#121110")
                )
            }

            var y = headerHeight + padding

            // Customer & Date
            drawText("CUSTOMER", padding, y, labelFont(), darkColor("#7D7970"))
            drawTextRight("DATE", width - padding, y, labelFont(), darkColor("#7D7970"))
            y += 18.0
            drawText(data.customerName, padding, y, boldFont(15.0), darkColor("#E5E3DF"))
            drawTextRight(data.dateFormatted, width - padding, y, regularFont(14.0), darkColor("#E5E3DF"))
            y += 22.0

            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 18.0

            // Items
            drawText("ITEMS", padding, y, labelFont(), darkColor("#7D7970"))
            y += 20.0
            data.items.forEach { item ->
                drawText(
                    "${item.quantity} × ${item.garmentName}",
                    padding,
                    y,
                    regularFont(14.0),
                    darkColor("#E5E3DF")
                )
                drawTextRight(
                    item.formattedPrice,
                    width - padding,
                    y,
                    regularFont(14.0),
                    darkColor("#E5E3DF")
                )
                y += lineSpacing
            }
            y += 8.0

            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 18.0

            // Payment
            drawText("Total", padding, y, boldFont(16.0), darkColor("#E5E3DF"))
            drawTextRight(data.totalFormatted, width - padding, y, boldFont(16.0), darkColor("#E8A800"))
            y += 24.0
            drawText("Deposit Paid", padding, y, regularFont(13.0), darkColor("#7D7970"))
            drawTextRight(data.depositFormatted, width - padding, y, regularFont(13.0), darkColor("#2D9E6B"))
            y += 24.0
            drawText("Balance", padding, y, regularFont(13.0), darkColor("#7D7970"))
            if (data.isFullyPaid) {
                drawTextRight("✓ PAID IN FULL", width - padding, y, boldFont(14.0), darkColor("#2D9E6B"))
            } else {
                drawTextRight("${data.balanceFormatted} DUE", width - padding, y, boldFont(14.0), darkColor("#E8A800"))
            }
            y += 26.0

            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 18.0

            // Status & Deadline
            drawText("STATUS", padding, y, labelFont(), darkColor("#7D7970"))
            if (data.deadlineFormatted != null) {
                drawTextRight("DEADLINE", width - padding, y, labelFont(), darkColor("#7D7970"))
            }
            y += 18.0
            drawText(
                "● ${data.statusLabel}",
                padding,
                y,
                boldFont(13.0),
                darkColor(data.statusColorHex)
            )
            if (data.deadlineFormatted != null) {
                drawTextRight(
                    data.deadlineFormatted,
                    width - padding,
                    y,
                    regularFont(13.0),
                    darkColor("#E5E3DF")
                )
            }
            y += 8.0

            if (data.priorityLabel != null) {
                y += 14.0
                drawBadge(
                    data.priorityLabel,
                    width - padding,
                    y,
                    darkColor("#D93B3B"),
                    UIColor.whiteColor,
                    boldFont(11.0)
                )
            }

            y += 24.0
            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 16.0
            drawCentered(
                "Order #${data.orderIdShort}",
                y = y,
                width = width,
                font = regularFont(11.0),
                color = darkColor("#3A3731")
            )
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderLightPdf(data: ReceiptData): NSURL {
        val pageWidth = 420.0
        val pageHeight = 595.0
        val padding = 30.0
        val fileUrl = tempFileUrl("pdf")

        val format = UIGraphicsPDFRendererFormat()
        val bounds = CGRectMake(0.0, 0.0, pageWidth, pageHeight)
        val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

        val pdfData = renderer.PDFDataWithActions { context ->
            val ctx = context ?: return@PDFDataWithActions
            ctx.beginPage()

            var y = padding

            // Header
            drawCentered(
                data.businessName,
                y = y,
                width = pageWidth,
                font = boldFont(18.0),
                color = darkColor("#1E1C1A")
            )
            y += 20.0
            if (data.businessPhone != null) {
                drawCentered(
                    data.businessPhone,
                    y = y,
                    width = pageWidth,
                    font = regularFont(10.0),
                    color = darkColor("#7D7970")
                )
                y += 16.0
            }
            y += 4.0
            // Saffron border
            val borderPaint = darkColor("#E8A800")
            borderPaint.setFill()
            platform.UIKit.UIRectFill(CGRectMake(padding, y, pageWidth - 2 * padding, 3.0))
            y += 18.0

            // Customer & Date
            drawText("CUSTOMER", padding, y, labelFont(8.0), darkColor("#7D7970"))
            drawTextRight("DATE", pageWidth - padding, y, labelFont(8.0), darkColor("#7D7970"))
            y += 14.0
            drawText(data.customerName, padding, y, boldFont(12.0), darkColor("#1E1C1A"))
            drawTextRight(data.dateFormatted, pageWidth - padding, y, regularFont(11.0), darkColor("#1E1C1A"))
            y += 16.0

            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0

            // Items
            drawText("ITEMS", padding, y, labelFont(8.0), darkColor("#7D7970"))
            y += 16.0
            data.items.forEach { item ->
                drawText(
                    "${item.quantity} × ${item.garmentName}",
                    padding,
                    y,
                    regularFont(11.0),
                    darkColor("#1E1C1A")
                )
                drawTextRight(
                    item.formattedPrice,
                    pageWidth - padding,
                    y,
                    regularFont(11.0),
                    darkColor("#1E1C1A")
                )
                y += 18.0
            }
            y += 6.0

            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0

            // Payment
            drawText("Total", padding, y, boldFont(13.0), darkColor("#1E1C1A"))
            drawTextRight(data.totalFormatted, pageWidth - padding, y, boldFont(13.0), darkColor("#C48E00"))
            y += 18.0
            drawText("Deposit Paid", padding, y, regularFont(11.0), darkColor("#7D7970"))
            drawTextRight(data.depositFormatted, pageWidth - padding, y, regularFont(11.0), darkColor("#2D9E6B"))
            y += 18.0
            drawText("Balance", padding, y, regularFont(11.0), darkColor("#7D7970"))
            if (data.isFullyPaid) {
                drawTextRight("✓ PAID IN FULL", pageWidth - padding, y, boldFont(11.0), darkColor("#2D9E6B"))
            } else {
                drawTextRight(
                    "${data.balanceFormatted} DUE",
                    pageWidth - padding,
                    y,
                    boldFont(11.0),
                    darkColor("#C48E00")
                )
            }
            y += 20.0

            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0

            // Status & Deadline
            drawText("STATUS", padding, y, labelFont(8.0), darkColor("#7D7970"))
            if (data.deadlineFormatted != null) {
                drawTextRight("DEADLINE", pageWidth - padding, y, labelFont(8.0), darkColor("#7D7970"))
            }
            y += 14.0
            drawText(
                "● ${data.statusLabel}",
                padding,
                y,
                boldFont(11.0),
                darkColor(data.statusColorHex)
            )
            if (data.deadlineFormatted != null) {
                drawTextRight(
                    data.deadlineFormatted,
                    pageWidth - padding,
                    y,
                    regularFont(11.0),
                    darkColor("#1E1C1A")
                )
            }
            y += 6.0

            if (data.priorityLabel != null) {
                y += 12.0
                drawBadge(
                    data.priorityLabel,
                    pageWidth - padding,
                    y,
                    darkColor("#D93B3B"),
                    UIColor.whiteColor,
                    boldFont(9.0)
                )
            }

            y += 24.0
            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0
            drawCentered(
                "Order #${data.orderIdShort}",
                y = y,
                width = pageWidth,
                font = regularFont(9.0),
                color = darkColor("#A8A49D")
            )
        }

        if (!pdfData.writeToURL(fileUrl, atomically = true)) {
            error("Failed to write receipt PDF to $fileUrl")
        }
        return fileUrl
    }

    // region Drawing Helpers

    private fun drawText(text: String, x: Double, y: Double, font: UIFont, color: UIColor) {
        val nsText = NSString.create(string = text)
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color
        )
        nsText.drawAtPoint(CGPointMake(x, y), withAttributes = attrs)
    }

    private fun drawTextRight(text: String, rightX: Double, y: Double, font: UIFont, color: UIColor) {
        val nsText = NSString.create(string = text)
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color
        )
        val size = nsText.sizeWithAttributes(attrs)
        size.useContents {
            nsText.drawAtPoint(CGPointMake(rightX - this.width, y), withAttributes = attrs)
        }
    }

    private fun drawCentered(text: String, y: Double, width: Double, font: UIFont, color: UIColor) {
        val nsText = NSString.create(string = text)
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to color
        )
        val size = nsText.sizeWithAttributes(attrs)
        size.useContents {
            nsText.drawAtPoint(CGPointMake((width - this.width) / 2.0, y), withAttributes = attrs)
        }
    }

    private fun drawDivider(x1: Double, y: Double, x2: Double, color: UIColor) {
        color.setFill()
        platform.UIKit.UIRectFill(CGRectMake(x1, y, x2 - x1, 1.0))
    }

    private fun drawBadge(text: String, rightX: Double, y: Double, bg: UIColor, fg: UIColor, font: UIFont) {
        val nsText = NSString.create(string = text)
        val attrs = mapOf<Any?, Any?>(
            NSFontAttributeName to font,
            NSForegroundColorAttributeName to fg
        )
        val size = nsText.sizeWithAttributes(attrs)
        size.useContents {
            val badgeWidth = this.width + 12.0
            val badgeHeight = this.height + 6.0
            val bx = rightX - badgeWidth
            bg.setFill()
            val path = platform.UIKit.UIBezierPath.bezierPathWithRoundedRect(
                CGRectMake(bx, y - 3.0, badgeWidth, badgeHeight),
                cornerRadius = 4.0
            )
            path.fill()
            nsText.drawAtPoint(CGPointMake(bx + 6.0, y), withAttributes = attrs)
        }
    }

    private fun darkColor(hex: String): UIColor {
        val cleaned = hex.removePrefix("#")
        val r = cleaned.substring(0, 2).toInt(16) / 255.0
        val g = cleaned.substring(2, 4).toInt(16) / 255.0
        val b = cleaned.substring(4, 6).toInt(16) / 255.0
        return UIColor.colorWithRed(r, green = g, blue = b, alpha = 1.0)
    }

    private fun regularFont(size: Double) = UIFont.systemFontOfSize(size)
    private fun boldFont(size: Double) = UIFont.boldSystemFontOfSize(size)
    private fun labelFont(size: Double = 10.0) = UIFont.boldSystemFontOfSize(size)

    // endregion

    // region File & Share

    private fun tempFileUrl(extension: String): NSURL {
        val dir = NSTemporaryDirectory()
        val name = "receipt_${NSUUID().UUIDString}.$extension"
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
    }

    // endregion
}
