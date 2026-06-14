package com.danzucker.stitchpad.feature.style.presentation.folders

import com.danzucker.stitchpad.core.domain.model.StyleFolder
import com.danzucker.stitchpad.core.domain.model.SubscriptionTier
import com.danzucker.stitchpad.core.presentation.UiText
import com.danzucker.stitchpad.feature.style.domain.StyleCollectionLimits

data class StyleFoldersState(
    val folders: List<StyleFolder> = emptyList(),
    val isLoading: Boolean = true,
    val limits: StyleCollectionLimits = StyleCollectionLimits.forInspiration(SubscriptionTier.FREE),
    val isInspiration: Boolean = false,
    val customerName: String? = null,
    val showCreateSheet: Boolean = false,
    val renameTarget: StyleFolder? = null,
    val deleteTarget: StyleFolder? = null,
    val errorMessage: UiText? = null,
)
