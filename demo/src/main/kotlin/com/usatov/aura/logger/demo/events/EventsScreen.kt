package com.usatov.aura.logger.demo.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.usatov.aura.logger.demo.R
import com.usatov.aura.logger.demo.events.EventsContract.DayLabel
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import com.usatov.aura.logger.demo.events.EventsContract.EventListItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** Delay after the last scroll before the displayed rows are reported as shown. */
private const val SHOWN_DEBOUNCE_MILLIS = 500L

/**
 * Events tab: the paged list (timestamp, name, properties if any), newest first, grouped under
 * sticky day headers (Today / Yesterday / date), with unseen events marked "New" and a hint
 * above the list; updated when events are tracked; "No events tracked" when empty. Reports displayed rows with
 * [EventsContract.Intent.ItemsShown].
 */
@Composable
fun EventsScreen(modifier: Modifier = Modifier) {
    val viewModel: EventsViewModel = hiltViewModel()
    val items = viewModel.events.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    ReportShownItems(listState, items) { ids -> viewModel.onIntent(EventsContract.Intent.ItemsShown(ids)) }

    val refresh = items.loadState.refresh
    when {
        items.itemCount > 0 -> EventsList(items, listState, modifier)
        refresh is LoadState.Loading -> Centered(modifier) { CircularProgressIndicator() }
        refresh is LoadState.Error -> Centered(modifier) {
            ErrorMessage(stringResource(R.string.events_load_failed), onRetry = items::retry)
        }
        else -> Centered(modifier) { Text(stringResource(R.string.events_empty)) }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ReportShownItems(
    listState: LazyListState,
    items: LazyPagingItems<EventListItem>,
    onShown: (List<String>) -> Unit,
) {
    LaunchedEffect(listState, items) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .filter { it.index < items.itemCount }
                .mapNotNull { (items.peek(it.index) as? EventListItem.Event)?.item?.takeUnless(EventItem::seen)?.id }
        }
            .distinctUntilChanged()
            .debounce(SHOWN_DEBOUNCE_MILLIS)
            .collect { ids -> if (ids.isNotEmpty()) onShown(ids) }
    }
}

@Composable
private fun EventsList(items: LazyPagingItems<EventListItem>, listState: LazyListState, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.events_seen_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            for (index in 0 until items.itemCount) {
                when (val entry = items.peek(index)) {
                    is EventListItem.DayHeader -> stickyHeader(key = headerKey(items, index, entry), contentType = "header") {
                        items[index]
                        DayHeaderRow(entry.label)
                    }
                    else -> item(key = (entry as? EventListItem.Event)?.item?.id ?: "pending_$index", contentType = "event") {
                        (items[index] as? EventListItem.Event)?.let { EventRow(it.item) }
                    }
                }
            }
            when (items.loadState.append) {
                is LoadState.Loading -> item(key = "append_loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LoadState.Error -> item(key = "append_error") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        ErrorMessage(stringResource(R.string.events_load_more_failed), onRetry = items::retry)
                    }
                }
                is LoadState.NotLoading -> Unit
            }
        }
    }
}

/** Keyed by the event below the header: unique even if a day appears twice (time zone change). */
private fun headerKey(items: LazyPagingItems<EventListItem>, index: Int, header: EventListItem.DayHeader): String {
    val next = if (index + 1 < items.itemCount) items.peek(index + 1) as? EventListItem.Event else null
    return "day_${header.dayKey}_${next?.item?.id ?: index}"
}

@Composable
private fun DayHeaderRow(label: DayLabel) {
    val text = when (label) {
        DayLabel.Today -> stringResource(R.string.events_day_today)
        DayLabel.Yesterday -> stringResource(R.string.events_day_yesterday)
        is DayLabel.Date -> label.text
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
    }
}

@Composable
private fun EventRow(item: EventItem) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.timestamp, style = MaterialTheme.typography.labelMedium, color = secondary)
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.seen) FontWeight.Normal else FontWeight.Bold,
                color = if (item.seen) secondary else MaterialTheme.colorScheme.onSurface,
            )
            item.properties?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = secondary) }
        }
        if (!item.seen) {
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(stringResource(R.string.events_new_badge))
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.events_retry)) }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { content() }
}
