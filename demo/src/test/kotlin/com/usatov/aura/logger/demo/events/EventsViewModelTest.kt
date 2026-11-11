package com.usatov.aura.logger.demo.events

import androidx.paging.testing.asSnapshot
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.events.EventsContract.Intent
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
    private val vm by lazy { EventsViewModel(tracker, EventFormatter(ZoneProvider { ZoneId.of("UTC") })) }

    @Test
    fun `lists all events newest first`() = runTest(main.dispatcher) {
        tracker.events = (50 downTo 1).map { event("e$it", it * 1000L) }

        val items = vm.events.asSnapshot { appendScrollWhile { true } }

        assertEquals((50 downTo 1).map { "e$it" }, items.map { it.id })
    }

    @Test
    fun `pages are requested with the contract page size`() = runTest(main.dispatcher) {
        tracker.events = (100 downTo 1).map { event("e$it", it * 1000L) }

        vm.events.asSnapshot()

        assertTrue(tracker.pageRequests.isNotEmpty())
        assertTrue(tracker.pageRequests.drop(1).all { it.second == EventsContract.PAGE_SIZE })
    }

    @Test
    fun `a change of events reloads the list`() = runTest(main.dispatcher) {
        tracker.events = listOf(event("old", 1000))
        assertEquals(listOf("old"), vm.events.asSnapshot().map { it.id })

        tracker.events = listOf(event("new", 2000), event("old", 1000))
        tracker.changeFlow.emit(Unit)
        runCurrent()

        assertEquals(listOf("new", "old"), vm.events.asSnapshot().map { it.id })
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
