@file:Suppress("MatchingDeclarationName") // file holds StylePickerFolder + all flattening helpers

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

/**
 * A folder together with all its styles, for use in the order style picker.
 *
 * [folderId] null = the default "My styles" folder (shown first).
 * [name] null for the default folder (UI substitutes a localised default name).
 * [coverUrl] is derived from the most-recently-created style that has an image.
 */
data class StylePickerFolder(
    val folderId: String?,
    val name: String?,
    val styles: List<Style>,
) {
    val coverUrl: String?
        get() = styles
            .sortedByDescending { it.createdAt }
            .firstNotNullOfOrNull { s ->
                s.localPhotoPath ?: s.photoUrl.takeIf { it.isNotBlank() }
            }

    /**
     * Stable identity for "which folder is open" — the default folder has a null
     * [folderId], so it gets a sentinel. Lets state store the id (not a stale snapshot)
     * and resolve the LIVE folder from the current list each render.
     */
    val key: String get() = folderId ?: DEFAULT_FOLDER_KEY

    companion object {
        const val DEFAULT_FOLDER_KEY = "__default__"
    }
}

/**
 * Observes all folders (default + named) for [root], each paired with its styles,
 * as a list of [StylePickerFolder]. The default folder (folderId null) is always first.
 *
 * [root] must be a root [StyleLocation.CustomerCloset] (folderId null) or a root
 * [StyleLocation.Inspiration] (folderId null).
 *
 * Resilient to transient errors via [keepingLastStyles] / [keepingLastFolders].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun StyleRepository.observeFoldersWithStyles(
    userId: String,
    root: StyleLocation,
): Flow<List<StylePickerFolder>> =
    observeFolders(userId, root)
        .keepingLastFolders()
        .flatMapLatest { namedFolders ->
            val defaultFlow = observeStyles(userId, root.withFolder(null))
                .keepingLastStyles()
                .map { styles -> StylePickerFolder(folderId = null, name = null, styles = styles) }

            val namedFlows = namedFolders.map { folder ->
                observeStyles(userId, root.withFolder(folder.id))
                    .keepingLastStyles()
                    .map { styles ->
                        StylePickerFolder(folderId = folder.id, name = folder.name, styles = styles)
                    }
            }

            val allFlows = buildList {
                add(defaultFlow)
                addAll(namedFlows)
            }
            combine(allFlows) { results -> results.toList() }
        }

/** Narrows a root [StyleLocation] to one that targets the given [folderId]. */
private fun StyleLocation.withFolder(folderId: String?): StyleLocation = when (this) {
    is StyleLocation.CustomerCloset -> copy(folderId = folderId)
    is StyleLocation.Inspiration -> copy(folderId = folderId)
}

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
