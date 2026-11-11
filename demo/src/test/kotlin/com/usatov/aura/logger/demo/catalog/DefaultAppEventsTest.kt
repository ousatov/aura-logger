package com.usatov.aura.logger.demo.catalog

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.core.model.Section
import com.usatov.aura.logger.demo.testing.FakeEventTracker
import com.usatov.aura.logger.demo.testing.TrackCall
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class DefaultAppEventsTest {

    /** Returns the given values from `nextInt(from, until)`, in order, and checks the requested range. */
    private class ScriptedRandom(vararg values: Int) : Random() {
        private val queue = ArrayDeque(values.toList())
        override fun nextBits(bitCount: Int): Int = error("unexpected nextBits")
        override fun nextInt(from: Int, until: Int): Int {
            check(from == 0 && until == 1_000_000) { "expected nextInt(0, 1_000_000), was ($from, $until)" }
            return queue.removeFirst()
        }
    }

    private val tracker = FakeEventTracker()

    private fun events(random: Random = ScriptedRandom(1)) = DefaultAppEvents(tracker, random)

    @Test
    fun `process wake up`() {
        events().processStarted()
        assertEquals(listOf(TrackCall("process_wake_up", emptyMap())), tracker.tracked)
    }

    @Test
    fun `section selected`() {
        Section.entries.forEach { events().sectionSelected(it) }
        assertEquals(Section.entries.map { TrackCall("section_selected", mapOf("section" to it.key)) }, tracker.tracked)
    }

    @Test
    fun `config changed with numbers as strings in key order`() {
        events().configChanged(EventTrackerConfig(retentionDays = 14, maxEvents = 2500))

        val call = tracker.tracked.single()
        assertEquals("config_changed", call.name)
        assertEquals(listOf("retention_days" to "14", "max_events" to "2500"), call.properties.toList())
    }

    @Test
    fun `events cleared`() {
        events().eventsCleared(0)
        events().eventsCleared(42)
        assertEquals(
            listOf(TrackCall("events_cleared", mapOf("deleted" to "0")), TrackCall("events_cleared", mapOf("deleted" to "42"))),
            tracker.tracked,
        )
    }

    @ParameterizedTest
    @CsvSource("0, test_event_000000", "4213, test_event_004213", "123456, test_event_123456", "999999, test_event_999999")
    fun `test event name has a zero-padded 6-digit suffix`(value: Int, expected: String) {
        events(ScriptedRandom(value)).testEvent()
        assertEquals(listOf(TrackCall(expected, emptyMap())), tracker.tracked)
    }

    @Test
    fun `test events use a new suffix for each event`() {
        events(ScriptedRandom(1, 22, 333)).testEvents(3)
        assertEquals(listOf("test_event_000001", "test_event_000022", "test_event_000333"), tracker.tracked.map { it.name })
        assertTrue(tracker.tracked.all { it.properties.isEmpty() })
    }

    @Test
    fun `100 test events`() {
        events(Random(7)).testEvents(100)
        assertEquals(100, tracker.tracked.size)
        assertTrue(tracker.tracked.all { Regex("test_event_\\d{6}").matches(it.name) })
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, Int.MIN_VALUE])
    fun `non-positive count tracks nothing and does not throw`(count: Int) {
        assertDoesNotThrow { events().testEvents(count) }
        assertTrue(tracker.tracked.isEmpty())
    }

    @Test
    fun `button click with properties in order`() {
        events().testEventWithProperties()

        val call = tracker.tracked.single()
        assertEquals("button_click", call.name)
        assertEquals(listOf("screen" to "home", "action" to "submit"), call.properties.toList())
    }

    @Test
    fun `names and keys match the documented constants`() {
        assertEquals("process_wake_up", AppEventNames.PROCESS_WAKE_UP)
        assertEquals("section_selected", AppEventNames.SECTION_SELECTED)
        assertEquals("config_changed", AppEventNames.CONFIG_CHANGED)
        assertEquals("events_cleared", AppEventNames.EVENTS_CLEARED)
        assertEquals("test_event_", AppEventNames.TEST_EVENT_PREFIX)
        assertEquals("button_click", AppEventNames.BUTTON_CLICK)
    }
}
