package com.usatov.aura.logger.core.testing

import app.cash.turbine.test
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.NewEventRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract of [EventStore], run against every implementation (the in-memory fake used by the
 * `sdk-core` tests and the Room store of the `sdk` module), so that the fake cannot silently
 * diverge from the real store.
 *
 * JUnit 4, so that Android implementations can run it with Robolectric.
 */
abstract class EventStoreContract {

    protected lateinit var store: EventStore

    /** Creates an empty store. Called before every test, inside the test runner's environment. */
    protected abstract fun createStore(): EventStore

    /** Releases the store. Called after every test. */
    protected open fun closeStore(store: EventStore) = Unit

    @Before
    fun setUpStore() {
        store = createStore()
    }

    @After
    fun tearDownStore() {
        closeStore(store)
    }

    private fun rec(id: String, timestamp: Long, date: String = "15/03/2026", json: String = "{}", name: String = "n_$id") =
        NewEventRecord(id, name, json, timestamp, date)

    private suspend fun ids(): List<String> = store.getNewest(Int.MAX_VALUE).map { it.id }

    @Test
    fun insertStoresAllFieldsAndEventsAreUnseen() = runBlocking {
        val record = rec("a", 1_000, json = "{\"k\":\"v \\\"quoted\\\" \u041F\u0440\u0438\u0432\u0456\u0442 \uD83D\uDC4B\"}", name = "weird \n name")
        store.insert(listOf(record))

        val stored = store.getNewest(10).single()
        assertEquals(record.id, stored.id)
        assertEquals(record.name, stored.name)
        assertEquals(record.propertiesJson, stored.propertiesJson)
        assertEquals(record.timestamp, stored.timestamp)
        assertEquals(record.createdDate, stored.createdDate)
        assertFalse(stored.seen)
    }

    @Test
    fun sequencesIncreaseInInsertionOrderAndAreNeverReused() = runBlocking {
        store.insert(listOf(rec("a", 1), rec("b", 1)))
        val newest = store.getNewest(10)
        assertTrue(newest.first { it.id == "b" }.sequence > newest.first { it.id == "a" }.sequence)
        val firstMax = newest.maxOf { it.sequence }
        store.deleteAll(keepUnseen = false)
        store.insert(listOf(rec("c", 1)))

        assertTrue(store.getNewest(1).single().sequence > firstMax)
    }

    @Test
    fun emptyInsertIsANoOp() = runBlocking {
        store.insert(emptyList())
        assertEquals(0, store.count())
    }

    @Test
    fun getNewestOrdersByTimestampThenSequence() = runBlocking {
        store.insert(listOf(rec("t1_first", 1), rec("t2", 2), rec("t1_second", 1), rec("t0", 0)))

        assertEquals(listOf("t2", "t1_second", "t1_first", "t0"), ids())
        assertEquals(listOf("t2", "t1_second"), store.getNewest(2).map { it.id })
    }

    @Test
    fun getOlderThanContinuesAfterThePosition() = runBlocking {
        store.insert(listOf(rec("a", 1), rec("b", 2), rec("c", 2), rec("d", 3)))
        val all = store.getNewest(10) // d, c, b, a
        val anchor = all[1] // c

        assertEquals(listOf("b", "a"), store.getOlderThan(anchor.timestamp, anchor.sequence, 10).map { it.id })
        assertEquals(listOf("b"), store.getOlderThan(anchor.timestamp, anchor.sequence, 1).map { it.id })
        assertTrue(store.getOlderThan(all.last().timestamp, all.last().sequence, 10).isEmpty())
    }

    @Test
    fun countAndCountInRangeBoundaries() = runBlocking {
        store.insert(listOf(rec("a", 99), rec("b", 100), rec("c", 199), rec("d", 200)))

        assertEquals(4, store.count())
        assertEquals(2, store.countInRange(100, 200))
        assertEquals(0, store.countInRange(300, 400))
    }

    @Test
    fun countByDayGroupsByDateAndOrdersByLatestTimestamp() = runBlocking {
        store.insert(
            listOf(
                rec("a", 10, "31/12/2025"),
                rec("b", 20, "01/01/2026"),
                rec("c", 25, "01/01/2026"),
                rec("d", 30, "09/01/2026"),
            ),
        )

        val days = store.countByDay()

        assertEquals(listOf("09/01/2026", "01/01/2026", "31/12/2025"), days.map { it.createdDate })
        assertEquals(listOf(1, 2, 1), days.map { it.count })
        assertEquals(25L, days[1].latestTimestamp)
    }

    @Test
    fun markSeenUpdatesOnlyKnownIds() = runBlocking {
        store.insert(listOf(rec("a", 1), rec("b", 2)))

        store.markSeen(listOf("a", "missing"))
        store.markSeen(emptyList())

        assertEquals(mapOf("a" to true, "b" to false), store.getNewest(10).associate { it.id to it.seen })
    }

    @Test
    fun markSeenAcceptsMoreIdsThanTheSqliteVariableLimit() = runBlocking {
        val records = (1..1500).map { rec("e$it", it.toLong()) }
        store.insert(records)

        store.markSeen(records.map { it.id })

        assertTrue(store.getNewest(2000).all { it.seen })
    }

    @Test
    fun deleteAllRespectsKeepUnseen() = runBlocking {
        store.insert(listOf(rec("a", 1), rec("b", 2), rec("c", 3)))
        store.markSeen(listOf("a"))

        assertEquals(1, store.deleteAll(keepUnseen = true))
        assertEquals(setOf("b", "c"), ids().toSet())
        assertEquals(2, store.deleteAll(keepUnseen = false))
        assertEquals(0, store.count())
    }

    @Test
    fun deleteOlderThanIsExclusive() = runBlocking {
        store.insert(listOf(rec("a", 99), rec("b", 100), rec("c", 101)))
        store.markSeen(listOf("a", "b", "c"))

        assertEquals(1, store.deleteOlderThan(100, keepUnseen = true))
        assertEquals(setOf("b", "c"), ids().toSet())
    }

    @Test
    fun deleteOlderThanKeepsUnseenOnlyWhenAsked() = runBlocking {
        store.insert(listOf(rec("seen", 1), rec("unseen", 2), rec("new", 1000)))
        store.markSeen(listOf("seen"))

        assertEquals(1, store.deleteOlderThan(500, keepUnseen = true))
        assertEquals(1, store.deleteOlderThan(500, keepUnseen = false))
        assertEquals(listOf("new"), ids())
    }

    @Test
    fun deleteOldestAboveDeletesOldestFirst() = runBlocking {
        store.insert((1..10).map { rec("e$it", it.toLong()) })

        assertEquals(6, store.deleteOldestAbove(4, keepUnseen = false))
        assertEquals(listOf("e10", "e9", "e8", "e7"), ids())
        assertEquals(0, store.deleteOldestAbove(4, keepUnseen = false))
        assertEquals(0, store.deleteOldestAbove(100, keepUnseen = false))
    }

    @Test
    fun deleteOldestAboveBreaksTimestampTiesBySequence() = runBlocking {
        store.insert((1..4).map { rec("e$it", 5) })

        store.deleteOldestAbove(2, keepUnseen = false)

        assertEquals(setOf("e3", "e4"), ids().toSet())
    }

    @Test
    fun deleteOldestAboveReachesTheLimitByDeletingTheOldestSeenEvents() = runBlocking {
        store.insert((1..10).map { rec("e$it", it.toLong()) })
        store.markSeen((1..10).filter { it % 2 == 1 }.map { "e$it" })

        assertEquals(4, store.deleteOldestAbove(6, keepUnseen = true))
        assertEquals(setOf("e2", "e4", "e6", "e8", "e9", "e10"), ids().toSet())
    }

    @Test
    fun deleteOldestAboveDeletesNewerSeenEventsWhenOlderOnesAreUnseen() = runBlocking {
        store.insert((1..10).map { rec("e$it", it.toLong()) })
        store.markSeen(listOf("e8", "e9", "e10"))

        assertEquals(3, store.deleteOldestAbove(5, keepUnseen = true))
        assertEquals((1..7).map { "e$it" }.toSet(), ids().toSet())
    }

    @Test
    fun deleteOldestAboveIsSoftWhenOnlyUnseenRemain() = runBlocking {
        store.insert((1..5).map { rec("e$it", it.toLong()) })

        assertEquals(0, store.deleteOldestAbove(2, keepUnseen = true))
        assertEquals(5, store.count())
    }

    @Test
    fun observeChangesEmitsAfterInsertMarkSeenAndDelete() = runBlocking {
        store.observeChanges().test {
            // Observers (for example Room's invalidation tracker) may register asynchronously after
            // collection starts; only changes made after that are reported.
            delay(SUBSCRIPTION_SETTLE_MILLIS)
            store.insert(listOf(rec("a", 1)))
            awaitItem()
            store.markSeen(listOf("a"))
            awaitItem()
            store.deleteAll(keepUnseen = false)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val SUBSCRIPTION_SETTLE_MILLIS = 300L
    }
}
