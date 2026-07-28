package com.danzucker.stitchpad.feature.collection.presentation

import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.collection.domain.CollectibleOrder
import com.danzucker.stitchpad.feature.collection.domain.CollectionFilter
import com.danzucker.stitchpad.feature.collection.domain.CollectionSort
import com.danzucker.stitchpad.feature.collection.domain.CollectionSummary

data class CustomerFilterOption(val id: String, val name: String)

data class ToCollectState(
    val items: List<CollectibleOrder> = emptyList(),
    val summary: CollectionSummary = CollectionSummary(0.0, 0, 0),
    val sort: CollectionSort = CollectionSort.OLDEST_OWED,
    val filter: CollectionFilter = CollectionFilter.None,
    val customerOptions: List<CustomerFilterOption> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
)
