package com.danzucker.stitchpad.feature.style.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.model.Style
import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.StyleLocation
import com.danzucker.stitchpad.core.domain.repository.StyleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.runningFold

/**
 * Observes EVERY style in a customer's closet — the flat default folder plus all
 * named folders — flattened into a single list. Style ids are globally unique, so
 * a flattened list is safe for styleId-based lookups (order picker, order detail
 * hero resolution).
 *
 * Resilient to transient observe errors: each underlying flow retains its last
 * successful emission via [runningFold], so a network hiccup keeps the last known
 * styles/folders visible instead of blanking the list or dropping a folder's members.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun StyleRepository.observeAllCustomerStyles(
    userId: String,
    customerId: String,
): Flow<List<Style>> =
    observeFolders(userId, StyleLocation.CustomerCloset(customerId))
        .keepingLastFolders()
        .flatMapLatest { folders ->
            val styleFlows = buildList {
                add(
                    observeStyles(
                        userId,
                        StyleLocation.CustomerCloset(customerId, folderId = null),
                    ).keepingLastStyles()
                )
                folders.forEach { folder ->
                    add(
                        observeStyles(
                            userId,
                            StyleLocation.CustomerCloset(customerId, folderId = folder.id),
                        ).keepingLastStyles()
                    )
                }
            }
            combine(styleFlows) { results -> results.flatMap { it } }
        }

/**
 * Observes EVERY style in the shared Inspiration library — the flat default folder
 * plus all named Inspiration folders — flattened into a single list. Uses the same
 * resilient keep-last strategy as [observeAllCustomerStyles].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun StyleRepository.observeAllInspirationStyles(userId: String): Flow<List<Style>> =
    observeFolders(userId, StyleLocation.Inspiration(folderId = null))
        .keepingLastFolders()
        .flatMapLatest { folders ->
            val styleFlows = buildList {
                add(
                    observeStyles(
                        userId,
                        StyleLocation.Inspiration(folderId = null),
                    ).keepingLastStyles()
                )
                folders.forEach { folder ->
                    add(
                        observeStyles(
                            userId,
                            StyleLocation.Inspiration(folderId = folder.id),
                        ).keepingLastStyles()
                    )
                }
            }
            combine(styleFlows) { results -> results.flatMap { it } }
        }

/** Retains the last successfully emitted style list, ignoring transient errors. */
private fun Flow<Result<List<Style>, DataError.Network>>.keepingLastStyles(): Flow<List<Style>> =
    runningFold(emptyList()) { last, r -> if (r is Result.Success) r.data else last }

/** Retains the last successfully emitted folder list, ignoring transient errors. */
private fun Flow<Result<List<StyleFolder>, DataError.Network>>.keepingLastFolders(): Flow<List<StyleFolder>> =
    runningFold(emptyList()) { last, r -> if (r is Result.Success) r.data else last }
