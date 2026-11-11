package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
import com.usatov.aura.logger.core.testing.FakeEventStore
import com.usatov.aura.logger.core.testing.FakeTimeZoneProvider
import com.usatov.aura.logger.core.testing.MutableClock
import com.usatov.aura.logger.core.testing.RecordingLogger
import com.usatov.aura.logger.core.testing.millisOf
import com.usatov.aura.logger.core.testing.record
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EventCleanerTest {

    private val store = FakeEventStore()
    private val clock = MutableClock(millisOf("UTC", 2026, 3, 15, 10))
    private val timeZone = FakeTimeZoneProvider("UTC")
    private val cleaner = EventCleaner(store, clock, timeZone, RecordingLogger())

    private fun config(
        retentionDays: Int = 7,
        maxEvents: Int = 100,
        onRetention: Boolean = true,
        onLimit: Boolean = true,
    ) = EventTrackerConfig(retentionDays, maxEvents, UnseenEventsProtection(onRetention, onLimit, onClearAll = true))

    private fun remainingIds() = store.all.map { it.id }.toSet()

    @Nested
    inner class Retention {
        @Test
        fun `deletes events before the start of the day retentionDays days ago`() = runTest {
            val cutoff = millisOf("UTC", 2026, 3, 8)
            store.seed(record("at_cutoff", cutoff), record("just_before", cutoff - 1), record("today", clock.nowMillis), seen = true)

            val result = cleaner.cleanup(config(retentionDays = 7))

            assertEquals(CleanupResult(deletedByRetention = 1, deletedByLimit = 0), result)
            assertEquals(setOf("at_cutoff", "today"), remainingIds())
        }

        @Test
        fun `retention of one day keeps yesterday`() = runTest {
            store.seed(
                record("yesterday_start", millisOf("UTC", 2026, 3, 14)),
                record("two_days_ago_end", millisOf("UTC", 2026, 3, 13, 23, 59, 59, 999)),
                seen = true,
            )

            cleaner.cleanup(config(retentionDays = 1))

            assertEquals(setOf("yesterday_start"), remainingIds())
        }

        @Test
        fun `cutoff does not depend on the time of day of the run`() = runTest {
            store.seed(record("boundary", millisOf("UTC", 2026, 3, 8)), seen = true)

            clock.nowMillis = millisOf("UTC", 2026, 3, 15, 23, 59, 59, 999)
            cleaner.cleanup(config(retentionDays = 7))
            assertEquals(setOf("boundary"), remainingIds())

            clock.nowMillis = millisOf("UTC", 2026, 3, 16)
            cleaner.cleanup(config(retentionDays = 7))
            assertTrue(remainingIds().isEmpty())
        }

        @Test
        fun `uses the device time zone at run time`() = runTest {
            val zone = "Asia/Tokyo" // UTC+9
            timeZone.zoneId = zone
            clock.nowMillis = millisOf(zone, 2026, 3, 15, 1) // still 14/03 in UTC
            val cutoff = millisOf(zone, 2026, 3, 8)
            store.seed(record("at_cutoff", cutoff, zone), record("before", cutoff - 1, zone), seen = true)

            cleaner.cleanup(config(retentionDays = 7))

            assertEquals(setOf("at_cutoff"), remainingIds())
        }

        @Test
        fun `counts calendar days across a daylight saving change`() = runTest {
            val zone = "Europe/Kyiv" // DST starts on 29/03/2026 at 03:00, the day has 23 hours
            timeZone.zoneId = zone
            clock.nowMillis = millisOf(zone, 2026, 3, 30, 0, 30)
            val cutoff = millisOf(zone, 2026, 3, 29) // start of 29/03 local = 28/03 22:00 UTC
            store.seed(
                record("start_of_29th", cutoff, zone),
                record("late_28th", cutoff - 30 * 60_000, zone), // 28/03 23:30 local
                seen = true,
            )

            cleaner.cleanup(config(retentionDays = 1))

            assertEquals(setOf("start_of_29th"), remainingIds())
        }

        @Test
        fun `unseen events are kept when onRetention protection is on`() = runTest {
            val old = millisOf("UTC", 2026, 1, 1)
            store.seed(record("old_unseen", old))
            store.seed(record("old_seen", old), seen = true)

            val result = cleaner.cleanup(config(onRetention = true))

            assertEquals(1, result.deletedByRetention)
            assertEquals(setOf("old_unseen"), remainingIds())
        }

        @Test
        fun `unseen events are deleted when onRetention protection is off`() = runTest {
            val old = millisOf("UTC", 2026, 1, 1)
            store.seed(record("old_unseen", old))
            store.seed(record("old_seen", old), seen = true)

            assertEquals(2, cleaner.cleanup(config(onRetention = false)).deletedByRetention)
            assertTrue(remainingIds().isEmpty())
        }

        @Test
        fun `future events are never deleted by retention`() = runTest {
            store.seed(record("future", millisOf("UTC", 2027, 1, 1)), seen = true)

            cleaner.cleanup(config(retentionDays = 1))

            assertEquals(setOf("future"), remainingIds())
        }

        @Test
        fun `very large retention does not overflow`() = runTest {
            store.seed(record("ancient", 0L), seen = true)

            val result = cleaner.cleanup(config(retentionDays = Int.MAX_VALUE))

            assertEquals(0, result.deletedByRetention)
            assertEquals(setOf("ancient"), remainingIds())
        }
    }

    @Nested
    inner class Limit {
        private fun seedSequential(count: Int, seen: (Int) -> Boolean = { true }) {
            (1..count).forEach { i -> store.seed(record("e$i", clock.nowMillis - (count - i) * 1000L), seen = seen(i)) }
        }

        @Test
        fun `deletes the oldest events above the limit`() = runTest {
            seedSequential(10)

            val result = cleaner.cleanup(config(maxEvents = 5))

            assertEquals(5, result.deletedByLimit)
            assertEquals((6..10).map { "e$it" }.toSet(), remainingIds())
        }

        @Test
        fun `nothing is deleted at or below the limit`() = runTest {
            seedSequential(5)

            assertEquals(0, cleaner.cleanup(config(maxEvents = 5)).deletedByLimit)
            assertEquals(5, store.all.size)
        }

        @Test
        fun `limit of one keeps only the newest event`() = runTest {
            seedSequential(3)

            cleaner.cleanup(config(maxEvents = 1))

            assertEquals(setOf("e3"), remainingIds())
        }

        @Test
        fun `ties in timestamp are broken by insertion order`() = runTest {
            (1..4).forEach { store.seed(record("e$it", clock.nowMillis), seen = true) }

            cleaner.cleanup(config(maxEvents = 2))

            assertEquals(setOf("e3", "e4"), remainingIds())
        }

        @Test
        fun `unseen events are kept but counted when onLimit protection is on`() = runTest {
            seedSequential(10) { it % 2 == 1 } // odd events are seen

            val result = cleaner.cleanup(config(maxEvents = 6, onLimit = true))

            assertEquals(4, result.deletedByLimit)
            assertEquals(setOf("e2", "e4", "e6", "e8", "e9", "e10"), remainingIds())
        }

        @Test
        fun `limit is soft when only unseen events remain`() = runTest {
            seedSequential(10) { it > 7 } // only the 3 newest are seen

            val result = cleaner.cleanup(config(maxEvents = 5, onLimit = true))

            assertEquals(3, result.deletedByLimit)
            assertEquals((1..7).map { "e$it" }.toSet(), remainingIds())
        }

        @Test
        fun `unseen events are deleted by the limit when onLimit protection is off`() = runTest {
            seedSequential(10) { false }

            cleaner.cleanup(config(maxEvents = 4, onLimit = false))

            assertEquals((7..10).map { "e$it" }.toSet(), remainingIds())
        }
    }

    @Nested
    inner class Combined {
        @Test
        fun `retention runs before the limit`() = runTest {
            val old = millisOf("UTC", 2026, 1, 1)
            (1..4).forEach { store.seed(record("old$it", old + it), seen = true) }
            (1..6).forEach { store.seed(record("new$it", clock.nowMillis - 10_000 + it), seen = true) }

            val result = cleaner.cleanup(config(retentionDays = 7, maxEvents = 5))

            assertEquals(CleanupResult(deletedByRetention = 4, deletedByLimit = 1), result)
            assertEquals((2..6).map { "new$it" }.toSet(), remainingIds())
        }

        @Test
        fun `independent protection flags`() = runTest {
            val old = millisOf("UTC", 2026, 1, 1)
            store.seed(record("old_unseen", old))
            (1..3).forEach { store.seed(record("new_unseen$it", clock.nowMillis + it)) }

            val result = cleaner.cleanup(config(maxEvents = 2, onRetention = true, onLimit = false))

            assertEquals(0, result.deletedByRetention)
            assertEquals(2, result.deletedByLimit)
            assertEquals(setOf("new_unseen2", "new_unseen3"), remainingIds())
        }

        @Test
        fun `empty store`() = runTest {
            assertEquals(CleanupResult(0, 0), cleaner.cleanup(config()))
        }

        @Test
        fun `running twice is idempotent`() = runTest {
            (1..10).forEach { store.seed(record("e$it", clock.nowMillis - it), seen = true) }

            cleaner.cleanup(config(maxEvents = 3))
            val second = cleaner.cleanup(config(maxEvents = 3))

            assertEquals(CleanupResult(0, 0), second)
            assertEquals(3, store.all.size)
        }

        @Test
        fun `storage errors are propagated`() = runTest {
            store.seed(record("e", clock.nowMillis))
            store.failDeletes = IllegalStateException("db locked")

            assertThrows<IllegalStateException> { cleaner.cleanup(config()) }
        }
    }
}
