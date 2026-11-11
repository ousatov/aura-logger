package com.usatov.aura.logger.demo.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usatov.aura.logger.demo.R

/**
 * Dashboard: total events, events today and events per day for the last 7 days
 * ("20/12/2024 - 15"), or "No events tracked". Sends [StatsContract.Intent.Visible] /
 * [StatsContract.Intent.Hidden] on lifecycle resume / pause.
 */
@Composable
fun StatsSection(modifier: Modifier = Modifier) {
    val viewModel: StatsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(StatsContract.Intent.Visible)
        onPauseOrDispose { viewModel.onIntent(StatsContract.Intent.Hidden) }
    }

    StatsContent(state, modifier)
}

@Composable
private fun StatsContent(state: StatsContract.State, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium)
        when {
            state.isLoading -> CircularProgressIndicator()
            state.isEmpty -> Text(stringResource(R.string.stats_empty))
            else -> {
                Text(stringResource(R.string.stats_total, state.total))
                Text(stringResource(R.string.stats_today, state.today))
                state.days.forEach { day ->
                    Text(stringResource(R.string.stats_day, day.date, day.count))
                }
            }
        }
        if (state.loadFailed) {
            Text(
                stringResource(R.string.stats_load_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
