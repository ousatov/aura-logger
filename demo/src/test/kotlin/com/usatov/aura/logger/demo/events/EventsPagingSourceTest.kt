package com.usatov.aura.logger.demo.events

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.usatov.aura.logger.core.EventCursor
import com.usatov.aura.logger.demo.core.time.ZoneProvider
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import com.usatov.aura.logger.demo.testing.FakeEventTracker
import com.usatov.aura.logger.demo.testing.event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZoneId

class EventsPagingSourceTest {

    private val tracker = FakeEventTracker().apply { events = (5 downTo 1).map { event("e$it", it * 1000L) } }
    private val source = EventsPagingSource(tracker, EventFormatter(ZoneProvider { ZoneId.of("UTC") }))

    private suspend fun refresh(size: Int) = source.load(PagingSource.LoadParams.Refresh(null, size, false))

    private suspend fun append(key: EventCursor, size: Int) = source.load(PagingSource.LoadParams.Append(key, size, false))

    @Test
    fun `first page starts from the newest event and has no previous page`() = runTest {
        val page = refresh(2) as LoadResult.Page

        assertEquals(listOf("e5", "e4"), page.data.map { it.id })
        assertNull(page.prevKey)
        assertNotNull(page.nextKey)
        assertEquals(null to 2, tracker.pageRequests.single())
    }

    @Test
    fun `next pages use the cursor of the previous page and the last page has no next key`() = runTest {
        val first = refresh(2) as LoadResult.Page
        val second = append(first.nextKey!!, 2) as LoadResult.Page
        val third = append(second.nextKey!!, 2) as LoadResult.Page

        assertEquals(listOf("e3", "e2"), second.data.map { it.id })
        assertEquals(listOf("e1"), third.data.map { it.id })
        assertNull(third.nextKey)
        assertEquals(listOf(first.nextKey, second.nextKey), tracker.pageRequests.drop(1).map { it.first })
    }

    @Test
    fun `uses the requested load size`() = runTest {
        refresh(30)
        assertEquals(30, tracker.pageRequests.single().second)
    }

    @Test
    fun `empty store gives an empty last page`() = runTest {
        tracker.events = emptyList()
        val page = refresh(10) as LoadResult.Page

        assertTrue(page.data.isEmpty())
        assertNull(page.nextKey)
    }

    @Test
    fun `items are formatted`() = runTest {
        val page = refresh(1) as LoadResult.Page
        assertEquals(EventItem("e5", "01/01/1970 00:00:05", "e_e5", null, false), page.data.single())
    }

    @Test
    fun `SDK errors become LoadResult Error`() = runTest {
        val failure = IllegalStateException("not initialised")
        tracker.pageFailure = failure

        val result = refresh(10)

        assertTrue(result is LoadResult.Error)
        assertEquals(failure, (result as LoadResult.Error).throwable)
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        tracker.pageFailure = CancellationException("cancelled")
        assertThrows<CancellationException> { refresh(10) }
    }

    @Test
    fun `refresh key is always null`() = runTest {
        val page = refresh(2) as LoadResult.Page
        val state = PagingState(listOf(page), anchorPosition = 1, config = PagingConfig(pageSize = 2), leadingPlaceholderCount = 0)

        assertNull(source.getRefreshKey(state))
    }
}
