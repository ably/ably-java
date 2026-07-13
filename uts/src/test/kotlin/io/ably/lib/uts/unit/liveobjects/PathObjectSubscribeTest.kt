package io.ably.lib.uts.unit.liveobjects

import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.liveobjects.path.PathObject
import io.ably.lib.liveobjects.path.PathObjectListener
import io.ably.lib.liveobjects.path.PathObjectSubscriptionEvent
import io.ably.lib.liveobjects.path.PathObjectSubscriptionOptions
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.ConnectionDetails
import io.ably.lib.uts.infra.unit.MockWebSocket
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/path_object_subscribe.md` (RTPO19, RTO24, RTO25) — path-based
 * subscriptions on `PathObject`.
 *
 * Mapping (§8): `pathObj.subscribe(PathObjectListener { event -> … }[, PathObjectSubscriptionOptions(depth)])`
 * returns a `Subscription`; the event exposes `getObject(): PathObject` and `getMessage(): ObjectMessage?`.
 * A non-positive `depth` throws `AblyException` 400/`40003` from the `PathObjectSubscriptionOptions(int)`
 * constructor (RTPO19c1a). Negative "listener did not fire" assertions use the quiescence-barrier pattern
 * (a still-subscribed control listener awaited via `pollUntil`, per standard_test_pool.md).
 *
 * Inbound MAP_SET / MAP_REMOVE ops on an **existing** standard-pool entry (baseline entry timeserial
 * `"t:0"`) must carry a serial that sorts *after* `"t:0"` under map-entry LWW (RTLM9e:
 * `incomingSerial > existingEntrySerial`, lexicographic), so they use `"t:1"`/`"t:2"`. (This was formerly
 * spec-issue SI-2, where those ops carried bare `"98"/"99"/"100"/"101"` serials that sort *before* `"t:0"`
 * and were correctly rejected as stale; it was fixed upstream in the spec.) Counter increments, new-key
 * MAP_SETs, and MAP_CLEAR are unaffected (no per-existing-entry LWW / fresh site), so they keep bare serials.
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
        // Custom mock (spec setup): responds to ATTACH with ATTACHED+OBJECT_SYNC and to DETACH with DETACHED.
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { conn ->
                conn.respondWithSuccess(
                    ProtocolMessage(ProtocolMessage.Action.connected).apply {
                        connectionId = "conn-1"
                        connectionDetails = ConnectionDetails {
                            connectionKey = "conn-key-1"
                            siteCode = SITE_CODE
                            objectsGCGracePeriod = 86_400_000L
                            maxMessageSize = 65_536
                        }
                    },
                )
            }
            onMessageFromClient = { msg ->
                when (msg.action) {
                    ProtocolMessage.Action.attach -> {
                        mockWs.sendToClient(
                            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                                channel = msg.channel
                                channelSerial = "sync1:"
                                setFlag(ProtocolMessage.Flag.has_objects)
                            },
                        )
                        mockWs.sendToClient(buildObjectSyncMessage(msg.channel, "sync1:", STANDARD_POOL_OBJECTS))
                    }
                    ProtocolMessage.Action.detach ->
                        mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.detached).apply { channel = msg.channel })
                    else -> Unit
                }
            }
        }
        val client = TestRealtimeClient {
            key = "fake:key"
            install(mockWs)
        }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )
        val root = channel.`object`.get().await()

        channel.detach()
        awaitChannelState(channel, ChannelState.detached)

        // RTO25b: access on a DETACHED channel throws ErrorInfo 400/90001.
        val ex = assertFailsWith<AblyException> { root.subscribe(PathObjectListener { }) }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTPO19c1a/subscribe-non-positive-depth-throws-0
     */
    @Test
    fun `RTPO19c1a - subscribe with non-positive depth throws 40003`() = runTest {
        setupSyncedChannel("test")

        // depth validation happens in the PathObjectSubscriptionOptions(int) constructor (RTPO19c1a).
        val ex = assertFailsWith<AblyException> { PathObjectSubscriptionOptions(0) }
        assertEquals(40003, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTPO19c1a/subscribe-negative-depth-throws-0
     */
    @Test
    fun `RTPO19c1a - subscribe with negative depth throws 40003`() = runTest {
        setupSyncedChannel("test")

        val ex = assertFailsWith<AblyException> { PathObjectSubscriptionOptions(-1) }
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
        // Quiescence control: an unlimited-depth root listener that covers the out-of-scope child path.
        val control = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { control.add(it) })

        // Self event (root map update) — path [] covered at depth 1.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Child event (root["score"], relativeDepth 2) — NOT covered by depth 1; await the control instead.
        val controlBefore = control.size
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )
        pollUntil(5.seconds) { control.size > controlBefore }

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
        val control = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { control.add(it) })

        // Self event (root map update) — candidate [] covered at depth 2.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Child event (root["score"] counter, relativeDepth 2 <= 2) — covered.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        // Grandchild event (root["profile"]["nested_counter"], relativeDepth 3 > 2) — NOT covered.
        val controlBefore = control.size
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:nested@1000", 1, "101", "remote"))),
        )
        pollUntil(5.seconds) { control.size > controlBefore }

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
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        // Grandchild update at map:prefs["theme"] — with no depth, a descendant at any depth is covered.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("map:prefs@1000", "theme", dataString("light"), remoteSerial(1), "remote"))),
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

        // Quiescence control: a separate, still-subscribed listener on the same path that WILL fire.
        val control = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { control.add(it) })

        assertNotNull(sub) // IS Subscription
        sub.unsubscribe()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { control.size >= 1 }
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
        assertEquals(107.0, events[0].getObject().asLiveCounter().value()) // 100 + 7
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
    fun `RTPO19e2 - subscribe event omits message when no operation`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { events.add(it) })

        // An OBJECT_SYNC that changes counter:score@1000's state via replaceData (no operation field).
        // The sync intentionally omits root: per RTO5c2a root is never removed from the pool, so it
        // is retained and still references "score" — counter:score stays reachable and its
        // sync-triggered update dispatches to the root subscription (message omitted).
        mockWs.sendToClient(
            buildObjectSyncMessage(
                "test",
                "sync2:",
                listOf(
                    buildObjectState(
                        "counter:score@1000",
                        mapOf("aaa" to "t:1"),
                        counter = counterState(0),
                        createOp = counterCreateOp(200),
                    ),
                ),
            ),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Events from sync-triggered updates carry no message (RTPO19e2 / RTO24b2b2).
        for (event in events) {
            assertEquals(null, event.getMessage())
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

        // Replace the counter at "score" with a new counter (identity change at the path).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), remoteSerial(0), "remote"))),
        )
        // Increment the NEW counter now at "score".
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:new@2000", 10, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Subscription is by path, so it delivers events for the new object living at "score".
        assertTrue(events.any { it.getObject().path() == "score" })
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
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
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

        // MAP_CLEAR is an object-level op from a fresh site ("remote"), so it is not gated by per-entry LWW.
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

        // Self update at "profile" (map entry change).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("map:profile@1000", "email", dataString("bob@example.com"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Child update (nested counter under profile) bubbles up to the "profile" subscription.
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

        // Control listener (root, unlimited depth) — also the barrier for seeding below.
        val control = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { control.add(it) })

        // Seed a grandchild object under profile.prefs (path ["profile","prefs","deep"]) BEFORE subscribing
        // the profile listener. "deep" is a NEW key on map:prefs, so this MAP_SET applies regardless of
        // serial (RTLM9d). Its own map:prefs update (candidate ["profile","prefs"], covered at depth 2) must
        // be dispatched BEFORE the profile listener is registered — dispatch is async, so without this
        // barrier the seed races past subscribe() and leaks into `events` as a spurious 3rd event. Await it
        // via the control listener.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("map:prefs@1000", "deep", dataObjectId("counter:deep@3000"), "50", "remote"))),
        )
        pollUntil(5.seconds) { control.size >= 1 }

        val events = mutableListOf<PathObjectSubscriptionEvent>()
        // Subscribe at "profile" depth 2: self and one child level covered; grandchild (depth 3) not.
        root.get("profile").subscribe(PathObjectListener { events.add(it) }, PathObjectSubscriptionOptions(2))

        // Self event (profile map update) — covered.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("map:profile@1000", "email", dataString("bob@example.com"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Child event (nested counter, relativeDepth 2) — covered.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:nested@1000", 3, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        // Grandchild event (counter:deep, relativeDepth 3) — NOT covered; await the control instead.
        val controlBefore = control.size
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:deep@3000", 1, "101", "remote"))),
        )
        pollUntil(5.seconds) { control.size > controlBefore }

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
        // Control listener at root fires on BOTH out-of-scope sends — the quiescence barrier.
        val controlEvents = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { controlEvents.add(it) })

        // Change at "score" — "profile" is not a prefix of "score" (counter_inc from fresh site applies).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        // Change at "name" — "profile" is not a prefix of "name".
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        // Await the control (fires for both sends), so any profile callback would also have run by then.
        pollUntil(5.seconds) { controlEvents.size >= 2 }

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
        // Child path "score" ([] + key "score") and the root path [] itself.
        root.get("score").subscribe(PathObjectListener { scoreEvents.add(it) })
        root.subscribe(PathObjectListener { rootEvents.add(it) })

        // MAP_SET on root with key "score" — candidates are [] (root) and ["score"] (from the update key).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), remoteSerial(0), "remote"))),
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
        // First listener throws; its exception must be caught and not stop the second listener.
        root.subscribe(PathObjectListener { throw RuntimeException("boom") })
        root.subscribe(PathObjectListener { events.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
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

        // "alias" is a NEW key (no existing entry), so this MAP_SET applies regardless of serial (RTLM9d).
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("root", "alias", dataObjectId("counter:score@1000"), "98", "remote")),
            ),
        )
        // Wait for the alias reference to resolve before subscribing.
        pollUntil(5.seconds) { root.get("alias").asLiveCounter().value() == 100.0 }

        root.get("score").subscribe(PathObjectListener { eventsScore.add(it) })
        root.get("alias").subscribe(PathObjectListener { eventsAlias.add(it) })

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
        // Root (unlimited depth) covers both candidate paths [] and ["score"].
        root.subscribe(PathObjectListener { events.add(it) })

        // MAP_SET on root with key "score" — candidates [] and ["score"], both covered, but fires ONCE.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Quiescence: a second single-candidate dispatch. Awaiting it proves the first (multi-candidate)
        // dispatch fired exactly once — otherwise events would already exceed 2.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:new@2000", 1, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        assertEquals(2, events.size)
    }
}
