package com.danzucker.stitchpad.feature.tutorials

import com.danzucker.stitchpad.feature.tutorials.data.BUNDLED_TUTORIALS
import com.danzucker.stitchpad.feature.tutorials.data.dto.TutorialDto
import com.danzucker.stitchpad.feature.tutorials.data.mapper.toTutorial
import com.danzucker.stitchpad.feature.tutorials.data.repository.mergeTutorialCatalog
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TutorialMappingTest {

    @Test
    fun dto_maps_to_domain_using_document_id() {
        val dto = TutorialDto(
            topicId = "add_customer",
            title = "Add a customer",
            description = "desc",
            storagePath = "tutorials/add_customer.mp4",
            thumbnailPath = "tutorials/add_customer_poster.jpg",
            durationSec = 42,
            sortOrder = 3,
        )
        val model = dto.toTutorial(id = "doc-1")
        assertEquals("doc-1", model.id)
        assertEquals("add_customer", model.topicId)
        assertEquals(TutorialTopic.AddCustomer, model.topic)
        assertEquals(42, model.durationSec)
    }

    @Test
    fun blank_thumbnail_path_maps_to_null() {
        val model = TutorialDto(storagePath = "tutorials/x.mp4", thumbnailPath = "  ").toTutorial("x")
        assertNull(model.thumbnailPath)
    }

    @Test
    fun bundled_fallback_covers_every_contextual_topic() {
        val topics = BUNDLED_TUTORIALS.mapNotNull { it.topic }.toSet()
        assertEquals(TutorialTopic.entries.toSet(), topics)
        BUNDLED_TUTORIALS.forEach { assertTrue(it.storagePath.isNotBlank()) }
    }

    @Test
    fun empty_remote_yields_full_bundled_catalog() {
        assertEquals(BUNDLED_TUTORIALS, mergeTutorialCatalog(emptyList()))
    }

    @Test
    fun partial_seed_keeps_bundled_fallback_for_unseeded_topics() {
        // Only add_customer is seeded remotely; the other four must still come from bundled.
        val docs = listOf(
            "add_customer" to TutorialDto(
                topicId = "add_customer",
                storagePath = "tutorials/add_customer.mp4",
                durationSec = 59,
                sortOrder = 1,
            ),
        )
        val merged = mergeTutorialCatalog(docs)
        val topics = merged.mapNotNull { it.topic }.toSet()
        assertEquals(TutorialTopic.entries.toSet(), topics) // all five still present
        assertEquals(59, merged.first { it.topicId == "add_customer" }.durationSec) // remote wins
    }

    @Test
    fun explicitly_disabled_remote_topic_stays_hidden() {
        val docs = listOf(
            "reports" to TutorialDto(
                topicId = "reports",
                storagePath = "tutorials/reports.mp4",
                enabled = false,
            ),
        )
        val merged = mergeTutorialCatalog(docs)
        // reports was explicitly present-but-disabled, so it must NOT fall back to bundled.
        assertFalse(merged.any { it.topicId == "reports" })
        assertTrue(merged.any { it.topicId == "quick_start" }) // other topics still fall back
    }

    @Test
    fun topic_lookup_round_trips() {
        TutorialTopic.entries.forEach { topic ->
            assertNotNull(TutorialTopic.fromId(topic.id))
            assertEquals(topic, TutorialTopic.fromId(topic.id))
        }
        assertNull(TutorialTopic.fromId("nope"))
    }
}
