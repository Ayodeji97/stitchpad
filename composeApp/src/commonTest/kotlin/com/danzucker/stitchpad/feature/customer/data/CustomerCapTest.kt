package com.danzucker.stitchpad.feature.customer.data

import com.danzucker.stitchpad.core.data.dto.CustomerDto
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsCalculator
import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for the customer cap logic.
 *
 * [FirebaseCustomerRepository] delegates ACTIVE-slot counting to
 * [countActiveCustomers] — a pure internal function. Tests here target that
 * function directly so we avoid needing to fake [dev.gitlive.firebase.firestore.FirebaseFirestore].
 *
 * Three tests mirror the plan's scenarios:
 *   1. at-cap → CAP_REACHED (standard 15-cap)
 *   2. under-cap → allowed (standard 15-cap)
 *   3. welcome window cap (200) → allowed when one slot is still free
 */
class CustomerCapTest {

    // ── countActiveCustomers pure-function tests ──────────────────────────

    @Test
    fun countActiveCustomers_returns_CapReached_when_active_slot_count_equals_cap() {
        // 15 ACTIVE slots, cap = 15 → at cap
        val dtos = List(15) { makeDto(slotState = "active") }
        val cap = EntitlementsCalculator.FREE_CUSTOMER_CAP // 15

        val activeCount = countActiveCustomers(dtos)
        val capReached = activeCount >= cap

        assertEquals(15, activeCount)
        assertEquals(true, capReached, "Expected cap to be reached at count=$activeCount / cap=$cap")
    }

    @Test
    fun countActiveCustomers_succeeds_when_under_cap() {
        // 14 ACTIVE slots, cap = 15 → one slot free
        val dtos = List(14) { makeDto(slotState = "active") }
        val cap = EntitlementsCalculator.FREE_CUSTOMER_CAP // 15

        val activeCount = countActiveCustomers(dtos)
        val capReached = activeCount >= cap

        assertEquals(14, activeCount)
        assertEquals(false, capReached, "Expected cap NOT to be reached at count=$activeCount / cap=$cap")
    }

    @Test
    fun countActiveCustomers_does_not_reach_welcome_cap_well_below_it() {
        // 29 ACTIVE slots, cap = WELCOME_CUSTOMER_CAP (First Month) → still well below the cap.
        // Asserts the count is the active count, not a stale "cap reached" flag.
        val dtos = List(29) { makeDto(slotState = "active") }
        val cap = EntitlementsCalculator.WELCOME_CUSTOMER_CAP

        val activeCount = countActiveCustomers(dtos)
        val capReached = activeCount >= cap

        assertEquals(29, activeCount)
        assertEquals(false, capReached, "Expected cap NOT to be reached at count=$activeCount / cap=$cap")
    }

    @Test
    fun countActiveCustomers_excludes_locked_slots() {
        // 10 ACTIVE + 5 LOCKED; count should be 10, not 15
        val dtos = List(10) { makeDto(slotState = "active") } +
            List(5) { makeDto(slotState = "locked") }

        assertEquals(10, countActiveCustomers(dtos))
    }

    @Test
    fun countActiveCustomers_treats_unknown_slotState_as_inactive() {
        // Unknown slot-state should not count toward the cap
        val dtos = listOf(makeDto(slotState = ""))
        assertEquals(0, countActiveCustomers(dtos))
    }

    // ── DataError.Network.CAP_REACHED mapping test ────────────────────────

    @Test
    fun capReached_error_is_distinct_from_unknown() {
        val error: DataError.Network = DataError.Network.CAP_REACHED
        assertIs<DataError.Network>(error)
        assertEquals(DataError.Network.CAP_REACHED, error)
    }

    // ── FakeEntitlementsProvider helper (used for VM-level tests) ─────────

    /**
     * Minimal [EntitlementsProvider] stub backed by a [StateFlow].
     * Useful when higher-level tests need to inject a specific cap.
     */
    private class FakeEntitlementsProvider(
        private val entitlements: UserEntitlements,
    ) : EntitlementsProvider {
        private val _flow = MutableStateFlow(entitlements)
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = entitlements
    }

    private fun makeEntitlements(customerCap: Int): UserEntitlements =
        EntitlementsCalculator.calculate(
            tier = SubscriptionTier.FREE,
            welcomeBonusAppliedAt = null,
            // Fixed instant rather than Clock.System.now() — `kotlinx.datetime
            // .Clock.System` is unresolved on iOS Native in 0.6.2 (see
            // [[feedback-ios-clock-injection]]).
            now = Instant.parse("2026-05-17T08:00:00Z"),
            timeZone = TimeZone.of("Africa/Lagos"),
        ).copy(customerCap = customerCap)

    @Suppress("unused")
    private fun fakeProvider(customerCap: Int): EntitlementsProvider =
        FakeEntitlementsProvider(makeEntitlements(customerCap))

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun makeDto(slotState: String = "active"): CustomerDto =
        CustomerDto(
            id = "cust-${slotState.hashCode()}",
            name = "Test Customer",
            phone = "+2348000000000",
            slotState = slotState,
        )
}
