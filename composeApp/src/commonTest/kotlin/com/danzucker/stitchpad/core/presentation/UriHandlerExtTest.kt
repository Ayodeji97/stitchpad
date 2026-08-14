package com.danzucker.stitchpad.core.presentation

import androidx.compose.ui.platform.UriHandler
import kotlin.test.Test
import kotlin.test.assertEquals

class UriHandlerExtTest {

    private class ThrowingUriHandler : UriHandler {
        override fun openUri(uri: String) {
            throw IllegalArgumentException("Can't open $uri.")
        }
    }

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) { opened += uri }
    }

    @Test
    fun openUriSafelySwallowsMissingHandlerApp() {
        // Must not throw — this is the WhatsApp-not-installed crash.
        ThrowingUriHandler().openUriSafely("https://wa.me/123", tag = "test")
    }

    @Test
    fun openUriSafelyDelegatesOnSuccess() {
        val handler = RecordingUriHandler()
        handler.openUriSafely("https://example.com", tag = "test")
        assertEquals(listOf("https://example.com"), handler.opened)
    }
}
