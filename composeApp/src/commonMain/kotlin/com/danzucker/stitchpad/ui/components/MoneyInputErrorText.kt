package com.danzucker.stitchpad.ui.components

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.Currency
import com.danzucker.stitchpad.core.presentation.UiText
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.money_input_amount_invalid
import stitchpad.composeapp.generated.resources.money_input_whole_amount_required

/** Shared localized recovery copy for currency-precision validation. */
@Composable
fun moneyInputErrorText(currency: Currency): String = if (currency.decimals == 0) {
    UiText.StringResourceText(Res.string.money_input_whole_amount_required).asString()
} else {
    UiText.StringResourceText(Res.string.money_input_amount_invalid, arrayOf<Any>(currency.decimals)).asString()
}
