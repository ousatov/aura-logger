package com.usatov.aura.logger.core.engine

import app.cash.turbine.test
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class EngineWriteTest {

    private suspend fun TestScope.started(config: EventTrackerConfig = EventTrackerConfig()): EngineHarness =
        EngineHarness(this).also {
            it.start(config)
            runCurrent()
        }

    private fun noClearProtection() =
        EventTrackerConfig(unseenEventsProtection = UnseenEventsProtection(onClearAll = false))

    @Nested
    inner class MarkSeen {
        @Test
        fun `marks only the given events`() = runTest {
            val h = started()
            repeat(3) { h.engine.track("e$it") }
            runCurrent()

            h.engine.markSeen(listOf("id-1", "id-3"))
            runCurrent()

            assertEquals(mapOf("id-1" to true, "id-2" to false, "id-3" to true), h.store.all.associate { it.id to it.seen })
        }

        @Test
        fun `unknown, duplicate and already seen ids are ignored`() = runTest {
            val h = started()
            h.engine.track("e")
            runCurrent()

            assertDoesNotThrow {
                h.engine.markSeen(listOf("id-1", "id-1", "missing", "", "   "))
                h.engine.markSeen(setOf("id-1"))
                h.engine.markSeen(emptyList())
            }
            runCurrent()

            assertTrue(h.store.all.single().seen)
        }

        @Test
        fun `markSeen right after track applies to that event`() = runTest {
            val h = started()

            h.engine.track("e")
            h.engine.markSeen(listOf("id-1"))
            runCurrent()

            assertTrue(h.store.all.single().seen)
        }

        @Test
        fun `store failure is logged and not thrown`() = runTest {
            val h = started()
            h.engine.track("e")
            runCurrent()
            h.store.failMarkSeen = RuntimeException("boom")

            assertDoesNotThrow { h.engine.markSeen(listOf("id-1")) }
            runCurrent()
            h.store.failMarkSeen = null
            h.engine.track("after")
            runCurrent()

            assertTrue(h.logger.errors.isNotEmpty())
            assertEquals(2, h.store.all.size)
        }

        @Test
        fun `large id collections are accepted`() = runTest {
            val h = started()
            repeat(1500) { h.engine.track("e$it") }

            h.engine.markSeen((1..1500).map { "id-$it" })
            runCurrent()

            assertTrue(h.store.all.all { it.seen })
        }
    }

    @Nested
    inner class ClearAll {
        @Test
        fun `with default protection only seen events are deleted`() = runTest {
            val h = started()
            repeat(4) { h.engine.track("e$it") }
            h.engine.markSeen(listOf("id-1", "id-2"))

            val deleted = h.engine.clearAll()

            assertEquals(2, deleted)
            assertEquals(setOf("id-3", "id-4"), h.store.all.map { it.id }.toSet())
        }

        @Test
        fun `without protection all events are deleted`() = runTest {
            val h = started(noClearProtection())
            repeat(4) { h.engine.track("e$it") }

            assertEquals(4, h.engine.clearAll())
            assertEquals(0, h.engine.getStats().totalCount)
        }

        @Test
        fun `events tracked before clearAll are written first and then cleared`() = runTest {
            val h = started(noClearProtection())
            repeat(100) { h.engine.track("e$it") }

            val deleted = h.engine.clearAll()

            assertEquals(100, deleted)
            runCurrent()
            assertTrue(h.store.all.isEmpty())
        }

        @Test
        fun `events tracked after clearAll are kept`() = runTest {
            val h = started(noClearProtection())
            h.engine.track("before")
            h.engine.clearAll()
            h.engine.track("after")
            runCurrent()

            assertEquals(listOf("after"), h.store.all.map { it.name })
        }

        @Test
        fun `clearAll on an empty store returns zero`() = runTest {
            assertEquals(0, started().engine.clearAll())
        }

        @Test
        fun `clearAll follows protection changed by updateConfig`() = runTest {
            val h = started()
            repeat(3) { h.engine.track("e$it") }

            assertEquals(0, h.engine.clearAll())
            h.engine.updateConfig(noClearProtection())
            assertEquals(3, h.engine.clearAll())
        }

        @Test
        fun `seen state reported before clearAll is respected`() = runTest {
            val h = started()
            h.engine.track("e")
            h.engine.markSeen(listOf("id-1"))

            assertEquals(1, h.engine.clearAll())
        }
    }

    @Nested
    inner class Changes {
        @Test
        fun `emits after an event is written`() = runTest {
            val h = started()
            h.engine.changes.test {
                h.engine.track("e")
                runCurrent()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `emits after markSeen and after clearAll`() = runTest {
            val h = started(noClearProtection())
            h.engine.track("e")
            runCurrent()

            h.engine.changes.test {
                h.engine.markSeen(listOf("id-1"))
                runCurrent()
                awaitItem()
                h.engine.clearAll()
                runCurrent()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `does not emit an initial value`() = runTest {
            val h = started()
            h.engine.changes.test {
                runCurrent()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `can be collected before start and emits after start`() = runTest {
            val h = EngineHarness(this)
            h.engine.changes.test {
                h.engine.track("buffered")
                runCurrent()
                expectNoEvents()

                h.start()
                runCurrent()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `emits for changes made directly in the store, such as a cleanup`() = runTest {
            val h = started()
            h.engine.track("e")
            runCurrent()

            h.engine.changes.test {
                h.store.deleteAll(keepUnseen = false)
                runCurrent()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
