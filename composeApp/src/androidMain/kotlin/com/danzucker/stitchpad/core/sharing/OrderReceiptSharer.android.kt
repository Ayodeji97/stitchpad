package com.danzucker.stitchpad.core.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val WATERMARK_TEXT_ALPHA = 18 // ~7% on a 0–255 scale

actual class OrderReceiptSharer(private val context: Context) {

    actual suspend fun shareReceiptAsImage(receiptData: ReceiptData) {
        val file = withContext(Dispatchers.Default) {
            val bitmap = renderDarkBitmap(receiptData)
            saveBitmapToCache(bitmap, "img")
        }
        shareFile(file, "image/png")
    }

    actual suspend fun shareReceiptAsPdf(receiptData: ReceiptData) {
        val file = withContext(Dispatchers.Default) {
            renderLightPdf(receiptData)
        }
        shareFile(file, "application/pdf")
    }

    // region Dark Theme Bitmap Rendering

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderDarkBitmap(data: ReceiptData): Bitmap {
        val width = 800
        val padding = 40f

        // Colors
        val bgColor = Color.parseColor("#121110")
        val headerBg = Color.parseColor("#2C3E7C") // indigo brand band (was saffron pre-rebrand)
        val headerText = Color.WHITE
        val bodyText = Color.parseColor("#E5E3DF")
        val labelColor = Color.parseColor("#7D7970")
        val dividerColor = Color.parseColor("#3A3731")
        val saffron = Color.parseColor("#E8A800")
        val green = Color.parseColor("#2D9E6B")
        val rushRed = Color.parseColor("#D93B3B")

        // Paints
        val headerTitlePaint = makePaint(headerText, 28f, bold = true)
        val headerPhonePaint = makePaint(headerText, 16f).apply { alpha = 190 }
        val labelPaint = makePaint(labelColor, 14f, bold = true)
        val bodyPaint = makePaint(bodyText, 18f)
        // Unit-price breakdown row — same 18f size as the "Price for N" row so the two
        // breakdown lines align; legible (light, not muted) with a bold value so the
        // unit price is clearly visible (design feedback).
        val unitLabelPaint = makePaint(bodyText, 18f)
        val unitValuePaint = makePaint(bodyText, 18f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val bodyBoldPaint = makePaint(bodyText, 18f, bold = true)
        val priceRightPaint = makePaint(bodyText, 18f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val totalLabelPaint = makePaint(bodyText, 20f, bold = true)
        val totalPaint = makePaint(saffron, 20f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val depositPaint = makePaint(green, 18f).apply { textAlign = Paint.Align.RIGHT }
        val balancePaint = makePaint(saffron, 18f, bold = true)
        val statusPaint = makePaint(Color.parseColor(data.statusColorHex), 17f, bold = true)
        val footerPaint = makePaint(dividerColor, 14f).apply { textAlign = Paint.Align.CENTER }
        val linePaint = Paint().apply {
            color = dividerColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val rushBadgePaint = makePaint(Color.WHITE, 13f, bold = true)
        val rushBgPaint = Paint().apply {
            color = rushRed
            style = Paint.Style.FILL
        }
        val balanceBgPaint = Paint().apply {
            color = saffron
            alpha = 30
            style = Paint.Style.FILL
        }
        val paidBgPaint = Paint().apply {
            color = green
            alpha = 30
            style = Paint.Style.FILL
        }

        // Estimate height
        val headerHeight = if (data.businessPhone != null) 90f else 70f
        var estimatedHeight = headerHeight + padding
        estimatedHeight += 40f // document type label
        estimatedHeight += 60f // customer + date row
        estimatedHeight += 20f // divider gap
        estimatedHeight += 30f // Items label
        // qty==1 → 1 row (~26px); qty>1 → 3 rows (22+24+30=76px).
        var itemsEstimate = 0f
        for (item in data.items) itemsEstimate += if (item.quantity == 1) 26f else 76f
        estimatedHeight += itemsEstimate
        estimatedHeight += 20f // gap
        estimatedHeight += 30f // divider gap
        estimatedHeight += 30f * 3 // total/deposit/balance
        if (data.bankBlock != null) {
            // Mirrors the y-advances in the draw block exactly: pre-divider (16)
            // + post-divider (24) + 3 inter-row advances of 26 + trailing (32).
            // Android crops to content height before encoding, so a mismatch is
            // cosmetic here; keeping it aligned with iOS for consistency.
            estimatedHeight += 16f + 24f + 3 * 26f + 32f
        }
        estimatedHeight += 30f // gap
        estimatedHeight += 20f // divider
        estimatedHeight += 50f // status + deadline row
        if (data.priorityLabel != null) estimatedHeight += 30f
        estimatedHeight += 50f // footer
        if (data.attribution !is ReceiptAttribution.None) estimatedHeight += 24f
        estimatedHeight += padding * 2

        val height = estimatedHeight.toInt().coerceAtLeast(500)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        val logoBitmap: android.graphics.Bitmap? = data.businessLogoBytes?.let { bytes ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        // Tier watermark — drawn FIRST so all subsequent content layers on top.
        // Dark theme: use a light gray that reads at low alpha on dark bg.
        drawWatermark(
            canvas = canvas,
            spec = data.watermark,
            canvasWidth = width.toFloat(),
            canvasHeight = height.toFloat(),
            inkColor = Color.parseColor("#A8A49D"),
        )

        var y = 0f

        // Header band
        val headerBgPaint = Paint().apply {
            color = headerBg
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerBgPaint)
        if (logoBitmap != null) {
            val logoSize = 40f
            val logoLeft = 32f
            val logoTop = (headerHeight - logoSize) / 2f
            val logoRect = android.graphics.RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize)
            val clipPath = android.graphics.Path().apply {
                addRoundRect(
                    logoRect,
                    6f,
                    6f,
                    android.graphics.Path.Direction.CW
                )
            }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(logoBitmap, null, logoRect, null)
            canvas.restore()
        }
        val headerCenterY = if (data.businessPhone != null) headerHeight / 2f - 10f else headerHeight / 2f
        canvas.drawText(
            data.businessName,
            width / 2f - headerTitlePaint.measureText(data.businessName) / 2f,
            headerCenterY + 10f,
            headerTitlePaint
        )
        if (data.businessPhone != null) {
            canvas.drawText(
                data.businessPhone,
                width / 2f - headerPhonePaint.measureText(data.businessPhone) / 2f,
                headerCenterY + 32f,
                headerPhonePaint
            )
        }
        y = headerHeight + 26f

        // Document type label (RECEIPT / INVOICE) — saffron heritage emphasis
        val docTypePaint = makePaint(saffron, 15f, bold = true).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.drawText(data.documentTypeLabel, width / 2f, y, docTypePaint)
        y += 30f

        // Customer & Date row
        canvas.drawText("CUSTOMER", padding, y, labelPaint)
        canvas.drawText("DATE", width - padding - labelPaint.measureText("DATE"), y, labelPaint)
        y += 22f
        canvas.drawText(data.customerName, padding, y, bodyBoldPaint)
        val dateWidth = bodyPaint.measureText(data.dateFormatted)
        canvas.drawText(data.dateFormatted, width - padding - dateWidth, y, bodyPaint)
        y += 24f

        // Dashed divider
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 20f

        // Items
        canvas.drawText("ITEMS", padding, y, labelPaint)
        y += 24f
        data.items.forEach { item ->
            if (item.quantity == 1) {
                // Single row: garment name (left) + line total (right, bold). No subtitle.
                canvas.drawText(item.garmentName, padding, y, bodyPaint)
                canvas.drawText(item.formattedPrice, width - padding, y, priceRightPaint)
                y += 26f
            } else {
                // Row 1: garment name only (no price on the right).
                canvas.drawText(item.garmentName, padding, y, bodyPaint)
                y += 22f
                // Row 2: "Unit price" label + value (light, value bold — clearly visible).
                val indent = padding + 14f
                canvas.drawText("Unit price", indent, y, unitLabelPaint)
                canvas.drawText(item.formattedUnitPrice, width - padding, y, unitValuePaint)
                y += 24f
                // Row 3: "Price for N" label (left) + line total (right, bold).
                canvas.drawText("Price for ${item.quantity}", indent, y, bodyBoldPaint)
                canvas.drawText(item.formattedPrice, width - padding, y, priceRightPaint)
                y += 30f
            }
        }
        y += 8f

        // Payment divider
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 22f

        // Total
        canvas.drawText("Total", padding, y, totalLabelPaint)
        canvas.drawText(data.totalFormatted, width - padding, y, totalPaint)
        y += 26f

        // Deposit
        canvas.drawText("Deposit Paid", padding, y, bodyPaint)
        canvas.drawText(data.depositFormatted, width - padding, y, depositPaint)
        y += 26f

        // Balance
        canvas.drawText("Balance", padding, y, bodyPaint)
        if (data.isFullyPaid) {
            val paidText = "✓ PAID IN FULL"
            val paidPaint = makePaint(green, 17f, bold = true)
            val tw = paidPaint.measureText(paidText)
            val rx = width - padding - tw - 14f
            val ry = y - 16f
            canvas.drawRoundRect(RectF(rx, ry, rx + tw + 14f, ry + 24f), 8f, 8f, paidBgPaint)
            canvas.drawText(paidText, rx + 7f, y, paidPaint)
        } else {
            val dueText = "${data.balanceFormatted} DUE"
            val tw = balancePaint.measureText(dueText)
            val rx = width - padding - tw - 14f
            val ry = y - 16f
            canvas.drawRoundRect(RectF(rx, ry, rx + tw + 14f, ry + 24f), 8f, 8f, balanceBgPaint)
            canvas.drawText(dueText, rx + 7f, y, balancePaint)
        }
        y += 28f

        // PAY VIA TRANSFER — bank block. Formatter nulls bankBlock on fully-paid
        // Receipts (nothing to collect) and on users without bank details, so this
        // never renders without a real call to action.
        val bank = data.bankBlock
        if (bank != null) {
            y += 16f
            canvas.drawLine(padding, y, width - padding, y, linePaint)
            y += 24f
            canvas.drawText("PAY VIA TRANSFER", padding, y, labelPaint)
            y += 26f
            val valueX = padding + 140f
            canvas.drawText("Bank", padding, y, bodyPaint)
            canvas.drawText(bank.bankName, valueX, y, bodyBoldPaint)
            y += 26f
            canvas.drawText("Account name", padding, y, bodyPaint)
            canvas.drawText(bank.accountName, valueX, y, bodyBoldPaint)
            y += 26f
            canvas.drawText("Account number", padding, y, bodyPaint)
            canvas.drawText(bank.accountNumber, valueX, y, bodyBoldPaint)
            y += 32f
        }

        // Status & Deadline divider
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 22f

        // Status
        canvas.drawText("STATUS", padding, y, labelPaint)
        if (data.deadlineFormatted != null) {
            canvas.drawText("DEADLINE", width - padding - labelPaint.measureText("DEADLINE"), y, labelPaint)
        }
        y += 22f
        canvas.drawText("● ${data.statusLabel}", padding, y, statusPaint)
        if (data.deadlineFormatted != null) {
            val dlWidth = bodyPaint.measureText(data.deadlineFormatted)
            canvas.drawText(data.deadlineFormatted, width - padding - dlWidth, y, bodyPaint)
        }
        y += 6f

        // Priority badge
        if (data.priorityLabel != null) {
            y += 18f
            val badgeX = if (data.deadlineFormatted != null) {
                width - padding - rushBadgePaint.measureText(data.priorityLabel) - 16f
            } else {
                padding
            }
            val badgeRect = RectF(
                badgeX,
                y - 14f,
                badgeX + rushBadgePaint.measureText(data.priorityLabel) + 16f,
                y + 8f
            )
            canvas.drawRoundRect(badgeRect, 6f, 6f, rushBgPaint)
            canvas.drawText(data.priorityLabel, badgeX + 8f, y + 4f, rushBadgePaint)
            y += 14f
        }

        // Footer
        y += 24f
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += 20f
        canvas.drawText("Order #${data.orderIdShort}", width / 2f, y, footerPaint)
        val attributionText = data.attribution.footerText
        if (attributionText != null) {
            y += 18f
            canvas.drawText(attributionText, width / 2f, y, footerPaint)
        }

        // Crop to actual content height
        val finalHeight = (y + padding).toInt().coerceAtMost(height)
        return Bitmap.createBitmap(bitmap, 0, 0, width, finalHeight)
    }

    // endregion

    // region Light Theme PDF Rendering

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderLightPdf(data: ReceiptData): File {
        // A5 size in PostScript points: 420 x 595
        val pageWidth = 420
        val padding = 30f

        // Compute dynamic page height by summing the same y-advances used in the
        // draw code below, so the page is always tall enough to show every section.
        var estimatedHeight = padding // y starts at padding
        estimatedHeight += if (data.businessPhone != null) 50f else 40f // header block
        estimatedHeight += 4f // headerBottomY + 4 offset
        estimatedHeight += 18f // border line gap
        estimatedHeight += 22f // document type label
        estimatedHeight += 16f // customer/date label row
        estimatedHeight += 18f // customer/date value row
        estimatedHeight += 16f // divider gap
        estimatedHeight += 18f // items label
        for (item in data.items) estimatedHeight += if (item.quantity == 1) 20f else 52f // per-item: 16+16+20
        estimatedHeight += 6f // post-items gap
        estimatedHeight += 18f // payment divider
        estimatedHeight += 20f // Total row
        estimatedHeight += 20f // Deposit row
        estimatedHeight += 22f // Balance row
        if (data.bankBlock != null) {
            // 12 (pre-divider) + 18 (post-divider) + 20 (header) + 20 (Bank) + 20 (Account name) + 24 (trailing)
            estimatedHeight += 12f + 18f + 20f + 20f + 20f + 24f
        }
        estimatedHeight += 16f // status divider
        estimatedHeight += 16f // status/deadline labels row
        estimatedHeight += 6f // status/deadline values row
        if (data.priorityLabel != null) estimatedHeight += 14f // priority badge
        estimatedHeight += 30f // pre-footer divider advance
        estimatedHeight += 16f // order-id line
        if (data.attribution !is ReceiptAttribution.None) estimatedHeight += 14f // attribution line
        estimatedHeight += padding // bottom breathing room

        val pageHeight = maxOf(595, kotlin.math.ceil(estimatedHeight).toInt())

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // Colors
        val bodyText = Color.parseColor("#1E1C1A")
        val labelColor = Color.parseColor("#7D7970")
        val dividerColor = Color.parseColor("#E8E6E3")
        val saffron = Color.parseColor("#C48E00")
        val green = Color.parseColor("#2D9E6B")
        val rushRed = Color.parseColor("#D93B3B")
        val headerBorderColor = Color.parseColor("#2C3E7C") // indigo brand (was saffron pre-rebrand)

        // Paints
        val headerTitlePaint = makePaint(bodyText, 22f, bold = true)
        val headerPhonePaint = makePaint(labelColor, 12f)
        val labelPaintPdf = makePaint(labelColor, 10f, bold = true)
        val bodyPaintPdf = makePaint(bodyText, 14f)
        // Unit-price breakdown row — same 14f size as the "Price for N" row so the two
        // breakdown lines align; legible (not muted) with a bold value so it stands out.
        val unitLabelPdf = makePaint(bodyText, 14f)
        val unitValuePdf = makePaint(bodyText, 14f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val bodyBoldPdf = makePaint(bodyText, 14f, bold = true)
        val priceRightPdf = makePaint(bodyText, 14f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val totalLabelPdf = makePaint(bodyText, 16f, bold = true)
        val totalPricePdf = makePaint(saffron, 16f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val depositPricePdf = makePaint(green, 14f).apply { textAlign = Paint.Align.RIGHT }
        val balancePdf = makePaint(saffron, 14f, bold = true)
        val statusPdf = makePaint(Color.parseColor(data.statusColorHex), 13f, bold = true)
        val footerPdf = makePaint(Color.parseColor("#A8A49D"), 10f).apply { textAlign = Paint.Align.CENTER }
        val linePdf = Paint().apply {
            color = dividerColor
            strokeWidth = 1f
        }
        val rushBadgePdf = makePaint(Color.WHITE, 10f, bold = true)
        val rushBgPdf = Paint().apply {
            color = rushRed
            style = Paint.Style.FILL
        }

        // White background
        canvas.drawColor(Color.WHITE)

        val logoBitmap: android.graphics.Bitmap? = data.businessLogoBytes?.let { bytes ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        // Tier watermark — drawn FIRST so all subsequent content layers on top.
        drawWatermark(
            canvas = canvas,
            spec = data.watermark,
            canvasWidth = pageWidth.toFloat(),
            canvasHeight = pageHeight.toFloat(),
            inkColor = Color.parseColor("#7D7970"),
        )

        var y = padding

        // Header (centered, with indigo brand bottom border)
        val headerBottomY = if (data.businessPhone != null) y + 50f else y + 40f
        if (logoBitmap != null) {
            val logoSize = 40f
            val logoLeft = 32f
            val logoTop = y + (headerBottomY - y - logoSize) / 2f
            val logoRect = android.graphics.RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize)
            val clipPath = android.graphics.Path().apply {
                addRoundRect(
                    logoRect,
                    6f,
                    6f,
                    android.graphics.Path.Direction.CW
                )
            }
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(logoBitmap, null, logoRect, null)
            canvas.restore()
        }
        canvas.drawText(
            data.businessName,
            pageWidth / 2f - headerTitlePaint.measureText(data.businessName) / 2f,
            y + 22f,
            headerTitlePaint
        )
        if (data.businessPhone != null) {
            canvas.drawText(
                data.businessPhone,
                pageWidth / 2f - headerPhonePaint.measureText(data.businessPhone) / 2f,
                y + 38f,
                headerPhonePaint
            )
        }
        y = headerBottomY + 4f
        val borderPaint = Paint().apply {
            color = headerBorderColor
            strokeWidth = 3f
        }
        canvas.drawLine(padding, y, pageWidth - padding, y, borderPaint)
        y += 18f

        // Document type label (RECEIPT / INVOICE) — saffron heritage emphasis
        val docTypePdf = makePaint(saffron, 11f, bold = true).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        canvas.drawText(data.documentTypeLabel, pageWidth / 2f, y, docTypePdf)
        y += 22f

        // Customer & Date
        canvas.drawText("CUSTOMER", padding, y, labelPaintPdf)
        canvas.drawText("DATE", pageWidth - padding - labelPaintPdf.measureText("DATE"), y, labelPaintPdf)
        y += 16f
        canvas.drawText(data.customerName, padding, y, bodyBoldPdf)
        canvas.drawText(
            data.dateFormatted,
            pageWidth - padding - bodyPaintPdf.measureText(data.dateFormatted),
            y,
            bodyPaintPdf
        )
        y += 18f

        // Divider
        canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
        y += 16f

        // Items
        canvas.drawText("ITEMS", padding, y, labelPaintPdf)
        y += 18f
        data.items.forEach { item ->
            if (item.quantity == 1) {
                // Single row: garment name (left) + line total (right, bold). No subtitle.
                canvas.drawText(item.garmentName, padding, y, bodyPaintPdf)
                canvas.drawText(item.formattedPrice, pageWidth - padding, y, priceRightPdf)
                y += 20f
            } else {
                // Row 1: garment name only (no price on the right).
                canvas.drawText(item.garmentName, padding, y, bodyPaintPdf)
                y += 16f
                // Row 2: "Unit price" label + value (legible, value bold — clearly visible).
                val indent = padding + 12f
                canvas.drawText("Unit price", indent, y, unitLabelPdf)
                canvas.drawText(item.formattedUnitPrice, pageWidth - padding, y, unitValuePdf)
                y += 16f
                // Row 3: "Price for N" label (left) + line total (right, bold).
                canvas.drawText("Price for ${item.quantity}", indent, y, bodyBoldPdf)
                canvas.drawText(item.formattedPrice, pageWidth - padding, y, priceRightPdf)
                y += 20f
            }
        }
        y += 6f

        // Payment divider
        canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
        y += 18f

        // Total
        canvas.drawText("Total", padding, y, totalLabelPdf)
        canvas.drawText(data.totalFormatted, pageWidth - padding, y, totalPricePdf)
        y += 20f

        // Deposit
        canvas.drawText("Deposit Paid", padding, y, bodyPaintPdf)
        canvas.drawText(data.depositFormatted, pageWidth - padding, y, depositPricePdf)
        y += 20f

        // Balance
        canvas.drawText("Balance", padding, y, bodyPaintPdf)
        if (data.isFullyPaid) {
            val paidText = "✓ PAID IN FULL"
            val pp = makePaint(green, 13f, bold = true)
            val tw = pp.measureText(paidText)
            val rx = pageWidth - padding - tw - 12f
            val bgp = Paint().apply {
                color = green
                alpha = 25
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(rx, y - 12f, rx + tw + 12f, y + 6f), 6f, 6f, bgp)
            canvas.drawText(paidText, rx + 6f, y, pp)
        } else {
            val dueText = "${data.balanceFormatted} DUE"
            val tw = balancePdf.measureText(dueText)
            val rx = pageWidth - padding - tw - 12f
            val bgp = Paint().apply {
                color = saffron
                alpha = 25
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(rx, y - 12f, rx + tw + 12f, y + 6f), 6f, 6f, bgp)
            canvas.drawText(dueText, rx + 6f, y, balancePdf)
        }
        y += 22f

        // PAY VIA TRANSFER — light PDF variant
        val bankPdf = data.bankBlock
        if (bankPdf != null) {
            y += 12f
            canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
            y += 18f
            canvas.drawText("PAY VIA TRANSFER", padding, y, labelPaintPdf)
            y += 20f
            val valueX = padding + 104f
            canvas.drawText("Bank", padding, y, bodyPaintPdf)
            canvas.drawText(bankPdf.bankName, valueX, y, bodyBoldPdf)
            y += 20f
            canvas.drawText("Account name", padding, y, bodyPaintPdf)
            canvas.drawText(bankPdf.accountName, valueX, y, bodyBoldPdf)
            y += 20f
            canvas.drawText("Account number", padding, y, bodyPaintPdf)
            canvas.drawText(bankPdf.accountNumber, valueX, y, bodyBoldPdf)
            y += 24f
        }

        // Status divider
        canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
        y += 16f

        // Status & Deadline
        canvas.drawText("STATUS", padding, y, labelPaintPdf)
        if (data.deadlineFormatted != null) {
            canvas.drawText("DEADLINE", pageWidth - padding - labelPaintPdf.measureText("DEADLINE"), y, labelPaintPdf)
        }
        y += 16f
        canvas.drawText("● ${data.statusLabel}", padding, y, statusPdf)
        if (data.deadlineFormatted != null) {
            canvas.drawText(
                data.deadlineFormatted,
                pageWidth - padding - bodyPaintPdf.measureText(data.deadlineFormatted),
                y,
                bodyPaintPdf
            )
        }
        y += 6f

        // Priority badge
        if (data.priorityLabel != null) {
            y += 14f
            val bx = if (data.deadlineFormatted != null) {
                pageWidth - padding - rushBadgePdf.measureText(data.priorityLabel) - 12f
            } else {
                padding
            }
            val rect = RectF(bx, y - 10f, bx + rushBadgePdf.measureText(data.priorityLabel) + 12f, y + 6f)
            canvas.drawRoundRect(rect, 4f, 4f, rushBgPdf)
            canvas.drawText(data.priorityLabel, bx + 6f, y + 2f, rushBadgePdf)
        }

        // Footer
        y += 30f
        canvas.drawLine(padding, y, pageWidth - padding, y, linePdf)
        y += 16f
        canvas.drawText("Order #${data.orderIdShort}", pageWidth / 2f, y, footerPdf)
        val attributionTextPdf = data.attribution.footerText
        if (attributionTextPdf != null) {
            y += 14f
            canvas.drawText(attributionTextPdf, pageWidth / 2f, y, footerPdf)
        }

        doc.finishPage(page)

        val file = cacheFile("pdf", "pdf")
        try {
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        pruneOldReceipts()
        return file
    }

    // endregion

    // region Helpers

    private fun makePaint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /**
     * Draws the tier-keyed background watermark before any content. Caller is
     * responsible for invoking this immediately after the canvas background
     * fill so the watermark sits at the lowest z-order.
     *
     * StitchPadDiagonal: a single large "STITCHPAD" wordmark, rotated -30°,
     * centered. inkColor is theme-aware (light gray on dark, dark gray on light).
     * None: no-op (paid tiers ship a clean document).
     */
    private fun drawWatermark(
        canvas: Canvas,
        spec: WatermarkSpec,
        canvasWidth: Float,
        canvasHeight: Float,
        inkColor: Int,
    ) {
        when (spec) {
            WatermarkSpec.None -> Unit
            WatermarkSpec.StitchPadDiagonal -> {
                val wmPaint = Paint().apply {
                    color = inkColor
                    textSize = canvasWidth * 0.12f
                    isAntiAlias = true
                    alpha = WATERMARK_TEXT_ALPHA
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    letterSpacing = 0.08f
                }
                canvas.save()
                canvas.rotate(-30f, canvasWidth / 2f, canvasHeight / 2f)
                canvas.drawText("STITCHPAD", canvasWidth / 2f, canvasHeight / 2f, wmPaint)
                canvas.restore()
            }
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap, prefix: String): File {
        val file = cacheFile(prefix, "png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        pruneOldReceipts()
        return file
    }

    private fun cacheFile(prefix: String, extension: String): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        return File(dir, "receipt_${prefix}_${System.currentTimeMillis()}.$extension")
    }

    private fun pruneOldReceipts() {
        val dir = File(context.cacheDir, "receipts")
        val files = dir.listFiles().orEmpty()
        if (files.size <= CACHE_LIMIT) return
        files.sortedByDescending { it.lastModified() }
            .drop(CACHE_LIMIT)
            .forEach { it.delete() }
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // endregion

    private companion object {
        const val CACHE_LIMIT = 10
    }
}
