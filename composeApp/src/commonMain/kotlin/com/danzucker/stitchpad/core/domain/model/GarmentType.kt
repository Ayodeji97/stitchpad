package com.danzucker.stitchpad.core.domain.model

enum class GarmentGender {
    MALE,
    FEMALE,
    UNISEX
}

enum class GarmentType(val fieldLabels: List<String>, val gender: GarmentGender) {
    // Male
    AGBADA(
        listOf("Neck", "Chest", "Waist", "Hip", "Shoulder Width", "Arm Length", "Gown Length"),
        GarmentGender.MALE
    ),
    SENATOR(
        listOf("Chest", "Waist", "Hip", "Length"),
        GarmentGender.MALE
    ),
    KAFTAN(
        listOf("Chest", "Waist", "Hip", "Length"),
        GarmentGender.MALE
    ),
    DANSHIKI(
        listOf("Chest", "Waist", "Shoulder Width", "Arm Length", "Length"),
        GarmentGender.MALE
    ),
    SUIT(
        listOf("Neck", "Chest", "Waist", "Hip", "Shoulder Width", "Arm Length", "Jacket Length", "Trouser Length"),
        GarmentGender.MALE
    ),
    VINTAGE(
        listOf("Chest", "Waist", "Hip", "Shoulder Width", "Arm Length", "Length"),
        GarmentGender.MALE
    ),

    // Female
    DRESS(
        listOf("Bust", "Waist", "Hip", "Shoulder Width", "Arm Length", "Dress Length"),
        GarmentGender.FEMALE
    ),
    BLOUSE(
        listOf("Bust", "Waist", "Hip", "Shoulder Width", "Arm Length", "Length"),
        GarmentGender.FEMALE
    ),
    CORSET(
        listOf("Bust", "Underbust", "Waist", "Hip", "Length"),
        GarmentGender.FEMALE
    ),
    ASOOKE(
        listOf("Bust", "Waist", "Hip", "Shoulder Width", "Length"),
        GarmentGender.FEMALE
    ),
    BRIDAL_GOWN(
        listOf("Bust", "Waist", "Hip", "Shoulder Width", "Arm Length", "Bodice Length", "Skirt Length"),
        GarmentGender.FEMALE
    ),
    ASOEBI(
        listOf("Bust", "Waist", "Hip", "Shoulder Width", "Arm Length", "Length"),
        GarmentGender.FEMALE
    ),
    TWO_PIECE(
        listOf("Bust", "Waist", "Hip", "Shoulder Width", "Arm Length", "Top Length", "Bottom Length"),
        GarmentGender.FEMALE
    ),
    CORPORATE_WEAR(
        listOf("Bust", "Waist", "Hip", "Shoulder Width", "Arm Length", "Length"),
        GarmentGender.FEMALE
    ),

    // Unisex
    TROUSER(
        listOf("Waist", "Hip", "Thigh", "Knee", "Inseam", "Length"),
        GarmentGender.UNISEX
    ),
    SHIRT(
        listOf("Neck", "Chest", "Waist", "Shoulder Width", "Arm Length", "Shirt Length"),
        GarmentGender.UNISEX
    ),
    CORPORATE_TROUSER(
        listOf("Waist", "Hip", "Thigh", "Knee", "Inseam", "Length"),
        GarmentGender.UNISEX
    )
}
