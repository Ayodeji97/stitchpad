package com.danzucker.stitchpad.feature.order.presentation

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.GarmentType
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

@Composable
fun garmentDisplayName(type: GarmentType): String = when (type) {
    GarmentType.AGBADA -> stringResource(Res.string.garment_type_agbada)
    GarmentType.SENATOR -> stringResource(Res.string.garment_type_senator)
    GarmentType.KAFTAN -> stringResource(Res.string.garment_type_kaftan)
    GarmentType.DANSHIKI -> stringResource(Res.string.garment_type_danshiki)
    GarmentType.SUIT -> stringResource(Res.string.garment_type_suit)
    GarmentType.VINTAGE -> stringResource(Res.string.garment_type_vintage)
    GarmentType.DRESS -> stringResource(Res.string.garment_type_dress)
    GarmentType.BLOUSE -> stringResource(Res.string.garment_type_blouse)
    GarmentType.CORSET -> stringResource(Res.string.garment_type_corset)
    GarmentType.ASOOKE -> stringResource(Res.string.garment_type_asooke)
    GarmentType.BRIDAL_GOWN -> stringResource(Res.string.garment_type_bridal_gown)
    GarmentType.ASOEBI -> stringResource(Res.string.garment_type_asoebi)
    GarmentType.TWO_PIECE -> stringResource(Res.string.garment_type_two_piece)
    GarmentType.CORPORATE_WEAR -> stringResource(Res.string.garment_type_corporate_wear)
    GarmentType.TROUSER -> stringResource(Res.string.garment_type_trouser)
    GarmentType.SHIRT -> stringResource(Res.string.garment_type_shirt)
    GarmentType.CORPORATE_TROUSER -> stringResource(Res.string.garment_type_corporate_trouser)
}
