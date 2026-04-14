package com.danzucker.stitchpad.core.domain.model

object BodyProfileTemplate {

    fun sectionsFor(gender: CustomerGender): List<MeasurementSection> = when (gender) {
        CustomerGender.FEMALE -> femaleSections
        CustomerGender.MALE -> maleSections
    }

    private val femaleSections = listOf(
        MeasurementSection(
            titleKey = "section_neck_shoulders",
            fields = listOf(
                MeasurementField("neck_circumference", "Neck circumference", isEssential = true),
                MeasurementField("neck_to_shoulder", "Neck to shoulder", isEssential = false),
                MeasurementField("shoulder_width", "Shoulder width", isEssential = true),
                MeasurementField("back_shoulder", "Back shoulder", isEssential = false),
                MeasurementField("front_shoulder", "Front shoulder", isEssential = false),
                MeasurementField("across_back", "Across back", isEssential = false),
                MeasurementField("across_front", "Across front", isEssential = false),
                MeasurementField("round_shoulder", "Round shoulder", isEssential = false),
            )
        ),
        MeasurementSection(
            titleKey = "section_bust",
            fields = listOf(
                MeasurementField("bust_circumference", "Bust circumference", isEssential = true),
                MeasurementField("bust_front_arc", "Bust front arc", isEssential = false),
                MeasurementField("bust_back_arc", "Bust back arc", isEssential = false),
                MeasurementField("bust_point", "Bust point", isEssential = false),
                MeasurementField("bust_span", "Bust span", isEssential = false),
                MeasurementField("bust_radius", "Bust radius", isEssential = false),
                MeasurementField("underbust_circumference", "Underbust circumference", isEssential = false),
                MeasurementField("underbust_length", "Underbust length", isEssential = false),
            )
        ),
        MeasurementSection(
            titleKey = "section_waist_hip",
            fields = listOf(
                MeasurementField("waist", "Waist", isEssential = true),
                MeasurementField("snatched_waist", "Snatched waist", isEssential = false),
                MeasurementField("hip_circumference", "Hip circumference", isEssential = true),
                MeasurementField("hip_front_arc", "Hip front arc", isEssential = false),
                MeasurementField("hip_back_arc", "Hip back arc", isEssential = false),
                MeasurementField("lower_abdomen_circumference", "Lower abdomen circumference", isEssential = false),
            )
        ),
        MeasurementSection(
            titleKey = "section_arms",
            fields = listOf(
                MeasurementField("arm_length", "Arm length", isEssential = true),
                MeasurementField("sleeve_length", "Sleeve length", isEssential = false),
                MeasurementField("bicep_circumference", "Bicep circumference", isEssential = false),
                MeasurementField("elbow_circumference", "Elbow circumference", isEssential = false),
                MeasurementField("wrist_circumference", "Wrist circumference", isEssential = false),
            )
        ),
        MeasurementSection(
            titleKey = "section_body_lengths",
            fields = listOf(
                MeasurementField("nape_to_waist", "Nape to waist", isEssential = true),
                MeasurementField("full_front_length", "Full front length", isEssential = true),
                MeasurementField("shoulder_to_hip", "Shoulder to hip", isEssential = false),
                MeasurementField("waist_to_floor", "Waist to floor", isEssential = true),
            )
        ),
        MeasurementSection(
            titleKey = "section_trouser",
            fields = listOf(
                MeasurementField("trouser_waist", "Waist", isEssential = true),
                MeasurementField("trouser_hip", "Hip", isEssential = false),
                MeasurementField("thigh_circumference", "Thigh", isEssential = false),
                MeasurementField("knee_circumference", "Knee", isEssential = false),
                MeasurementField("inseam", "Inseam", isEssential = false),
                MeasurementField("trouser_length", "Length", isEssential = true),
                MeasurementField("ankle_circumference", "Ankle circumference", isEssential = false),
            )
        ),
    )

    private val maleSections = listOf(
        MeasurementSection(
            titleKey = "section_upper_body",
            fields = listOf(
                MeasurementField("shoulder_back", "Shoulder / back width", isEssential = true),
                MeasurementField("neck_circumference", "Neck circumference", isEssential = true),
                MeasurementField("chest", "Chest", isEssential = true),
                MeasurementField("tummy", "Tummy", isEssential = false),
                MeasurementField("shirt_length", "Shirt length", isEssential = true),
            )
        ),
        MeasurementSection(
            titleKey = "section_arms",
            fields = listOf(
                MeasurementField("round_sleeve_circumference", "Round sleeve circumference", isEssential = false),
                MeasurementField("short_sleeve_length", "Short sleeve length", isEssential = false),
                MeasurementField("three_quarter_sleeve_length", "3/4 sleeve length", isEssential = false),
                MeasurementField("long_sleeve_length", "Long sleeve length", isEssential = true),
            )
        ),
        MeasurementSection(
            titleKey = "section_trouser",
            fields = listOf(
                MeasurementField("trouser_waist", "Waist", isEssential = true),
                MeasurementField("trouser_hip", "Hip", isEssential = false),
                MeasurementField("thigh_circumference", "Thigh / lap", isEssential = false),
                MeasurementField("knee_circumference", "Knee", isEssential = false),
                MeasurementField("trouser_length", "Length", isEssential = true),
                MeasurementField("ankle_circumference", "Ankle circumference", isEssential = false),
            )
        ),
    )
}
