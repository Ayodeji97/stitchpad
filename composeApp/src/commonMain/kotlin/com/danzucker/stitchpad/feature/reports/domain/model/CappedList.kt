package com.danzucker.stitchpad.feature.reports.domain.model

/**
 * A list of items capped to a preview size, alongside the unfiltered total.
 *
 * Reports cards (Top Customers, Outstanding Balances) show only the top N
 * entries, but their headers need to know whether more exist — so the
 * "View all" affordance can be hidden when there's nothing extra and
 * annotated with the total when there is.
 */
data class CappedList<T>(
    val items: List<T>,
    val totalCount: Int
) {
    val hasMore: Boolean get() = totalCount > items.size

    companion object {
        fun <T> empty(): CappedList<T> = CappedList(items = emptyList(), totalCount = 0)
    }
}
