package com.danzucker.stitchpad.core.data

import com.danzucker.stitchpad.core.domain.session.ActiveWorkshopProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Never opens an owner-private listener before session resolution or for staff. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> ActiveWorkshopProvider.ownerOnlyListener(
    userId: String,
    fallback: T,
    observe: () -> Flow<T>,
): Flow<T> = flow {
    awaitHydrated()
    emitAll(
        flow.map { it.isOwner && it.authUid == userId && userId.isNotBlank() }
            .distinctUntilChanged()
            .flatMapLatest { allowed -> if (allowed) observe() else flowOf(fallback) },
    )
}
