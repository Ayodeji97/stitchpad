package com.danzucker.stitchpad.feature.dashboard.presentation.model

/**
 * Drives the persistent "Order setup" checklist that follows a tailor from
 * FirstCustomer through the moment their first order has both a due date
 * and a deposit recorded. Non-null only while at least one of those is
 * still missing — once the first order is fully set up, this flips to
 * null and the checklist disappears.
 *
 * When the user has more than one order (i.e. they've moved past
 * onboarding), this is also null — the checklist is first-order-only.
 *
 * @param customerName name of the customer the first order is for; surfaces
 *   in the focus card copy ("Finish setting up Omobolanle's order").
 * @param orderId the first order's id — null when no order exists yet.
 *   Used to route the SetDueDate / RecordDeposit step taps to that order's
 *   detail screen.
 * @param hasOrder whether the first order has been created at all.
 * @param hasDueDate whether the first order has a deadline set.
 * @param hasDeposit whether the first order has a deposit recorded (>0).
 */
data class FirstOrderSetupUi(
    val customerName: String,
    val orderId: String?,
    val hasOrder: Boolean,
    val hasDueDate: Boolean,
    val hasDeposit: Boolean,
    /** Garment label for the order (e.g. "Senator"). Empty when no order yet. */
    val garmentLabel: String = "",
    /** Order total in naira. 0.0 when no order yet. */
    val totalAmount: Double = 0.0,
)
