package com.usatov.aura.logger

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.PropertiesCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The facade is a process-wide object, so only its state before `init` is tested here.
 * No test in this module may call `EventTrackerSDK.init`.
 */
@RunWith(RobolectricTestRunner::class)
class EventTrackerSDKBeforeInitTest {

    private fun assertIllegalState(block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("Expected IllegalStateException")
        } catch (expected: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun isNotInitialized() {
        assertFalse(EventTrackerSDK.isInitialized)
    }

    @Test
    fun trackAndMarkSeenDoNotThrowBeforeInit() {
        EventTrackerSDK.track("button_clicked", mapOf("button_id" to "submit"))
        EventTrackerSDK.track("")
        EventTrackerSDK.track("typed", 1, PropertiesCodec { mapOf("v" to it.toString()) })
        EventTrackerSDK.track("broken", 1, PropertiesCodec { error("boom") })
        EventTrackerSDK.markSeen(listOf("x"))
    }

    @Test
    fun suspendFunctionsThrowBeforeInit() {
        assertIllegalState { EventTrackerSDK.getRecentEvents(10) }
        assertIllegalState { EventTrackerSDK.getEventsPage(null, 10) }
        assertIllegalState { EventTrackerSDK.getStats() }
        assertIllegalState { EventTrackerSDK.clearAll() }
        assertIllegalState { EventTrackerSDK.updateConfig(EventTrackerConfig()) }
    }

    @Test
    fun changesCanBeCollectedBeforeInitWithoutEmitting() = runBlocking {
        assertNull(withTimeoutOrNull(200) { EventTrackerSDK.changes.first() })
    }
}
