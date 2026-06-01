package com.danzucker.stitchpad.core.domain.model

/**
 * Resolves the user-visible name of an order item's garment.
 *
 * Custom values (`garmentType == OTHER && customGarmentName != null`) are
 * stored as-typed and returned verbatim. Preset garments fall through to
 * the caller's [resolveLabel] — pass a Compose `stringResource(...)` resolver
 * from a `@Composable` context, or a pre-resolved map lookup from background
 * text builders (receipt sharing).
 */
fun OrderItem.displayGarmentName(resolveLabel: (GarmentType) -> String): String =
    if (garmentType == GarmentType.OTHER && !customGarmentName.isNullOrBlank()) {
        customGarmentName
    } else {
        resolveLabel(garmentType)
    }
