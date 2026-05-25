package com.danzucker.stitchpad.feature.freemium.presentation.reconcile

import com.danzucker.stitchpad.core.domain.entitlement.EntitlementsProvider
import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.feature.freemium.domain.FreemiumRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ReconcileCoordinatorTest {

    @Test
    fun fires_once_on_initial_signed_in_emission() = runTest {
        val (uidFlow, entitlementsProvider, repo) = setup()
        TestScope(UnconfinedTestDispatcher(testScheduler)).also { scope ->
            ReconcileCoordinator(
                uidFlow = uidFlow,
                entitlementsProvider = entitlementsProvider,
                freemiumRepository = repo,
                scope = scope,
            )
            uidFlow.value = "user-A"
            testScheduler.advanceUntilIdle()
        }

        assertEquals(1, repo.calls)
    }

    @Test
    fun does_not_fire_when_signed_out() = runTest {
        val (uidFlow, entitlementsProvider, repo) = setup()
        TestScope(UnconfinedTestDispatcher(testScheduler)).also { scope ->
            ReconcileCoordinator(
                uidFlow = uidFlow,
                entitlementsProvider = entitlementsProvider,
                freemiumRepository = repo,
                scope = scope,
            )
            uidFlow.value = null
            testScheduler.advanceUntilIdle()
        }

        assertEquals(0, repo.calls)
    }

    @Test
    fun dedupes_identical_key_emissions() = runTest {
        val (uidFlow, entitlementsProvider, repo) = setup()
        TestScope(UnconfinedTestDispatcher(testScheduler)).also { scope ->
            ReconcileCoordinator(
                uidFlow = uidFlow,
                entitlementsProvider = entitlementsProvider,
                freemiumRepository = repo,
                scope = scope,
            )
            // Same uid, same tier, same welcome window flag — should fire once,
            // even with re-emissions on the same value.
            uidFlow.value = "user-A"
            testScheduler.advanceUntilIdle()
            entitlementsProvider.emit(entitlementsProvider.flow.value)
            testScheduler.advanceUntilIdle()
            entitlementsProvider.emit(entitlementsProvider.flow.value)
            testScheduler.advanceUntilIdle()
        }

        assertEquals(1, repo.calls)
    }

    @Test
    fun fires_again_when_tier_changes() = runTest {
        val (uidFlow, entitlementsProvider, repo) = setup()
        TestScope(UnconfinedTestDispatcher(testScheduler)).also { scope ->
            ReconcileCoordinator(
                uidFlow = uidFlow,
                entitlementsProvider = entitlementsProvider,
                freemiumRepository = repo,
                scope = scope,
            )
            uidFlow.value = "user-A"
            testScheduler.advanceUntilIdle()
            // User upgrades to Pro
            entitlementsProvider.emit(entitlementsProvider.flow.value.copy(tier = SubscriptionTier.PRO))
            testScheduler.advanceUntilIdle()
        }

        assertEquals(2, repo.calls)
    }

    @Test
    fun fires_again_when_welcome_window_flips() = runTest {
        val (uidFlow, entitlementsProvider, repo) = setup()
        TestScope(UnconfinedTestDispatcher(testScheduler)).also { scope ->
            ReconcileCoordinator(
                uidFlow = uidFlow,
                entitlementsProvider = entitlementsProvider,
                freemiumRepository = repo,
                scope = scope,
            )
            uidFlow.value = "user-A"
            testScheduler.advanceUntilIdle()
            // Welcome window ends
            entitlementsProvider.emit(
                entitlementsProvider.flow.value.copy(isInWelcomeWindow = false),
            )
            testScheduler.advanceUntilIdle()
        }

        assertEquals(2, repo.calls)
    }

    @Test
    fun resets_dedup_on_sign_out_so_next_sign_in_fires() = runTest {
        val (uidFlow, entitlementsProvider, repo) = setup()
        TestScope(UnconfinedTestDispatcher(testScheduler)).also { scope ->
            ReconcileCoordinator(
                uidFlow = uidFlow,
                entitlementsProvider = entitlementsProvider,
                freemiumRepository = repo,
                scope = scope,
            )
            uidFlow.value = "user-A"
            testScheduler.advanceUntilIdle()
            uidFlow.value = null
            testScheduler.advanceUntilIdle()
            // Same uid signs in again — should fire even though the key tuple
            // matches the last-fired one, because the sign-out reset cleared the
            // dedup cache.
            uidFlow.value = "user-A"
            testScheduler.advanceUntilIdle()
        }

        assertEquals(2, repo.calls)
    }

    @Test
    fun logs_failure_but_does_not_block_future_calls() = runTest {
        val (uidFlow, entitlementsProvider, repo) = setup()
        repo.result = Result.Error(DataError.Network.NO_INTERNET)
        TestScope(UnconfinedTestDispatcher(testScheduler)).also { scope ->
            ReconcileCoordinator(
                uidFlow = uidFlow,
                entitlementsProvider = entitlementsProvider,
                freemiumRepository = repo,
                scope = scope,
            )
            uidFlow.value = "user-A"
            testScheduler.advanceUntilIdle()
            assertEquals(1, repo.calls)
            // A later tier change still fires reconcile — the previous failure
            // does NOT poison subsequent triggers.
            repo.result = Result.Success(Unit)
            entitlementsProvider.emit(entitlementsProvider.flow.value.copy(tier = SubscriptionTier.PRO))
            testScheduler.advanceUntilIdle()
        }

        assertEquals(2, repo.calls)
    }

    private fun setup(): Triple<MutableStateFlow<String?>, FakeEntitlementsProvider, FakeFreemiumRepository> {
        val uidFlow = MutableStateFlow<String?>(null)
        val provider = FakeEntitlementsProvider(
            UserEntitlements(
                tier = SubscriptionTier.FREE,
                customerCap = 200,
                smartCoinAllowance = 5,
                isInWelcomeWindow = true,
                welcomeEndsAt = Instant.fromEpochMilliseconds(0),
                isWithinWelcomeEndingWarning = false,
                welcomeDaysLeft = 23,
            ),
        )
        val repo = FakeFreemiumRepository()
        return Triple(uidFlow, provider, repo)
    }

    private class FakeEntitlementsProvider(initial: UserEntitlements) : EntitlementsProvider {
        private val _flow = MutableStateFlow(initial)
        override val flow: StateFlow<UserEntitlements> = _flow
        override fun current(): UserEntitlements = _flow.value
        override suspend fun awaitHydrated(): UserEntitlements = _flow.value
        fun emit(value: UserEntitlements) {
            _flow.value = value
        }
    }

    private class FakeFreemiumRepository : FreemiumRepository {
        var calls = 0
        var result: EmptyResult<DataError.Network> = Result.Success(Unit)
        override suspend fun reconcileSlots(): EmptyResult<DataError.Network> {
            calls++
            return result
        }
        override suspend fun swapCustomerSlot(
            promote: String,
            demote: String,
        ): EmptyResult<DataError.Network> = Result.Success(Unit)
    }
}
