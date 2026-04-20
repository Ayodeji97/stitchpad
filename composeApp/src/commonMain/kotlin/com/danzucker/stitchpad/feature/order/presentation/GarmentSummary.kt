package com.danzucker.stitchpad.feature.order.presentation

import com.danzucker.stitchpad.core.domain.model.GarmentType
import org.jetbrains.compose.resources.StringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.order_summary_one_agbada
import stitchpad.composeapp.generated.resources.order_summary_one_asoebi
import stitchpad.composeapp.generated.resources.order_summary_one_asooke
import stitchpad.composeapp.generated.resources.order_summary_one_blouse
import stitchpad.composeapp.generated.resources.order_summary_one_bridal_gown
import stitchpad.composeapp.generated.resources.order_summary_one_corporate_trouser
import stitchpad.composeapp.generated.resources.order_summary_one_corporate_wear
import stitchpad.composeapp.generated.resources.order_summary_one_corset
import stitchpad.composeapp.generated.resources.order_summary_one_danshiki
import stitchpad.composeapp.generated.resources.order_summary_one_dress
import stitchpad.composeapp.generated.resources.order_summary_one_kaftan
import stitchpad.composeapp.generated.resources.order_summary_one_senator
import stitchpad.composeapp.generated.resources.order_summary_one_shirt
import stitchpad.composeapp.generated.resources.order_summary_one_suit
import stitchpad.composeapp.generated.resources.order_summary_one_trouser
import stitchpad.composeapp.generated.resources.order_summary_one_two_piece
import stitchpad.composeapp.generated.resources.order_summary_one_vintage
import stitchpad.composeapp.generated.resources.order_summary_other_agbada
import stitchpad.composeapp.generated.resources.order_summary_other_asoebi
import stitchpad.composeapp.generated.resources.order_summary_other_asooke
import stitchpad.composeapp.generated.resources.order_summary_other_blouse
import stitchpad.composeapp.generated.resources.order_summary_other_bridal_gown
import stitchpad.composeapp.generated.resources.order_summary_other_corporate_trouser
import stitchpad.composeapp.generated.resources.order_summary_other_corporate_wear
import stitchpad.composeapp.generated.resources.order_summary_other_corset
import stitchpad.composeapp.generated.resources.order_summary_other_danshiki
import stitchpad.composeapp.generated.resources.order_summary_other_dress
import stitchpad.composeapp.generated.resources.order_summary_other_kaftan
import stitchpad.composeapp.generated.resources.order_summary_other_senator
import stitchpad.composeapp.generated.resources.order_summary_other_shirt
import stitchpad.composeapp.generated.resources.order_summary_other_suit
import stitchpad.composeapp.generated.resources.order_summary_other_trouser
import stitchpad.composeapp.generated.resources.order_summary_other_two_piece
import stitchpad.composeapp.generated.resources.order_summary_other_vintage

private data class GarmentSummaryResPair(
    val one: StringResource,
    val other: StringResource
)

@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun garmentSummaryPair(type: GarmentType): GarmentSummaryResPair = when (type) {
    GarmentType.AGBADA -> GarmentSummaryResPair(
        Res.string.order_summary_one_agbada,
        Res.string.order_summary_other_agbada
    )
    GarmentType.SENATOR -> GarmentSummaryResPair(
        Res.string.order_summary_one_senator,
        Res.string.order_summary_other_senator
    )
    GarmentType.KAFTAN -> GarmentSummaryResPair(
        Res.string.order_summary_one_kaftan,
        Res.string.order_summary_other_kaftan
    )
    GarmentType.DANSHIKI -> GarmentSummaryResPair(
        Res.string.order_summary_one_danshiki,
        Res.string.order_summary_other_danshiki
    )
    GarmentType.SUIT -> GarmentSummaryResPair(
        Res.string.order_summary_one_suit,
        Res.string.order_summary_other_suit
    )
    GarmentType.VINTAGE -> GarmentSummaryResPair(
        Res.string.order_summary_one_vintage,
        Res.string.order_summary_other_vintage
    )
    GarmentType.DRESS -> GarmentSummaryResPair(
        Res.string.order_summary_one_dress,
        Res.string.order_summary_other_dress
    )
    GarmentType.BLOUSE -> GarmentSummaryResPair(
        Res.string.order_summary_one_blouse,
        Res.string.order_summary_other_blouse
    )
    GarmentType.CORSET -> GarmentSummaryResPair(
        Res.string.order_summary_one_corset,
        Res.string.order_summary_other_corset
    )
    GarmentType.ASOOKE -> GarmentSummaryResPair(
        Res.string.order_summary_one_asooke,
        Res.string.order_summary_other_asooke
    )
    GarmentType.BRIDAL_GOWN -> GarmentSummaryResPair(
        Res.string.order_summary_one_bridal_gown,
        Res.string.order_summary_other_bridal_gown
    )
    GarmentType.ASOEBI -> GarmentSummaryResPair(
        Res.string.order_summary_one_asoebi,
        Res.string.order_summary_other_asoebi
    )
    GarmentType.TWO_PIECE -> GarmentSummaryResPair(
        Res.string.order_summary_one_two_piece,
        Res.string.order_summary_other_two_piece
    )
    GarmentType.CORPORATE_WEAR -> GarmentSummaryResPair(
        Res.string.order_summary_one_corporate_wear,
        Res.string.order_summary_other_corporate_wear
    )
    GarmentType.TROUSER -> GarmentSummaryResPair(
        Res.string.order_summary_one_trouser,
        Res.string.order_summary_other_trouser
    )
    GarmentType.SHIRT -> GarmentSummaryResPair(
        Res.string.order_summary_one_shirt,
        Res.string.order_summary_other_shirt
    )
    GarmentType.CORPORATE_TROUSER -> GarmentSummaryResPair(
        Res.string.order_summary_one_corporate_trouser,
        Res.string.order_summary_other_corporate_trouser
    )
}

fun garmentSummaryRes(type: GarmentType, count: Int): StringResource {
    val pair = garmentSummaryPair(type)
    return if (count == 1) pair.one else pair.other
}
