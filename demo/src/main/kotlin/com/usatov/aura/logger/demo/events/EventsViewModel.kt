package com.usatov.aura.logger.demo.events

import androidx.paging.PagingData
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.core.mvi.MviViewModel
import com.usatov.aura.logger.demo.events.EventsContract.Effect
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import com.usatov.aura.logger.demo.events.EventsContract.Intent
import com.usatov.aura.logger.demo.events.EventsContract.State
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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

    val events: Flow<PagingData<EventItem>>
        get() = TODO("D3")

    override fun onIntent(intent: Intent): Unit = TODO("D3")
}
