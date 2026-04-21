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
        val headerBg = Color.parseColor("#E8A800")
        val headerText = Color.parseColor("#121110")
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
        val bodyBoldPaint = makePaint(bodyText, 18f, bold = true)
        val priceRightPaint = makePaint(bodyText, 18f).apply { textAlign = Paint.Align.RIGHT }
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
        estimatedHeight += 60f // customer + date row
        estimatedHeight += 20f // divider gap
        estimatedHeight += 30f // Items label
        estimatedHeight += data.items.size * 30f // items
        estimatedHeight += 20f // gap
        estimatedHeight += 30f // divider gap
        estimatedHeight += 30f * 3 // total/deposit/balance
        estimatedHeight += 30f // gap
        estimatedHeight += 20f // divider
        estimatedHeight += 50f // status + deadline row
        if (data.priorityLabel != null) estimatedHeight += 30f
        estimatedHeight += 50f // footer
        estimatedHeight += padding * 2

        val height = estimatedHeight.toInt().coerceAtLeast(500)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        var y = 0f

        // Header band
        val headerBgPaint = Paint().apply {
            color = headerBg
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerBgPaint)
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
        y = headerHeight + padding

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
            val itemText = "${item.quantity} × ${item.garmentName}"
            canvas.drawText(itemText, padding, y, bodyPaint)
            canvas.drawText(item.formattedPrice, width - padding, y, priceRightPaint)
            y += 28f
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
        val pageHeight = 595
        val padding = 30f

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
        val headerBorderColor = Color.parseColor("#E8A800")

        // Paints
        val headerTitlePaint = makePaint(bodyText, 22f, bold = true)
        val headerPhonePaint = makePaint(labelColor, 12f)
        val labelPaintPdf = makePaint(labelColor, 10f, bold = true)
        val bodyPaintPdf = makePaint(bodyText, 14f)
        val bodyBoldPdf = makePaint(bodyText, 14f, bold = true)
        val priceRightPdf = makePaint(bodyText, 14f).apply { textAlign = Paint.Align.RIGHT }
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

        var y = padding

        // Header (centered, with saffron bottom border)
        val headerBottomY = if (data.businessPhone != null) y + 50f else y + 40f
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
            val text = "${item.quantity} × ${item.garmentName}"
            canvas.drawText(text, padding, y, bodyPaintPdf)
            canvas.drawText(item.formattedPrice, pageWidth - padding, y, priceRightPdf)
            y += 20f
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
