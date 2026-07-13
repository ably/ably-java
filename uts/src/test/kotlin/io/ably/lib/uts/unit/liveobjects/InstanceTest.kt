package io.ably.lib.uts.unit.liveobjects

import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.instance.Instance
import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/instance.md` (RTINS1–RTINS16) — the typed `Instance` view of a resolved
 * LiveObject / primitive.
 *
 * ably-java implements the typed-SDK variant (RTTS), so the spec's single polymorphic `Instance` is
 * partitioned: `id`, `value`, `get`, `set`, `subscribe`, … live on `LiveMapInstance` /
 * `LiveCounterInstance` / the primitive instances, reached through `as*` casts. Unlike `PathObject`, an
 * `Instance` cast **fails fast with `IllegalStateException`** on a type mismatch (RTTS9d). Consequences
 * for translation, recorded in `deviations.md`:
 *  - "wrong-type write/subscribe → ErrorInfo 92007" (RTINS12d/14d/16c) surfaces instead as the `as*` cast
 *    throwing `IllegalStateException` — there is no typed view on which to even call the wrong method.
 *  - `value()` on a map / `size()` on a counter (RTINS4d/RTINS9c) are not expressible — those accessors are
 *    partitioned off the wrong-typed view.
 *  - `compact()` is not implemented (RTTS7d); `compactJson()` is the supported snapshot (RTINS10).
 */
class InstanceTest {

    /**
     * @UTS objects/unit/RTINS3/id-returns-objectid-0
     */
    @Test
    fun `RTINS3 - id property returns objectId`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val counterInst = root.get("score").instance()
        assertEquals("counter:score@1000", counterInst!!.asLiveCounter().id)

        val mapInst = root.get("profile").instance()
        assertEquals("map:profile@1000", mapInst!!.asLiveMap().id)
    }

    /**
     * @UTS objects/unit/RTINS4/value-counter-0
     */
    @Test
    fun `RTINS4 - value returns counter number or primitive`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val counterInst = root.get("score").instance()
        assertEquals(100.0, counterInst!!.asLiveCounter().value())

        // DEVIATION (RTINS4d): spec asserts `map_inst.value() == null`, but ably-java's typed
        // LiveMapInstance has no value() accessor (partitioned per RTTS10) — "value() on a map" is not
        // expressible. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTINS5/get-wraps-entry-0
     */
    @Test
    fun `RTINS5 - get returns Instance wrapping entry value`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val rootInst = root.instance()!!.asLiveMap()

        val nameInst = rootInst.get("name")
        assertNotNull(nameInst) // RTINS5c: IS Instance
        assertEquals("Alice", nameInst!!.asString().value())

        val scoreInst = rootInst.get("score")
        assertEquals("counter:score@1000", scoreInst!!.asLiveCounter().id)

        val nullInst = rootInst.get("nonexistent")
        assertNull(nullInst)
    }

    /**
     * @UTS objects/unit/RTINS6/entries-yields-instances-0
     */
    @Test
    fun `RTINS6 - entries returns key Instance pairs`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val rootInst = root.instance()!!.asLiveMap()

        val entries = mutableMapOf<String, Instance>()
        for ((key, inst) in rootInst.entries()) {
            entries[key] = inst
        }

        assertEquals(7, entries.size)
        assertNotNull(entries["name"]) // IS Instance
        assertEquals("Alice", entries["name"]!!.asString().value())
    }

    /**
     * @UTS objects/unit/RTINS9/size-0
     */
    @Test
    fun `RTINS9 - size returns non-tombstoned count`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val rootInst = root.instance()!!.asLiveMap()
        assertEquals(7L, rootInst.size())

        // DEVIATION (RTINS9c): spec asserts `counter_inst.size() == null`, but ably-java's typed
        // LiveCounterInstance has no size() accessor (partitioned per RTTS10) — not expressible.
        // See deviations.md.
    }

    /**
     * @UTS objects/unit/RTINS10/compact-0
     */
    @Test
    fun `RTINS10 - compact recursively compacts`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val rootInst = root.instance()!!.asLiveMap()

        // DEVIATION (RTINS10): ably-java does not implement `compact()` (RTTS7d); `compactJson()` is the
        // supported recursively-compacted snapshot. Assertions navigate the JsonObject. See deviations.md.
        val snapshot = rootInst.compactJson()

        assertEquals("Alice", snapshot.get("name").asString)
        assertEquals(100, snapshot.get("score").asInt)
        assertEquals("alice@example.com", snapshot.getAsJsonObject("profile").get("email").asString)
    }

    /**
     * @UTS objects/unit/RTINS12/set-delegates-0
     */
    @Test
    fun `RTINS12 - set delegates to LiveMap set`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val rootInst = root.instance()!!.asLiveMap()

        rootInst.set("name", LiveMapValue.of("Bob")).await()

        assertEquals("Bob", root.get("name").asString().value())
    }

    /**
     * @UTS objects/unit/RTINS12d/set-non-map-throws-0
     */
    @Test
    fun `RTINS12d - set on non-LiveMap throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()

        // DEVIATION (RTINS12d): spec expects `set()` to fail with ErrorInfo 92007. ably-java has no `set`
        // on a non-map typed view; the failure surfaces as the `asLiveMap()` cast throwing
        // IllegalStateException (RTTS9d). See deviations.md.
        assertFailsWith<IllegalStateException> { counterInst!!.asLiveMap() }
    }

    /**
     * @UTS objects/unit/RTINS13/remove-delegates-0
     */
    @Test
    fun `RTINS13 - remove delegates to LiveMap remove`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val rootInst = root.instance()!!.asLiveMap()

        rootInst.remove("name").await()

        assertNull(root.get("name").asString().value())
    }

    /**
     * @UTS objects/unit/RTINS14/increment-delegates-0
     */
    @Test
    fun `RTINS14 - increment delegates to LiveCounter increment`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()!!.asLiveCounter()

        counterInst.increment(25).await()

        assertEquals(125.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTINS14d/increment-non-counter-throws-0
     */
    @Test
    fun `RTINS14d - increment on non-LiveCounter throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val mapInst = root.instance()

        // DEVIATION (RTINS14d): spec expects ErrorInfo 92007; ably-java has no `increment` on a non-counter
        // typed view, so the failure surfaces as `asLiveCounter()` throwing IllegalStateException (RTTS9d).
        // See deviations.md.
        assertFailsWith<IllegalStateException> { mapInst!!.asLiveCounter() }
    }

    /**
     * @UTS objects/unit/RTINS15/decrement-delegates-0
     */
    @Test
    fun `RTINS15 - decrement delegates to LiveCounter decrement`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()!!.asLiveCounter()

        counterInst.decrement(10).await()

        assertEquals(90.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTINS14a/increment-default-0
     */
    @Test
    fun `RTINS14a - increment defaults to 1`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()!!.asLiveCounter()

        counterInst.increment().await()

        assertEquals(101.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTINS15a/decrement-default-0
     */
    @Test
    fun `RTINS15a - decrement defaults to 1`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()!!.asLiveCounter()

        counterInst.decrement().await()

        assertEquals(99.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTINS16/subscribe-receives-events-0
     */
    @Test
    fun `RTINS16 - subscribe receives InstanceSubscriptionEvent`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()!!.asLiveCounter()
        val events = mutableListOf<InstanceSubscriptionEvent>()
        val sub: Subscription = counterInst.subscribe(InstanceListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertNotNull(sub) // IS Subscription
        assertEquals(1, events.size)
        assertNotNull(events[0].getObject()) // IS Instance
        assertEquals("counter:score@1000", events[0].getObject().asLiveCounter().id)
    }

    /**
     * @UTS objects/unit/RTINS16c/subscribe-primitive-throws-0
     */
    @Test
    fun `RTINS16c - subscribe on primitive throws`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val nameInst = root.instance()!!.asLiveMap().get("name")

        // DEVIATION (RTINS16c): spec expects ErrorInfo 92007. ably-java's primitive instances expose no
        // `subscribe`; obtaining a subscribable (map/counter) view of a primitive fails fast with
        // IllegalStateException (RTTS9d). See deviations.md.
        assertFailsWith<IllegalStateException> { nameInst!!.asLiveCounter() }
    }

    /**
     * @UTS objects/unit/RTINS16e2/subscription-event-message-0
     */
    @Test
    fun `RTINS16e2 - InstanceSubscriptionEvent contains ObjectMessage`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val rootInst = root.instance()!!.asLiveMap()
        val events = mutableListOf<InstanceSubscriptionEvent>()
        rootInst.subscribe(InstanceListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // RTINS16e1: event.object is an Instance wrapping the LiveObject (the root map).
        assertNotNull(events[0].getObject())
        assertEquals("root", events[0].getObject().asLiveMap().id)
        // RTINS16e2: event.message is the PublicAPI::ObjectMessage derived from the triggering op.
        val message = events[0].getMessage()
        assertNotNull(message)
        assertEquals("test", message!!.channel)
        assertEquals(ObjectOperationAction.MAP_SET, message.operation.action)
        assertEquals("root", message.operation.objectId)
        assertEquals("name", message.operation.mapSet!!.key)
    }

    /**
     * @UTS objects/unit/RTINS16f/subscribe-returns-subscription-0
     */
    @Test
    fun `RTINS16f - subscribe returns Subscription for deregistration`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()!!.asLiveCounter()
        val events = mutableListOf<InstanceSubscriptionEvent>()
        val sub = counterInst.subscribe(InstanceListener { events.add(it) })
        sub.unsubscribe()

        // Quiescence control (standard_test_pool.md "Negative-assertion quiescence"): a second,
        // still-subscribed listener on the same counter instance that WILL fire on the same dispatch.
        val controlEvents = mutableListOf<InstanceSubscriptionEvent>()
        counterInst.subscribe(InstanceListener { controlEvents.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )

        // Await the control listener; once it has fired, the unsubscribed listener would also have fired
        // had it remained subscribed — THEN assert its count is unchanged.
        pollUntil(5.seconds) { controlEvents.size >= 1 }
        assertEquals(0, events.size)
    }

    /**
     * @UTS objects/unit/RTINS16g/subscription-follows-identity-0
     */
    @Test
    fun `RTINS16g - Instance subscription follows identity not path`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()!!.asLiveCounter()
        val events = mutableListOf<InstanceSubscriptionEvent>()
        counterInst.subscribe(InstanceListener { events.add(it) })

        // Re-point root.score at a different counter, then increment the original counter by identity.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), remoteSerial(0), "remote")),
            ),
        )
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertTrue(events.size >= 1)
        // RTINS16e1: assert the delivered event's object id (not the pre-existing handle's).
        assertNotNull(events[0].getObject())
        assertEquals("counter:score@1000", events[0].getObject().asLiveCounter().id)
    }

    /**
     * @UTS objects/unit/RTINS16h/subscribe-no-side-effects-0
     */
    @Test
    fun `RTINS16h - subscribe has no side effects`() = runTest {
        val (_, channel, root, _) = setupSyncedChannel("test")
        val counterInst = root.get("score").instance()!!.asLiveCounter()
        val channelStateBefore = channel.state

        counterInst.subscribe(InstanceListener { })

        assertEquals(channelStateBefore, channel.state)
    }
}
