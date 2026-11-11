package com.usatov.aura.logger.core

import kotlinx.coroutines.flow.Flow

/**
 * Tracks app events, stores them locally and gives access to stored events and statistics.
 *
 * The Android entry point is the `EventTrackerSDK` object of the `sdk` module, which implements
 * this interface. Depend on this interface in client code so that it can be replaced with a fake
 * in tests.
 *
 * ## Lifecycle
 * The tracker must be initialised once per process (on Android: `EventTrackerSDK.init`).
 * - [track] works before initialisation: events are kept in a bounded in-memory buffer and are
 *   written when the tracker is initialised.
 * - [markSeen] is ignored before initialisation (with a warning).
 * - Every `suspend` function throws [IllegalStateException] when called before initialisation.
 * - [changes] can be collected at any time; it starts emitting after initialisation.
 *
 * ## Threading
 * All functions are thread-safe. Non-suspending functions never block the caller: they validate
 * their input and hand the work over to a single serial background queue. Suspending functions
 * run their work on a background dispatcher and are safe to call from the main thread. They are
 * cancellable; cancelling the caller does not undo work that has already been queued.
 *
 * ## Ordering and consistency
 * Writes ([track], [markSeen], [clearAll]) are executed in the order in which they were called.
 * Events are ordered newest first by [TrackedEvent.timestamp]; events with equal timestamps are
 * ordered by the order in which [track] was called (later call first).
 * Reads see every write that was queued before the read started.
 */
public interface EventTracker {

    /**
     * Records an event.
     *
     * The id and the timestamp are generated synchronously at call time, so the event keeps its
     * position even if it is written later. The write itself happens on the background queue;
     * this function never blocks and never throws. Concurrent and rapid calls (hundreds per
     * second) are all stored.
     *
     * Invalid input does not throw: an event whose name or properties violate
     * [EventTrackerLimits] is dropped and a warning is logged. If writing to the database fails,
     * the event is lost and an error is logged.
     *
     * Events that are still queued when the process dies are lost.
     *
     * @param name event name, for example `"button_clicked"`. Must not be blank.
     * @param properties optional key-value pairs, for example `mapOf("button_id" to "submit")`.
     */
    public fun track(name: String, properties: Map<String, String> = emptyMap())

    /**
     * Records an event with typed properties converted by [codec].
     *
     * Behaves exactly like [track] with a map. [PropertiesCodec.encode] is called synchronously
     * on the calling thread; if it throws, the event is dropped and a warning is logged.
     */
    public fun <T> track(name: String, properties: T, codec: PropertiesCodec<T>)

    /**
     * Marks events as seen by the user, which makes them eligible for deletion by the operations
     * protected by [UnseenEventsProtection].
     *
     * Call it for events that were actually displayed. Unknown ids (for example, of deleted
     * events) and events that are already seen are ignored. Never blocks and never throws; the
     * update goes through the same serial queue as [track].
     */
    public fun markSeen(eventIds: Collection<String>)

    /**
     * Returns the newest stored events, newest first.
     *
     * @param limit maximum number of events to return; must be positive.
     * @throws IllegalArgumentException if [limit] is not positive.
     * @throws IllegalStateException if the tracker is not initialised.
     */
    public suspend fun getRecentEvents(limit: Int): List<TrackedEvent>

    /**
     * Returns one page of stored events, newest first.
     *
     * Pass `null` as [cursor] for the first (newest) page and [EventPage.nextCursor] of the
     * previous page for the following ones.
     *
     * @param pageSize maximum number of events in the page; must be positive.
     * @throws IllegalArgumentException if [pageSize] is not positive.
     * @throws IllegalStateException if the tracker is not initialised.
     */
    public suspend fun getEventsPage(cursor: EventCursor?, pageSize: Int): EventPage

    /**
     * Returns current statistics: total count, today's count and counts per day.
     *
     * @throws IllegalStateException if the tracker is not initialised.
     */
    public suspend fun getStats(): EventStats

    /**
     * Deletes all stored events, except unseen events when
     * [UnseenEventsProtection.onClearAll] is enabled.
     *
     * Runs on the serial write queue: events tracked before this call are written first and then
     * deleted (if not protected); events tracked after it are kept. Events still in the
     * pre-initialisation buffer cannot exist here, because this function requires initialisation.
     *
     * @return number of deleted events.
     * @throws IllegalStateException if the tracker is not initialised.
     */
    public suspend fun clearAll(): Int

    /**
     * Replaces the retention policy at runtime.
     *
     * Persists [config] (it is also used by later background cleanups, including those in a new
     * process) and schedules a one-time cleanup that applies it soon, without waiting for the next
     * periodic run. Repeated calls replace the pending one-time cleanup. Returns after [config]
     * has been persisted.
     *
     * @throws IllegalStateException if the tracker is not initialised.
     */
    public suspend fun updateConfig(config: EventTrackerConfig)

    /**
     * Emits [Unit] every time stored events change: an event is written, marked seen or deleted
     * (by [clearAll] or by a cleanup).
     *
     * The flow is conflated: rapid changes can be reported by a single emission. It does not emit
     * an initial value, never completes and does not fail. Collecting it before initialisation is
     * allowed; emissions start after initialisation. Changes made by another process are not
     * reported.
     */
    public val changes: Flow<Unit>
}
