package com.usatov.aura.logger.demo.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.usatov.aura.logger.demo.R
import com.usatov.aura.logger.demo.actions.ActionsSection
import com.usatov.aura.logger.demo.config.ConfigSection
import com.usatov.aura.logger.demo.core.model.Section
import com.usatov.aura.logger.demo.core.ui.LocalSnackbarHostState
import com.usatov.aura.logger.demo.debug.DebugToolsSection
import com.usatov.aura.logger.demo.events.EventsScreen
import com.usatov.aura.logger.demo.stats.StatsSection

/** App shell: bottom navigation with Dashboard (stats + actions), Events and Settings (config + debug tools). */
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    Section.entries.forEach { section ->
                        NavigationBarItem(
                            selected = section == state.section,
                            onClick = { viewModel.onIntent(MainContract.Intent.SelectSection(section)) },
                            icon = { Icon(section.icon(), contentDescription = null) },
                            label = { Text(stringResource(section.label())) },
                        )
                    }
                }
            },
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            when (state.section) {
                Section.DASHBOARD -> Column(modifier.verticalScroll(rememberScrollState())) {
                    StatsSection()
                    ActionsSection()
                }
                Section.EVENTS -> EventsScreen(modifier)
                Section.SETTINGS -> Column(modifier.verticalScroll(rememberScrollState())) {
                    ConfigSection()
                    DebugToolsSection()
                }
            }
        }
    }
}

private fun Section.label(): Int = when (this) {
    Section.DASHBOARD -> R.string.section_dashboard
    Section.EVENTS -> R.string.section_events
    Section.SETTINGS -> R.string.section_settings
}

@Suppress("DEPRECATION") // Icons.Filled.List: the auto-mirrored variant is not in material-icons-core 1.7.8.
private fun Section.icon(): ImageVector = when (this) {
    Section.DASHBOARD -> Icons.Filled.Home
    Section.EVENTS -> Icons.Filled.List
    Section.SETTINGS -> Icons.Filled.Settings
}
