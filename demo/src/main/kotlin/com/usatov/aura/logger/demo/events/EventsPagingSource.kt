package com.usatov.aura.logger.demo.events

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.core.EventTracker
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import kotlinx.coroutines.CancellationException

/**
 * Pages of `EventTracker.getEventsPage`, newest first.
 *
 * - Key: `null` for the first page, then `EventPage.nextCursor`; the last page has `nextKey = null`.
 *   There is never a previous page (`prevKey = null`).
 * - Page size: `LoadParams.loadSize`.
 * - Errors of the SDK call are returned as `LoadResult.Error` (cancellation is rethrown).
 * - [getRefreshKey] is always `null`: a refresh reloads from the newest event.
 */
class EventsPagingSource(
    private val tracker: EventTracker,
    private val formatter: EventFormatter,
) : PagingSource<EventCursor, EventItem>() {

    override suspend fun load(params: LoadParams<EventCursor>): LoadResult<EventCursor, EventItem> =
        try {
            val page = tracker.getEventsPage(cursor = params.key, pageSize = params.loadSize)
            LoadResult.Page(
                data = page.events.map(formatter::format),
                prevKey = null,
                nextKey = page.nextCursor,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<EventCursor, EventItem>): EventCursor? = null
}
