package com.danzucker.stitchpad.core.domain.model

object BodyProfileTemplate {

    fun sectionsFor(gender: CustomerGender): List<MeasurementSection> = when (gender) {
        CustomerGender.FEMALE -> femaleSections
        CustomerGender.MALE -> maleSections
    }

    private val femaleSections = listOf(
        MeasurementSection(
            titleKey = "section_upper_body",
            fields = listOf(
                MeasurementField("shoulder_width", "Shoulder", isEssential = true),
                MeasurementField("bust_circumference", "Bust", isEssential = true),
                MeasurementField("bust_point", "Shoulder to nipple point", isEssential = true),
                MeasurementField("shoulder_to_underbust", "Shoulder to underbust", isEssential = true),
                MeasurementField("bust_span", "Bust span", isEssential = true),
                MeasurementField("waist", "Waist", isEssential = true),
                MeasurementField("neck_circumference", "Neck circumference", isEssential = false),
                MeasurementField("underbust_circumference", "Underbust", isEssential = false),
            )
        ),
        MeasurementSection(
            titleKey = "section_body_lengths",
            fields = listOf(
                MeasurementField("shoulder_to_waist", "Shoulder to waist", isEssential = true),
                MeasurementField("hip_circumference", "Hip", isEssential = true),
                MeasurementField("full_length_gown", "Full length of gown", isEssential = true),
                MeasurementField("sleeve_length", "Sleeve length", isEssential = true),
                MeasurementField("wrist_circumference", "Wrist", isEssential = true),
                MeasurementField("nape_to_waist", "Nape to waist", isEssential = false),
                MeasurementField("full_front_length", "Full front length", isEssential = false),
                MeasurementField("arm_length", "Arm length", isEssential = false),
            )
        ),
        MeasurementSection(
            titleKey = "section_trouser",
            fields = listOf(
                MeasurementField("trouser_waist", "Waist", isEssential = true),
                MeasurementField("trouser_length", "Length", isEssential = true),
                MeasurementField("trouser_hip", "Hip", isEssential = false),
                MeasurementField("thigh_circumference", "Thigh", isEssential = false),
                MeasurementField("inseam", "Inseam", isEssential = false),
            )
        ),
    )

    private val maleSections = listOf(
        MeasurementSection(
            titleKey = "section_upper_body",
            fields = listOf(
                MeasurementField("shoulder_back", "Back", isEssential = true),
                MeasurementField("chest", "Chest", isEssential = true),
                MeasurementField("shirt_length", "Length of kaftan", isEssential = true),
                MeasurementField("long_sleeve_length", "Sleeve length", isEssential = true),
                MeasurementField("elbow_circumference", "Elbow circumference", isEssential = true),
                MeasurementField("neck_circumference", "Neck", isEssential = false),
                MeasurementField("tummy", "Tummy", isEssential = false),
            )
        ),
        MeasurementSection(
            titleKey = "section_trouser",
            fields = listOf(
                MeasurementField("trouser_waist", "Waist", isEssential = true),
                MeasurementField("trouser_hip", "Hip", isEssential = true),
                MeasurementField("trouser_length", "Length", isEssential = true),
                MeasurementField("thigh_circumference", "Thigh", isEssential = false),
                MeasurementField("knee_circumference", "Knee", isEssential = false),
            )
        ),
    )
}
