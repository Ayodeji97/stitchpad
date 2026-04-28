package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * Discrete action types ranked by revenue impact (lowest ordinal = highest priority).
 * The ordinal drives the carousel sort order; the type drives the card's icon, accent
 * colour, copy template, and whether the primary CTA opens WhatsApp or order detail.
 */
enum class NextBestActionType {
    CollectOverdue,
    CollectOnReady,
    FinishStale,
    DeliverStale,
    CollectDeposit,
    StartSoon
}

/**
 * A concrete suggested action surfaced on the dashboard carousel. All fields are derived
 * from existing Order + Customer data — no new domain fields required.
 *
 * `daysCount` carries different meaning per type:
 *  - CollectOverdue: days past deadline
 *  - CollectOnReady / DeliverStale: days since order entered READY
 *  - FinishStale: days since order entered IN_PROGRESS
 *  - CollectDeposit: days since order was created
 *  - StartSoon: days until deadline
 */
data class NextBestAction(
    val type: NextBestActionType,
    val orderId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val garmentLabel: String,
    val balanceAmount: Double,
    val daysCount: Int
) {
    val id: String get() = "${type.name}:$orderId"

    val opensWhatsApp: Boolean get() =
        type == NextBestActionType.CollectOverdue || type == NextBestActionType.CollectOnReady
}
