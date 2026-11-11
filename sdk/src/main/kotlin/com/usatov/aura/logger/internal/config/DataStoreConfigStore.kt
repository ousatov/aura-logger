package com.usatov.aura.logger.internal.config

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
import com.usatov.aura.logger.core.spi.ConfigStore
import kotlinx.coroutines.flow.first

/**
 * [ConfigStore] backed by Preferences DataStore.
 *
 * Only one [DataStore] instance per file may exist in a process, so production code passes the
 * process-wide instance (file
 * [com.usatov.aura.logger.core.AuraLoggerSettings.configFileName]); tests can pass their own.
 *
 * Reading a config that is missing, incomplete or invalid (for example, persisted by a future
 * version) returns `null`.
 */
internal class DataStoreConfigStore(
    private val dataStore: DataStore<Preferences>,
) : ConfigStore {

    override suspend fun read(): EventTrackerConfig? {
        val preferences = try {
            dataStore.data.first()
        } catch (_: CorruptionException) {
            // Unreadable file: treated as an invalid config. Other I/O errors are thrown so that
            // the caller (the cleanup worker) retries instead of applying defaults.
            return null
        }
        if (preferences[SCHEMA_VERSION] != CURRENT_SCHEMA_VERSION) return null
        val retentionDays = preferences[RETENTION_DAYS] ?: return null
        val maxEvents = preferences[MAX_EVENTS] ?: return null
        val protection = UnseenEventsProtection(
            onRetention = preferences[PROTECT_ON_RETENTION] ?: return null,
            onLimit = preferences[PROTECT_ON_LIMIT] ?: return null,
            onClearAll = preferences[PROTECT_ON_CLEAR_ALL] ?: return null,
        )
        return try {
            EventTrackerConfig(retentionDays, maxEvents, protection)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun write(config: EventTrackerConfig) {
        dataStore.edit { preferences ->
            preferences[SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
            preferences[RETENTION_DAYS] = config.retentionDays
            preferences[MAX_EVENTS] = config.maxEvents
            preferences[PROTECT_ON_RETENTION] = config.unseenEventsProtection.onRetention
            preferences[PROTECT_ON_LIMIT] = config.unseenEventsProtection.onLimit
            preferences[PROTECT_ON_CLEAR_ALL] = config.unseenEventsProtection.onClearAll
        }
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val SCHEMA_VERSION = intPreferencesKey("schema_version")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val MAX_EVENTS = intPreferencesKey("max_events")
        val PROTECT_ON_RETENTION = booleanPreferencesKey("protect_unseen_on_retention")
        val PROTECT_ON_LIMIT = booleanPreferencesKey("protect_unseen_on_limit")
        val PROTECT_ON_CLEAR_ALL = booleanPreferencesKey("protect_unseen_on_clear_all")
    }
}
