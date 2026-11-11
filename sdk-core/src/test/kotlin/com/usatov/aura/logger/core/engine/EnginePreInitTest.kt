package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.PropertiesCodec
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class EnginePreInitTest {

    @Test
    fun `events tracked before start are buffered and written on start in call order`() = runTest {
        val h = EngineHarness(this)

        h.engine.track("a")
        h.engine.track("b", mapOf("k" to "v"))
        h.engine.track("c", Unit, PropertiesCodec { mapOf("typed" to "yes") })
        runCurrent()
        assertTrue(h.store.all.isEmpty())

        h.start()
        runCurrent()

        assertEquals(listOf("a", "b", "c"), h.store.all.sortedBy { it.sequence }.map { it.name })
    }

    @Test
    fun `buffered events keep the id, timestamp and date taken at call time`() = runTest {
        val h = EngineHarness(this)

        h.engine.track("early")
        h.preInitClock.advanceBy(86_400_000L * 3)
        h.clock.advanceBy(86_400_000L * 3)
        h.timeZone.zoneId = "Pacific/Kiritimati"
        h.start()
        runCurrent()

        val stored = h.store.all.single()
        assertEquals("id-1", stored.id)
        assertEquals(T0, stored.timestamp)
        assertEquals("15/03/2026", stored.createdDate)
    }

    @Test
    fun `events before start use the initial clock, after start the clock passed to start`() = runTest {
        val h = EngineHarness(this)
        h.clock.nowMillis = T0 + 1_000_000

        h.engine.track("before")
        h.start()
        h.engine.track("after")
        runCurrent()

        val byName = h.store.all.associateBy { it.name }
        assertEquals(T0, byName.getValue("before").timestamp)
        assertEquals(T0 + 1_000_000, byName.getValue("after").timestamp)
    }

    @Test
    fun `buffered events are written before events tracked right after start`() = runTest {
        val h = EngineHarness(this)

        h.engine.track("buffered")
        h.start()
        h.engine.track("live")
        runCurrent()

        assertEquals(listOf("buffered", "live"), h.store.all.sortedBy { it.sequence }.map { it.name })
    }

    @Test
    fun `buffer overflow drops the oldest events with a warning`() = runTest {
        val h = EngineHarness(this, AuraLoggerSettings(preInitBufferCapacity = 3))

        (1..5).forEach { h.engine.track("e$it") }
        assertTrue(h.logger.warnings.isNotEmpty())
        h.start()
        runCurrent()

        assertEquals(listOf("e3", "e4", "e5"), h.store.all.sortedBy { it.sequence }.map { it.name })
    }

    @Test
    fun `buffer accepts exactly its capacity without warnings`() = runTest {
        val h = EngineHarness(this, AuraLoggerSettings(preInitBufferCapacity = 3))

        (1..3).forEach { h.engine.track("e$it") }
        h.start()
        runCurrent()

        assertEquals(3, h.store.all.size)
        assertTrue(h.logger.warnings.isEmpty())
    }

    @Test
    fun `default buffer keeps 500 events`() = runTest {
        val h = EngineHarness(this)

        (1..501).forEach { h.engine.track("e$it") }
        h.start()
        runCurrent()

        val names = h.store.all.sortedBy { it.sequence }.map { it.name }
        assertEquals(500, names.size)
        assertEquals("e2", names.first())
        assertEquals("e501", names.last())
    }

    @Test
    fun `invalid events are dropped before they take buffer space`() = runTest {
        val h = EngineHarness(this, AuraLoggerSettings(preInitBufferCapacity = 2))

        h.engine.track("a")
        h.engine.track("")
        h.engine.track("b")
        h.start()
        runCurrent()

        assertEquals(listOf("a", "b"), h.store.all.sortedBy { it.sequence }.map { it.name })
    }

    @Test
    fun `markSeen before start is ignored with a warning`() = runTest {
        val h = EngineHarness(this)

        assertDoesNotThrow { h.engine.markSeen(listOf("id-1")) }
        assertTrue(h.logger.warnings.isNotEmpty())
        h.engine.track("e")
        h.start()
        runCurrent()

        assertEquals(false, h.store.all.single().seen)
    }

    @Test
    fun `failed write of buffered events does not break later tracking`() = runTest {
        val h = EngineHarness(this)
        h.engine.track("buffered")
        h.store.failNextInsert = RuntimeException("boom")

        h.start()
        runCurrent()
        h.engine.track("live")
        runCurrent()

        assertTrue(h.store.all.any { it.name == "live" })
        assertTrue(h.logger.errors.isNotEmpty())
    }
}
