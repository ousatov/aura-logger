package com.usatov.aura.logger.demo.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usatov.aura.logger.demo.R
import com.usatov.aura.logger.demo.actions.ActionsContract.Effect
import com.usatov.aura.logger.demo.actions.ActionsContract.Intent
import com.usatov.aura.logger.demo.core.ui.LocalSnackbarHostState

/**
 * Dashboard: "Track Event", "Track 100 Events", "Track with Properties" and "Clear All Events"
 * (with a confirmation dialog). Effects are shown as snackbars through `LocalSnackbarHostState`.
 */
@Composable
fun ActionsSection(modifier: Modifier = Modifier) {
    val viewModel: ActionsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val resources = LocalResources.current

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is Effect.Tracked -> resources.getQuantityString(
                    R.plurals.actions_tracked,
                    effect.count,
                    effect.count,
                )
                is Effect.Cleared -> {
                    val deleted = resources.getQuantityString(
                        R.plurals.actions_cleared,
                        effect.deleted,
                        effect.deleted,
                    )
                    val kept = effect.kept
                    if (kept == null || kept <= 0) {
                        deleted
                    } else {
                        deleted + " " + resources.getQuantityString(R.plurals.actions_cleared_kept, kept, kept)
                    }
                }
                Effect.ClearFailed -> resources.getString(R.string.actions_clear_failed)
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.actions_title), style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { viewModel.onIntent(Intent.TrackEvent) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.actions_track_event)) }
        Button(
            onClick = { viewModel.onIntent(Intent.TrackHundredEvents) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.actions_track_hundred)) }
        Button(
            onClick = { viewModel.onIntent(Intent.TrackWithProperties) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.actions_track_with_properties)) }
        OutlinedButton(
            onClick = { viewModel.onIntent(Intent.ClearRequested) },
            enabled = !state.isClearing,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.actions_clear_all)) }
    }

    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(Intent.ClearDismissed) },
            title = { Text(stringResource(R.string.actions_clear_dialog_title)) },
            text = { Text(stringResource(R.string.actions_clear_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(Intent.ClearConfirmed) }) {
                    Text(stringResource(R.string.actions_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(Intent.ClearDismissed) }) {
                    Text(stringResource(R.string.actions_clear_cancel))
                }
            },
        )
    }
}
