package com.usatov.aura.logger.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ModelsTest {

    @Nested
    inner class EventTrackerConfigTest {
        @Test
        fun `defaults match the assignment`() {
            val config = EventTrackerConfig()
            assertEquals(7, config.retentionDays)
            assertEquals(100, config.maxEvents)
            assertEquals(UnseenEventsProtection(), config.unseenEventsProtection)
            assertEquals(7, EventTrackerConfig.DEFAULT_RETENTION_DAYS)
            assertEquals(100, EventTrackerConfig.DEFAULT_MAX_EVENTS)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, Int.MIN_VALUE])
        fun `non-positive retentionDays is rejected`(value: Int) {
            assertThrows<IllegalArgumentException> { EventTrackerConfig(retentionDays = value) }
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, Int.MIN_VALUE])
        fun `non-positive maxEvents is rejected`(value: Int) {
            assertThrows<IllegalArgumentException> { EventTrackerConfig(maxEvents = value) }
        }

        @Test
        fun `smallest and largest valid values are accepted`() {
            EventTrackerConfig(retentionDays = 1, maxEvents = 1)
            EventTrackerConfig(retentionDays = Int.MAX_VALUE, maxEvents = Int.MAX_VALUE)
        }

        @Test
        fun `copy with invalid value is rejected`() {
            assertThrows<IllegalArgumentException> { EventTrackerConfig().copy(maxEvents = 0) }
        }
    }

    @Nested
    inner class UnseenEventsProtectionTest {
        @Test
        fun `all flags are enabled by default`() {
            val protection = UnseenEventsProtection()
            assertTrue(protection.onRetention)
            assertTrue(protection.onLimit)
            assertTrue(protection.onClearAll)
        }
    }

    @Nested
    inner class EventCursorTest {
        @Test
        fun `cursors with equal position are equal and have equal hash codes`() {
            assertEquals(EventCursor(10, 2), EventCursor(10, 2))
            assertEquals(EventCursor(10, 2).hashCode(), EventCursor(10, 2).hashCode())
        }

        @Test
        fun `cursors with different position are not equal`() {
            assertNotEquals(EventCursor(10, 2), EventCursor(10, 3))
            assertNotEquals(EventCursor(10, 2), EventCursor(11, 2))
            assertFalse(EventCursor(10, 2).equals(null))
            assertFalse(EventCursor(10, 2).equals("EventCursor(10, 2)"))
        }
    }

    @Nested
    inner class AuraLoggerSettingsTest {
        @Test
        fun `defaults`() {
            val settings = AuraLoggerSettings()
            assertEquals(500, settings.preInitBufferCapacity)
            assertEquals(128, settings.maxEventNameLength)
            assertEquals(50, settings.maxPropertyCount)
            assertEquals(128, settings.maxPropertyKeyLength)
            assertEquals(1024, settings.maxPropertyValueLength)
            assertEquals(24, settings.cleanupPeriodHours)
            assertEquals(1, settings.cleanupInitialDelayHours)
        }

        @Test
        fun `work names and file names are distinct`() {
            val settings = AuraLoggerSettings()
            assertNotEquals(settings.periodicCleanupWorkName, settings.oneTimeCleanupWorkName)
            assertNotEquals(settings.databaseName, settings.configFileName)
        }

        @Test
        fun `invalid values are rejected`() {
            assertThrows<IllegalArgumentException> { AuraLoggerSettings(preInitBufferCapacity = 0) }
            assertThrows<IllegalArgumentException> { AuraLoggerSettings(maxEventNameLength = 0) }
            assertThrows<IllegalArgumentException> { AuraLoggerSettings(maxPropertyCount = -1) }
            assertThrows<IllegalArgumentException> { AuraLoggerSettings(maxPropertyKeyLength = 0) }
            assertThrows<IllegalArgumentException> { AuraLoggerSettings(maxPropertyValueLength = -1) }
            assertThrows<IllegalArgumentException> { AuraLoggerSettings(cleanupPeriodHours = 0) }
            assertThrows<IllegalArgumentException> { AuraLoggerSettings(cleanupInitialDelayHours = -1) }
        }

        @Test
        fun `zero properties and zero delay are allowed`() {
            AuraLoggerSettings(maxPropertyCount = 0, maxPropertyValueLength = 0, cleanupInitialDelayHours = 0)
        }
    }
}
