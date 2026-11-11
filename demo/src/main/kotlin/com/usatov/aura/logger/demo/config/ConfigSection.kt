package com.usatov.aura.logger.demo.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usatov.aura.logger.demo.R
import com.usatov.aura.logger.demo.config.ConfigContract.Effect
import com.usatov.aura.logger.demo.config.ConfigContract.InputError
import com.usatov.aura.logger.demo.config.ConfigContract.Intent
import com.usatov.aura.logger.demo.core.ui.Dimens
import com.usatov.aura.logger.demo.core.ui.LocalSnackbarHostState
import com.usatov.aura.logger.demo.core.ui.showLatest

/** Settings: retention and max events fields and two switches, all saved when the tab is left. */
@Composable
fun ConfigSection(modifier: Modifier = Modifier) {
    val viewModel: ConfigViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val resources = LocalResources.current

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.effects.collectLatest { effect ->
            val message = when (effect) {
                Effect.Saved -> resources.getString(R.string.config_saved)
                Effect.SaveFailed -> resources.getString(R.string.config_save_failed)
            }
            snackbarHostState.showLatest(message)
        }
    }

    // Persist pending switch changes when the tab is left (the section leaves composition) ...
    DisposableEffect(viewModel) {
        onDispose { viewModel.onIntent(Intent.ScreenHidden) }
    }
    // ... and when the app goes to the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onIntent(Intent.ScreenHidden) }

    val loaded = state.savedConfig != null
    Column(
        modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
    ) {
        Text(stringResource(R.string.config_title), style = MaterialTheme.typography.titleMedium)
        NumberField(
            label = stringResource(R.string.config_retention_label),
            value = state.retentionInput,
            error = state.retentionError,
            range = ConfigContract.RETENTION_DAYS_RANGE,
            enabled = loaded && !state.isSaving,
            onChange = { viewModel.onIntent(Intent.RetentionChanged(it)) },
        )
        NumberField(
            label = stringResource(R.string.config_max_events_label),
            value = state.maxEventsInput,
            error = state.maxEventsError,
            range = ConfigContract.MAX_EVENTS_RANGE,
            enabled = loaded && !state.isSaving,
            onChange = { viewModel.onIntent(Intent.MaxEventsChanged(it)) },
        )
        SwitchRow(
            label = stringResource(R.string.config_track_navigation_label),
            checked = state.trackNavigation,
            enabled = loaded,
            onChange = { viewModel.onIntent(Intent.TrackNavigationChanged(it)) },
        )
        SwitchRow(
            label = stringResource(R.string.config_clear_unseen_label),
            hint = stringResource(R.string.config_clear_unseen_hint),
            checked = state.clearDeletesUnseen,
            enabled = loaded,
            onChange = { viewModel.onIntent(Intent.ClearDeletesUnseenChanged(it)) },
        )
        Text(
            text = stringResource(R.string.config_autosave_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: InputError?,
    range: IntRange,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(errorText(it, range)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun errorText(error: InputError, range: IntRange): String = when (error) {
    InputError.EMPTY -> stringResource(R.string.config_error_empty)
    InputError.NOT_A_NUMBER -> stringResource(R.string.config_error_not_a_number)
    InputError.OUT_OF_RANGE -> stringResource(R.string.config_error_out_of_range, range.first, range.last)
}
