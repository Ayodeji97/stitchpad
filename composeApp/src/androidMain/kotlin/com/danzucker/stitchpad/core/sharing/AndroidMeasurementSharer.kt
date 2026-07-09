package com.danzucker.stitchpad.core.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Paper-light measurement card renderer + Android share sheet. Structurally mirrors
 * [OrderReceiptSharer], but the card only ever renders in the light Adire Atelier
 * palette (image AND PDF) — there's no dark variant.
 */
@Suppress("LargeClass")
class AndroidMeasurementSharer(private val context: Context) : MeasurementSharer {

    override suspend fun shareAsImage(data: MeasurementShareData) {
        val file = withContext(Dispatchers.Default) {
            saveBitmapToCache(renderCardBitmap(data), "measurement")
        }
        shareFile(file, "image/png")
    }

    override suspend fun shareAsPdf(data: MeasurementShareData) {
        val file = withContext(Dispatchers.Default) {
            renderCardPdf(data)
        }
        shareFile(file, "application/pdf")
    }

    // region Card Bitmap Rendering (paper light)

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderCardBitmap(data: MeasurementShareData): Bitmap {
        val width = 800
        val padding = 40f

        // Colors
        val bgColor = Color.parseColor("#FAF6EC")
        val indigo = Color.parseColor("#2C3E7C")
        val muted = Color.parseColor("#7D7970")
        val dividerColor = Color.parseColor("#E5E3DF")
        val ink = Color.parseColor("#14110E")
        val metaColor = Color.parseColor("#57534C")
        val sienna = Color.parseColor("#8E4524")

        // Paints
        val wordmarkPaint = makePaint(indigo, 34f, bold = true)
        val cardLabelPaint = makePaint(muted, 18f).apply {
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.15f
        }
        val dividerPaint = Paint().apply {
            color = dividerColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val customerNamePaint = makePaint(ink, 44f, bold = true)
        val metaPaint = makePaint(metaColor, 22f)
        val sectionTitlePaint = makePaint(sienna, 20f, bold = true).apply { letterSpacing = 0.12f }
        val labelPaint = makePaint(metaColor, 24f)
        val valuePaint = Paint().apply {
            color = ink
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val notesPaint = Paint().apply {
            color = metaColor
            textSize = 22f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val footerPaint = makePaint(muted, 18f).apply { textAlign = Paint.Align.CENTER }

        val wrappedNotes = data.notes?.let { wrapText(it, notesPaint, width - 2 * padding) }.orEmpty()

        // Estimate height generously — Android crops to actual content height below.
        var estimatedHeight = 228f // header + customer + meta (incl. wider header-divider gaps)
        data.sections.forEach { section ->
            estimatedHeight += 56f + 44f * section.rows.size
        }
        estimatedHeight += 34f * wrappedNotes.size // per notes line
        estimatedHeight += 120f // footer
        estimatedHeight += 200f // slack

        val height = estimatedHeight.toInt().coerceAtLeast(500)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        // Header row: wordmark left, card label right
        var y = padding + 26f
        canvas.drawText("StitchPad", padding, y, wordmarkPaint)
        canvas.drawText("MEASUREMENT CARD", width - padding, y - 6f, cardLabelPaint)
        y += 36f // header-to-divider gap (was 24f)
        canvas.drawLine(padding, y, width - padding, y, dividerPaint)
        y += 46f // divider-to-customer gap (was 30f)

        // Customer name
        canvas.drawText(data.customerName, padding, y, customerNamePaint)
        y += 34f

        // Meta line
        val metaParts = buildList {
            add(data.measurementName)
            add(data.genderLabel)
            add(data.unitLabel)
            data.dateFormatted?.let { add("Taken $it") }
            data.businessName?.let { add(it) }
        }
        canvas.drawText(metaParts.joinToString(" · "), padding, y, metaPaint)

        // Sections
        data.sections.forEach { section ->
            y += 28f
            canvas.drawLine(padding, y, width - padding, y, dividerPaint)
            y += 28f
            canvas.drawText(section.title.uppercase(), padding, y, sectionTitlePaint)
            section.rows.forEach { row ->
                y += 44f
                canvas.drawText(row.label, padding, y, labelPaint)
                canvas.drawText("${row.value}${data.unitSuffix}", width - padding, y, valuePaint)
            }
        }

        // Notes
        if (wrappedNotes.isNotEmpty()) {
            y += 28f
            canvas.drawLine(padding, y, width - padding, y, dividerPaint)
            y += 28f
            wrappedNotes.forEach { line ->
                y += 34f
                canvas.drawText(line, padding, y, notesPaint)
            }
        }

        // Footer
        y += 32f
        canvas.drawLine(padding, y, width - padding, y, dividerPaint)
        y += 32f
        canvas.drawText(FOOTER_TEXT, width / 2f, y, footerPaint)

        // Crop to actual content height
        val finalHeight = (y + padding).toInt().coerceAtMost(height)
        return Bitmap.createBitmap(bitmap, 0, 0, width, finalHeight)
    }

    // endregion

    // region Card PDF Rendering (paper light)

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun renderCardPdf(data: MeasurementShareData): File {
        val pageWidth = 420
        val padding = 30f

        // Colors — same palette as the bitmap renderer
        val bgColor = Color.parseColor("#FAF6EC")
        val indigo = Color.parseColor("#2C3E7C")
        val muted = Color.parseColor("#7D7970")
        val dividerColor = Color.parseColor("#E5E3DF")
        val ink = Color.parseColor("#14110E")
        val metaColor = Color.parseColor("#57534C")
        val sienna = Color.parseColor("#8E4524")

        // Paints — half the bitmap font sizes
        val wordmarkPaint = makePaint(indigo, 17f, bold = true)
        val cardLabelPaint = makePaint(muted, 9f).apply {
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.15f
        }
        val dividerPaint = Paint().apply {
            color = dividerColor
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val customerNamePaint = makePaint(ink, 22f, bold = true)
        val metaPaint = makePaint(metaColor, 11f)
        val sectionTitlePaint = makePaint(sienna, 10f, bold = true).apply { letterSpacing = 0.12f }
        val labelPaint = makePaint(metaColor, 12f)
        val valuePaint = Paint().apply {
            color = ink
            textSize = 12f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val notesPaint = Paint().apply {
            color = metaColor
            textSize = 11f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        val footerPaint = makePaint(muted, 9f).apply { textAlign = Paint.Align.CENTER }

        val wrappedNotes = data.notes?.let { wrapText(it, notesPaint, pageWidth - 2 * padding) }.orEmpty()

        // Compute an exact page height by summing the same y-advances the draw code
        // below uses — a PDF page can't be cropped after the fact like the bitmap.
        var estimatedHeight = padding
        estimatedHeight += 13f + 18f // header gap + header-to-divider gap (was 12f)
        estimatedHeight += 23f // divider-to-customer gap (was 15f)
        estimatedHeight += 17f // customer-to-meta gap
        data.sections.forEach { section ->
            estimatedHeight += 14f + 14f + 22f * section.rows.size
        }
        if (wrappedNotes.isNotEmpty()) {
            estimatedHeight += 14f + 14f + 17f * wrappedNotes.size
        }
        estimatedHeight += 16f + 16f // footer gaps
        estimatedHeight += padding // bottom breathing room

        val pageHeight = maxOf(595, kotlin.math.ceil(estimatedHeight).toInt())

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(bgColor)

        var y = padding + 13f
        canvas.drawText("StitchPad", padding, y, wordmarkPaint)
        canvas.drawText("MEASUREMENT CARD", pageWidth - padding, y - 3f, cardLabelPaint)
        y += 18f // header-to-divider gap (was 12f)
        canvas.drawLine(padding, y, pageWidth - padding, y, dividerPaint)
        y += 23f // divider-to-customer gap (was 15f)

        canvas.drawText(data.customerName, padding, y, customerNamePaint)
        y += 17f

        val metaParts = buildList {
            add(data.measurementName)
            add(data.genderLabel)
            add(data.unitLabel)
            data.dateFormatted?.let { add("Taken $it") }
            data.businessName?.let { add(it) }
        }
        canvas.drawText(metaParts.joinToString(" · "), padding, y, metaPaint)

        data.sections.forEach { section ->
            y += 14f
            canvas.drawLine(padding, y, pageWidth - padding, y, dividerPaint)
            y += 14f
            canvas.drawText(section.title.uppercase(), padding, y, sectionTitlePaint)
            section.rows.forEach { row ->
                y += 22f
                canvas.drawText(row.label, padding, y, labelPaint)
                canvas.drawText("${row.value}${data.unitSuffix}", pageWidth - padding, y, valuePaint)
            }
        }

        if (wrappedNotes.isNotEmpty()) {
            y += 14f
            canvas.drawLine(padding, y, pageWidth - padding, y, dividerPaint)
            y += 14f
            wrappedNotes.forEach { line ->
                y += 17f
                canvas.drawText(line, padding, y, notesPaint)
            }
        }

        y += 16f
        canvas.drawLine(padding, y, pageWidth - padding, y, dividerPaint)
        y += 16f
        canvas.drawText(FOOTER_TEXT, pageWidth / 2f, y, footerPaint)

        doc.finishPage(page)

        val file = cacheFile("pdf", "pdf")
        try {
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        pruneOldFiles()
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

    /** Greedily wraps [text] into lines that fit within [maxWidth] under [paint]'s metrics. */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun saveBitmapToCache(bitmap: Bitmap, prefix: String): File {
        val file = cacheFile(prefix, "png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        pruneOldFiles()
        return file
    }

    private fun cacheFile(prefix: String, extension: String): File {
        val dir = File(context.cacheDir, "measurements").apply { mkdirs() }
        return File(dir, "measurement_${prefix}_${System.currentTimeMillis()}.$extension")
    }

    private fun pruneOldFiles() {
        val dir = File(context.cacheDir, "measurements")
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
        const val FOOTER_TEXT = "Made with StitchPad — the smart work pad for tailors · getstitchpad.com"
    }
}
