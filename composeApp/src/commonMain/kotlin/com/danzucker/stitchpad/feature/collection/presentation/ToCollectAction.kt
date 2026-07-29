package com.danzucker.stitchpad.feature.collection.presentation

import com.danzucker.stitchpad.feature.collection.domain.CollectionFilter
import com.danzucker.stitchpad.feature.collection.domain.CollectionSort

sealed interface ToCollectAction {
    data object OnBackClick : ToCollectAction
    data class OnSortSelected(val sort: CollectionSort) : ToCollectAction
    data class OnFilterSelected(val filter: CollectionFilter) : ToCollectAction
    data class OnRowClick(val orderId: String) : ToCollectAction
    data class OnChaseClick(val orderId: String) : ToCollectAction
}
