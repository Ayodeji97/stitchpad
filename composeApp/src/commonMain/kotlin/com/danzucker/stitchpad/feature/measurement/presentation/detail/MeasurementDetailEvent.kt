package com.danzucker.stitchpad.feature.measurement.presentation.detail

sealed interface MeasurementDetailEvent {
    data object NavigateBack : MeasurementDetailEvent
    data class NavigateToEdit(val customerId: String, val measurementId: String) : MeasurementDetailEvent

    /** Locked (over-cap) customer tapped a gated action — Edit, Rename, or Delete. */
    data object NavigateToUpgrade : MeasurementDetailEvent

    /** WhatsApp share chosen and the customer has a phone on file — Root launches the WhatsApp intent/URL. */
    data class LaunchWhatsApp(val phone: String, val message: String) : MeasurementDetailEvent
}

/** GA4 `source` values for [com.danzucker.stitchpad.core.analytics.domain.AnalyticsEvent.MeasurementDetailViewed]. */
object MeasurementDetailSource {
    const val CUSTOMER_DETAIL = "customer_detail"
    const val ORDER_DETAIL = "order_detail"
    const val POST_SAVE = "post_save"
    const val DASHBOARD = "dashboard"
    const val CUSTOMER_ACTIONS_SHEET = "customer_actions_sheet"
}
