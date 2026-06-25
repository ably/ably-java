package io.ably.lib.uts.unit.liveobjects

import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.types.AblyException
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.MockEvent
import io.ably.lib.uts.infra.unit.MockWebSocket
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/live_counter_api.md` (RTLC5, RTLC11–RTLC13) — the **public** read/write
 * surface of a LiveCounter via `PathObject` / `Instance`.
 *
 * ably-java implements the typed-SDK variant (RTTS): the spec's polymorphic `root.get("score")` is a base
 * `PathObject`; counter reads/writes live on `LiveCounterPathObject`, reached via `asLiveCounter()`. So
 * `counter.value()` → `root.get("score").asLiveCounter().value(): Double?` (assert `100.0`), and
 * `counter.increment(n)` / `.decrement(n)` → `…asLiveCounter().increment(n)` returning
 * `CompletableFuture<Void>` (`.await()`).
 *
 * Two translation notes (recorded in `deviations.md`):
 *  - The "increment sends a v6 COUNTER_INC wire message" / "decrement negates the amount" assertions
 *    (RTLC12e2/e3/e5, RTLC13b) inspect the **outbound wire `ObjectMessage`** (`captured.state[0].operation`).
 *    That wire form (`WireObjectMessage` / `WireObjectOperation`) is `internal` to `:liveobjects` and not part
 *    of the public API, so it is read by reflection off the captured `ProtocolMessage.state` (the same
 *    reflection pattern `helpers.kt` / `PublicObjectMessageTest.kt` already use). The observable public-API
 *    outcome (counter value after the await) is asserted alongside where the spec provides it.
 *  - RTLC12e1 feeds non-`Number` increment amounts and expects `40003`. ably-java's
 *    `increment(@NotNull Number)` signature rejects every one of those at compile time, so the cases are not
 *    expressible as runtime assertions — see deviations.md.
 *
 * All tests use `setupSyncedChannel` (helpers.kt), which needs the SDK's OBJECT_SYNC processing +
 * `RealtimeObject.get()` — still TODO — so these compile now and run once that lands (translate-only).
 */
class LiveCounterApiTest {

    /**
     * @UTS objects/unit/RTLC5/value-returns-data-0
     */
    @Test
    fun `RTLC5 - value returns current counter data`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val counter = root.get("score")
        assertEquals(100.0, counter.asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTLC12/increment-sends-counter-inc-0
     */
    @Test
    fun `RTLC12 - increment sends v6 COUNTER_INC message`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(25).await()

        // DEVIATION (RTLC12e2/e3/e5): the spec asserts on the outbound wire ObjectMessage
        // (captured.state[0].operation.action/objectId/counterInc.number). The wire form
        // (WireObjectMessage/WireObjectOperation) is internal to :liveobjects; read it by reflection off the
        // captured ProtocolMessage.state. See deviations.md.
        val captured = capturedObjectMessages(mockWs)
        assertEquals(1, captured.size)
        val op = wireOperation(captured[0].state!![0]!!)
        assertEquals("CounterInc", wireActionName(op))
        assertEquals("counter:score@1000", wireObjectId(op))
        assertEquals(25.0, wireCounterIncNumber(op))
    }

    /**
     * @UTS objects/unit/RTLC12/increment-applies-locally-0
     */
    @Test
    fun `RTLC12 - increment applies locally after ACK`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(50).await()

        assertEquals(150.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTLC12e1/increment-non-number-0
     */
    @Test
    fun `RTLC12e1 - increment with non-number throws`() = runTest {
        setupSyncedChannel("test")

        // DEVIATION (RTLC12e1): spec calls `increment("not_a_number")` and expects ErrorInfo 40003. ably-java's
        // `LiveCounterPathObject.increment(@NotNull Number)` rejects a String at compile time, so the case is
        // not expressible as a runtime assertion. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTLC13/decrement-negates-0
     */
    @Test
    fun `RTLC13 - decrement delegates to increment with negated amount`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().decrement(15).await()

        // DEVIATION (RTLC13b): the spec asserts the outbound wire counterInc.number == -15 (decrement is an
        // alias for increment with a negated amount). Read the internal wire form by reflection; see
        // deviations.md. The public value outcome is asserted directly.
        val captured = capturedObjectMessages(mockWs)
        assertEquals(-15.0, wireCounterIncNumber(wireOperation(captured[0].state!![0]!!)))
        assertEquals(85.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTLC11/counter-update-on-inc-0
     */
    @Test
    fun `RTLC11 - LiveCounterUpdate emitted on increment`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildCounterInc("counter:score@1000", 7, "99", "remote-site")),
            ),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        // DEVIATION (RTLC11b1): the spec reads `updates[0].message.operation.counterInc.number == 7`. ably-java's
        // public InstanceSubscriptionEvent carries no LiveCounterUpdate diff (no `update.amount`), but it does
        // expose the originating public ObjectMessage via getMessage(); assert the counterInc on that. See
        // deviations.md.
        val message = updates[0].getMessage()!!
        assertEquals(ObjectOperationAction.COUNTER_INC, message.operation.action)
        assertEquals(7.0, message.operation.counterInc!!.number)
    }

    /**
     * @UTS objects/unit/RTLC12e1/increment-invalid-amounts-table-0
     */
    @Test
    fun `RTLC12e1 - Table-driven invalid increment amounts`() = runTest {
        // DEVIATION (RTLC12e1): the table feeds null / NaN / ±Infinity / String / Boolean / array / object as
        // the increment amount, each expecting ErrorInfo 40003. ably-java's `increment(@NotNull Number)`
        // signature makes the non-Number rows (null, String, Boolean, array, object) compile errors, so they
        // are not expressible. The numeric-but-invalid rows (NaN, +Infinity, -Infinity) ARE expressible as
        // runtime assertions and are exercised below. See deviations.md.
        val (_, _, root, _) = setupSyncedChannel("test")
        val counter = root.get("score").asLiveCounter()

        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            // The non-finite amounts must be rejected with 40003 (RTLC12e1).
            val ex = assertFailsWith<AblyException> { counter.increment(invalid).await() }
            assertEquals(40003, ex.errorInfo.code)
        }
    }
}

// ---------------------------------------------------------------------------
// Reflective access to the outbound wire ObjectMessage (internal to :liveobjects).
//
// The SDK serializes a published OBJECT operation into ProtocolMessage.state as internal
// WireObjectMessage instances. These are decoded back by the mock and recorded in mockWs.events. Their
// operation/action/objectId/counterInc fields are internal Kotlin data-class properties — addressable by
// their declared field names on the JVM (Kotlin `internal` is not name-mangled here), reached with
// isAccessible since they are package-private/internal. Mirrors the reflection pattern in helpers.kt.
// ---------------------------------------------------------------------------

private fun capturedObjectMessages(mockWs: MockWebSocket): List<ProtocolMessage> =
    mockWs.events
        .filterIsInstance<MockEvent.MessageFromClient>()
        .map { it.message }
        .filter { it.action == ProtocolMessage.Action.`object` }

private fun field(target: Any, name: String): Any? =
    target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

private fun wireOperation(wireObjectMessage: Any): Any =
    field(wireObjectMessage, "operation") ?: error("wire ObjectMessage has no operation")

private fun wireActionName(wireOperation: Any): String =
    (field(wireOperation, "action") as Enum<*>).name

private fun wireObjectId(wireOperation: Any): String? =
    field(wireOperation, "objectId") as String?

private fun wireCounterIncNumber(wireOperation: Any): Double {
    val counterInc = field(wireOperation, "counterInc") ?: error("wire operation has no counterInc")
    return (field(counterInc, "number") as Number).toDouble()
}
