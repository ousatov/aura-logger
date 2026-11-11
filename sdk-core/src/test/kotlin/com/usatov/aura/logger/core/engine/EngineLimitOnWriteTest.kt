package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.LimitOverflowStrategy
import com.usatov.aura.logger.core.UnseenEventsProtection
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.testing.FakeEventStore
import com.usatov.aura.logger.core.testing.record
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private fun TestScope.startWith(
        store: EventStore,
        config: EventTrackerConfig,
        settings: AuraLoggerSettings = AuraLoggerSettings(),
        scope: CoroutineScope = backgroundScope,
    ): EngineHarness = EngineHarness(this, settings).also {
        it.engine.start(
            EngineDependencies(config, store, it.configStore, it.scheduler, it.clock, scope, StandardTestDispatcher(testScheduler)),
        )
        runCurrent()
    }

    @Test
    fun `failure to apply the limit after a write keeps the new events and is logged`() = runTest {
        val delegate = FakeEventStore()
        val store = object : EventStore by delegate {
            override suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int = throw IllegalStateException("locked")
        }
        val h = startWith(store, config(maxEvents = 1, onLimit = false))

        h.trackAll("e1", "e2")
        runCurrent()
        h.trackAll("e3")
        runCurrent()

        assertEquals(3, delegate.all.size)
        assertTrue(h.logger.errors.isNotEmpty())
    }

    @Test
    fun `DROP_NEW_EVENTS stores the batch above the limit when room cannot be computed`() = runTest {
        val delegate = FakeEventStore()
        val store = object : EventStore by delegate {
            override suspend fun count(): Int = throw IllegalStateException("locked")
        }
        val h = startWith(store, config(maxEvents = 1), AuraLoggerSettings(limitOverflowStrategy = LimitOverflowStrategy.DROP_NEW_EVENTS))

        h.trackAll("e1", "e2", "e3")
        runCurrent()

        assertEquals(3, delegate.all.size)
        assertTrue(h.logger.errors.isNotEmpty())
    }

    @Test
    fun `DROP_NEW_EVENTS drops the whole batch when there is no room at all`() = runTest {
        val h = started(config(maxEvents = 2), AuraLoggerSettings(limitOverflowStrategy = LimitOverflowStrategy.DROP_NEW_EVENTS))
        h.trackAll("e1", "e2")
        runCurrent()

        h.trackAll("e3")
        runCurrent()

        assertEquals(listOf("e2", "e1"), h.names())
        assertTrue(h.logger.warnings.isNotEmpty())
    }

    @Test
    fun `stopping the queue while the limit is applied keeps the written events`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val delegate = FakeEventStore()
        val store = object : EventStore by delegate {
            override suspend fun deleteOldestAbove(maxEvents: Int, keepUnseen: Boolean): Int {
                gate.await()
                return delegate.deleteOldestAbove(maxEvents, keepUnseen)
            }
        }
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val h = startWith(store, config(maxEvents = 1, onLimit = false), scope = queueScope)

        h.trackAll("e1", "e2")
        runCurrent()
        queueScope.cancel()
        runCurrent()

        assertEquals(2, delegate.all.size)
    }

    @Test
    fun `without protection an older event written later is deleted right after it is written`() = runTest {
        val h = EngineHarness(this)
        // Two newer events stored by a previous process.
        h.store.seed(record("newer1", T0 + 1_000), record("newer2", T0 + 2_000))
        h.engine.track("early") // tracked before init, timestamp T0

        h.start(config(maxEvents = 2, onLimit = false))
        runCurrent()

        assertEquals(listOf("newer2", "newer1"), h.store.newestFirst.map { it.id })
    }

    @Test
    fun `with protection an older unseen event written later is kept`() = runTest {
        val h = EngineHarness(this)
        h.store.seed(record("newer1", T0 + 1_000), record("newer2", T0 + 2_000), seen = true)
        h.engine.track("early")

        h.start(config(maxEvents = 2, onLimit = true))
        runCurrent()

        assertTrue(h.store.all.any { it.name == "early" })
        assertEquals(2, h.store.all.size)
    }
}
