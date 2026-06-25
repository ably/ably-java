package io.ably.lib.uts.unit.liveobjects

import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.liveobjects.path.PathObjectListener
import io.ably.lib.liveobjects.path.PathObjectSubscriptionEvent
import io.ably.lib.liveobjects.path.PathObjectSubscriptionOptions
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/path_object_subscribe.md` (RTPO19, RTO24, RTO25) — path-based
 * subscriptions on the typed `PathObject`.
 *
 * Translation rules (mapping §8): `pathObj.subscribe(closure)` becomes
 * `pathObj.subscribe(PathObjectListener { event -> … })` returning a `Subscription` synchronously;
 * `{ depth: n }` becomes `PathObjectSubscriptionOptions(n)` (non-positive depth throws AblyException
 * 400/40003, RTPO19c1a); `event.object` / `event.message` become `event.getObject()` /
 * `event.getMessage()`. Counter `value()` is `Double` (assert `107.0`). The DETACHED-precondition case
 * (RTPO19b) drives the channel to DETACHED via a server-sent DETACHED protocol message over the existing
 * mock (the shared helper's mock does not respond to DETACH), then asserts the synchronous failure.
 *
 * All tests use `setupSyncedChannel` (helpers.kt), which needs the SDK's OBJECT_SYNC processing +
 * `RealtimeObject.get()` — still TODO — so these compile now and run once that lands (translate-only).
 */
class PathObjectSubscribeTest {

    /**
     * @UTS objects/unit/RTPO19/subscribe-receives-events-0
     */
    @Test
    fun `RTPO19 - subscribe returns Subscription and receives events`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        val sub: Subscription = root.get("score").subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertNotNull(sub) // IS Subscription
        assertEquals(1, events.size)
        assertNotNull(events[0].getObject()) // IS PathObject
        assertEquals("score", events[0].getObject().path())
        val message = events[0].getMessage()
        assertNotNull(message)
        assertEquals("99", message!!.serial)
        assertEquals("remote", message.siteCode)
        assertNotNull(message.operation)
        assertEquals(ObjectOperationAction.COUNTER_INC, message.operation.action)
        assertEquals("test", message.channel)
    }

    /**
     * @UTS objects/unit/RTPO19b/subscribe-precondition-detached-0
     */
    @Test
    fun `RTPO19b - subscribe on DETACHED channel throws 90001`() = runTest {
        val (_, channel, root, mockWs) = setupSyncedChannel("test")

        // Drive the channel to DETACHED. The shared helper's mock does not respond to DETACH, so the
        // server-sent DETACHED is injected directly over the existing mock.
        channel.detach()
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.detached).apply { this.channel = "test" },
        )
        awaitChannelState(channel, ChannelState.detached)

        val ex = assertFailsWith<AblyException> { root.subscribe(PathObjectListener { }) }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTPO19c1a/subscribe-non-positive-depth-throws-0
     */
    @Test
    fun `RTPO19c1a - subscribe with non-positive depth throws 40003`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> {
            root.subscribe(PathObjectListener { }, PathObjectSubscriptionOptions(0))
        }
        assertEquals(40003, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTPO19c1a/subscribe-negative-depth-throws-0
     */
    @Test
    fun `RTPO19c1a - subscribe with negative depth throws 40003`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> {
            root.subscribe(PathObjectListener { }, PathObjectSubscriptionOptions(-1))
        }
        assertEquals(40003, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTPO19c1/subscribe-depth-1-self-only-0
     */
    @Test
    fun `RTPO19c1 - subscribe with depth 1 only receives self events`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { events.add(it) }, PathObjectSubscriptionOptions(1))

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )

        assertEquals(1, events.size)
    }

    /**
     * @UTS objects/unit/RTPO19c1/subscribe-depth-2-children-0
     */
    @Test
    fun `RTPO19c1 - subscribe with depth 2 receives self and children`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { events.add(it) }, PathObjectSubscriptionOptions(2))

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:profile@1000", "email", dataString("bob@example.com"), "101", "remote")),
            ),
        )

        assertEquals(2, events.size)
    }

    /**
     * @UTS objects/unit/RTPO19c1/subscribe-unlimited-depth-0
     */
    @Test
    fun `RTPO19c1 - subscribe with no depth receives all descendants`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:prefs@1000", "theme", dataString("light"), "101", "remote")),
            ),
        )
        pollUntil(5.seconds) { events.size >= 3 }

        assertTrue(events.size >= 3)
    }

    /**
     * @UTS objects/unit/RTPO19d/subscribe-returns-subscription-0
     */
    @Test
    fun `RTPO19d - subscribe returns Subscription with unsubscribe`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        val sub = root.get("score").subscribe(PathObjectListener { events.add(it) })

        assertNotNull(sub) // IS Subscription
        sub.unsubscribe()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )

        assertEquals(0, events.size)
    }

    /**
     * @UTS objects/unit/RTPO19e1/event-path-object-correct-0
     */
    @Test
    fun `RTPO19e1 - subscribe event provides correct PathObject`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertNotNull(events[0].getObject()) // IS PathObject
        assertEquals("score", events[0].getObject().path())
        assertEquals(107.0, events[0].getObject().asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTPO19e2/event-message-delivery-0
     */
    @Test
    fun `RTPO19e2 - subscribe event delivers ObjectMessage for operations`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 42, "serial-1", "site-a"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        val message = events[0].getMessage()
        assertNotNull(message)
        assertEquals("test", message!!.channel)
        assertEquals("serial-1", message.serial)
        assertEquals("site-a", message.siteCode)
        assertNotNull(message.operation)
        assertEquals(ObjectOperationAction.COUNTER_INC, message.operation.action)
        assertEquals("counter:score@1000", message.operation.objectId)
        assertEquals(42.0, message.operation.counterInc!!.number)
    }

    /**
     * @UTS objects/unit/RTPO19e2/event-message-omitted-no-operation-0
     */
    @Test
    fun `RTPO19e2 - subscribe event omits message when objectMessage has no operation`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { events.add(it) })

        // OBJECT_SYNC that changes counter:score@1000's state without an operation field — the resulting
        // update flows through replaceData, so the delivered event carries no ObjectMessage.
        mockWs.sendToClient(
            buildObjectSyncMessage(
                "test",
                "sync2:",
                listOf(
                    buildObjectState(
                        "counter:score@1000",
                        mapOf("aaa" to "t:1"),
                        counter = counterState(200),
                        createOp = counterCreateOp(200),
                    ),
                ),
            ),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        for (event in events) {
            assertNull(event.getMessage())
        }
    }

    /**
     * @UTS objects/unit/RTPO19f/subscribe-follows-path-0
     */
    @Test
    fun `RTPO19f - subscribe follows path not identity`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { events.add(it) })

        // Replace the counter at "score" with a new counter, then increment the new counter.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), "99", "remote")),
            ),
        )
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:new@2000", 10, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        var foundNew = false
        for (event in events) {
            if (event.getObject().path() == "score") foundNew = true
        }
        assertTrue(foundNew)
    }

    /**
     * @UTS objects/unit/RTPO19g/subscribe-no-side-effects-0
     */
    @Test
    fun `RTPO19g - subscribe has no side effects`() = runTest {
        val (_, channel, root, _) = setupSyncedChannel("test")
        val stateBefore = channel.state

        root.get("score").subscribe(PathObjectListener { })

        assertEquals(stateBefore, channel.state)
    }

    /**
     * @UTS objects/unit/RTPO19/subscribe-primitive-path-0
     */
    @Test
    fun `RTPO19 - subscribe on primitive path receives change events`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("name").subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertEquals(1, events.size)
        assertEquals("name", events[0].getObject().path())
    }

    /**
     * @UTS objects/unit/RTPO19/map-clear-triggers-child-events-0
     */
    @Test
    fun `RTPO19 - MAP_CLEAR triggers subscription events on child paths`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapClear("root", "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertTrue(events.size >= 1)
    }

    /**
     * @UTS objects/unit/RTPO19/child-events-bubble-0
     */
    @Test
    fun `RTPO19 - child events bubble up to parent subscription`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("profile").subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:profile@1000", "email", dataString("bob@example.com"), "99", "remote")),
            ),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:nested@1000", 3, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        assertTrue(events.size >= 2)
    }

    /**
     * @UTS objects/unit/RTO24c1/depth-filtering-formula-0
     */
    @Test
    fun `RTO24c1 - depth filtering formula`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("profile").subscribe(PathObjectListener { events.add(it) }, PathObjectSubscriptionOptions(2))

        // Self event (profile map update).
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:profile@1000", "email", dataString("bob@example.com"), "99", "remote")),
            ),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Child event (nested counter).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:nested@1000", 3, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        // Grandchild event (prefs.theme) — should NOT be received.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:prefs@1000", "theme", dataString("light"), "101", "remote")),
            ),
        )

        assertEquals(2, events.size)
    }

    /**
     * @UTS objects/unit/RTO24c1/prefix-mismatch-0
     */
    @Test
    fun `RTO24c1 - prefix mismatch does not trigger subscription`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val profileEvents = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("profile").subscribe(PathObjectListener { profileEvents.add(it) })

        // Change at "score" — "profile" is not a prefix of "score".
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )

        // Change at "name" — "profile" is not a prefix of "name".
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), "100", "remote"))),
        )

        assertEquals(0, profileEvents.size)
    }

    /**
     * @UTS objects/unit/RTO24b2a/candidate-paths-map-keys-0
     */
    @Test
    fun `RTO24b2a - candidate path construction includes map update keys`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val scoreEvents = mutableListOf<PathObjectSubscriptionEvent>()
        val rootEvents = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { scoreEvents.add(it) })
        root.subscribe(PathObjectListener { rootEvents.add(it) })

        // MAP_SET on root with key "score" — candidates [] (root) and ["score"]; both subscriptions fire.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), "99", "remote")),
            ),
        )
        pollUntil(5.seconds) { scoreEvents.size >= 1 }
        pollUntil(5.seconds) { rootEvents.size >= 1 }

        assertEquals(1, scoreEvents.size)
        assertEquals("score", scoreEvents[0].getObject().path())
        assertEquals(1, rootEvents.size)
    }

    /**
     * @UTS objects/unit/RTO24b2c/listener-exception-caught-0
     */
    @Test
    fun `RTO24b2c - listener exception does not affect other listeners`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { throw RuntimeException("boom") })
        root.subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertEquals(1, events.size)
    }

    /**
     * @UTS objects/unit/RTO24b1/multi-path-dispatch-0
     */
    @Test
    fun `RTO24b1 - dispatch via getFullPaths for multi-path objects`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val eventsScore = mutableListOf<PathObjectSubscriptionEvent>()
        val eventsAlias = mutableListOf<PathObjectSubscriptionEvent>()

        // Add a second reference "alias" -> counter:score@1000 so it has two paths.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("root", "alias", dataObjectId("counter:score@1000"), "98", "remote")),
            ),
        )

        root.get("score").subscribe(PathObjectListener { eventsScore.add(it) })
        root.get("alias").subscribe(PathObjectListener { eventsAlias.add(it) })

        // Increment counter:score@1000 — getFullPaths returns ["score"] and ["alias"].
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "99", "remote"))),
        )
        pollUntil(5.seconds) { eventsScore.size >= 1 }
        pollUntil(5.seconds) { eventsAlias.size >= 1 }

        assertEquals(1, eventsScore.size)
        assertEquals("score", eventsScore[0].getObject().path())
        assertEquals(1, eventsAlias.size)
        assertEquals("alias", eventsAlias[0].getObject().path())
    }

    /**
     * @UTS objects/unit/RTO24b2b/fires-once-per-dispatch-0
     */
    @Test
    fun `RTO24b2b - subscription fires exactly once per dispatch`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        // Subscribe at root (unlimited depth) — covers both [] and ["score"].
        root.subscribe(PathObjectListener { events.add(it) })

        // MAP_SET on root with key "score" — candidates [] and ["score"]; root fires exactly once.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), "99", "remote")),
            ),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertEquals(1, events.size)
    }
}
