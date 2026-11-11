package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.TrackedEvent
import com.usatov.aura.logger.core.testing.millisOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EngineReadTest {

    private suspend fun TestScope.started(zone: String = "UTC"): EngineHarness =
        EngineHarness(this, zone = zone).also {
            it.start()
            advanceUntilIdle()
        }

    private fun EngineHarness.trackAt(name: String, timestamp: Long, properties: Map<String, String> = emptyMap()) {
        clock.nowMillis = timestamp
        engine.track(name, properties)
    }

    @Nested
    inner class RecentEvents {
        @Test
        fun `returns newest first, limited`() = runTest {
            val h = started()
            h.trackAt("old", T0)
            h.trackAt("mid", T0 + 1)
            h.trackAt("new", T0 + 2)

            assertEquals(listOf("new", "mid"), h.engine.getRecentEvents(2).map { it.name })
        }

        @Test
        fun `limit larger than the number of events returns all`() = runTest {
            val h = started()
            h.trackAt("a", T0)

            assertEquals(1, h.engine.getRecentEvents(Int.MAX_VALUE).size)
        }

        @Test
        fun `empty store returns an empty list`() = runTest {
            assertTrue(started().engine.getRecentEvents(10).isEmpty())
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, Int.MIN_VALUE])
        fun `non-positive limit is rejected`(limit: Int) = runTest {
            val h = started()
            assertThrows<IllegalArgumentException> { h.engine.getRecentEvents(limit) }
        }

        @Test
        fun `equal timestamps are ordered by call order, later first`() = runTest {
            val h = started()
            listOf("first", "second", "third").forEach { h.trackAt(it, T0) }

            assertEquals(listOf("third", "second", "first"), h.engine.getRecentEvents(10).map { it.name })
        }

        @Test
        fun `events with a timestamp earlier than existing ones are ordered by timestamp`() = runTest {
            val h = started()
            h.trackAt("later", T0 + 10_000)
            h.trackAt("earlier_but_tracked_last", T0)

            assertEquals(listOf("later", "earlier_but_tracked_last"), h.engine.getRecentEvents(10).map { it.name })
        }

        @Test
        fun `returned event maps every stored field`() = runTest {
            val h = started()
            h.trackAt("e", T0, mapOf("a" to "1"))
            advanceUntilIdle()
            val id = h.store.all.single().id
            h.engine.markSeen(listOf(id))

            val event = h.engine.getRecentEvents(1).single()
            assertEquals(TrackedEvent(id, "e", mapOf("a" to "1"), T0, "15/03/2026", seen = true), event)
        }

        @Test
        fun `reads see writes queued before them without waiting`() = runTest {
            val h = started()
            repeat(100) { h.engine.track("e$it") }

            assertEquals(100, h.engine.getRecentEvents(1000).size)
            assertEquals(100, h.engine.getStats().totalCount)
        }
    }

    @Nested
    inner class Paging {
        @Test
        fun `empty store gives an empty first page without a next cursor`() = runTest {
            val page = started().engine.getEventsPage(null, 10)
            assertTrue(page.events.isEmpty())
            assertNull(page.nextCursor)
        }

        @Test
        fun `pages walk all events newest first without duplicates or gaps`() = runTest {
            val h = started()
            (1..7).forEach { h.trackAt("e$it", T0 + it) }

            val names = mutableListOf<String>()
            var cursor: EventCursor? = null
            var pages = 0
            do {
                val page = h.engine.getEventsPage(cursor, 3)
                assertTrue(page.events.size <= 3)
                names += page.events.map { it.name }
                cursor = page.nextCursor
                pages++
            } while (cursor != null && pages < 10)

            assertEquals((7 downTo 1).map { "e$it" }, names)
            assertEquals(3, pages)
        }

        @Test
        fun `last page that exactly fills the page size has no next cursor`() = runTest {
            val h = started()
            (1..4).forEach { h.trackAt("e$it", T0 + it) }

            val first = h.engine.getEventsPage(null, 2)
            assertNotNull(first.nextCursor)
            val second = h.engine.getEventsPage(first.nextCursor, 2)

            assertEquals(listOf("e2", "e1"), second.events.map { it.name })
            assertNull(second.nextCursor)
            assertNull(h.engine.getEventsPage(null, 4).nextCursor)
        }

        @Test
        fun `page boundary inside a group of equal timestamps loses nothing`() = runTest {
            val h = started()
            (1..5).forEach { h.trackAt("e$it", T0) }

            val p1 = h.engine.getEventsPage(null, 2)
            val p2 = h.engine.getEventsPage(p1.nextCursor, 2)
            val p3 = h.engine.getEventsPage(p2.nextCursor, 2)

            assertEquals(listOf("e5", "e4", "e3", "e2", "e1"), (p1.events + p2.events + p3.events).map { it.name })
            assertNull(p3.nextCursor)
        }

        @Test
        fun `events tracked after the first page do not shift later pages`() = runTest {
            val h = started()
            (1..4).forEach { h.trackAt("e$it", T0 + it) }

            val p1 = h.engine.getEventsPage(null, 2)
            h.trackAt("new1", T0 + 100)
            h.trackAt("new2", T0 + 101)
            val p2 = h.engine.getEventsPage(p1.nextCursor, 2)

            assertEquals(listOf("e4", "e3"), p1.events.map { it.name })
            assertEquals(listOf("e2", "e1"), p2.events.map { it.name })
        }

        @Test
        fun `cursor stays valid when its anchor event is deleted`() = runTest {
            val h = started()
            (1..4).forEach { h.trackAt("e$it", T0 + it) }
            advanceUntilIdle()

            val p1 = h.engine.getEventsPage(null, 2)
            h.store.all.filter { it.name == "e3" }.forEach { h.engine.markSeen(listOf(it.id)) }
            h.engine.clearAll() // removes only the seen e3 (protection on by default)
            val p2 = h.engine.getEventsPage(p1.nextCursor, 2)

            assertEquals(listOf("e2", "e1"), p2.events.map { it.name })
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -5])
        fun `non-positive page size is rejected`(size: Int) = runTest {
            val h = started()
            assertThrows<IllegalArgumentException> { h.engine.getEventsPage(null, size) }
        }

        @Test
        fun `paging sees writes queued before the read`() = runTest {
            val h = started()
            repeat(10) { h.engine.track("e$it") }

            assertEquals(10, h.engine.getEventsPage(null, 50).events.size)
        }
    }

    @Nested
    inner class Stats {
        @Test
        fun `empty store`() = runTest {
            val stats = started().engine.getStats()
            assertEquals(0, stats.totalCount)
            assertEquals(0, stats.todayCount)
            assertTrue(stats.dailyCounts.isEmpty())
        }

        @Test
        fun `today count uses day boundaries in the device time zone`() = runTest {
            val zone = "Europe/Kyiv"
            val h = started(zone)
            h.trackAt("yesterday_last_ms", millisOf(zone, 2026, 3, 14, 23, 59, 59, 999))
            h.trackAt("today_first_ms", millisOf(zone, 2026, 3, 15))
            h.trackAt("today_last_ms", millisOf(zone, 2026, 3, 15, 23, 59, 59, 999))
            h.trackAt("tomorrow_first_ms", millisOf(zone, 2026, 3, 16))

            h.clock.nowMillis = millisOf(zone, 2026, 3, 15, 12)
            val stats = h.engine.getStats()

            assertEquals(4, stats.totalCount)
            assertEquals(2, stats.todayCount)
        }

        @Test
        fun `today follows the clock at the time of the call`() = runTest {
            val h = started()
            h.trackAt("e", T0)

            assertEquals(1, h.engine.getStats().todayCount)
            h.clock.advanceBy(86_400_000)
            assertEquals(0, h.engine.getStats().todayCount)
        }

        @Test
        fun `daily counts are ordered by time, not by the date string`() = runTest {
            val h = started()
            h.trackAt("a", millisOf("UTC", 2025, 12, 31, 23))
            h.trackAt("b", millisOf("UTC", 2026, 1, 1, 1))
            h.trackAt("c", millisOf("UTC", 2026, 1, 1, 2))
            h.trackAt("d", millisOf("UTC", 2026, 1, 9, 12))
            h.trackAt("e", millisOf("UTC", 2026, 2, 10, 12))

            val days = h.engine.getStats().dailyCounts

            assertEquals(listOf("10/02/2026", "09/01/2026", "01/01/2026", "31/12/2025"), days.map { it.date })
            assertEquals(listOf(1, 1, 2, 1), days.map { it.count })
            assertEquals(millisOf("UTC", 2026, 1, 1, 2), days[2].latestTimestamp)
        }

        @Test
        fun `days are grouped by createdDate even if the time zone changed later`() = runTest {
            val h = started()
            h.trackAt("utc_evening", millisOf("UTC", 2026, 3, 15, 23, 30))
            h.timeZone.zoneId = "Europe/Kyiv"

            val days = h.engine.getStats().dailyCounts

            assertEquals(listOf("15/03/2026"), days.map { it.date })
        }

        @Test
        fun `sum of daily counts equals total`() = runTest {
            val h = started()
            (0 until 30).forEach { h.trackAt("e$it", T0 - it * 7_200_000L) }

            val stats = h.engine.getStats()
            assertEquals(stats.totalCount, stats.dailyCounts.sumOf { it.count })
            assertTrue(stats.dailyCounts.all { it.count > 0 })
        }
    }
}
