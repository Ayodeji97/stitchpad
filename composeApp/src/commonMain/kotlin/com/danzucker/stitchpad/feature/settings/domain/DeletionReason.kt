package com.danzucker.stitchpad.feature.settings.domain

enum class DeletionReason(val analyticsKey: String) {
    DUPLICATE_OR_WRONG_ACCOUNT("duplicate_or_wrong_account"),
    TOO_COMPLEX("too_complex"),
    MISSING_FEATURES("missing_features"),
    SWITCHING_APP("switching_app"),
    PRIVACY_CONCERNS("privacy_concerns"),
    JUST_TRYING("just_trying"),
    OTHER("other"),
}
