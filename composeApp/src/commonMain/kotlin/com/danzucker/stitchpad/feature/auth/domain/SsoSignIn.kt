package com.danzucker.stitchpad.feature.auth.domain

import com.danzucker.stitchpad.core.domain.model.User

/**
 * Outcome of an SSO credential exchange. [isNewUser] distinguishes account creation
 * from a returning login (Firebase's additionalUserInfo), so analytics can log
 * sign_up vs login correctly — SSO buttons live on BOTH auth screens.
 */
data class SsoSignIn(val user: User, val isNewUser: Boolean)
