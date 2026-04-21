package com.danzucker.stitchpad.feature.order.presentation

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.GarmentType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.garment_type_agbada
import stitchpad.composeapp.generated.resources.garment_type_asoebi
import stitchpad.composeapp.generated.resources.garment_type_asooke
import stitchpad.composeapp.generated.resources.garment_type_blouse
import stitchpad.composeapp.generated.resources.garment_type_bridal_gown
import stitchpad.composeapp.generated.resources.garment_type_corporate_trouser
import stitchpad.composeapp.generated.resources.garment_type_corporate_wear
import stitchpad.composeapp.generated.resources.garment_type_corset
import stitchpad.composeapp.generated.resources.garment_type_danshiki
import stitchpad.composeapp.generated.resources.garment_type_dress
import stitchpad.composeapp.generated.resources.garment_type_kaftan
import stitchpad.composeapp.generated.resources.garment_type_senator
import stitchpad.composeapp.generated.resources.garment_type_shirt
import stitchpad.composeapp.generated.resources.garment_type_suit
import stitchpad.composeapp.generated.resources.garment_type_trouser
import stitchpad.composeapp.generated.resources.garment_type_two_piece
import stitchpad.composeapp.generated.resources.garment_type_vintage

@Suppress("CyclomaticComplexMethod")
private fun garmentNameResource(type: GarmentType): StringResource = when (type) {
    GarmentType.AGBADA -> Res.string.garment_type_agbada
    GarmentType.SENATOR -> Res.string.garment_type_senator
    GarmentType.KAFTAN -> Res.string.garment_type_kaftan
    GarmentType.DANSHIKI -> Res.string.garment_type_danshiki
    GarmentType.SUIT -> Res.string.garment_type_suit
    GarmentType.VINTAGE -> Res.string.garment_type_vintage
    GarmentType.DRESS -> Res.string.garment_type_dress
    GarmentType.BLOUSE -> Res.string.garment_type_blouse
    GarmentType.CORSET -> Res.string.garment_type_corset
    GarmentType.ASOOKE -> Res.string.garment_type_asooke
    GarmentType.BRIDAL_GOWN -> Res.string.garment_type_bridal_gown
    GarmentType.ASOEBI -> Res.string.garment_type_asoebi
    GarmentType.TWO_PIECE -> Res.string.garment_type_two_piece
    GarmentType.CORPORATE_WEAR -> Res.string.garment_type_corporate_wear
    GarmentType.TROUSER -> Res.string.garment_type_trouser
    GarmentType.SHIRT -> Res.string.garment_type_shirt
    GarmentType.CORPORATE_TROUSER -> Res.string.garment_type_corporate_trouser
}

@Composable
fun garmentDisplayName(type: GarmentType): String = stringResource(garmentNameResource(type))

/**
 * Non-Composable resolver for contexts without a Compose scope (e.g. receipt rendering,
 * notifications). Keeps receipts in lock-step with UI labels and localization.
 */
suspend fun garmentDisplayNameAsync(type: GarmentType): String = getString(garmentNameResource(type))
