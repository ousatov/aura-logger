package com.usatov.aura.logger.core.engine

import app.cash.turbine.test
import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.spi.CleanupScheduler
import com.usatov.aura.logger.core.spi.ConfigStore
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.NewEventRecord
import com.usatov.aura.logger.core.testing.FakeEventStore
import com.usatov.aura.logger.core.testing.record
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Failure paths: broken ports, malformed data, a stopped write queue. */
class EngineFailureTest {

    private fun TestScope.started(h: EngineHarness = EngineHarness(this)): EngineHarness = h.also {
        it.start()
        runCurrent()
    }

    @Nested
    inner class UpdateConfigFailures {
        @Test
        fun `config write failure is thrown to the caller`() = runTest {
            val h = EngineHarness(this)
            val failing = object : ConfigStore by h.configStore {
                var fail = false
                override suspend fun write(config: EventTrackerConfig) {
                    if (fail) throw IOException("disk full")
                    h.configStore.write(config)
                }
            }
            h.engine.start(
                EngineDependencies(EventTrackerConfig(), h.store, failing, h.scheduler, h.clock, backgroundScope, StandardTestDispatcher(testScheduler)),
            )
            runCurrent()
            failing.fail = true

            assertThrows<IOException> { h.engine.updateConfig(EventTrackerConfig(maxEvents = 5)) }
            assertEquals(0, h.scheduler.oneTimeCalls.get())
        }

        @Test
        fun `scheduling failure after the config was persisted is logged, not thrown`() = runTest {
            val h = EngineHarness(this)
            val scheduler = object : CleanupScheduler {
                override fun schedulePeriodic() = Unit
                override fun scheduleOneTime() = throw IllegalStateException("WorkManager is not initialised")
            }
            h.engine.start(
                EngineDependencies(EventTrackerConfig(), h.store, h.configStore, scheduler, h.clock, backgroundScope, StandardTestDispatcher(testScheduler)),
            )
            runCurrent()
            val config = EventTrackerConfig(maxEvents = 5)

            assertDoesNotThrow { h.engine.updateConfig(config) }
            assertEquals(config, h.configStore.stored)
            assertTrue(h.logger.errors.isNotEmpty())
        }

        @Test
        fun `periodic scheduling failure on start does not break tracking`() = runTest {
            val h = EngineHarness(this)
            val scheduler = object : CleanupScheduler {
                override fun schedulePeriodic() = throw IllegalStateException("boom")
                override fun scheduleOneTime() = Unit
            }
            assertDoesNotThrow {
                h.engine.start(
                    EngineDependencies(EventTrackerConfig(), h.store, h.configStore, scheduler, h.clock, backgroundScope, StandardTestDispatcher(testScheduler)),
                )
            }
            h.engine.track("e")
            runCurrent()

            assertEquals(1, h.store.all.size)
            assertTrue(h.logger.errors.isNotEmpty())
        }
    }

    @Nested
    inner class MalformedData {
        @Test
        fun `malformed stored properties are returned as empty with a warning`() = runTest {
            val h = started()
            h.store.seed(record("bad", 1, propertiesJson = "{not json"))
            h.store.seed(record("array", 2, propertiesJson = "[1,2]"))
            h.store.seed(record("nested", 3, propertiesJson = """{"k":{"x":1}}"""))

            val events = h.engine.getRecentEvents(10)

            assertEquals(3, events.size)
            assertTrue(events.first { it.id == "bad" }.properties.isEmpty())
            assertTrue(events.first { it.id == "array" }.properties.isEmpty())
            assertTrue(h.logger.warnings.size >= 2)
        }

        @Test
        fun `empty stored properties string is read as no properties`() = runTest {
            val h = started()
            h.store.seed(record("empty", 1, propertiesJson = ""))

            assertTrue(h.engine.getRecentEvents(1).single().properties.isEmpty())
        }

        @Test
        fun `unknown time zone id does not break tracking`() = runTest {
            val h = started()
            h.timeZone.zoneId = "Not/A_Zone"

            assertDoesNotThrow { h.engine.track("e") }
            runCurrent()

            val stored = h.store.all.single()
            assertTrue(Regex("""\d{2}/\d{2}/\d{4}""").matches(stored.createdDate))
            assertDoesNotThrow { h.engine.getStats() }
        }

        @Test
        fun `page size Int MAX_VALUE does not overflow`() = runTest {
            val h = started()
            h.engine.track("e")

            val page = h.engine.getEventsPage(null, Int.MAX_VALUE)

            assertEquals(1, page.events.size)
            assertEquals(null, page.nextCursor)
        }

        @Test
        fun `cursor toString shows its position`() {
            assertEquals("EventCursor(timestamp=5, sequence=7)", EventCursor(5, 7).toString())
        }
    }

    @Nested
    inner class ChangesFailures {
        @Test
        fun `failing store observation is retried and the flow does not fail`() = runTest {
            val attempts = AtomicInteger()
            val delegate = FakeEventStore()
            val store = object : EventStore by delegate {
                override fun observeChanges(): Flow<Unit> = flow {
                    if (attempts.incrementAndGet() == 1) throw IllegalStateException("db closed")
                    emit(Unit)
                    kotlinx.coroutines.awaitCancellation()
                }
            }
            val h = EngineHarness(this)
            h.engine.start(
                EngineDependencies(EventTrackerConfig(), store, h.configStore, h.scheduler, h.clock, backgroundScope, StandardTestDispatcher(testScheduler)),
            )

            h.engine.changes.test {
                runCurrent()
                advanceTimeBy(1_500)
                runCurrent()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(attempts.get() >= 2)
            assertTrue(h.logger.warnings.isNotEmpty())
        }

        @Test
        fun `completed store observation does not complete the changes flow`() = runTest {
            val store = object : EventStore by FakeEventStore() {
                override fun observeChanges(): Flow<Unit> = emptyFlow()
            }
            val h = EngineHarness(this)
            h.engine.start(
                EngineDependencies(EventTrackerConfig(), store, h.configStore, h.scheduler, h.clock, backgroundScope, StandardTestDispatcher(testScheduler)),
            )

            h.engine.changes.test {
                runCurrent()
                advanceTimeBy(10_000)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class StoppedQueue {
        private fun TestScope.startWithQueueScope(h: EngineHarness, store: EventStore = h.store): CoroutineScope {
            val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            h.engine.start(
                EngineDependencies(EventTrackerConfig(), store, h.configStore, h.scheduler, h.clock, queueScope, StandardTestDispatcher(testScheduler)),
            )
            runCurrent()
            return queueScope
        }

        @Test
        fun `after the queue scope is cancelled suspend calls fail with IllegalStateException`() = runTest {
            val h = EngineHarness(this)
            startWithQueueScope(h).cancel()
            runCurrent()

            assertThrows<IllegalStateException> { h.engine.getStats() }
            assertThrows<IllegalStateException> { h.engine.getRecentEvents(1) }
            assertThrows<IllegalStateException> { h.engine.clearAll() }
        }

        @Test
        fun `after the queue scope is cancelled track and markSeen do not throw`() = runTest {
            val h = EngineHarness(this)
            startWithQueueScope(h).cancel()
            runCurrent()

            assertDoesNotThrow {
                h.engine.track("late")
                h.engine.markSeen(listOf("id-1"))
            }
            runCurrent()
            assertTrue(h.store.all.isEmpty())
        }

        @Test
        fun `events in flight when the queue stops are reported as lost`() = runTest {
            val gate = CompletableDeferred<Unit>()
            val delegate = FakeEventStore()
            val store = object : EventStore by delegate {
                override suspend fun insert(events: List<NewEventRecord>) {
                    gate.await()
                    delegate.insert(events)
                }
            }
            val h = EngineHarness(this)
            val queueScope = startWithQueueScope(h, store)
            h.engine.track("in_flight")
            runCurrent()

            queueScope.cancel()
            runCurrent()

            assertTrue(delegate.all.isEmpty())
            assertTrue(h.logger.errors.isNotEmpty())
        }

        @Test
        fun `a read waiting for the queue fails when the queue stops`() = runTest {
            val gate = CompletableDeferred<Unit>()
            val delegate = FakeEventStore()
            val store = object : EventStore by delegate {
                override suspend fun insert(events: List<NewEventRecord>) {
                    gate.await()
                    delegate.insert(events)
                }
            }
            val h = EngineHarness(this)
            val queueScope = startWithQueueScope(h, store)
            h.engine.track("blocking")
            val read = backgroundScope.async { runCatching { h.engine.getStats() } }
            runCurrent()

            queueScope.cancel()
            runCurrent()

            assertTrue(read.await().exceptionOrNull() is IllegalStateException)
        }
    }
}
