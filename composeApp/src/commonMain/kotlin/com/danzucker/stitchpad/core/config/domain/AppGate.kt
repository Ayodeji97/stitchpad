package com.danzucker.stitchpad.core.config.domain

import com.danzucker.stitchpad.core.config.domain.model.AppConfig

/**
 * The break-glass decision for whether the app may run normally, or must show a
 * blocking screen, based on the remote [AppConfig]. Pure and platform-agnostic so
 * it can be exhaustively unit-tested; the platform (`isIos`) and running build
 * number are supplied by the caller.
 */
sealed interface AppGateDecision {
    /** Normal operation — render the app. */
    data object Allowed : AppGateDecision

    /** Running build is below the remote floor: block until the user updates. */
    data class ForceUpdate(
        val message: String?,
        val updateUrl: String?,
    ) : AppGateDecision

    /** Global soft-lock during an incident. */
    data class Maintenance(val message: String?) : AppGateDecision
}

object AppGate {

    /**
     * Evaluate the gate. Fail-open by construction: [AppConfig.Disabled] (and any
     * config with the break-glass fields unset) yields [AppGateDecision.Allowed],
     * and a null [currentBuild] never triggers a forced update — an app that can't
     * identify its own build must not lock the user out.
     *
     * Force-update takes precedence over maintenance: a below-floor build is
     * presumed broken, so pushing the user to a fixed binary is the priority.
     */
    fun evaluate(
        config: AppConfig,
        isIos: Boolean,
        currentBuild: Int?,
    ): AppGateDecision {
        val floor = if (isIos) config.minSupportedBuildIos else config.minSupportedBuildAndroid
        val mustUpdate = floor != null && currentBuild != null && currentBuild < floor
        return when {
            mustUpdate -> AppGateDecision.ForceUpdate(
                message = config.forceUpdateMessage,
                updateUrl = if (isIos) config.updateUrlIos else config.updateUrlAndroid,
            )

            config.maintenanceMode -> AppGateDecision.Maintenance(config.maintenanceMessage)

            else -> AppGateDecision.Allowed
        }
    }
}
