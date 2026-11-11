package com.usatov.aura.logger.demo.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/** Snackbar host of the app shell; features show their one-off messages here. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("LocalSnackbarHostState is provided by MainScreen")
}
