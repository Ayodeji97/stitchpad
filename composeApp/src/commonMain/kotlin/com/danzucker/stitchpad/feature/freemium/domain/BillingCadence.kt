package com.danzucker.stitchpad.feature.freemium.domain

enum class BillingCadence(val wireValue: String) {
    MONTHLY("monthly"),
    ANNUAL("annual");

    companion object {
        /** Parse a wire value, defaulting to MONTHLY for unknown/missing input (never throws). */
        fun fromWire(value: String?): BillingCadence = when (value?.lowercase()) {
            "annual" -> ANNUAL
            else -> MONTHLY
        }
    }
}
