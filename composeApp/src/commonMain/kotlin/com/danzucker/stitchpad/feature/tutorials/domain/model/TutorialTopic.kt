package com.danzucker.stitchpad.feature.tutorials.domain.model

/**
 * Stable identifiers for the contextual "Watch how it works" tutorials surfaced in
 * each empty state. The [id] is shared verbatim with the `topicId` field on the
 * Firestore `tutorials` documents and with the per-topic "seen" flag persisted in
 * OnboardingPreferencesStore — never rename an [id] once shipped.
 *
 * Tutorials in the Help library that are NOT tied to an empty surface simply carry a
 * `topicId` that does not match any entry here ([fromId] returns null for them).
 */
enum class TutorialTopic(val id: String) {
    QuickStart("quick_start"),
    AddCustomer("add_customer"),
    CreateOrder("create_order"),
    Styles("styles"),
    Reports("reports");

    companion object {
        fun fromId(id: String): TutorialTopic? = entries.firstOrNull { it.id == id }
    }
}
