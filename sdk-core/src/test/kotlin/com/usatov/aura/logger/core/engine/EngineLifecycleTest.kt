package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EngineLifecycleTest {

    @Test
    fun `engine is not started initially`() = runTest {
        val h = EngineHarness(this)
        assertFalse(h.engine.isStarted)
    }

    @Test
    fun `first start returns true, persists config and schedules periodic cleanup`() = runTest {
        val h = EngineHarness(this)
        val config = EventTrackerConfig(retentionDays = 3, maxEvents = 42)

        assertTrue(h.start(config))
        advanceUntilIdle()

        assertTrue(h.engine.isStarted)
        assertEquals(config, h.configStore.stored)
        assertEquals(1, h.scheduler.periodicCalls.get())
        assertEquals(0, h.scheduler.oneTimeCalls.get())
    }

    @Test
    fun `later starts are ignored with a warning`() = runTest {
        val h = EngineHarness(this)
        val first = EventTrackerConfig(retentionDays = 3)
        h.start(first)
        advanceUntilIdle()
        val warningsBefore = h.logger.warnings.size

        assertFalse(h.start(EventTrackerConfig(retentionDays = 30)))
        assertFalse(h.start(first))
        advanceUntilIdle()

        assertEquals(first, h.configStore.stored)
        assertEquals(listOf(first), h.configStore.writes)
        assertEquals(1, h.scheduler.periodicCalls.get())
        assertTrue(h.logger.warnings.size >= warningsBefore + 2)
    }

    @Test
    fun `start persists the init config even if another config was persisted before`() = runTest {
        val h = EngineHarness(this)
        h.configStore.stored = EventTrackerConfig(retentionDays = 99, maxEvents = 99)
        val config = EventTrackerConfig(retentionDays = 2, maxEvents = 5)

        h.start(config)
        advanceUntilIdle()

        assertEquals(config, h.configStore.stored)
    }

    @Test
    fun `suspend functions throw IllegalStateException before start`() = runTest {
        val h = EngineHarness(this)
        assertThrows<IllegalStateException> { h.engine.getRecentEvents(10) }
        assertThrows<IllegalStateException> { h.engine.getEventsPage(null, 10) }
        assertThrows<IllegalStateException> { h.engine.getStats() }
        assertThrows<IllegalStateException> { h.engine.clearAll() }
        assertThrows<IllegalStateException> { h.engine.updateConfig(EventTrackerConfig()) }
    }

    @Test
    fun `updateConfig persists the config before returning and schedules a one-time cleanup`() = runTest {
        val h = EngineHarness(this)
        h.start()
        advanceUntilIdle()
        val newConfig = EventTrackerConfig(
            retentionDays = 1,
            maxEvents = 10,
            unseenEventsProtection = UnseenEventsProtection(onRetention = false, onLimit = true, onClearAll = false),
        )

        h.engine.updateConfig(newConfig)

        assertEquals(newConfig, h.configStore.stored)
        assertEquals(1, h.scheduler.oneTimeCalls.get())
        assertEquals(1, h.scheduler.periodicCalls.get())
    }

    @Test
    fun `every updateConfig schedules a one-time cleanup and the last config wins`() = runTest {
        val h = EngineHarness(this)
        h.start()
        advanceUntilIdle()

        h.engine.updateConfig(EventTrackerConfig(maxEvents = 10))
        h.engine.updateConfig(EventTrackerConfig(maxEvents = 20))
        h.engine.updateConfig(EventTrackerConfig(maxEvents = 30))

        assertEquals(30, h.configStore.stored?.maxEvents)
        assertEquals(3, h.scheduler.oneTimeCalls.get())
    }

    @Test
    fun `start ignored later does not change the config set by updateConfig`() = runTest {
        val h = EngineHarness(this)
        h.start(EventTrackerConfig(maxEvents = 1))
        advanceUntilIdle()
        h.engine.updateConfig(EventTrackerConfig(maxEvents = 2))

        h.start(EventTrackerConfig(maxEvents = 3))
        advanceUntilIdle()

        assertEquals(2, h.configStore.stored?.maxEvents)
    }
}
