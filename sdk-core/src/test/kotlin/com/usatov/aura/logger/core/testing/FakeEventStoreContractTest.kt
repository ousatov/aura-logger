package com.usatov.aura.logger.core.testing

import com.usatov.aura.logger.core.spi.EventStore

/** Keeps the fake used by the engine tests consistent with the [EventStore] contract. */
class FakeEventStoreContractTest : EventStoreContract() {
    override fun createStore(): EventStore = FakeEventStore()
}
