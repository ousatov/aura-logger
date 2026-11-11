package com.usatov.aura.logger.internal.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.spi.ConfigStore

/**
 * [ConfigStore] backed by Preferences DataStore.
 *
 * Only one [DataStore] instance per file may exist in a process, so production code passes the
 * process-wide instance (file [FILE_NAME]); tests can pass their own.
 *
 * Reading a config that is missing, incomplete or invalid (for example, persisted by a future
 * version) returns `null`.
 */
internal class DataStoreConfigStore(
    private val dataStore: DataStore<Preferences>,
) : ConfigStore {

    override suspend fun read(): EventTrackerConfig? = TODO("Not implemented yet")

    override suspend fun write(config: EventTrackerConfig): Unit = TODO("Not implemented yet")

    companion object {
        /** Preferences DataStore file name of the SDK config. */
        const val FILE_NAME: String = "aura_logger_config"
    }
}
