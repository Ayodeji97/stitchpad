package com.danzucker.stitchpad.feature.style.presentation.form

import com.preat.peekaboo.image.picker.SelectionMode

/**
 * Resolves the image-picker selection mode for the style form.
 *
 * Multi-photo selection is only valid when at least two photos may actually be picked:
 * Android's `PickMultipleVisualMedia` contract requires `maxItems > 1` and throws
 * `IllegalArgumentException("Max items must be higher than 1")` otherwise. When the
 * folder has a single remaining slot ([maxPhotoSelection] == 1, e.g. a free-tier
 * customer closet near its image cap) we fall back to [SelectionMode.Single] — the user
 * can still pick that one photo, but we never construct the crashing `Multiple(1)`.
 */
internal fun styleFormSelectionMode(
    allowMultiPhoto: Boolean,
    maxPhotoSelection: Int,
): SelectionMode =
    if (allowMultiPhoto && maxPhotoSelection > 1) {
        SelectionMode.Multiple(maxSelection = maxPhotoSelection)
    } else {
        SelectionMode.Single
    }
