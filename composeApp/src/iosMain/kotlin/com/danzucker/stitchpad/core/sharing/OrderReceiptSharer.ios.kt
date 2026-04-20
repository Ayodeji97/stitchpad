package com.danzucker.stitchpad.core.sharing

import com.danzucker.stitchpad.core.domain.model.Order
import com.danzucker.stitchpad.core.domain.model.OrderPriority
import com.danzucker.stitchpad.core.domain.model.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

actual class OrderReceiptSharer {

    actual suspend fun shareReceipt(order: Order) {
        val text = buildReceiptText(order)
        // UIKit presentation must happen on the main thread; shareReceipt is suspend
        // and can be invoked from any dispatcher.
        withContext(Dispatchers.Main) {
            val rootVC = activeKeyWindow()?.rootViewController ?: return@withContext
            val presenter = rootVC.presentedViewController ?: rootVC
            val activityVC = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null
            )
            presenter.presentViewController(activityVC, animated = true, completion = null)
        }
    }

    // Resolve the foreground-active window via connected scenes (iOS 13+).
    // UIApplication.keyWindow is deprecated and returns null in multi-scene apps.
    private fun activeKeyWindow(): UIWindow? {
        val scenes = UIApplication.sharedApplication.connectedScenes
        val activeScene = scenes.firstOrNull {
            it is UIWindowScene && it.activationState == UISceneActivationStateForegroundActive
        } as? UIWindowScene ?: scenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
        val windows = activeScene?.windows.orEmpty()
        return windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
            ?: windows.firstOrNull() as? UIWindow
    }

    private fun buildReceiptText(order: Order): String = buildString {
        appendLine("\uD83D\uDCCB ORDER RECEIPT")
        appendLine("\u2501".repeat(20))
        appendLine()
        appendLine("Customer: ${order.customerName}")
        appendLine()

        appendLine("ITEMS:")
        order.items.forEach { item ->
            val garmentName = item.garmentType.name.replace("_", " ")
            appendLine("\u2022 $garmentName \u2014 \u20A6${formatPrice(item.price)}")
            if (item.description.isNotBlank()) {
                appendLine("  ${item.description}")
            }
        }
        appendLine()

        appendLine("PAYMENT:")
        appendLine("Total: \u20A6${formatPrice(order.totalPrice)}")
        appendLine("Deposit: \u20A6${formatPrice(order.depositPaid)}")
        appendLine("Balance: \u20A6${formatPrice(order.balanceRemaining)}")
        appendLine()

        val statusText = when (order.status) {
            OrderStatus.PENDING -> "Pending"
            OrderStatus.IN_PROGRESS -> "In Progress"
            OrderStatus.READY -> "Ready"
            OrderStatus.DELIVERED -> "Delivered"
        }
        appendLine("Status: $statusText")

        if (order.deadline != null) {
            val formatter = NSDateFormatter().apply { dateFormat = "dd/MM/yyyy" }
            val date = NSDate.dateWithTimeIntervalSince1970(order.deadline / 1000.0)
            appendLine("Deadline: ${formatter.stringFromDate(date)}")
        }

        if (order.priority != OrderPriority.NORMAL) {
            val priorityText = when (order.priority) {
                OrderPriority.NORMAL -> "Normal"
                OrderPriority.URGENT -> "Urgent"
                OrderPriority.RUSH -> "Rush"
            }
            appendLine("Priority: $priorityText")
        }

        if (!order.notes.isNullOrBlank()) {
            appendLine()
            appendLine("Notes: ${order.notes}")
        }

        appendLine()
        appendLine("\u2014 StitchPad")
    }

    private fun formatPrice(price: Double): String {
        val long = price.toLong()
        if (price == long.toDouble()) return addThousandsSeparator(long.toString())
        val parts = price.toString().split(".")
        val decimal = (parts.getOrElse(1) { "00" } + "00").take(2)
        return addThousandsSeparator(parts[0]) + "." + decimal
    }

    private fun addThousandsSeparator(intPart: String): String {
        val negative = intPart.startsWith("-")
        val digits = if (negative) intPart.drop(1) else intPart
        val result = buildString {
            digits.reversed().forEachIndexed { i, c ->
                if (i > 0 && i % 3 == 0) append(',')
                append(c)
            }
        }.reversed()
        return if (negative) "-$result" else result
    }
}
