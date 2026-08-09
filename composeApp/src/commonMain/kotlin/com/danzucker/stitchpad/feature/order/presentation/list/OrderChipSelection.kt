package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus

/**
 * Whether the "All" filter chip should render as selected. "All" means NO filter of any
 * kind is active — so it must yield to Archived, myWorkOnly (Task 8 dashboard deep-link
 * seeds myWorkOnly = true with selectedStatus = null; without the myWorkOnly check here,
 * "All" would incorrectly highlight alongside "My work" in that state), and now
 * assigneeFilter (an `assignee:` deep link — Task 8's own filter, and the one Task 9's
 * Team workload rows seed).
 */
internal fun allChipSelected(
    showArchived: Boolean,
    selectedStatus: OrderStatus?,
    myWorkOnly: Boolean,
    assigneeFilter: String? = null,
): Boolean = !showArchived && selectedStatus == null && !myWorkOnly && assigneeFilter == null
