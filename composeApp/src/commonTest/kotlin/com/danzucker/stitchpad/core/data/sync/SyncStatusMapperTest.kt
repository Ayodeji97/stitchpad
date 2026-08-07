package com.danzucker.stitchpad.core.data.sync

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusMapperTest {

    @Test
    fun from_cache_is_offline_regardless_of_pending_writes() {
        assertEquals(SyncStatus.OFFLINE, syncStatusOf(isFromCache = true, hasPendingWrites = false))
        assertEquals(SyncStatus.OFFLINE, syncStatusOf(isFromCache = true, hasPendingWrites = true))
    }

    @Test
    fun server_snapshot_with_pending_writes_is_syncing() {
        assertEquals(SyncStatus.SYNCING, syncStatusOf(isFromCache = false, hasPendingWrites = true))
    }

    @Test
    fun server_snapshot_with_no_pending_writes_is_synced() {
        assertEquals(SyncStatus.SYNCED, syncStatusOf(isFromCache = false, hasPendingWrites = false))
    }

    @Test
    fun a_brief_offline_blip_is_swallowed_by_the_debounce() = runTest {
        // Models a cold start: the first snapshot is always cache-served, then the
        // server responds. The banner must never flash in this case.
        flow {
            emit(SyncStatus.OFFLINE)
            delay(200)
            emit(SyncStatus.SYNCED)
        }.debounceOffline(delayMs = 2_000).test {
            assertEquals(SyncStatus.SYNCED, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun a_sustained_offline_state_is_emitted_after_the_delay() = runTest {
        flowOf(SyncStatus.OFFLINE).debounceOffline(delayMs = 2_000).test {
            assertEquals(SyncStatus.OFFLINE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun non_offline_statuses_are_not_delayed() = runTest {
        flowOf(SyncStatus.SYNCING).debounceOffline(delayMs = 2_000).test {
            assertEquals(SyncStatus.SYNCING, awaitItem())
            awaitComplete()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun repeated_offline_emissions_within_the_window_keep_restarting_the_debounce() = runTest {
        // Pins the hazard that motivates placing distinctUntilChanged BEFORE
        // debounceOffline in SyncStatusObserver: debounceOffline is built on
        // transformLatest, which restarts its delay window on EVERY element it
        // receives -- including duplicates -- not just the first of a run. With
        // includeMetadataChanges = true, Firestore can emit repeated OFFLINE
        // snapshots while disconnected; without an upstream distinctUntilChanged
        // to collapse those, the window here would be perpetually restarted and
        // the banner would never appear -- the exact bug this feature exists to
        // fix. Three re-emissions land 800ms apart, inside the 2s window, so no
        // OFFLINE should surface until 2s after the LAST one, not 2s after the
        // first.
        val emissionIntervalMs = 800L
        val windowMs = 2_000L
        val source = flow {
            repeat(3) {
                emit(SyncStatus.OFFLINE)
                delay(emissionIntervalMs)
            }
        }

        source.debounceOffline(delayMs = windowMs).test {
            // By this point all three emissions have landed (t=0, 800, 1600) and
            // we are at t=2100 -- past a full window from the FIRST emission
            // (t=2000) but short of a full window from the LAST one (t=3600).
            // If the window were not restarting per-emission, the first
            // emission's timer would already have fired here.
            advanceTimeBy(emissionIntervalMs * 2 + 500)
            expectNoEvents()

            // Advance through the remainder of the window measured from the
            // last emission (upstream has since completed at t=2400, so nothing
            // new arrives to cancel this one).
            advanceTimeBy(windowMs)
            assertEquals(SyncStatus.OFFLINE, awaitItem())
            awaitComplete()
        }
    }
}
