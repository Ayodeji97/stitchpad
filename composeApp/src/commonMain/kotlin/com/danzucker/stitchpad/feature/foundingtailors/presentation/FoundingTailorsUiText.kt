package com.danzucker.stitchpad.feature.foundingtailors.presentation

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.presentation.UiText
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.error_no_internet
import stitchpad.composeapp.generated.resources.error_unknown

/** Maps a failed [com.danzucker.stitchpad.feature.referral.domain.ReferralRepository.getOrCreateMyReferralLink]
 *  call to a user-facing message. Mirrors the per-feature `toXUiText()` convention
 *  (see DashboardUiText/ReportsUiText) rather than a shared `DataError.Network.toUiText()`,
 *  since the copy is feature-specific.
 */
fun DataError.Network.toFoundingTailorsUiText(): UiText = when (this) {
    DataError.Network.NO_INTERNET -> UiText.StringResourceText(Res.string.error_no_internet)
    else -> UiText.StringResourceText(Res.string.error_unknown)
}
