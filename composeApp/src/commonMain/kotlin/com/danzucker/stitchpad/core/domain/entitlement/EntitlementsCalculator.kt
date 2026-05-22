package com.danzucker.stitchpad.core.domain.entitlement

import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Pure function: turn user-doc fields into a [UserEntitlements] snapshot.
 *
 * Welcome window definition (per the freemium spec): a new tailor's
 * welcome covers the calendar month they signed up in, in their local
 * timezone. The window ends at midnight on the first day of the NEXT
 * calendar month. Hard-coded to Africa/Lagos for V1.0 — V1.5 will pass
 * the user's timezone through.
 *
 * All limits (15-cap, 30-cap during welcome, coin allowances) live here
 * as constants — change them here, change them once.
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

    fun calculate(
        tier: SubscriptionTier,
        welcomeBonusAppliedAt: Instant?,
        now: Instant,
        timeZone: TimeZone,
    ): UserEntitlements {
        val welcomeEndsAt = welcomeBonusAppliedAt?.let { signedUp ->
            val signupLocal = signedUp.toLocalDateTime(timeZone)
            // First day of the NEXT calendar month, at 00:00 local.
            val nextMonth = signupLocal.date.plusMonths(1).withDayOfMonth1()
            nextMonth.atStartOfDayIn(timeZone)
        }

        val isInWelcomeWindow = welcomeEndsAt != null && now < welcomeEndsAt

        // Lagos calendar days remaining. Computed once and used by both
        // the warning flag and the banner copy so the dashboard never shows
        // "2 days left" while the warning flag thinks 3 days remain
        // (or vice versa) — that drift happened before, when the banner
        // copy did `ms / 86_400_000` in the system default timezone.
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

    // ----- date helpers (kotlinx.datetime doesn't have these built in) -----

    private fun LocalDate.plusMonths(n: Int): LocalDate {
        val totalMonths = this.monthNumber + n
        val newYear = this.year + (totalMonths - 1) / 12
        val newMonth = ((totalMonths - 1) % 12) + 1
        val daysInMonth = daysInMonth(newYear, newMonth)
        return LocalDate(newYear, newMonth, this.dayOfMonth.coerceAtMost(daysInMonth))
    }

    private fun LocalDate.withDayOfMonth1(): LocalDate =
        LocalDate(this.year, this.monthNumber, 1)

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> error("bad month $month")
    }
}
