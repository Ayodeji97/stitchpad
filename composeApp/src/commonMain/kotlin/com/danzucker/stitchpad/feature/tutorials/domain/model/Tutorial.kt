package com.danzucker.stitchpad.feature.tutorials.domain.model

/**
 * A single how-to clip. Sourced from the Firestore `tutorials` collection (so videos can
 * be added/reordered without an app release) with a bundled fallback for offline / pre-load.
 *
 * @param id Firestore document id; the stable key used in the player route.
 * @param topicId links the clip to a [TutorialTopic] surface; blank/unknown for library-only clips.
 * @param title remote-authored title. For known [TutorialTopic]s the UI prefers a localized
 *   string resource and treats this as an optional override (see TutorialCopy in presentation).
 * @param storagePath Firebase Storage object path, e.g. `tutorials/add_customer.mp4`.
 * @param thumbnailPath Firebase Storage path to a poster still; null falls back to a placeholder.
 * @param durationSec clip length for the "0:45" badge; 0 hides the badge.
 * @param sortOrder ascending order in the Help library.
 */
data class Tutorial(
    val id: String,
    val topicId: String,
    val title: String,
    val description: String,
    val storagePath: String,
    val thumbnailPath: String?,
    val durationSec: Int,
    val sortOrder: Int,
) {
    val topic: TutorialTopic? get() = TutorialTopic.fromId(topicId)
}
