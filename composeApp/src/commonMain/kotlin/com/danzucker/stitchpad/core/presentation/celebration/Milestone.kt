package com.danzucker.stitchpad.core.presentation.celebration

/**
 * A once-ever moment worth celebrating. [key] is the stable identifier used for
 * the one-shot preference flag and the GA4 `celebration_shown` param — never
 * rename a key once shipped or users could see the celebration a second time.
 */
sealed interface Milestone {
    val key: String

    /** Workshop setup finished OR skipped — either way, the tailor is in. */
    data object WorkshopReady : Milestone {
        override val key: String = "workshop_ready"
    }

    data class FirstCustomer(
        val customerFirstName: String,
        /**
         * True when the create chains straight into Add Measurements — the
         * card's body/CTA then hand the user into that task instead of a
         * generic "Continue". Presentation-only: key/flag/analytics ignore it.
         */
        val addingMeasurementsNext: Boolean = false,
    ) : Milestone {
        override val key: String = "first_customer"
    }

    data class FirstOrder(val customerFirstName: String) : Milestone {
        override val key: String = "first_order"
    }
}
