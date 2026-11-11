package com.usatov.aura.logger.demo.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usatov.aura.logger.demo.R
import com.usatov.aura.logger.demo.config.ConfigContract.Effect
import com.usatov.aura.logger.demo.config.ConfigContract.InputError
import com.usatov.aura.logger.demo.config.ConfigContract.Intent
import com.usatov.aura.logger.demo.core.ui.Dimens
import com.usatov.aura.logger.demo.core.ui.LocalSnackbarHostState

/** Settings: retention period and max events fields with validation and a Save button. */
@Composable
fun ConfigSection(modifier: Modifier = Modifier) {
    val viewModel: ConfigViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val resources = LocalResources.current

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                Effect.Saved -> resources.getString(R.string.config_saved)
                Effect.SaveFailed -> resources.getString(R.string.config_save_failed)
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

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
        Button(
            onClick = { viewModel.onIntent(Intent.Save) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(R.string.config_save))
            }
        }
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
