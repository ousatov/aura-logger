package com.usatov.aura.logger.demo.events

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.events.EventsContract.Effect
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import com.usatov.aura.logger.demo.events.EventsContract.Intent
import com.usatov.aura.logger.demo.events.EventsContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private var currentSource: EventsPagingSource? = null

    private val pagedEvents: Flow<PagingData<EventItem>> =
        Pager(config = PagingConfig(pageSize = EventsContract.PAGE_SIZE, enablePlaceholders = false)) {
            EventsPagingSource(tracker, formatter).also { currentSource = it }
        }.flow
            .map { data -> data.map { item -> item.also { if (it.seen) skipIds.add(it.id) } } }
            .cachedIn(viewModelScope)

    val events: Flow<PagingData<EventItem>>
        get() = pagedEvents

    init {
        viewModelScope.launch {
            tracker.changes.collect { currentSource?.invalidate() }
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
}
