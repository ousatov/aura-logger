package com.usatov.aura.logger.demo.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.usatov.aura.logger.demo.R
import com.usatov.aura.logger.demo.events.EventsContract.EventItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** Delay after the last scroll before the displayed rows are reported as shown. */
private const val SHOWN_DEBOUNCE_MILLIS = 500L

/**
 * Events tab: the paged list (timestamp, name, properties if any), newest first, updated when
 * events are tracked; "No events tracked" when empty. Reports displayed rows with
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
    items: LazyPagingItems<EventItem>,
    onShown: (List<String>) -> Unit,
) {
    LaunchedEffect(listState, items) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .filter { it.index < items.itemCount }
                .mapNotNull { items.peek(it.index)?.takeUnless(EventItem::seen)?.id }
        }
            .distinctUntilChanged()
            .debounce(SHOWN_DEBOUNCE_MILLIS)
            .collect { ids -> if (ids.isNotEmpty()) onShown(ids) }
    }
}

@Composable
private fun EventsList(items: LazyPagingItems<EventItem>, listState: LazyListState, modifier: Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
            items[index]?.let { EventRow(it) }
            HorizontalDivider()
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

@Composable
private fun EventRow(item: EventItem) {
    ListItem(
        overlineContent = { Text(item.timestamp) },
        headlineContent = { Text(item.name) },
        supportingContent = item.properties?.let { properties -> { Text(properties) } },
    )
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
