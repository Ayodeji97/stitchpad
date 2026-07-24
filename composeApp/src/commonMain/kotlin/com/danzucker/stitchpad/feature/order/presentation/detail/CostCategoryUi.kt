package com.danzucker.stitchpad.feature.order.presentation.detail

import androidx.compose.runtime.Composable
import com.danzucker.stitchpad.core.domain.model.CostCategory
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.*

val costCategoryOrder: List<CostCategory> = listOf(
    CostCategory.FABRIC,
    CostCategory.MATERIALS_TRIMS,
    CostCategory.EMBELLISHMENT,
    CostCategory.LABOUR,
    CostCategory.LOGISTICS,
    CostCategory.OTHER,
)

@Composable
fun CostCategory.label(): String = when (this) {
    CostCategory.FABRIC -> stringResource(Res.string.cost_category_fabric)
    CostCategory.MATERIALS_TRIMS -> stringResource(Res.string.cost_category_materials_trims)
    CostCategory.EMBELLISHMENT -> stringResource(Res.string.cost_category_embellishment)
    CostCategory.LABOUR -> stringResource(Res.string.cost_category_labour)
    CostCategory.LOGISTICS -> stringResource(Res.string.cost_category_logistics)
    CostCategory.OTHER -> stringResource(Res.string.cost_category_other)
}

@Composable
fun CostCategory.hint(): String? = when (this) {
    CostCategory.FABRIC -> null
    CostCategory.MATERIALS_TRIMS -> stringResource(Res.string.cost_hint_materials_trims)
    CostCategory.EMBELLISHMENT -> stringResource(Res.string.cost_hint_embellishment)
    CostCategory.LABOUR -> stringResource(Res.string.cost_hint_labour)
    CostCategory.LOGISTICS -> stringResource(Res.string.cost_hint_logistics)
    CostCategory.OTHER -> stringResource(Res.string.cost_hint_other)
}
