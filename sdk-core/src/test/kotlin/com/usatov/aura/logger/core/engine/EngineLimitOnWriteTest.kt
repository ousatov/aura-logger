package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.LimitOverflowStrategy
import com.usatov.aura.logger.core.UnseenEventsProtection
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The maxEvents limit applied right after writes (AuraLoggerSettings.enforceLimitOnWrite). */
class EngineLimitOnWriteTest {

    private fun config(maxEvents: Int, onLimit: Boolean = true) =
        EventTrackerConfig(maxEvents = maxEvents, unseenEventsProtection = UnseenEventsProtection(onLimit = onLimit))

    private fun TestScope.started(
        config: EventTrackerConfig,
        settings: AuraLoggerSettings = AuraLoggerSettings(),
    ): EngineHarness = EngineHarness(this, settings).also {
        it.start(config)
        runCurrent()
    }

    private fun EngineHarness.names() = store.newestFirst.map { it.name }

    private fun EngineHarness.trackAll(vararg names: String) = names.forEach {
        clock.advanceBy(1)
        engine.track(it)
    }

    @Test
    fun `defaults enforce the limit on write and keep new events`() {
        val settings = AuraLoggerSettings()
        assertTrue(settings.enforceLimitOnWrite)
        assertEquals(LimitOverflowStrategy.KEEP_NEW_EVENTS, settings.limitOverflowStrategy)
    }

    @Test
    fun `oldest seen events are deleted right after a write, without a cleanup run`() = runTest {
        val h = started(config(maxEvents = 3))
        h.trackAll("e1", "e2", "e3")
        runCurrent()
        h.engine.markSeen(listOf("id-1", "id-2", "id-3"))

        h.trackAll("e4", "e5")
        runCurrent()

        assertEquals(listOf("e5", "e4", "e3"), h.names())
        assertEquals(0, h.scheduler.oneTimeCalls.get())
    }

    @Test
    fun `new unseen events are kept above the limit when only unseen events exist`() = runTest {
        val h = started(config(maxEvents = 3))

        h.trackAll("e1", "e2", "e3", "e4", "e5")
        runCurrent()

        assertEquals(5, h.store.all.size)
    }

    @Test
    fun `newer seen events are deleted before older unseen ones to reach the limit`() = runTest {
        val h = started(config(maxEvents = 3))
        h.trackAll("e1", "e2", "e3", "e4")
        runCurrent()
        h.engine.markSeen(listOf("id-3", "id-4"))

        h.trackAll("e5")
        runCurrent()

        assertEquals(listOf("e5", "e2", "e1"), h.names())
    }

    @Test
    fun `without protection the oldest events are deleted regardless of seen state`() = runTest {
        val h = started(config(maxEvents = 3, onLimit = false))

        h.trackAll("e1", "e2", "e3", "e4", "e5")
        runCurrent()

        assertEquals(listOf("e5", "e4", "e3"), h.names())
    }

    @Test
    fun `100 rapid events with limit 100 twice keep the newest 100 when all are seen`() = runTest {
        val h = started(config(maxEvents = 100))
        repeat(100) { h.engine.track("first_$it") }
        runCurrent()
        h.engine.markSeen((1..100).map { "id-$it" })

        repeat(100) { h.engine.track("second_$it") }
        runCurrent()

        assertEquals(100, h.store.all.size)
        assertTrue(h.store.all.all { it.name.startsWith("second_") })
    }

    @Test
    fun `buffered events written on start are limited too`() = runTest {
        val h = EngineHarness(this)
        repeat(5) { h.engine.track("e$it") }

        h.start(config(maxEvents = 2, onLimit = false))
        runCurrent()

        assertEquals(listOf("e4", "e3"), h.names())
    }

    @Test
    fun `the limit from updateConfig is used for later writes`() = runTest {
        val h = started(config(maxEvents = 10, onLimit = false))
        h.trackAll("e1", "e2", "e3")
        runCurrent()

        h.engine.updateConfig(config(maxEvents = 2, onLimit = false))
        h.trackAll("e4")
        runCurrent()

        assertEquals(listOf("e4", "e3"), h.names())
    }

    @Test
    fun `reads after a write already see the limit applied`() = runTest {
        val h = started(config(maxEvents = 2, onLimit = false))
        h.trackAll("e1", "e2", "e3")

        assertEquals(2, h.engine.getStats().totalCount)
        assertEquals(listOf("e3", "e2"), h.engine.getRecentEvents(10).map { it.name })
    }

    @Test
    fun `the limit is not applied on write when enforceLimitOnWrite is false`() = runTest {
        val h = started(config(maxEvents = 2, onLimit = false), AuraLoggerSettings(enforceLimitOnWrite = false))

        h.trackAll("e1", "e2", "e3", "e4")
        runCurrent()

        assertEquals(4, h.store.all.size)
    }

    @Test
    fun `DROP_NEW_EVENTS drops new events only when nothing can be deleted`() = runTest {
        val h = started(
            config(maxEvents = 3),
            AuraLoggerSettings(limitOverflowStrategy = LimitOverflowStrategy.DROP_NEW_EVENTS),
        )
        h.trackAll("e1", "e2", "e3")
        runCurrent()
        h.engine.markSeen(listOf("id-1"))

        h.trackAll("e4", "e5")
        runCurrent()

        assertEquals(listOf("e4", "e3", "e2"), h.names())
        assertTrue(h.logger.warnings.isNotEmpty())
    }

    @Test
    fun `DROP_NEW_EVENTS keeps earlier calls over later ones`() = runTest {
        val h = started(
            config(maxEvents = 2),
            AuraLoggerSettings(limitOverflowStrategy = LimitOverflowStrategy.DROP_NEW_EVENTS),
        )

        h.trackAll("e1", "e2", "e3", "e4")
        runCurrent()

        assertEquals(listOf("e2", "e1"), h.names())
    }

    @Test
    fun `DROP_NEW_EVENTS never drops when protection is off`() = runTest {
        val h = started(
            config(maxEvents = 2, onLimit = false),
            AuraLoggerSettings(limitOverflowStrategy = LimitOverflowStrategy.DROP_NEW_EVENTS),
        )

        h.trackAll("e1", "e2", "e3")
        runCurrent()

        assertEquals(listOf("e3", "e2"), h.names())
    }

    @Test
    fun `DROP_NEW_EVENTS has no effect when the limit is not enforced on write`() = runTest {
        val h = started(
            config(maxEvents = 1),
            AuraLoggerSettings(enforceLimitOnWrite = false, limitOverflowStrategy = LimitOverflowStrategy.DROP_NEW_EVENTS),
        )

        h.trackAll("e1", "e2", "e3")
        runCurrent()

        assertEquals(3, h.store.all.size)
    }
}
