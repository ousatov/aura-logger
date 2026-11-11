package com.usatov.aura.logger.demo.events

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.events.EventsContract.DayLabel
import com.usatov.aura.logger.demo.events.EventsContract.Effect
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import com.usatov.aura.logger.demo.events.EventsContract.EventListItem
import com.usatov.aura.logger.demo.events.EventsContract.Intent
import com.usatov.aura.logger.demo.events.EventsContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Events tab. [events] pages through [EventsPagingSource] with [EventsContract.PAGE_SIZE], is cached
 * in the ViewModel scope, and invalidates its paging source on every `EventTracker.changes`
 * emission so that new and deleted events appear. A [EventListItem.DayHeader] is inserted before
 * the first event and wherever the day changes between two neighbours; "today" is taken from
 * [clock] in the [zoneProvider] zone each time a page set is emitted. See
 * [EventsContract.Intent.ItemsShown] for seen tracking. Rows reported with `markSeen` are shown as
 * seen at once, without reloading the list.
 */
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val tracker: EventTracker,
    private val formatter: EventFormatter,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : MviViewModel<State, Intent, Effect>(State) {

    /** Ids that need no `markSeen`: already reported by this ViewModel or loaded as seen. */
    private val skipIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Ids reported with `markSeen` by this ViewModel. Overlaid on the loaded rows, because a
     * `markSeen` alone does not reload the list (see `init`).
     */
    private val reportedSeen = MutableStateFlow<Set<String>>(emptySet())

    @Volatile
    private var currentSource: PagingSource<EventCursor, EventItem>? = null

    /** Content read right before the latest refresh load; `null` before the first one. */
    @Volatile
    private var refreshedContent: ContentSnapshot? = null

    private val pagedEvents: Flow<PagingData<EventListItem>> =
        Pager(config = PagingConfig(pageSize = EventsContract.PAGE_SIZE, enablePlaceholders = false)) {
            SnapshottingPagingSource(EventsPagingSource(tracker, formatter)).also { currentSource = it }
        }.flow
            .map { data -> data.map { item -> item.also { if (it.seen) skipIds.add(it.id) } } }
            .map(::withDayHeaders)
            .cachedIn(viewModelScope)

    val events: Flow<PagingData<EventListItem>> = combine(pagedEvents, reportedSeen) { data, seen ->
        if (seen.isEmpty()) data else data.map { entry -> entry.markedSeenIfIn(seen) }
    }

    init {
        viewModelScope.launch {
            tracker.changes.collect {
                // markSeen (also our own) is a change too; reloading for it would reset the list
                // position, because a refresh always starts from the newest event. Invalidate only
                // when events were added or deleted since the last refresh.
                val content = contentSnapshotOrNull()
                if (content == null || content != refreshedContent) currentSource?.invalidate()
            }
        }
    }

    override fun onIntent(intent: Intent) {
        when (intent) {
            is Intent.ItemsShown -> reportShown(intent.ids)
        }
    }

    private fun reportShown(ids: List<String>) {
        val newIds = ids.distinct().filter { skipIds.add(it) }
        if (newIds.isEmpty()) return
        tracker.markSeen(newIds)
        reportedSeen.update { it + newIds }
    }

    private fun EventListItem.markedSeenIfIn(seen: Set<String>): EventListItem =
        if (this is EventListItem.Event && !item.seen && item.id in seen) EventListItem.Event(item.copy(seen = true)) else this

    private fun withDayHeaders(data: PagingData<EventItem>): PagingData<EventListItem> {
        val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zoneProvider.zone()).toLocalDate()
        val todayKey = DAY_FORMAT.format(today)
        val yesterdayKey = DAY_FORMAT.format(today.minusDays(1))
        return data
            .map<EventItem, EventListItem> { EventListItem.Event(it) }
            .insertSeparators { before, after ->
                val afterDay = (after as? EventListItem.Event)?.item?.let(::dayKeyOf) ?: return@insertSeparators null
                val beforeDay = (before as? EventListItem.Event)?.item?.let(::dayKeyOf)
                if (afterDay == beforeDay) {
                    null
                } else {
                    val label = when (afterDay) {
                        todayKey -> DayLabel.Today
                        yesterdayKey -> DayLabel.Yesterday
                        else -> DayLabel.Date(afterDay)
                    }
                    EventListItem.DayHeader(dayKey = afterDay, label = label)
                }
            }
    }

    /** `DD/MM/YYYY` part of [EventItem.timestamp] (formatted in the device zone at display time). */
    private fun dayKeyOf(item: EventItem): String = item.timestamp.substringBefore(' ')

    /** Stored content without seen flags; `null` if it cannot be read (then the list is reloaded). */
    private suspend fun contentSnapshotOrNull(): ContentSnapshot? = try {
        ContentSnapshot(
            totalCount = tracker.getStats().totalCount,
            newestId = tracker.getRecentEvents(limit = 1).firstOrNull()?.id,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * Additions change [newestId] and deletions change [totalCount] (both at once can only keep
     * the count if a new event was added, which changes [newestId]).
     */
    private data class ContentSnapshot(val totalCount: Int, val newestId: String?)

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }

    /**
     * Delegates to [delegate] and records [refreshedContent] before every refresh load, so that
     * every change after the read differs from it and none is missed.
     */
    private inner class SnapshottingPagingSource(
        private val delegate: EventsPagingSource,
    ) : PagingSource<EventCursor, EventItem>() {

        override suspend fun load(params: LoadParams<EventCursor>): LoadResult<EventCursor, EventItem> {
            if (params is LoadParams.Refresh) refreshedContent = contentSnapshotOrNull()
            return delegate.load(params)
        }

        override fun getRefreshKey(state: PagingState<EventCursor, EventItem>): EventCursor? =
            delegate.getRefreshKey(state)
    }
}
