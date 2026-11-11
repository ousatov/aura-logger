package com.usatov.aura.logger.demo.stats

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Dashboard: total events, events today and events per day for the last 7 days
 * ("20/12/2024 - 15"), or "No events tracked". Sends [StatsContract.Intent.Visible] /
 * [StatsContract.Intent.Hidden] on lifecycle resume / pause.
 */
@Composable
fun StatsSection(modifier: Modifier = Modifier) {
    Text("Statistics: not implemented yet (D2)", modifier)
}
