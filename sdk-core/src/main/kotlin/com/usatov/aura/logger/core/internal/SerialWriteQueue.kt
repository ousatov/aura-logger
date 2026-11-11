package com.usatov.aura.logger.core.internal

import com.usatov.aura.logger.core.spi.AuraLogger
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.NewEventRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single serial queue that executes write operations one after another, in submission order.
 *
 * Consecutive [WriteOp.Insert] operations are merged into one [EventStore.insert] call (one
 * transaction) of at most [maxBatchSize] events, so that bursts of `track` calls are written
 * efficiently. If a batch insert fails, its events are inserted one by one so that one bad
 * event does not lose the others. [insertPolicy] decides how each batch is written (for example,
 * applying the event limit right after the write).
 *
 * The consumer runs in [scope]. If the scope is cancelled, pending and later operations are
 * abandoned: lost events are logged and awaiting callers fail with [IllegalStateException].
 */
internal class SerialWriteQueue(
    scope: CoroutineScope,
    private val eventStore: EventStore,
    private val logger: AuraLogger,
    private val insertPolicy: InsertPolicy = InsertPolicy.PLAIN,
    private val maxBatchSize: Int = DEFAULT_MAX_BATCH_SIZE,
) {
    private val sink = object : InsertSink {
        override suspend fun insert(records: List<NewEventRecord>) {
            if (records.isNotEmpty()) this@SerialWriteQueue.insert(records)
        }

        override fun dropped(records: List<NewEventRecord>) {
            unwrittenInBatch -= records.size
        }
    }

    private val channel = Channel<WriteOp>(
        capacity = Channel.UNLIMITED,
        onUndeliveredElement = { op -> op.abandon(logger) },
    )

    init {
        val job = scope.launch { consume() }
        job.invokeOnCompletion { cause ->
            channel.cancel(CancellationException("Aura Logger write queue stopped", cause))
        }
    }

    /** Submits [op]. Never blocks and never throws; if the queue is stopped, [op] is abandoned. */
    fun submit(op: WriteOp) {
        if (channel.trySend(op).isFailure) op.abandon(logger)
    }

    /** Events of the in-flight insert batch that are neither written nor reported as failed. */
    private var unwrittenInBatch = 0

    private suspend fun consume() {
        // Operations already taken out of the channel are no longer covered by
        // onUndeliveredElement; they are abandoned here whenever the consumer stops.
        var pending: WriteOp? = null
        val inFlight = ArrayList<WriteOp>()
        try {
            while (true) {
                val op = pending ?: channel.receive()
                pending = null
                inFlight += op
                when (op) {
                    is WriteOp.Insert -> {
                        val batch = ArrayList(op.records)
                        while (batch.size < maxBatchSize) {
                            val next = channel.tryReceive().getOrNull() ?: break
                            if (next is WriteOp.Insert) {
                                inFlight += next
                                batch += next.records
                            } else {
                                pending = next
                                break
                            }
                        }
                        unwrittenInBatch = batch.size
                        writeBatch(batch)
                    }
                    is WriteOp.Task<*> -> op.run(logger)
                }
                inFlight.clear()
            }
        } finally {
            abandonInFlight(inFlight)
            pending?.let { safely { it.abandon(logger) } }
        }
    }

    private suspend fun writeBatch(batch: List<NewEventRecord>) {
        try {
            insertPolicy.write(batch, sink)
        } catch (e: Throwable) {
            if (e is CancellationException && !currentCoroutineContext().isActive) throw e
            // The policy handles storage errors itself; this is a last resort that keeps the queue running.
            logger.error("Unexpected error while writing $unwrittenInBatch event(s); they are lost", e)
        }
        unwrittenInBatch = 0
    }

    /** Abandons operations taken from the channel; only the unwritten events of a batch are reported lost. */
    private fun abandonInFlight(inFlight: List<WriteOp>) {
        var hasInserts = false
        for (op in inFlight) {
            if (op is WriteOp.Insert) {
                hasInserts = true
                op.discard()
            } else {
                safely { op.abandon(logger) }
            }
        }
        if (hasInserts && unwrittenInBatch > 0) {
            safely { logger.error("Aura Logger is stopped; $unwrittenInBatch event(s) are lost") }
        }
        unwrittenInBatch = 0
    }

    /** Runs cleanup [block] so that a failure of one step does not skip the others. */
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // The logger must not throw; if it does, the remaining cleanup still runs.
        }
    }

    private suspend fun insert(records: List<NewEventRecord>) {
        try {
            eventStore.insert(records)
            unwrittenInBatch -= records.size
        } catch (e: Throwable) {
            // Cancellation of the queue's own scope stops the queue; any other exception,
            // including a CancellationException thrown by the store itself (for example a
            // timeout), is a failure of this write only.
            if (e is CancellationException && !currentCoroutineContext().isActive) throw e
            if (records.size == 1) {
                unwrittenInBatch -= 1
                logger.error("Failed to write event '${records[0].name.abbreviate()}'; it is lost", e)
                return
            }
            logger.warn("Failed to write a batch of ${records.size} events; writing them one by one", e)
            for (record in records) insert(listOf(record))
        }
    }

    internal companion object {
        const val DEFAULT_MAX_BATCH_SIZE: Int = 500
    }
}

/** Operation executed by [SerialWriteQueue]. */
internal sealed class WriteOp {

    private val finished = AtomicBoolean(false)

    /** Called at most once when the operation can no longer be executed. */
    fun abandon(logger: AuraLogger) {
        if (finished.compareAndSet(false, true)) onAbandoned(logger)
    }

    protected fun markFinished(): Boolean = finished.compareAndSet(false, true)

    /** Marks the operation finished without reporting it; the caller reports it instead. */
    fun discard() {
        finished.set(true)
    }

    protected abstract fun onAbandoned(logger: AuraLogger)

    /** Inserts [records] in list order. */
    class Insert(val records: List<NewEventRecord>) : WriteOp() {
        override fun onAbandoned(logger: AuraLogger) {
            logger.error("Aura Logger is stopped; ${records.size} event(s) are lost")
        }
    }

    /**
     * Runs [block]. Its result or failure completes [result]; without [result], a failure is
     * logged with [description].
     */
    class Task<T>(
        private val description: String,
        private val result: CompletableDeferred<T>? = null,
        private val block: suspend () -> T,
    ) : WriteOp() {

        suspend fun run(logger: AuraLogger) {
            if (!markFinished()) return
            try {
                val value = block()
                result?.complete(value)
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) {
                    // The queue was stopped; the caller was not cancelled, so it gets a stopped-queue error.
                    val error = stoppedError(e)
                    if (result != null) result.completeExceptionally(error) else logger.error(error.message.orEmpty(), e)
                    throw e
                }
                // Thrown by the block itself (for example a timeout) while the queue is active:
                // an ordinary failure of this task. Wrapped so that the awaiting caller does not
                // mistake it for its own cancellation.
                fail(IllegalStateException("Failed to $description", e), logger)
            } catch (e: Throwable) {
                fail(e, logger)
            }
        }

        private fun fail(error: Throwable, logger: AuraLogger) {
            if (result != null) result.completeExceptionally(error) else logger.error("Failed to $description", error)
        }

        override fun onAbandoned(logger: AuraLogger) {
            val error = stoppedError()
            if (result != null) result.completeExceptionally(error) else logger.error(error.message.orEmpty())
        }

        private fun stoppedError(cause: Throwable? = null): IllegalStateException =
            IllegalStateException("Aura Logger is stopped; could not $description", cause)
    }
}
