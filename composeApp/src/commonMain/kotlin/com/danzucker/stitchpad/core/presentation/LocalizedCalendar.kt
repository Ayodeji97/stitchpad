package com.danzucker.stitchpad.core.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.calendar_months_long
import stitchpad.composeapp.generated.resources.calendar_months_short

/** Resource-backed display names; month numbers and saved dates remain unchanged. */
@Composable
fun localizedMonthName(month: Int, short: Boolean = false): String {
    val names = stringResource(if (short) Res.string.calendar_months_short else Res.string.calendar_months_long)
    return names.split("|").getOrElse(month - 1) { "" }
}
