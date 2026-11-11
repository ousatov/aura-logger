package com.usatov.aura.logger.demo.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.usatov.aura.logger.demo.R
import com.usatov.aura.logger.demo.debug.TimeShiftContract.Intent
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** Settings, debug builds only: time-shift tool (current shifted time, step buttons, reset). */
@Composable
fun DebugToolsSection(modifier: Modifier = Modifier) {
    val viewModel: TimeShiftViewModel = hiltViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepTimeFresh() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.debug_shifted_time, state.shiftedNow))
        Text(stringResource(R.string.debug_offset, formatOffset(state.offset)))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeShiftContract.STEPS.forEach { step ->
                Button(onClick = { viewModel.onIntent(Intent.ShiftBy(step)) }) { Text(formatStep(step)) }
            }
            OutlinedButton(onClick = { viewModel.onIntent(Intent.Reset) }) {
                Text(stringResource(R.string.debug_reset))
            }
        }
        Text(stringResource(R.string.debug_note), style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatStep(step: Duration): String {
    val hours = step.toDouble(DurationUnit.HOURS).toLong()
    val sign = if (hours < 0) "-" else "+"
    val abs = kotlin.math.abs(hours)
    return if (abs % 24 == 0L) "$sign${abs / 24}d" else "$sign${abs}h"
}

private fun formatOffset(offset: Duration): String {
    if (offset == Duration.ZERO) return "0"
    val negative = offset.isNegative()
    return offset.absoluteValue.toComponents { days, hours, minutes, seconds, _ ->
        buildString {
            append(if (negative) '-' else '+')
            if (days > 0) append("${days}d ")
            append("${hours}h ${minutes}m ${seconds}s")
        }
    }
}
