package com.danzucker.stitchpad.feature.measurement.presentation.detail

sealed interface MeasurementDetailEvent {
    data object NavigateBack : MeasurementDetailEvent
    data class NavigateToEdit(val customerId: String, val measurementId: String) : MeasurementDetailEvent

    /** Locked (over-cap) customer tapped a gated action — Edit, Rename, or Delete. */
    data object NavigateToUpgrade : MeasurementDetailEvent
}

/** GA4 `source` values for [com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent.MeasurementDetailViewed]. */
object MeasurementDetailSource {
    const val CUSTOMER_DETAIL = "customer_detail"
    const val ORDER_DETAIL = "order_detail"
    const val POST_SAVE = "post_save"
}
