package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.platform.activeKeyWindow
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIGraphicsPDFRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.drawAtPoint
import platform.UIKit.drawInRect
import platform.UIKit.popoverPresentationController
import platform.UIKit.sizeWithAttributes

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("TooManyFunctions", "LargeClass")
actual class OrderReceiptSharer {

    actual suspend fun shareReceiptAsImage(receiptData: ReceiptData) {
        val fileUrl = withContext(Dispatchers.Default) {
            val image = renderDarkImage(receiptData)
            val pngData = UIImagePNGRepresentation(image)
                ?: error("Failed to encode receipt image as PNG")
            val url = tempFileUrl("png")
            if (!pngData.writeToURL(url, atomically = true)) {
                error("Failed to write receipt PNG to $url")
            }
            url
        }
        shareUrl(fileUrl)
    }

    actual suspend fun shareReceiptAsPdf(receiptData: ReceiptData) {
        val fileUrl = withContext(Dispatchers.Default) {
            renderLightPdf(receiptData)
        }
        shareUrl(fileUrl)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderDarkImage(data: ReceiptData): UIImage {
        val width = 800.0
        val padding = 40.0
        val headerHeight = if (data.businessPhone != null) 90.0 else 70.0
        val lineSpacing = 28.0
        var estimatedHeight = headerHeight + padding * 2
        estimatedHeight += 40.0 // document type label
        estimatedHeight += 60.0 + 20.0 // customer row
        // qty==1 → 1 row (~30px); qty>1 → 2 rows (~22+26=48px).
        val itemsHeight = data.items.sumOf { item -> if (item.quantity == 1) 30.0 else 48.0 }
        estimatedHeight += 30.0 + itemsHeight + 20.0 // items
        estimatedHeight += lineSpacing * 3 + 30.0 // payment
        if (data.discountFormatted != null) {
            estimatedHeight += 22.0 + 20.0 // subtotal row + discount row
            if (data.discountReason != null) estimatedHeight += 18.0 // reason caption
        }
        if (data.bankBlock != null) {
            // Mirrors the y-advances in the draw block exactly: pre-divider gap
            // (16) + post-divider gap (24) + 3 inter-row advances of 26 between
            // header / Bank / Account name / Account number + trailing space (32).
            // The estimate must match the draw exactly — the iOS image renderer
            // allocates the bitmap at this height with NO post-crop step, so
            // over-estimating bleeds dark background below the last content.
            estimatedHeight += 16.0 + 24.0 + 3 * 26.0 + 32.0
        }
        estimatedHeight += 60.0 // status
        if (data.priorityLabel != null) estimatedHeight += 30.0
        estimatedHeight += 50.0 // footer
        if (data.attribution !is ReceiptAttribution.None) estimatedHeight += 22.0

        val size = CGSizeMake(width, estimatedHeight)
        val format = UIGraphicsImageRendererFormat().apply { opaque = true }
        val renderer = UIGraphicsImageRenderer(size = size, format = format)

        return renderer.imageWithActions { context ->
            val ctx = context ?: return@imageWithActions
            val logoImage = data.businessLogoBytes?.toUIImage()

            // Background
            darkColor("#121110").setFill()
            platform.UIKit.UIRectFill(CGRectMake(0.0, 0.0, width, estimatedHeight))

            // Tier watermark — drawn FIRST so all subsequent content layers on top.
            drawWatermark(
                spec = data.watermark,
                canvasWidth = width,
                canvasHeight = estimatedHeight,
                inkHex = "#A8A49D",
            )

            // Header band — indigo brand (was saffron pre-rebrand)
            darkColor("#2C3E7C").setFill()
            platform.UIKit.UIRectFill(CGRectMake(0.0, 0.0, width, headerHeight))

            if (logoImage != null) {
                val logoSize = 40.0
                val logoLeft = 32.0
                val logoTop = (headerHeight - logoSize) / 2.0
                val logoRect = CGRectMake(logoLeft, logoTop, logoSize, logoSize)
                val path = UIBezierPath.bezierPathWithRoundedRect(rect = logoRect, cornerRadius = 6.0)
                UIGraphicsGetCurrentContext()?.let { gfxCtx ->
                    CGContextSaveGState(gfxCtx)
                    path.addClip()
                    logoImage.drawInRect(logoRect)
                    CGContextRestoreGState(gfxCtx)
                }
            }

            drawCentered(
                data.businessName,
                y = headerHeight / 2.0 - 14.0,
                width = width,
                font = boldFont(22.0),
                color = darkColor("#FFFFFF")
            )
            if (data.businessPhone != null) {
                drawCentered(
                    data.businessPhone,
                    y = headerHeight / 2.0 + 8.0,
                    width = width,
                    font = regularFont(13.0),
                    color = darkColor("#FFFFFF")
                )
            }

            var y = headerHeight + 22.0

            // Document type label (RECEIPT / INVOICE)
            drawCentered(
                data.documentTypeLabel,
                y = y,
                width = width,
                font = boldFont(13.0),
                color = darkColor("#E8A800")
            )
            y += 28.0

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
                if (item.quantity == 1) {
                    // Single row: garment name (left) + line total (right, bold). No subtitle.
                    drawText(item.garmentName, padding, y, boldFont(14.0), darkColor("#E5E3DF"))
                    drawTextRight(item.formattedPrice, width - padding, y, boldFont(14.0), darkColor("#E5E3DF"))
                    y += 30.0
                } else {
                    // Row 1: "<name> ×N" (left, bold) + line total (right, bold).
                    drawText("${item.garmentName} ×${item.quantity}", padding, y, boldFont(14.0), darkColor("#E5E3DF"))
                    drawTextRight(item.formattedPrice, width - padding, y, boldFont(14.0), darkColor("#E5E3DF"))
                    y += 22.0
                    // Row 2 (caption): "<unit price> each", muted, no right-column figure.
                    drawText("${item.formattedUnitPrice} each", padding, y, regularFont(12.0), darkColor("#7D7970"))
                    y += 26.0
                }
            }
            y += 8.0

            drawDivider(padding, y, width - padding, darkColor("#3A3731"))
            y += 18.0

            // Payment
            data.discountFormatted?.let { discount ->
                drawText("Subtotal", padding, y, regularFont(13.0), darkColor("#7D7970"))
                drawTextRight(data.subtotalFormatted, width - padding, y, regularFont(13.0), darkColor("#E5E3DF"))
                y += 22.0
                drawText("Discount", padding, y, regularFont(13.0), darkColor("#7D7970"))
                drawTextRight(discount, width - padding, y, boldFont(13.0), darkColor("#2D9E6B"))
                y += 20.0
                data.discountReason?.let { reason ->
                    drawText(reason, padding, y, regularFont(11.0), darkColor("#7D7970"))
                    y += 18.0
                }
            }
            // Deposit drawn before Total so the customer reads what they've paid
            // first, then the Total it offsets, then the Balance still due.
            drawText("Deposit Paid", padding, y, regularFont(13.0), darkColor("#7D7970"))
            drawTextRight(data.depositFormatted, width - padding, y, regularFont(13.0), darkColor("#2D9E6B"))
            y += 24.0
            drawText("Total", padding, y, boldFont(16.0), darkColor("#E5E3DF"))
            drawTextRight(data.totalFormatted, width - padding, y, boldFont(16.0), darkColor("#E8A800"))
            y += 24.0
            drawText("Balance", padding, y, regularFont(13.0), darkColor("#7D7970"))
            if (data.isFullyPaid) {
                drawTextRight("✓ PAID IN FULL", width - padding, y, boldFont(14.0), darkColor("#2D9E6B"))
            } else {
                drawTextRight("${data.balanceFormatted} DUE", width - padding, y, boldFont(14.0), darkColor("#E8A800"))
            }
            y += 26.0

            // PAY VIA TRANSFER — bank block. Formatter nulls bankBlock on
            // fully-paid Receipts (no balance to collect) and on users without
            // bank details, so this never renders without a real call to action.
            val bank = data.bankBlock
            if (bank != null) {
                y += 16.0
                drawDivider(padding, y, width - padding, darkColor("#3A3731"))
                y += 24.0
                drawText("PAY VIA TRANSFER", padding, y, labelFont(), darkColor("#7D7970"))
                y += 26.0
                val valueX = padding + 140.0
                drawText("Bank", padding, y, regularFont(13.0), darkColor("#7D7970"))
                drawText(bank.bankName, valueX, y, boldFont(14.0), darkColor("#E5E3DF"))
                y += 26.0
                drawText("Account name", padding, y, regularFont(13.0), darkColor("#7D7970"))
                drawText(bank.accountName, valueX, y, boldFont(14.0), darkColor("#E5E3DF"))
                y += 26.0
                drawText("Account number", padding, y, regularFont(13.0), darkColor("#7D7970"))
                drawText(bank.accountNumber, valueX, y, boldFont(14.0), darkColor("#E5E3DF"))
                y += 32.0
            }

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
            val attributionText = data.attribution.footerText
            if (attributionText != null) {
                y += 16.0
                drawCentered(
                    attributionText,
                    y = y,
                    width = width,
                    font = regularFont(10.0),
                    color = darkColor("#3A3731")
                )
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderLightPdf(data: ReceiptData): NSURL {
        val pageWidth = 420.0
        val padding = 30.0

        // Compute dynamic page height by summing the same y-advances used in the
        // draw code below, so the page is always tall enough to show every section.
        var estimatedHeight = padding // y starts at padding
        estimatedHeight += if (data.businessPhone != null) 50.0 else 40.0 // header block
        estimatedHeight += 4.0 // headerBottomY + 4 offset
        estimatedHeight += 16.0 // border fill gap
        estimatedHeight += 20.0 // document type label
        estimatedHeight += 14.0 // customer/date label row
        estimatedHeight += 16.0 // customer/date value row
        estimatedHeight += 14.0 // divider gap
        estimatedHeight += 16.0 // items label
        // per-item height: qty==1 → 22; qty>1 → 16+14 = 30.
        data.items.forEach { item -> estimatedHeight += if (item.quantity == 1) 22.0 else 30.0 }
        estimatedHeight += 6.0 // post-items gap
        estimatedHeight += 14.0 // payment divider
        if (data.discountFormatted != null) {
            estimatedHeight += 18.0 + 18.0 // subtotal row + discount row
            if (data.discountReason != null) estimatedHeight += 14.0 // reason caption
        }
        estimatedHeight += 18.0 // Total row
        estimatedHeight += 18.0 // Deposit row
        estimatedHeight += 20.0 // Balance row
        if (data.bankBlock != null) {
            // 12 (pre-divider) + 18 (post-divider) + 20 (header) + 20 (Bank) + 20 (Account name) + 24 (trailing)
            estimatedHeight += 12.0 + 18.0 + 20.0 + 20.0 + 20.0 + 24.0
        }
        estimatedHeight += 14.0 // status divider
        estimatedHeight += 14.0 // status/deadline labels row
        estimatedHeight += 6.0 // status/deadline values row
        if (data.priorityLabel != null) estimatedHeight += 12.0 // priority badge
        estimatedHeight += 24.0 // pre-footer divider advance
        estimatedHeight += 14.0 // order-id line
        if (data.attribution !is ReceiptAttribution.None) estimatedHeight += 14.0 // attribution line
        estimatedHeight += padding // bottom breathing room

        val pageHeight = maxOf(595.0, kotlin.math.ceil(estimatedHeight))

        val fileUrl = tempFileUrl("pdf")

        val format = UIGraphicsPDFRendererFormat()
        val bounds = CGRectMake(0.0, 0.0, pageWidth, pageHeight)
        val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = format)

        val pdfData = renderer.PDFDataWithActions { context ->
            val ctx = context ?: return@PDFDataWithActions
            ctx.beginPage()
            val logoImage = data.businessLogoBytes?.toUIImage()

            // Tier watermark — drawn FIRST so all subsequent content layers on top.
            drawWatermark(
                spec = data.watermark,
                canvasWidth = pageWidth,
                canvasHeight = pageHeight,
                inkHex = "#7D7970",
            )

            var y = padding

            // Header — match Android light PDF: logo first, then text paints on top.
            val headerBottomY = if (data.businessPhone != null) y + 50.0 else y + 40.0
            if (logoImage != null) {
                val logoSize = 40.0
                val logoLeft = 32.0
                val logoTop = y + (headerBottomY - y - logoSize) / 2.0
                val logoRect = CGRectMake(logoLeft, logoTop, logoSize, logoSize)
                val path = UIBezierPath.bezierPathWithRoundedRect(rect = logoRect, cornerRadius = 6.0)
                UIGraphicsGetCurrentContext()?.let { gfxCtx ->
                    CGContextSaveGState(gfxCtx)
                    path.addClip()
                    logoImage.drawInRect(logoRect)
                    CGContextRestoreGState(gfxCtx)
                }
            }
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
            y = headerBottomY + 4.0
            // Indigo brand border (was saffron pre-rebrand)
            val borderPaint = darkColor("#2C3E7C")
            borderPaint.setFill()
            platform.UIKit.UIRectFill(CGRectMake(padding, y, pageWidth - 2 * padding, 3.0))
            y += 16.0

            // Document type label (RECEIPT / INVOICE)
            drawCentered(
                data.documentTypeLabel,
                y = y,
                width = pageWidth,
                font = boldFont(10.0),
                color = darkColor("#C48E00")
            )
            y += 20.0

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
                if (item.quantity == 1) {
                    // Single row: garment name (left) + line total (right, bold). No subtitle.
                    drawText(item.garmentName, padding, y, boldFont(11.0), darkColor("#1E1C1A"))
                    drawTextRight(item.formattedPrice, pageWidth - padding, y, boldFont(11.0), darkColor("#1E1C1A"))
                    y += 22.0
                } else {
                    // Row 1: "<name> ×N" (left, bold) + line total (right, bold).
                    drawText("${item.garmentName} ×${item.quantity}", padding, y, boldFont(11.0), darkColor("#1E1C1A"))
                    drawTextRight(item.formattedPrice, pageWidth - padding, y, boldFont(11.0), darkColor("#1E1C1A"))
                    y += 16.0
                    // Row 2 (caption): "<unit price> each", muted, no right-column figure.
                    drawText("${item.formattedUnitPrice} each", padding, y, regularFont(10.0), darkColor("#7D7970"))
                    y += 14.0
                }
            }
            y += 6.0

            drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
            y += 14.0

            // Payment
            data.discountFormatted?.let { discount ->
                drawText("Subtotal", padding, y, regularFont(11.0), darkColor("#7D7970"))
                drawTextRight(data.subtotalFormatted, pageWidth - padding, y, regularFont(11.0), darkColor("#1E1C1A"))
                y += 18.0
                drawText("Discount", padding, y, regularFont(11.0), darkColor("#7D7970"))
                drawTextRight(discount, pageWidth - padding, y, boldFont(11.0), darkColor("#2D9E6B"))
                y += 18.0
                data.discountReason?.let { reason ->
                    drawText(reason, padding, y, regularFont(10.0), darkColor("#7D7970"))
                    y += 14.0
                }
            }
            // Deposit drawn before Total so the customer reads what they've paid
            // first, then the Total it offsets, then the Balance still due.
            drawText("Deposit Paid", padding, y, regularFont(11.0), darkColor("#7D7970"))
            drawTextRight(data.depositFormatted, pageWidth - padding, y, regularFont(11.0), darkColor("#2D9E6B"))
            y += 18.0
            drawText("Total", padding, y, boldFont(13.0), darkColor("#1E1C1A"))
            drawTextRight(data.totalFormatted, pageWidth - padding, y, boldFont(13.0), darkColor("#C48E00"))
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

            // PAY VIA TRANSFER — light PDF variant
            val bankPdf = data.bankBlock
            if (bankPdf != null) {
                y += 12.0
                drawDivider(padding, y, pageWidth - padding, darkColor("#E8E6E3"))
                y += 18.0
                drawText("PAY VIA TRANSFER", padding, y, labelFont(8.0), darkColor("#7D7970"))
                y += 20.0
                val valueX = padding + 104.0
                drawText("Bank", padding, y, regularFont(11.0), darkColor("#7D7970"))
                drawText(bankPdf.bankName, valueX, y, boldFont(11.0), darkColor("#1E1C1A"))
                y += 20.0
                drawText("Account name", padding, y, regularFont(11.0), darkColor("#7D7970"))
                drawText(bankPdf.accountName, valueX, y, boldFont(11.0), darkColor("#1E1C1A"))
                y += 20.0
                drawText("Account number", padding, y, regularFont(11.0), darkColor("#7D7970"))
                drawText(bankPdf.accountNumber, valueX, y, boldFont(11.0), darkColor("#1E1C1A"))
                y += 24.0
            }

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
            val attributionTextPdf = data.attribution.footerText
            if (attributionTextPdf != null) {
                y += 14.0
                drawCentered(
                    attributionTextPdf,
                    y = y,
                    width = pageWidth,
                    font = regularFont(8.0),
                    color = darkColor("#A8A49D")
                )
            }
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

    @Suppress("TooGenericExceptionCaught")
    private fun ByteArray.toUIImage(): UIImage? {
        if (isEmpty()) return null
        val nsData = usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
        return UIImage.imageWithData(nsData)
    }

    private fun darkColor(hex: String): UIColor {
        val cleaned = hex.removePrefix("#")
        val r = cleaned.substring(0, 2).toInt(16) / 255.0
        val g = cleaned.substring(2, 4).toInt(16) / 255.0
        val b = cleaned.substring(4, 6).toInt(16) / 255.0
        return UIColor.colorWithRed(r, green = g, blue = b, alpha = 1.0)
    }

    /**
     * Draws the tier-keyed background watermark before any content. Caller is
     * responsible for invoking this immediately after the canvas background
     * fill so the watermark sits at the lowest z-order. Mirrors the Android
     * implementation at the spec level so both platforms produce comparable
     * receipts. None branch ships a clean document (paid tiers).
     */
    private fun drawWatermark(
        spec: WatermarkSpec,
        canvasWidth: Double,
        canvasHeight: Double,
        inkHex: String,
    ) {
        when (spec) {
            WatermarkSpec.None -> Unit
            WatermarkSpec.StitchPadDiagonal -> {
                val ctx = UIGraphicsGetCurrentContext() ?: return
                val cx = canvasWidth / 2.0
                val cy = canvasHeight / 2.0
                CGContextSaveGState(ctx)
                platform.CoreGraphics.CGContextTranslateCTM(ctx, cx, cy)
                platform.CoreGraphics.CGContextRotateCTM(ctx, -kotlin.math.PI / 6.0) // -30°
                platform.CoreGraphics.CGContextTranslateCTM(ctx, -cx, -cy)
                val fontSize = canvasWidth * 0.12
                val font = UIFont.boldSystemFontOfSize(fontSize)
                val color = darkColor(inkHex).colorWithAlphaComponent(WATERMARK_TEXT_ALPHA_IOS)
                // Kern matches Android's letterSpacing = 0.08f (which scales by EM).
                // 0.08 * fontSize is the equivalent absolute per-character spacing.
                val kern = fontSize * WATERMARK_KERN_EM
                val text = "STITCHPAD"
                val attrs = mapOf<Any?, Any?>(
                    NSFontAttributeName to font,
                    NSForegroundColorAttributeName to color,
                    platform.UIKit.NSKernAttributeName to kern,
                )
                val nsText = NSString.create(string = text)
                val size = nsText.sizeWithAttributes(attrs)
                size.useContents {
                    nsText.drawAtPoint(
                        CGPointMake(cx - this.width / 2.0, cy - this.height / 2.0),
                        withAttributes = attrs,
                    )
                }
                CGContextRestoreGState(ctx)
            }
        }
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
        const val WATERMARK_TEXT_ALPHA_IOS = 0.07
        const val WATERMARK_KERN_EM = 0.08
    }

    // endregion
}
