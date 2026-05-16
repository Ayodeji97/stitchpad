package com.danzucker.stitchpad.feature.smart.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * In-process cache for the last-known remaining free-tier draft quota.
 *
 * Updated by [com.danzucker.stitchpad.feature.smart.presentation.draft.DraftMessageViewModel]
 * after each successful draft (it reads the fresh count from the
 * Cloud Function response) and observed by the dashboard so the
 * SmartSectionCard counter chip stays in sync without an extra server
 * round-trip on every dashboard mount.
 *
 * The cache is process-local — it resets when the app process dies.
 * That's acceptable for V1: the next successful draft repopulates it,
 * and on a cold start the chip simply stays hidden until the user
 * generates their first draft of the session. A V1.5 follow-up could
 * persist the value (per-user) or pre-fetch it on app launch.
 */
interface SmartUsageStore {
    /**
     * null = unknown (no draft yet this session, or premium tier).
     * Non-null = the count returned by the most recent successful draft.
     */
    val remainingFreeQuota: StateFlow<Int?>

    fun update(remaining: Int?)
}
