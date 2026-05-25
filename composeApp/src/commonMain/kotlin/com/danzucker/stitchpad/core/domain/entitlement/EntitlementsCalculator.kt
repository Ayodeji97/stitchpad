package com.danzucker.stitchpad.core.domain.entitlement

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * Pure function: turn user-doc fields into a [UserEntitlements] snapshot.
 *
 * Welcome window definition (per the freemium V1.0 design spec, updated
 * 2026-05-22): a new tailor's First Month is a **rolling 30 days from
 * signup**. The window ends exactly 30 days after `welcomeBonusAppliedAt`,
 * regardless of when in the calendar month they signed up — so every user
 * gets the same 30-day experience, not "whatever's left of the month".
 *
 * (The previous calendar-month-aligned model was unfair to late-in-month
 * signups: a tailor signing up on the 28th only got 3 days of First Month.
 * Smart-draft monthly quota still resets on the Lagos calendar 1st — that's
 * a separate billing-cycle concept handled by `freeTierCounter.ts`.)
 *
 * All limits (15-cap post-First-Month, 200-cap during First Month, coin
 * allowances, 30-day welcome) live here as constants — change them here,
 * change them once. The server-side counterpart is
 * `functions/src/freemium/reconcileSlots.ts` — keep `effectiveCap` and
 * the welcome-end calculation in lockstep with the values below.
 */
object EntitlementsCalculator {

    const val FREE_CUSTOMER_CAP: Int = 15

    /**
     * First-Month customer cap for newly signed-up Free tailors.
     *
     * Public framing: "unlimited" (see freemium V1.0 design spec). This 200 is a system
     * safety ceiling, not a user-facing limit — the UI never displays the number. The rare
     * power user who reaches 200 hits a white-glove escalation screen (PR 6), not a generic
     * cap-reached state.
     */
    const val WELCOME_CUSTOMER_CAP: Int = 200
    const val FREE_COIN_ALLOWANCE: Int = 5
    const val PRO_COIN_ALLOWANCE: Int = 50
    const val ATELIER_COIN_ALLOWANCE: Int = 500
    const val WELCOME_ENDING_WARNING_DAYS: Int = 3

    /**
     * Length of the rolling First Month window in days, measured from
     * `welcomeBonusAppliedAt`. MUST stay in lockstep with the server-side
     * `WELCOME_WINDOW_DAYS` in `functions/src/freemium/reconcileSlots.ts`.
     */
    const val WELCOME_WINDOW_DAYS: Int = 30

    fun calculate(
        tier: SubscriptionTier,
        welcomeBonusAppliedAt: Instant?,
        now: Instant,
        timeZone: TimeZone,
    ): UserEntitlements {
        // `Instant + Duration` is the canonical signature across Kotlin/Native and JVM.
        // The 3-arg form `plus(value, unit)` exists only on JVM kotlinx.datetime and
        // silently breaks the iOS framework build — see [[feedback-kmp-jvm-only-apis]].
        val welcomeEndsAt = welcomeBonusAppliedAt?.plus(WELCOME_WINDOW_DAYS.days)

        val isInWelcomeWindow = welcomeEndsAt != null && now < welcomeEndsAt

        // Days-remaining is computed against the user's local calendar day in
        // [timeZone] (Africa/Lagos for V1.0) so the chip shows the same N
        // regardless of what side of midnight the user opens the app — that's
        // a UX requirement, not a math nicety. The conversion to LocalDate
        // truncates time-of-day so partial-day fractions don't round oddly.
        val welcomeDaysLeft: Int? = welcomeEndsAt?.takeIf { isInWelcomeWindow }?.let { end ->
            val nowLocal = now.toLocalDateTime(timeZone).date
            val endLocal = end.toLocalDateTime(timeZone).date
            nowLocal.daysUntil(endLocal)
        }

        val isWithinWelcomeEndingWarning =
            welcomeDaysLeft != null && welcomeDaysLeft <= WELCOME_ENDING_WARNING_DAYS

        val customerCap = when {
            tier == SubscriptionTier.FREE && isInWelcomeWindow -> WELCOME_CUSTOMER_CAP
            tier == SubscriptionTier.FREE -> FREE_CUSTOMER_CAP
            else -> Int.MAX_VALUE
        }

        val coinAllowance = when (tier) {
            SubscriptionTier.FREE -> FREE_COIN_ALLOWANCE
            SubscriptionTier.PRO -> PRO_COIN_ALLOWANCE
            SubscriptionTier.ATELIER -> ATELIER_COIN_ALLOWANCE
        }

        return UserEntitlements(
            tier = tier,
            customerCap = customerCap,
            smartCoinAllowance = coinAllowance,
            isInWelcomeWindow = isInWelcomeWindow,
            welcomeEndsAt = welcomeEndsAt,
            isWithinWelcomeEndingWarning = isWithinWelcomeEndingWarning,
            welcomeDaysLeft = welcomeDaysLeft,
        )
    }
}
