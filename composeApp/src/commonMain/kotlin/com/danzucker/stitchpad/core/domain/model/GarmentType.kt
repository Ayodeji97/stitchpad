package com.danzucker.stitchpad.core.domain.model

enum class GarmentType(val fieldLabels: List<String>) {
    AGBADA(listOf("Neck", "Chest", "Waist", "Hip", "Shoulder Width", "Arm Length", "Gown Length")),
    SENATOR_KAFTAN(listOf("Chest", "Waist", "Hip", "Length")),
    BUBA_AND_SKIRT(listOf("Bust", "Waist", "Hip", "Blouse Length", "Skirt Length")),
    DRESS(listOf("Bust", "Waist", "Hip", "Shoulder Width", "Arm Length", "Dress Length")),
    TROUSER(listOf("Waist", "Hip", "Thigh", "Knee", "Inseam", "Length")),
    SHIRT(listOf("Neck", "Chest", "Waist", "Shoulder Width", "Arm Length", "Shirt Length")),
    BLOUSE(listOf("Bust", "Waist", "Hip", "Shoulder Width", "Arm Length", "Length")),
    SUIT(listOf("Neck", "Chest", "Waist", "Hip", "Shoulder Width", "Arm Length", "Jacket Length", "Trouser Length"))
}
