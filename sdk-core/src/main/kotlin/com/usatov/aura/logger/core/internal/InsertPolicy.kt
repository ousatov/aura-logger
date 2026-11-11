package com.usatov.aura.logger.core.internal

import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.LimitOverflowStrategy
import com.usatov.aura.logger.core.spi.AuraLogger
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.NewEventRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Target of an [InsertPolicy]: writes or drops the events of one batch. */
internal interface InsertSink {
    /** Inserts [records] in list order; a failing event is logged and lost, the others are written. */
    suspend fun insert(records: List<NewEventRecord>)

    /** Records that [records] were dropped on purpose (already logged by the policy). */
    fun dropped(records: List<NewEventRecord>)
}

/** Decides how one batch of new events is written by [SerialWriteQueue]. */
internal fun interface InsertPolicy {
    /** Writes [batch] through [sink]; every event must end up either inserted or dropped. */
    suspend fun write(batch: List<NewEventRecord>, sink: InsertSink)

    companion object {
        /** Inserts every event, with no limit. */
        val PLAIN: InsertPolicy = InsertPolicy { batch, sink -> sink.insert(batch) }
    }
}

/**
 * Applies the `maxEvents` limit right after every write (see the KDoc of `EventTrackerEngine`).
 *
 * After the batch is written, `deleteOldestAbove(maxEvents, keepUnseen = onLimit)` runs with the
 * latest config. With [LimitOverflowStrategy.DROP_NEW_EVENTS] and `onLimit`, events that do not
 * fit even after deleting every seen event are dropped, the latest calls first; without `onLimit`
 * room can always be made, so nothing is dropped.
 *
 * Storage errors of the limit itself are logged and never cause a new event to be lost: if the
 * room cannot be computed, the whole batch is stored.
 *
 * @param config returns the latest config; called on the write queue.
 */
internal class LimitEnforcingInsertPolicy(
    private val eventStore: EventStore,
    private val strategy: LimitOverflowStrategy,
    private val logger: AuraLogger,
    private val config: () -> EventTrackerConfig,
) : InsertPolicy {

    override suspend fun write(batch: List<NewEventRecord>, sink: InsertSink) {
        val current = config()
        val keepUnseen = current.unseenEventsProtection.onLimit
        if (strategy == LimitOverflowStrategy.DROP_NEW_EVENTS && keepUnseen) {
            writeDroppingOverflow(batch, sink, current.maxEvents)
        } else {
            sink.insert(batch)
        }
        try {
            eventStore.deleteOldestAbove(current.maxEvents, keepUnseen)
        } catch (e: Throwable) {
            rethrowIfStopped(e)
            logger.error("Failed to apply the limit of ${current.maxEvents} events after a write", e)
        }
    }

    /**
     * Deleting the oldest seen events one per new event, in call order, until none is left, is
     * the same as deleting up to `stored + batch.size - maxEvents` of them at once and then
     * keeping the earliest events that fit.
     */
    private suspend fun writeDroppingOverflow(batch: List<NewEventRecord>, sink: InsertSink, maxEvents: Int) {
        val room = try {
            if (eventStore.count() + batch.size <= maxEvents) {
                batch.size
            } else {
                eventStore.deleteOldestAbove((maxEvents - batch.size).coerceAtLeast(0), keepUnseen = true)
                (maxEvents - eventStore.count()).coerceAtLeast(0)
            }
        } catch (e: Throwable) {
            rethrowIfStopped(e)
            logger.error("Failed to make room for new events; storing them above the limit", e)
            batch.size
        }
        if (room >= batch.size) {
            sink.insert(batch)
            return
        }
        sink.insert(batch.subList(0, room))
        val dropped = batch.subList(room, batch.size)
        logger.warn(
            "Limit of $maxEvents events reached and only unseen events are left; " +
                "dropped ${dropped.size} new event(s), the first was '${dropped.first().name.abbreviate()}'",
        )
        sink.dropped(dropped)
    }

    /** Cancellation of the queue's scope stops the queue; any other exception is a storage failure. */
    private suspend fun rethrowIfStopped(e: Throwable) {
        if (e is CancellationException && !currentCoroutineContext().isActive) throw e
    }
}
