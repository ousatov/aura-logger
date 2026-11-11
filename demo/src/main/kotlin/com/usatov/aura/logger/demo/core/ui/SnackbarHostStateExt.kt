package com.usatov.aura.logger.demo.core.ui

import androidx.compose.material3.SnackbarHostState

/**
 * Shows [message] in place of whatever snackbar is visible, so messages never queue up.
 *
 * Suspends until the snackbar is dismissed. Collect effects with `collectLatest` so that an older
 * message is cancelled (and removed) as soon as a newer effect arrives.
 */
suspend fun SnackbarHostState.showLatest(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}
