package com.danzucker.stitchpad.core.data.sync

import app.cash.turbine.test
import com.danzucker.stitchpad.core.domain.model.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
}
