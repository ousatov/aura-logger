package com.usatov.aura.logger.demo.events

import androidx.paging.testing.asSnapshot
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.events.EventsContract.DayLabel
import com.usatov.aura.logger.demo.events.EventsContract.EventListItem
import com.usatov.aura.logger.demo.events.EventsContract.Intent
import com.usatov.aura.logger.demo.testing.FixedClock
import com.usatov.aura.logger.demo.testing.millisOf
import com.usatov.aura.logger.demo.testing.FakeEventTracker
import com.usatov.aura.logger.demo.testing.MainDispatcherExtension
import com.usatov.aura.logger.demo.testing.event
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.ZoneId

class EventsViewModelTest {

    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val tracker = FakeEventTracker()
    private val clock = FixedClock(millisOf("UTC", 2026, 3, 15, 10))
    private val zone = ZoneProvider { ZoneId.of("UTC") }
    private val vm by lazy { EventsViewModel(tracker, EventFormatter(zone), clock, zone) }

    private fun List<EventListItem>.ids() = filterIsInstance<EventListItem.Event>().map { it.item.id }

    @Test
    fun `lists all events newest first`() = runTest(main.dispatcher) {
        tracker.events = (50 downTo 1).map { event("e$it", it * 1000L) }

        val items = vm.events.asSnapshot { appendScrollWhile { true } }

        assertEquals((50 downTo 1).map { "e$it" }, items.ids())
    }

    @Test
    fun `pages are requested with the contract page size`() = runTest(main.dispatcher) {
        tracker.events = (300 downTo 1).map { event("e$it", it * 1000L) }

        val items = vm.events.asSnapshot { appendScrollWhile { true } }

        assertEquals(300, items.ids().size)
        val appends = tracker.pageRequests.drop(1)
        assertTrue(appends.isNotEmpty(), "expected append loads after the initial load")
        assertTrue(appends.all { it.second == EventsContract.PAGE_SIZE }, "append sizes: ${appends.map { it.second }}")
    }

    @Test
    fun `a change of events reloads the list`() = runTest(main.dispatcher) {
        tracker.events = listOf(event("old", 1000))
        assertEquals(listOf("old"), vm.events.asSnapshot().ids())

        tracker.events = listOf(event("new", 2000), event("old", 1000))
        tracker.changeFlow.emit(Unit)
        runCurrent()

        assertEquals(listOf("new", "old"), vm.events.asSnapshot().ids())
    }

    @Test
    fun `day headers precede the newest event of each day`() = runTest(main.dispatcher) {
        tracker.events = listOf(
            event("t1", millisOf("UTC", 2026, 3, 15, 9)),
            event("t2", millisOf("UTC", 2026, 3, 15, 8)),
            event("y1", millisOf("UTC", 2026, 3, 14, 20)),
            event("o1", millisOf("UTC", 2026, 3, 10, 12)),
            event("o2", millisOf("UTC", 2026, 3, 10, 0)),
        )

        val items = vm.events.asSnapshot { appendScrollWhile { true } }

        assertEquals(
            listOf("H:15/03/2026:Today", "t1", "t2", "H:14/03/2026:Yesterday", "y1", "H:10/03/2026:10/03/2026", "o1", "o2"),
            items.map {
                when (it) {
                    is EventListItem.Event -> it.item.id
                    is EventListItem.DayHeader -> "H:${it.dayKey}:" + when (val label = it.label) {
                        DayLabel.Today -> "Today"
                        DayLabel.Yesterday -> "Yesterday"
                        is DayLabel.Date -> label.text
                    }
                }
            },
        )
    }

    @Test
    fun `shown items are reported as seen once`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.ItemsShown(listOf("a", "b")))
        vm.onIntent(Intent.ItemsShown(listOf("b", "c")))
        vm.onIntent(Intent.ItemsShown(listOf("a", "b", "c")))
        runCurrent()

        assertEquals(listOf("a", "b", "c"), tracker.markedSeen.flatten())
        assertEquals(setOf("a", "b", "c"), tracker.markedSeen.flatten().toSet())
    }

    @Test
    fun `empty shown lists are ignored`() = runTest(main.dispatcher) {
        vm.onIntent(Intent.ItemsShown(emptyList()))
        runCurrent()

        assertTrue(tracker.markedSeen.isEmpty())
    }

    @Test
    fun `events already seen are not reported`() = runTest(main.dispatcher) {
        tracker.events = listOf(event("seen", 2000, seen = true), event("unseen", 1000))
        vm.events.asSnapshot()

        vm.onIntent(Intent.ItemsShown(listOf("seen", "unseen")))
        runCurrent()

        assertEquals(listOf("unseen"), tracker.markedSeen.flatten())
    }
}
