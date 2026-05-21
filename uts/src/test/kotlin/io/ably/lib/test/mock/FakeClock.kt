package io.ably.lib.test.mock

import io.ably.lib.util.Clock
import io.ably.lib.util.NamedTimer
import io.ably.lib.util.TimerInstance
import java.util.TimerTask
import kotlin.time.Duration

/**
 * Virtual clock for deterministic time control in unit tests.
 *
 * Install via `enableFakeTimers(fakeClock)` inside a `TestRealtimeClient` or `TestRestClient` block.
 * Time only advances when [advance] is called; timer callbacks fire synchronously within that call.
 */
class FakeClock(initialTimeMs: Long = 0L) : Clock {
    @Volatile private var time = initialTimeMs
    private val timers = mutableMapOf<String, FakeNamedTimer>()
    private val waiters = mutableListOf<Waiter>()

    override fun currentTimeMillis() = time

    override fun newTimer(name: String): NamedTimer {
        val t = FakeNamedTimer(name)
        timers[name] = t
        return t
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    override fun waitOn(target: Any, timeout: Long) {
        synchronized(waiters) {
            waiters.add(Waiter(target as Object, time + timeout))
        }
        (target as Object).wait()
    }

    /** Advance virtual time by [ms] milliseconds, firing any timers that become due. */
    fun advance(ms: Long) {
        time += ms
        timers.values.forEach { it.fireDue(time) }
        val due = synchronized(waiters) {
            waiters.filter { it.fireAt <= time }.also {
                waiters.removeIf { it.fireAt <= time }
            }
        }
        // notifyAll() requires holding the target's monitor.
        due.forEach { waiter ->
            synchronized(waiter.target) {
                waiter.target.notifyAll()
            }
        }
    }

    /** Advance virtual time by [time], firing any timers that become due. */
    fun advance(time: Duration) = advance(time.inWholeMilliseconds)

    /** Number of tasks currently scheduled on the named timer — useful for asserting retry state. */
    fun pendingTaskCount(timerName: String) = timers[timerName]?.pendingCount ?: 0

    inner class FakeNamedTimer(val name: String) : NamedTimer {
        private val pending = mutableListOf<Scheduled>()
        val pendingCount get() = pending.size

        override fun schedule(task: TimerTask, delayMs: Long): TimerInstance {
            val s = Scheduled(task, time + delayMs)
            pending += s
            pending.sortBy { it.fireAt }
            return TimerInstance { task.cancel(); pending -= s }
        }

        override fun cancel() {
            pending.forEach { it.task.cancel() }
            pending.clear()
        }

        fun fireDue(now: Long) {
            val due = pending.filter { it.fireAt <= now }
            pending -= due.toSet()
            due.forEach { it.task.run() }
        }
    }

    class Scheduled(val task: TimerTask, val fireAt: Long)

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    class Waiter(val target: Object, val fireAt: Long)
}
