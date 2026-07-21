package io.ably.lib.uts.unit.liveobjects

import com.google.gson.JsonObject
import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/live_object_subscribe.md`
 * (RTLO4b, RTLO4b3, RTLO4b4c1, RTLO4b4c3a, RTLO4b4c3c, RTLO4b4d, RTLO4b4e, RTLO4b6, RTLO4b7) — the public
 * `LiveObject#subscribe` surface, exercised via `Instance#subscribe` (RTINS16).
 *
 * ably-java subscribes through the public `instance.subscribe(...)`, delivering an
 * `InstanceSubscriptionEvent` that exposes only `getObject()` + `getMessage()` (mapping §8). The internal
 * `LiveObjectUpdate` diff fields (`update` / `noop` / `tombstone`) are **not** public, so:
 *  - "listener fired N times" / "returns a `Subscription`" translate directly;
 *  - the *noop* case (RTLO4b4c1) is observable only as **suppressed delivery** — proven with the
 *    negative-assertion quiescence barrier (a follow-up observable message awaited via a separate control
 *    listener), exactly as the spec now frames it;
 *  - the *tombstone* case (RTLO4b4c3c / RTLO4b4e) is identified via the public
 *    `message.operation.action == OBJECT_DELETE`, not an internal `tombstone` flag.
 *
 * See `deviations.md` for the per-test notes. (The former SI-2 stale-serial issue — inbound map serials
 * sorting below the pool baseline `"t:0"` — was fixed upstream in the spec; the affected tests now use
 * `"t:1"` and run faithfully.)
 */
class LiveObjectSubscribeTest {

    /**
     * @UTS objects/unit/RTLO4b/subscribe-receives-updates-0
     */
    @Test
    fun `RTLO4b - subscribe registers listener for data updates`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        val sub: Subscription = instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertNotNull(sub) // IS Subscription
        assertEquals(1, updates.size)
    }

    /**
     * @UTS objects/unit/RTLO4b7/subscribe-returns-subscription-0
     */
    @Test
    fun `RTLO4b7 - subscribe returns Subscription with unsubscribe`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val instance = root.get("score").instance()!!.asLiveCounter()

        val sub = instance.subscribe(InstanceListener { })

        assertNotNull(sub) // IS Subscription
        // DEVIATION (RTLO4b7 "sub.unsubscribe IS Function"): a JS-ism. In ably-java `unsubscribe()` is a
        // method declared on the `Subscription` interface, guaranteed at compile time — the runtime
        // "is it a function" check is a static-type tautology. We invoke it to show it exists. See deviations.md.
        sub.unsubscribe()
    }

    /**
     * @UTS objects/unit/RTLO4b7/subscription-unsubscribe-stops-delivery-0
     */
    @Test
    fun `RTLO4b7 - unsubscribe stops delivery`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val control = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        val sub = instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "01", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        sub.unsubscribe()

        // Negative-assertion quiescence (standard_test_pool.md): a control listener that WILL fire on the
        // same dispatch as the message under test; await it, then assert `updates` is unchanged.
        instance.subscribe(InstanceListener { control.add(it) })
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "02", "remote"))),
        )
        pollUntil(5.seconds) { control.size >= 1 }

        assertEquals(1, updates.size)
    }

    /**
     * @UTS objects/unit/RTLO4b7/subscription-unsubscribe-idempotent-0
     */
    @Test
    fun `RTLO4b7 - unsubscribe is idempotent`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val instance = root.get("score").instance()!!.asLiveCounter()
        val sub = instance.subscribe(InstanceListener { })

        // No error thrown — both calls complete without error.
        sub.unsubscribe()
        sub.unsubscribe()
    }

    /**
     * @UTS objects/unit/RTLO4b4c1/noop-no-trigger-0
     */
    @Test
    fun `RTLO4b4c1 - noop update does not trigger listener`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val control = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        instance.subscribe(InstanceListener { updates.add(it) })

        // "01" — a real increment, fires the listener.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "01", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        // "02" — a COUNTER_INC with NO `number` field: the real RTLC9h/RTLO4b4c1 noop branch (a
        // `number: 0` would exist per RTLC9g and produce a non-noop update). No Helpers builder produces
        // an empty counterInc, so build the raw wire ObjectMessage inline (action code 4 == COUNTER_INC).
        val noopMsg = JsonObject().apply {
            addProperty("serial", "02")
            addProperty("siteCode", "remote")
            add(
                "operation",
                JsonObject().apply {
                    addProperty("action", 4) // COUNTER_INC wire code (Helpers Action.COUNTER_INC)
                    addProperty("objectId", "counter:score@1000")
                    add("counterInc", JsonObject()) // empty -> no `number` -> noop
                },
            )
        }
        mockWs.sendToClient(buildObjectMessage("test", listOf(noopMsg)))

        // Negative-assertion quiescence: drive a follow-up "03" increment awaited via a SEPARATE control
        // listener. "03" is dispatched after the noop "02" on the same channel, so once the control fires
        // the noop has certainly been processed. Kept separate so it does not inflate `updates`.
        val controlSub = instance.subscribe(InstanceListener { control.add(it) })
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 3, "03", "remote"))),
        )
        pollUntil(5.seconds) { control.size >= 1 }
        controlSub.unsubscribe()

        // The noop "02" produced no update; the listener fired only for "01" and "03".
        assertEquals(2, updates.size)
    }

    /**
     * @UTS objects/unit/RTLO4b6/subscribe-no-side-effects-0
     */
    @Test
    fun `RTLO4b6 - subscribe has no side effects`() = runTest {
        val (_, channel, root, _) = setupSyncedChannel("test")
        val stateBefore = channel.state
        val instance = root.get("score").instance()!!.asLiveCounter()

        instance.subscribe(InstanceListener { })

        assertEquals(stateBefore, channel.state)
    }

    /**
     * @UTS objects/unit/RTLO4b/subscribe-map-update-0
     */
    @Test
    fun `RTLO4b - subscribe on LiveMap receives update`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.instance()!!.asLiveMap()
        instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        // RTLO4b: a MAP_SET on an existing key emits one LiveMapUpdate (key -> "updated").
        assertEquals(1, updates.size)
    }

    /**
     * @UTS objects/unit/RTLO4b4c3c/tombstone-deregisters-listeners-0
     */
    @Test
    fun `RTLO4b4c3c - tombstone deregisters all listeners`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updatesA = mutableListOf<InstanceSubscriptionEvent>()
        val updatesB = mutableListOf<InstanceSubscriptionEvent>()
        val control = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        instance.subscribe(InstanceListener { updatesA.add(it) })
        instance.subscribe(InstanceListener { updatesB.add(it) })

        // OBJECT_DELETE tombstones the counter; both listeners receive the tombstone update first.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "50", "remote"))),
        )
        pollUntil(5.seconds) { updatesA.size >= 1 }
        pollUntil(5.seconds) { updatesB.size >= 1 }

        assertEquals(1, updatesA.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, updatesA[0].getMessage()!!.operation.action)
        assertEquals(1, updatesB.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, updatesB[0].getMessage()!!.operation.action)

        // Quiescence via a SEPARATE LIVE object (a tombstoned object ignores further ops, RTLC7e, so it
        // can't be a control): subscribe a control on map:profile@1000 and drive an observable update on
        // it AFTER the message under test. Messages process in order, so once control fires "51" is done.
        val controlInst = root.get("profile").instance()!!.asLiveMap()
        controlInst.subscribe(InstanceListener { control.add(it) })
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 3, "51", "remote"))),
        )
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:profile@1000", "quiescence_probe", dataString("x"), "52", "remote")),
            ),
        )
        pollUntil(5.seconds) { control.size >= 1 }

        // Control delivered, so any still-registered original listener would also have run.
        assertEquals(1, updatesA.size)
        assertEquals(1, updatesB.size)
    }

    /**
     * @UTS objects/unit/RTLO4b4d/update-has-object-message-0
     */
    @Test
    fun `RTLO4b4d - event message populated from source ObjectMessage`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(1, updates.size)
        val message = updates[0].getMessage()
        assertNotNull(message)
        assertEquals("99", message!!.serial)
        assertEquals("remote", message.siteCode)
        assertEquals(ObjectOperationAction.COUNTER_INC, message.operation.action)
        assertEquals("counter:score@1000", message.operation.objectId)
    }

    /**
     * @UTS objects/unit/RTLO4b4e/tombstone-flag-true-0
     */
    @Test
    fun `RTLO4b4e - tombstone update identified by OBJECT_DELETE action`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "50", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(1, updates.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, updates[0].getMessage()!!.operation.action)
    }

    /**
     * @UTS objects/unit/RTLO4b4e/tombstone-flag-false-0
     */
    @Test
    fun `RTLO4b4e - normal update carries non-tombstone action`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(1, updates.size)
        assertEquals(ObjectOperationAction.COUNTER_INC, updates[0].getMessage()!!.operation.action)
    }
}
