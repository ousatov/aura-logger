package com.usatov.aura.logger.demo.events

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Events tab: the paged list (timestamp, name, properties if any), newest first, updated when
 * events are tracked; "No events tracked" when empty. Reports displayed rows with
 * [EventsContract.Intent.ItemsShown].
 */
@Composable
fun EventsScreen(modifier: Modifier = Modifier) {
    Text("Events: not implemented yet (D3)", modifier)
}
