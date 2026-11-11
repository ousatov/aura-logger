package com.usatov.aura.logger.demo.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.demo.core.di.DemoPreferences
import kotlinx.coroutines.flow.Flow
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
        get() = TODO("D5")

    override suspend fun current(): EventTrackerConfig = TODO("D5")

    override suspend fun save(config: EventTrackerConfig): Unit = TODO("D5")
}
