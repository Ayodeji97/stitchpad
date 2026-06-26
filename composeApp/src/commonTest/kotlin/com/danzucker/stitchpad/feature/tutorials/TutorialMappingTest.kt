package com.danzucker.stitchpad.feature.tutorials

import com.danzucker.stitchpad.feature.tutorials.data.BUNDLED_TUTORIALS
import com.danzucker.stitchpad.feature.tutorials.data.dto.TutorialDto
import com.danzucker.stitchpad.feature.tutorials.data.mapper.toTutorial
import com.danzucker.stitchpad.feature.tutorials.domain.model.TutorialTopic
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun topic_lookup_round_trips() {
        TutorialTopic.entries.forEach { topic ->
            assertNotNull(TutorialTopic.fromId(topic.id))
            assertEquals(topic, TutorialTopic.fromId(topic.id))
        }
        assertNull(TutorialTopic.fromId("nope"))
    }
}
