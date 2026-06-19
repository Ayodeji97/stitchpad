package com.danzucker.stitchpad.feature.style.presentation.folders

import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits
import com.danzucker.stitchpad.feature.style.presentation.cap.StyleCapInfo

/**
 * UI model for a single folder card (default or named). Computed live from observed
 * styles so counts and covers are always accurate.
 *
 * @param folderId null for the always-present default "My styles" card.
 * @param name     null for default (screen substitutes the localised default name).
 * @param count    live style count derived from observed styles for this location.
 * @param coverUrl most-recent non-blank photoUrl from the folder's styles; null = placeholder.
 * @param source   underlying [StyleFolder] used for rename/delete actions; null for default.
 */
data class FolderCardUi(
    val folderId: String?,
    val name: String?,
    val count: Int,
    val coverUrl: String?,
    val source: StyleFolder?,
)

data class StyleFoldersState(
    /** Live-derived folder cards (default first, then named in folder order). */
    val cards: List<FolderCardUi> = emptyList(),
    /** Number of named folders only (excludes the default); used for create-cap check. */
    val namedFolderCount: Int = 0,
    val isLoading: Boolean = true,
    val limits: StyleCollectionLimits = StyleCollectionLimits.forInspiration(SubscriptionTier.FREE),
    val isInspiration: Boolean = false,
    val customerName: String? = null,
    val showCreateSheet: Boolean = false,
    val renameTarget: StyleFolder? = null,
    val deleteTarget: StyleFolder? = null,
    /** The folder whose long-press action sheet is open; null = sheet hidden. */
    val actionSheetFolder: StyleFolder? = null,
    val errorMessage: UiText? = null,
    /** Non-null while the cap-reached upgrade sheet should be shown. */
    val capSheet: StyleCapInfo? = null,
)
