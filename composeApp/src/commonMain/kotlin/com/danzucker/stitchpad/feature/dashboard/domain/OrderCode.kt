package com.danzucker.stitchpad.feature.dashboard.domain

/**
 * A speakable shorthand for an order, derived from its id — StitchPad has no
 * stored human order number. Pure and deterministic: the same order always
 * renders the same code, so a tailor can say "ORD-C3D4" out loud on the shop
 * floor and it means the same thing every time. Used on the staff dashboard
 * focus-queue hero's tear-line footer (2026-08-14 design spec).
 */
fun orderCodeFor(orderId: String): String = "ORD-" + orderId.takeLast(4).uppercase()
