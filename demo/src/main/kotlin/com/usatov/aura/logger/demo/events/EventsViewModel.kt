package com.usatov.aura.logger.demo.events

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.map
import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.events.EventsContract.Effect
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import com.usatov.aura.logger.demo.events.EventsContract.Intent
import com.usatov.aura.logger.demo.events.EventsContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Events tab. [events] pages through [EventsPagingSource] with [EventsContract.PAGE_SIZE], is cached
 * in the ViewModel scope, and invalidates its paging source on every `EventTracker.changes`
 * emission so that new and deleted events appear. See [EventsContract.Intent.ItemsShown] for
 * seen tracking.
 */
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val tracker: EventTracker,
    private val formatter: EventFormatter,
) : MviViewModel<State, Intent, Effect>(State) {

    /** Ids that need no `markSeen`: already reported by this ViewModel or loaded as seen. */
    private val skipIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var currentSource: PagingSource<EventCursor, EventItem>? = null

    /** Content read right before the latest refresh load; `null` before the first one. */
    @Volatile
    private var refreshedContent: ContentSnapshot? = null

    private val pagedEvents: Flow<PagingData<EventItem>> =
        Pager(config = PagingConfig(pageSize = EventsContract.PAGE_SIZE, enablePlaceholders = false)) {
            SnapshottingPagingSource(EventsPagingSource(tracker, formatter)).also { currentSource = it }
        }.flow
            .map { data -> data.map { item -> item.also { if (it.seen) skipIds.add(it.id) } } }
            .cachedIn(viewModelScope)

    val events: Flow<PagingData<EventItem>>
        get() = pagedEvents

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
        if (newIds.isNotEmpty()) tracker.markSeen(newIds)
    }

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
