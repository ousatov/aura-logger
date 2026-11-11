package com.usatov.aura.logger.internal.store

import androidx.test.core.app.ApplicationProvider
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.NewEventRecord
import com.usatov.aura.logger.core.testing.EventStoreContract
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the shared [EventStoreContract] against Room, plus Room-specific checks. */
@RunWith(RobolectricTestRunner::class)
class RoomEventStoreTest : EventStoreContract() {

    override fun createStore(): EventStore =
        RoomEventStore.create(ApplicationProvider.getApplicationContext(), databaseName = null)

    override fun closeStore(store: EventStore) {
        (store as RoomEventStore).close()
    }

    @Test
    fun twoInMemoryStoresAreIsolated() = runBlocking {
        val other = RoomEventStore.create(ApplicationProvider.getApplicationContext(), databaseName = null)
        try {
            store.insert(listOf(NewEventRecord("a", "n", "{}", 1, "15/03/2026")))
            assertEquals(0, other.count())
        } finally {
            other.close()
        }
    }
}
