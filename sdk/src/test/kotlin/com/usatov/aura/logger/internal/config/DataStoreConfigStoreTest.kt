package com.usatov.aura.logger.internal.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.UnseenEventsProtection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = ::file)
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
        scopes.forEach { it.cancel() }
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
    fun `write after a corrupted file succeeds`() = runBlocking {
        file().writeBytes("garbage".toByteArray())
        val store = DataStoreConfigStore(newDataStore())
        val config = EventTrackerConfig(maxEvents = 7)

        store.write(config)

        assertEquals(config, store.read())
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
