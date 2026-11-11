package com.usatov.aura.logger.core.engine

import com.usatov.aura.logger.core.AuraLoggerSettings
import com.usatov.aura.logger.core.EventTrackerConfig
import com.usatov.aura.logger.core.spi.EventStore
import com.usatov.aura.logger.core.spi.NewEventRecord
import com.usatov.aura.logger.core.testing.FakeCleanupScheduler
import com.usatov.aura.logger.core.testing.FakeConfigStore
import com.usatov.aura.logger.core.testing.FakeEventStore
import com.usatov.aura.logger.core.testing.FakeTimeZoneProvider
import com.usatov.aura.logger.core.testing.RecordingLogger
import com.usatov.aura.logger.core.testing.SequentialIdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock

/** Real threads and dispatchers: thread safety, no data loss, non-blocking track. */
@Timeout(30)
class EngineConcurrencyTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pool = Executors.newFixedThreadPool(8)

    @AfterEach
    fun tearDown() {
        pool.shutdownNow()
        scope.cancel()
    }

    private fun newEngine(settings: AuraLoggerSettings = AuraLoggerSettings()) =
        EventTrackerEngine(SequentialIdGenerator(), FakeTimeZoneProvider(), RecordingLogger(), Clock.System, settings)

    private fun deps(store: EventStore) = EngineDependencies(
        config = EventTrackerConfig(),
        eventStore = store,
        configStore = FakeConfigStore(),
        cleanupScheduler = FakeCleanupScheduler(),
        clock = Clock.System,
        scope = scope,
        readDispatcher = Dispatchers.Default,
    )

    private fun runOnThreads(threads: Int, perThread: Int, action: (thread: Int, index: Int) -> Unit) {
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failure = AtomicReference<Throwable>()
        repeat(threads) { t ->
            pool.execute {
                try {
                    start.await()
                    repeat(perThread) { i -> action(t, i) }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(20, TimeUnit.SECONDS), "worker threads did not finish")
        failure.get()?.let { throw it }
    }

    @Test
    fun `concurrent tracking from many threads loses no events and ids are unique`() = runBlocking {
        val store = FakeEventStore()
        val engine = newEngine()
        engine.start(deps(store))

        runOnThreads(threads = 8, perThread = 250) { t, i -> engine.track("t${t}_e$i") }

        val events = engine.getRecentEvents(10_000)
        assertEquals(2000, events.size)
        assertEquals(2000, events.map { it.id }.toSet().size)
    }

    @Test
    fun `per-thread call order is preserved`() = runBlocking {
        val store = FakeEventStore()
        val engine = newEngine()
        engine.start(deps(store))

        runOnThreads(threads = 4, perThread = 200) { t, i -> engine.track("t$t", mapOf("i" to i.toString())) }
        engine.getStats()

        store.all.sortedBy { it.sequence }.groupBy { it.name }.values.forEach { perThread ->
            val indices = perThread.map { it.propertiesJson.substringAfter(":\"").substringBefore("\"").toInt() }
            assertEquals(indices.sorted(), indices)
        }
    }

    @Test
    fun `concurrent start calls start the engine exactly once`() = runBlocking {
        val store = FakeEventStore()
        val engine = newEngine()
        val started = AtomicInteger()

        runOnThreads(threads = 8, perThread = 5) { _, _ -> if (engine.start(deps(store))) started.incrementAndGet() }

        assertEquals(1, started.get())
        assertTrue(engine.isStarted)
    }

    @Test
    fun `tracking concurrently with start loses no events`() = runBlocking {
        val store = FakeEventStore()
        val engine = newEngine()
        val starter = Thread { Thread.sleep(5); engine.start(deps(store)) }

        starter.start()
        runOnThreads(threads = 4, perThread = 100) { t, i -> engine.track("t${t}_e$i") }
        starter.join()

        assertEquals(400, engine.getRecentEvents(10_000).size)
    }

    @Test
    fun `track does not block while the store is busy`() = runBlocking {
        val release = CountDownLatch(1)
        val store = object : EventStore by FakeEventStore() {
            override suspend fun insert(events: List<NewEventRecord>) {
                release.await(10, TimeUnit.SECONDS)
            }
        }
        val engine = newEngine()
        engine.start(deps(store))

        val startNanos = System.nanoTime()
        repeat(200) { engine.track("e$it") }
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        release.countDown()
        assertTrue(elapsedMillis < 1_000, "track blocked for $elapsedMillis ms")
    }
}
