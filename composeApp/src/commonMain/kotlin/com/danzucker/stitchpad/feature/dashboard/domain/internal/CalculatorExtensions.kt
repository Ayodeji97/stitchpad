package com.danzucker.stitchpad.feature.dashboard.domain.internal

import com.danzucker.stitchpad.core.domain.model.GarmentType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * Helpers shared by the dashboard's pure-function calculators. Kept under
 * `domain/internal/` so they are visible to every calculator in this feature
 * but not part of any public API.
 */

@OptIn(ExperimentalTime::class)
internal fun Long.toLocalDate(tz: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(tz).date

internal fun GarmentType.simpleLabel(): String =
    name.lowercase().split('_').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }
