package io.ably.lib.uts.unit.liveobjects

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
 * Derived from UTS `objects/unit/live_object_subscribe.md` (RTLO4b, RTLO4b3, RTLO4b4c1, RTLO4b4c3a,
 * RTLO4b4c3c, RTLO4b4d, RTLO4b4e, RTLO4b6, RTLO4b7) — registering a listener for LiveObject data updates.
 *
 * The spec subscribes through the **public** `instance.subscribe(...)` (RTINS16) and cites the *internal*
 * `RTLO4b` `LiveObjectUpdate` diff (fields `update` / `noop` / `objectMessage` / `tombstone`). In ably-java
 * the public event is [InstanceSubscriptionEvent], which exposes only `getObject()` and `getMessage()` — no
 * diff / `noop` / `tombstone` accessors (mapping §8). So:
 *  - "listener fired N times" and "returns a Subscription" translate directly (`events.size`, `Subscription`).
 *  - The noop case (RTLO4b4c1) is observed only as *suppressed delivery* (no event), since there is no public
 *    `update.noop` flag to assert — adapted, recorded in deviations.md.
 *  - The tombstone diff flag (RTLO4b4c3c / RTLO4b4e) is observed through `message.operation.action ==
 *    OBJECT_DELETE` (the spec itself prescribes this public-API proxy), not a `tombstone` boolean.
 *
 * All tests use `setupSyncedChannel` (Helpers.kt), which needs the SDK's OBJECT_SYNC processing +
 * `RealtimeObject.get()` — still TODO — so these compile now and run once that lands (translate-only).
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
    fun `RTLO4b7 - subscribe returns Subscription with unsubscribe method`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val instance = root.get("score").instance()!!.asLiveCounter()

        val sub: Subscription = instance.subscribe(InstanceListener { })

        assertNotNull(sub) // IS Subscription
        // `sub.unsubscribe IS Function` -> the Subscription exposes a callable unsubscribe(); calling it is a no-op.
        sub.unsubscribe()
    }

    /**
     * @UTS objects/unit/RTLO4b7/subscription-unsubscribe-stops-delivery-0
     */
    @Test
    fun `RTLO4b7 - Subscription unsubscribe stops delivery`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        val sub = instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "01", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        sub.unsubscribe()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "02", "remote"))),
        )

        assertEquals(1, updates.size)
    }

    /**
     * @UTS objects/unit/RTLO4b7/subscription-unsubscribe-idempotent-0
     */
    @Test
    fun `RTLO4b7 - Subscription unsubscribe is idempotent`() = runTest {
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
        val instance = root.get("score").instance()!!.asLiveCounter()
        instance.subscribe(InstanceListener { updates.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "01", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        // Serial "02" passes the newness check (RTLO4a6); the zero increment is the noop.
        // DEVIATION (RTLO4b4c1): the spec asserts on the internal `LiveObjectUpdate.noop` flag. The public
        // InstanceSubscriptionEvent has no `noop` accessor (mapping §8), so the noop is observed only as
        // suppressed delivery — the listener is not fired a second time. See deviations.md.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 0, "02", "remote"))),
        )

        assertEquals(1, updates.size)
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
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), "99", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(1, updates.size)
    }

    /**
     * @UTS objects/unit/RTLO4b4c3c/tombstone-deregisters-listeners-0
     */
    @Test
    fun `RTLO4b4c3c - tombstone update deregisters all Instance subscribe listeners`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val updatesA = mutableListOf<InstanceSubscriptionEvent>()
        val updatesB = mutableListOf<InstanceSubscriptionEvent>()
        val instance = root.get("score").instance()!!.asLiveCounter()
        instance.subscribe(InstanceListener { updatesA.add(it) })
        instance.subscribe(InstanceListener { updatesB.add(it) })

        // Send an OBJECT_DELETE which causes a tombstone.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "50", "remote"))),
        )
        pollUntil(5.seconds) { updatesA.size >= 1 }

        // Both listeners should have received the tombstone update. The tombstone is identified by the
        // OBJECT_DELETE action (spec-prescribed public-API proxy for the internal `tombstone` flag).
        assertEquals(1, updatesA.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, updatesA[0].getMessage()!!.operation.action)
        assertEquals(1, updatesB.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, updatesB[0].getMessage()!!.operation.action)

        // Send another update — listeners should have been deregistered by the tombstone.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 3, "51", "remote"))),
        )

        assertEquals(1, updatesA.size)
        assertEquals(1, updatesB.size)
    }

    /**
     * @UTS objects/unit/RTLO4b4d/update-has-object-message-0
     */
    @Test
    fun `RTLO4b4d - InstanceSubscriptionEvent message is populated from source ObjectMessage`() = runTest {
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
