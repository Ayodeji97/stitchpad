package com.danzucker.stitchpad.feature.order.presentation.list

import com.danzucker.stitchpad.core.domain.model.OrderStatus

/**
 * Whether the "All" filter chip should render as selected. "All" means NO filter of any
 * kind is active — so it must yield to both Archived and myWorkOnly (Task 8 dashboard
 * deep-link seeds myWorkOnly = true with selectedStatus = null; without the myWorkOnly
 * check here, "All" would incorrectly highlight alongside "My work" in that state).
 */
internal fun allChipSelected(
    showArchived: Boolean,
    selectedStatus: OrderStatus?,
    myWorkOnly: Boolean
): Boolean = !showArchived && selectedStatus == null && !myWorkOnly
