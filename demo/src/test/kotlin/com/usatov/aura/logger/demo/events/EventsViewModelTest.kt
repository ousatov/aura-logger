package com.usatov.aura.logger.demo.events

import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventPage
import com.usatov.aura.logger.core.EventStats
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.PropertiesCodec
import com.usatov.aura.logger.core.TrackedEvent
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.usatov.aura.logger.demo.events.EventsContract.EventListItem
import com.usatov.aura.logger.demo.events.EventsContract.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import kotlin.time.Clock

class EventsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val tracker = FakeEventTracker()
    private val formatter = EventFormatter { ZoneOffset.UTC }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repeat(3) { tracker.add("event_$it") }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `markSeen alone does not reload the list from the start`() = runTest(dispatcher) {
        val viewModel = EventsViewModel(tracker, formatter, Clock.System) { ZoneOffset.UTC }
        val presenter = present(viewModel)
        advanceUntilIdle()
        assertEquals(1, tracker.firstPageLoads)
        assertEquals(listOf("id_2", "id_1", "id_0"), presenter.snapshot().items.eventIds())

        viewModel.onIntent(Intent.ItemsShown(listOf("id_0", "id_1")))
        advanceUntilIdle()
        viewModel.onIntent(Intent.ItemsShown(listOf("id_2")))
        advanceUntilIdle()

        assertEquals(listOf(listOf("id_0", "id_1"), listOf("id_2")), tracker.markSeenCalls)
        assertEquals(1, tracker.firstPageLoads)
    }

    @Test
    fun `tracked and deleted events reload the list`() = runTest(dispatcher) {
        val viewModel = EventsViewModel(tracker, formatter, Clock.System) { ZoneOffset.UTC }
        val presenter = present(viewModel)
        advanceUntilIdle()

        tracker.track("new_event")
        advanceUntilIdle()
        assertEquals(2, tracker.firstPageLoads)
        assertEquals("id_3", presenter.snapshot().items.eventIds().first())

        tracker.clearAll()
        advanceUntilIdle()
        assertEquals(3, tracker.firstPageLoads)
        assertEquals(emptyList<String>(), presenter.snapshot().items.eventIds())
    }

    /** Presents [EventsViewModel.events] like the UI does, so that pages are actually loaded. */
    private fun TestScope.present(viewModel: EventsViewModel): PagingDataPresenter<EventListItem> {
        val presenter = object : PagingDataPresenter<EventListItem>(mainContext = dispatcher) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<EventListItem>) = Unit
        }
        backgroundScope.launch { viewModel.events.collectLatest(presenter::collectFrom) }
        return presenter
    }

    /** Ids of the event rows, without day headers. */
    private fun List<EventListItem?>.eventIds(): List<String> = filterIsInstance<EventListItem.Event>().map { it.item.id }

    private class FakeEventTracker : EventTracker {
        private val events = mutableListOf<TrackedEvent>()
        private var nextId = 0
        var firstPageLoads = 0
        val markSeenCalls = mutableListOf<List<String>>()

        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

        fun add(name: String) {
            val id = nextId++
            events.add(0, TrackedEvent("id_$id", name, emptyMap(), id.toLong(), "01/01/1970", seen = false))
        }

        override fun track(name: String, properties: Map<String, String>) {
            add(name)
            changes.tryEmit(Unit)
        }

        override fun <T> track(name: String, properties: T, codec: PropertiesCodec<T>) =
            track(name, codec.encode(properties))

        override fun markSeen(eventIds: Collection<String>) {
            markSeenCalls += eventIds.toList()
            events.replaceAll { if (it.id in eventIds) it.copy(seen = true) else it }
            changes.tryEmit(Unit)
        }

        override suspend fun getRecentEvents(limit: Int): List<TrackedEvent> = events.take(limit)

        override suspend fun getEventsPage(cursor: EventCursor?, pageSize: Int): EventPage {
            if (cursor == null) firstPageLoads++
            return EventPage(events.take(pageSize), nextCursor = null)
        }

        override suspend fun getStats(): EventStats = EventStats(events.size, events.size, emptyList())

        override suspend fun clearAll(): Int = events.size.also {
            events.clear()
            changes.tryEmit(Unit)
        }

        override suspend fun updateConfig(config: EventTrackerConfig) = Unit
    }
}
