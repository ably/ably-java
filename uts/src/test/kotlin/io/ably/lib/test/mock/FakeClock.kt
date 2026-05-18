package io.ably.lib.test.mock

import io.ably.lib.util.Clock
import io.ably.lib.util.NamedTimer
import io.ably.lib.util.TimerInstance
import java.util.TimerTask
import kotlin.time.Duration

class FakeClock(initialTimeMs: Long = 0L) : Clock {
    @Volatile private var time = initialTimeMs
    private val timers = mutableMapOf<String, FakeNamedTimer>()

    override fun currentTimeMillis() = time

    override fun newTimer(name: String): NamedTimer {
        val t = FakeNamedTimer(name)
        timers[name] = t
        return t
    }

    fun advance(ms: Long) {
        time += ms
        timers.values.forEach { it.fireDue(time) }
    }

    fun advance(time: Duration) = advance(time.inWholeMilliseconds)

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
}
