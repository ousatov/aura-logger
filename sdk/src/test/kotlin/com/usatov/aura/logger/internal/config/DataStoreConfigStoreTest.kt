package com.usatov.aura.logger.internal.config

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

class DataStoreConfigStoreTest {

    @TempDir
    lateinit var dir: File

    private val scopes = mutableListOf<CoroutineScope>()

    @AfterEach
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun file() = File(dir, "config.preferences_pb")

    private fun newDataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        // Same corruption handling as the production DataStore in AuraLoggerRuntime.
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = ::file,
        )
    }

    @Test
    fun `read returns null when nothing was written`() = runBlocking {
        assertNull(DataStoreConfigStore(newDataStore()).read())
    }

    @Test
    fun `written config is read back`() = runBlocking {
        val store = DataStoreConfigStore(newDataStore())
        val config = EventTrackerConfig(retentionDays = 3, maxEvents = 250)

        store.write(config)

        assertEquals(config, store.read())
    }

    @Test
    fun `write replaces the previous config`() = runBlocking {
        val store = DataStoreConfigStore(newDataStore())

        store.write(EventTrackerConfig(retentionDays = 1, maxEvents = 1))
        store.write(EventTrackerConfig(retentionDays = 30, maxEvents = 1000))

        assertEquals(EventTrackerConfig(retentionDays = 30, maxEvents = 1000), store.read())
    }

    @ParameterizedTest
    @MethodSource("protections")
    fun `every protection combination round-trips`(protection: UnseenEventsProtection) = runBlocking {
        val store = DataStoreConfigStore(newDataStore())
        val config = EventTrackerConfig(unseenEventsProtection = protection)

        store.write(config)

        assertEquals(config, store.read())
    }

    @Test
    fun `extreme values round-trip`() = runBlocking {
        val store = DataStoreConfigStore(newDataStore())
        val config = EventTrackerConfig(retentionDays = Int.MAX_VALUE, maxEvents = 1)

        store.write(config)

        assertEquals(config, store.read())
    }

    @Test
    fun `config survives a new DataStore instance on the same file`() = runBlocking {
        val config = EventTrackerConfig(retentionDays = 2, maxEvents = 3)
        DataStoreConfigStore(newDataStore()).write(config)
        // DataStore releases the file only when its scope's job has completed; cancel() alone is asynchronous.
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()

        assertEquals(config, DataStoreConfigStore(newDataStore()).read())
    }

    @Test
    fun `unrelated preferences are treated as missing config`() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit {
            it[stringPreferencesKey("foo")] = "bar"
            it[intPreferencesKey("retention")] = -5
        }

        assertNull(DataStoreConfigStore(dataStore).read())
    }

    @Test
    fun `corrupted file is treated as missing config`() = runBlocking {
        file().writeBytes(byteArrayOf(0x0F, 0x7F, 0x00, 0x13, 0x37) + "not a protobuf".toByteArray())

        assertNull(DataStoreConfigStore(newDataStore()).read())
    }

    @Test
    fun `corrupted file without a corruption handler is read as missing config`() = runBlocking {
        file().writeBytes("garbage".toByteArray())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val withoutHandler = PreferenceDataStoreFactory.create(scope = scope, produceFile = ::file)

        assertNull(DataStoreConfigStore(withoutHandler).read())
    }

    @Test
    fun `write after a corrupted file succeeds`() = runBlocking {
        file().writeBytes("garbage".toByteArray())
        val store = DataStoreConfigStore(newDataStore())
        val config = EventTrackerConfig(maxEvents = 7)

        store.write(config)

        assertEquals(config, store.read())
    }

    @Test
    fun `config with any stored key missing is treated as missing`() = runBlocking {
        val dataStore = newDataStore()
        val store = DataStoreConfigStore(dataStore)
        store.write(EventTrackerConfig(retentionDays = 3, maxEvents = 9))
        val keys = dataStore.data.first().asMap().keys.toList()
        assertTrue(keys.size >= 5, "expected every config field to be stored, got $keys")

        for (key in keys) {
            store.write(EventTrackerConfig(retentionDays = 3, maxEvents = 9))
            dataStore.edit { it.remove(key) }
            assertNull(store.read(), "read() must be null without ${key.name}")
        }
    }

    @Test
    fun `config with an invalid or unknown stored number is treated as missing`() = runBlocking {
        val dataStore = newDataStore()
        val store = DataStoreConfigStore(dataStore)
        store.write(EventTrackerConfig())
        @Suppress("UNCHECKED_CAST")
        val intKeys = dataStore.data.first().asMap().filterValues { it is Int }.keys.map { it as Preferences.Key<Int> }
        assertTrue(intKeys.isNotEmpty())

        for (key in intKeys) {
            for (bad in listOf(0, -1, Int.MIN_VALUE)) {
                store.write(EventTrackerConfig())
                dataStore.edit { it[key] = bad }
                assertNull(store.read(), "read() must be null when ${key.name} = $bad")
            }
        }
    }

    companion object {
        @JvmStatic
        fun protections(): List<UnseenEventsProtection> =
            listOf(true, false).flatMap { r ->
                listOf(true, false).flatMap { l ->
                    listOf(true, false).map { c -> UnseenEventsProtection(r, l, c) }
                }
            }
    }
}
