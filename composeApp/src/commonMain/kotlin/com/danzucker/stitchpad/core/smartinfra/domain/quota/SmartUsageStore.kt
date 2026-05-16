package com.danzucker.stitchpad.core.smartinfra.domain.quota

import kotlinx.coroutines.flow.StateFlow

/**
 * In-process cache for the last-known remaining free-tier Smart-feature
 * quota.
 *
 * Updated by each Smart-feature ViewModel after a successful generation
 * (it reads the fresh count from the Cloud Function response) and
 * observed by the dashboard's SmartSectionCard counter chip so the chip
 * stays in sync without an extra server round-trip on every dashboard
 * mount.
 *
 * The cache is process-local — it resets when the app process dies.
 * That's acceptable for V1: the next successful generation repopulates
 * it, and on a cold start the chip simply stays hidden until the user
 * triggers their first generation of the session.
 *
 * The cache holds a single total across all Smart features, matching
 * the server-side shared monthly counter. Per-feature breakdowns live
 * in the server's perFeature map and are not surfaced in the cache.
 */
interface SmartUsageStore {
    /**
     * null = unknown (no generation yet this session, or premium tier).
     * Non-null = the count returned by the most recent successful generation.
     */
    val remainingFreeQuota: StateFlow<Int?>

    fun update(remaining: Int?)
}
