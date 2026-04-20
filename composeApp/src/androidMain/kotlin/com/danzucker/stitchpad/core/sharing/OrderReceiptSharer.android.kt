package com.danzucker.stitchpad.core.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.share_order_receipt_chooser_title
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

actual class OrderReceiptSharer(private val context: Context) {

    actual suspend fun shareReceipt(order: Order) {
        // Bitmap rendering + PNG encode + disk write are heavy — keep off main.
        val file = withContext(Dispatchers.Default) {
            val bitmap = generateReceiptBitmap(order)
            saveBitmapToCache(bitmap, order.id)
        }
        val chooserTitle = getString(Res.string.share_order_receipt_chooser_title)
        shareImage(file, chooserTitle)
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun generateReceiptBitmap(order: Order): Bitmap {
        val width = 800
        val padding = 40f

        // Paints
        val titlePaint = Paint().apply {
            color = Color.parseColor("#E8A800")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.parseColor("#A8A49D")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#E5E3DF")
            textSize = 24f
            isAntiAlias = true
        }
        val bodyBoldPaint = Paint().apply {
            color = Color.parseColor("#E5E3DF")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val pricePaint = Paint().apply {
            color = Color.parseColor("#E5E3DF")
            textSize = 24f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val accentPaint = Paint().apply {
            color = Color.parseColor("#E8A800")
            textSize = 24f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtlePaint = Paint().apply {
            color = Color.parseColor("#7D7970")
            textSize = 20f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#3A3731")
            strokeWidth = 1f
        }

        // Calculate height
        var y = 0f
        val lineHeight = 36f
        val sectionGap = 24f

        // Pre-calculate height
        var estimatedHeight = padding + // top padding
            40f + // title
            sectionGap +
            lineHeight + // customer
            sectionGap +
            lineHeight + // "Items" header
            order.items.size * (lineHeight * 2) + // items
            sectionGap +
            lineHeight + // "Payment" header
            lineHeight * 3 + // total, deposit, balance
            sectionGap
        if (order.deadline != null) estimatedHeight += lineHeight * 2 + sectionGap
        estimatedHeight += lineHeight * 2 + sectionGap // priority
        estimatedHeight += lineHeight + sectionGap // status
        if (!order.notes.isNullOrBlank()) estimatedHeight += lineHeight * 3 + sectionGap
        estimatedHeight += lineHeight * 2 + padding // footer

        val height = estimatedHeight.toInt().coerceAtLeast(400)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#121110"))

        y = padding

        // Title
        canvas.drawText("ORDER RECEIPT", padding, y + 32f, titlePaint)
        y += 40f

        // Divider
        y += 8f
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += sectionGap

        // Customer
        canvas.drawText("CUSTOMER", padding, y + 22f, headerPaint)
        y += lineHeight
        canvas.drawText(order.customerName, padding, y + 24f, bodyBoldPaint)
        y += lineHeight + sectionGap

        // Divider
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += sectionGap

        // Items
        canvas.drawText("ITEMS", padding, y + 22f, headerPaint)
        y += lineHeight

        order.items.forEach { item ->
            val garmentName = item.garmentType.name.replace("_", " ")
            canvas.drawText("\u2022 $garmentName", padding, y + 24f, bodyPaint)
            canvas.drawText(
                "\u20A6${formatPrice(item.price)}",
                width - padding,
                y + 24f,
                pricePaint
            )
            y += lineHeight
            if (item.description.isNotBlank()) {
                canvas.drawText("  ${item.description}", padding + 16f, y + 20f, subtlePaint)
                y += lineHeight * 0.8f
            }
        }
        y += sectionGap

        // Divider
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += sectionGap

        // Payment
        canvas.drawText("PAYMENT", padding, y + 22f, headerPaint)
        y += lineHeight

        canvas.drawText("Total", padding, y + 24f, bodyPaint)
        canvas.drawText(
            "\u20A6${formatPrice(order.totalPrice)}",
            width - padding,
            y + 24f,
            pricePaint
        )
        y += lineHeight

        canvas.drawText("Deposit", padding, y + 24f, bodyPaint)
        canvas.drawText(
            "\u20A6${formatPrice(order.depositPaid)}",
            width - padding,
            y + 24f,
            pricePaint
        )
        y += lineHeight

        canvas.drawText("Balance", padding, y + 24f, bodyBoldPaint)
        canvas.drawText(
            "\u20A6${formatPrice(order.balanceRemaining)}",
            width - padding,
            y + 24f,
            accentPaint
        )
        y += lineHeight + sectionGap

        // Status
        canvas.drawText("STATUS", padding, y + 22f, headerPaint)
        y += lineHeight
        val statusText = when (order.status) {
            OrderStatus.PENDING -> "Pending"
            OrderStatus.IN_PROGRESS -> "In Progress"
            OrderStatus.READY -> "Ready"
            OrderStatus.DELIVERED -> "Delivered"
        }
        canvas.drawText(statusText, padding, y + 24f, bodyPaint)
        y += lineHeight + sectionGap

        // Deadline
        if (order.deadline != null) {
            canvas.drawText("DEADLINE", padding, y + 22f, headerPaint)
            y += lineHeight
            val date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(order.deadline))
            canvas.drawText(date, padding, y + 24f, bodyPaint)
            y += lineHeight + sectionGap
        }

        // Priority
        if (order.priority != OrderPriority.NORMAL) {
            canvas.drawText("PRIORITY", padding, y + 22f, headerPaint)
            y += lineHeight
            val priorityText = when (order.priority) {
                OrderPriority.NORMAL -> "Normal"
                OrderPriority.URGENT -> "Urgent"
                OrderPriority.RUSH -> "Rush"
            }
            canvas.drawText(priorityText, padding, y + 24f, bodyPaint)
            y += lineHeight + sectionGap
        }

        // Notes
        if (!order.notes.isNullOrBlank()) {
            canvas.drawText("NOTES", padding, y + 22f, headerPaint)
            y += lineHeight
            canvas.drawText(order.notes, padding, y + 24f, subtlePaint)
            y += lineHeight + sectionGap
        }

        // Footer
        y += 8f
        canvas.drawLine(padding, y, width - padding, y, linePaint)
        y += sectionGap
        val footerPaint = Paint().apply {
            color = Color.parseColor("#7D7970")
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Generated by StitchPad", width / 2f, y + 18f, footerPaint)

        return bitmap
    }

    private fun saveBitmapToCache(bitmap: Bitmap, orderId: String): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        // Unique filename per share avoids overwriting a pending intent's source file
        // if the user triggers two shares quickly.
        val safeId = orderId.ifBlank { "unknown" }.take(RECEIPT_ID_MAX)
        val file = File(dir, "order_receipt_${safeId}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        pruneOldReceipts(dir)
        return file
    }

    private fun pruneOldReceipts(dir: File) {
        val files = dir.listFiles().orEmpty()
        if (files.size <= RECEIPT_CACHE_LIMIT) return
        files.sortedByDescending { it.lastModified() }
            .drop(RECEIPT_CACHE_LIMIT)
            .forEach { it.delete() }
    }

    private fun shareImage(file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun formatPrice(price: Double): String {
        val long = price.toLong()
        if (price == long.toDouble()) return String.format(Locale.US, "%,d", long)
        return String.format(Locale.US, "%,.2f", price)
    }

    private companion object {
        const val RECEIPT_ID_MAX = 16
        const val RECEIPT_CACHE_LIMIT = 5
    }
}
