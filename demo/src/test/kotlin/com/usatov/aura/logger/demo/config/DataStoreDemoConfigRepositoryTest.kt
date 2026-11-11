package com.usatov.aura.logger.demo.config

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.usatov.aura.logger.core.EventTrackerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreDemoConfigRepositoryTest {

    @TempDir
    lateinit var dir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Same corruption handling as the app's DataStore (AppModule).
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = { File(dir, "demo_settings.preferences_pb") },
        )
    }
    private val repository by lazy { DataStoreDemoConfigRepository(dataStore) }

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `defaults when nothing is stored`() = runBlocking {
        assertEquals(EventTrackerConfig(), repository.current())
        assertEquals(EventTrackerConfig(), repository.config.first())
    }

    @Test
    fun `saved config is read back`() = runBlocking {
        val config = EventTrackerConfig(retentionDays = 30, maxEvents = 5000)
        repository.save(config)

        assertEquals(config, repository.current())
        assertEquals(config, repository.config.first())
    }

    @Test
    fun `config flow emits the current value and then changes`() = runBlocking {
        repository.config.test {
            assertEquals(EventTrackerConfig(), awaitItem())
            repository.save(EventTrackerConfig(retentionDays = 2, maxEvents = 3))
            assertEquals(EventTrackerConfig(retentionDays = 2, maxEvents = 3), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unrelated stored values read as defaults`() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("something_else")] = "x" }

        assertEquals(EventTrackerConfig(), repository.current())
    }

    @Test
    fun `invalid stored numbers read as defaults instead of throwing`() = runBlocking {
        repository.save(EventTrackerConfig(retentionDays = 3, maxEvents = 4))
        // The storage format is not specified: make every stored number invalid, whatever its type.
        val stored = dataStore.data.first().asMap()
        assertTrue(stored.isNotEmpty())
        @Suppress("UNCHECKED_CAST")
        dataStore.edit { prefs ->
            stored.forEach { (key, value) ->
                when (value) {
                    is Int -> prefs[key as Preferences.Key<Int>] = 0
                    is Long -> prefs[key as Preferences.Key<Long>] = 0L
                    is String -> prefs[key as Preferences.Key<String>] = "0"
                    else -> Unit
                }
            }
        }

        assertEquals(EventTrackerConfig(), repository.current())
        assertEquals(EventTrackerConfig(), repository.config.first())
    }

    @Test
    fun `corrupted file reads as defaults`() = runBlocking {
        File(dir, "demo_settings.preferences_pb").writeBytes("garbage".toByteArray())

        assertEquals(EventTrackerConfig(), repository.current())
    }
}
