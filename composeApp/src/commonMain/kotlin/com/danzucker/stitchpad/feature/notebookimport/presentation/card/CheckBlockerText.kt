package com.danzucker.stitchpad.feature.notebookimport.presentation.card

import com.danzucker.stitchpad.feature.notebookimport.domain.CheckBlocker
import org.jetbrains.compose.resources.StringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.notebook_card_duplicate_needed
import stitchpad.composeapp.generated.resources.notebook_card_gender_needed
import stitchpad.composeapp.generated.resources.notebook_card_match_needed
import stitchpad.composeapp.generated.resources.notebook_card_name_needed
import stitchpad.composeapp.generated.resources.notebook_card_values_needed

fun CheckBlocker.hintResource(): StringResource = when (this) {
    CheckBlocker.GENDER -> Res.string.notebook_card_gender_needed
    CheckBlocker.NAME -> Res.string.notebook_card_name_needed
    CheckBlocker.VALUES -> Res.string.notebook_card_values_needed
    CheckBlocker.DUPLICATE -> Res.string.notebook_card_duplicate_needed
    CheckBlocker.MATCH -> Res.string.notebook_card_match_needed
}
