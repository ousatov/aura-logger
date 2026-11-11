package com.usatov.aura.logger.demo.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.core.di.DemoPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The demo app's own copy of the SDK config (retention days and max events), edited in Settings
 * and used to initialise the SDK on every process start.
 *
 * Missing, incomplete or invalid stored values (for example a value rejected by
 * [EventTrackerConfig]) are read as the defaults `EventTrackerConfig()`.
 */
interface DemoConfigRepository {
    /** The stored config; emits the current value first and then every change. */
    val config: Flow<EventTrackerConfig>

    /** The stored config, or defaults. */
    suspend fun current(): EventTrackerConfig

    /** Persists [config]; returns after the write is durable. Storage errors are thrown. */
    suspend fun save(config: EventTrackerConfig)
}

class DataStoreDemoConfigRepository @Inject constructor(
    @param:DemoPreferences private val dataStore: DataStore<Preferences>,
) : DemoConfigRepository {

    override val config: Flow<EventTrackerConfig>
        get() = dataStore.data
            .map { it.toConfig() }
            .distinctUntilChanged()

    override suspend fun current(): EventTrackerConfig = config.first()

    override suspend fun save(config: EventTrackerConfig) {
        dataStore.edit {
            it[RETENTION_DAYS] = config.retentionDays
            it[MAX_EVENTS] = config.maxEvents
        }
    }

    private fun Preferences.toConfig(): EventTrackerConfig {
        val retention = this[RETENTION_DAYS]
        val maxEvents = this[MAX_EVENTS]
        if (retention == null || maxEvents == null) return EventTrackerConfig()
        return try {
            EventTrackerConfig(retentionDays = retention, maxEvents = maxEvents)
        } catch (_: IllegalArgumentException) {
            EventTrackerConfig()
        }
    }

    private companion object {
        val RETENTION_DAYS = intPreferencesKey("config_retention_days")
        val MAX_EVENTS = intPreferencesKey("config_max_events")
    }
}
