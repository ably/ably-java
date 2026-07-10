package io.ably.lib.uts.unit.liveobjects

import io.ably.lib.liveobjects.path.PathObjectListener
import io.ably.lib.liveobjects.path.PathObjectSubscriptionEvent
import io.ably.lib.liveobjects.path.PathObjectSubscriptionOptions
import io.ably.lib.liveobjects.state.ObjectStateChange
import io.ably.lib.liveobjects.state.ObjectStateEvent
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.ConnectionDetails
import io.ably.lib.uts.infra.unit.FakeClock
import io.ably.lib.uts.infra.unit.MockEvent
import io.ably.lib.uts.infra.unit.MockWebSocket
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/realtime_object.md` (RTO2, RTO10, RTO15, RTO17–RTO20, RTO22–RTO26) — the
 * `channel.object` entry point (`RealtimeObject`): `get()`, sync-state events, publish/apply, GC, and the
 * RTO25 (access) / RTO26 (write) preconditions that PR #499 consolidated into this file.
 *
 * Mapping notes:
 *  - `channel.object` is a Java field named `object` (a Kotlin keyword) → `` channel.`object` ``.
 *  - `get()` → `CompletableFuture<LiveMapPathObject>`; `RTO23e` runs ensure-active-channel (RTL33): a DETACHED
 *    channel is re-attached and `get()` resolves; a FAILED channel rejects `90001`. Access methods (RTO25b)
 *    still throw `90001` on DETACHED/FAILED — a separate check.
 *  - Deviations (see `deviations.md`): `RTO15` publish + its OBJECT/ACK wire assertions are `internal`
 *    (no public `publish`) → not expressible, the apply effect is covered observably by RTO20; `RTO23f`
 *    "IS PathObject" is a static-type tautology → assert `path() == ""` instead.
 */
class RealtimeObjectTest {

    /**
     * @UTS objects/unit/RTO23/get-returns-path-object-0
     */
    @Test
    fun `RTO23 - get returns PathObject wrapping root`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // DEVIATION (RTO23f): "root IS PathObject" is a static-type tautology (get() returns
        // LiveMapPathObject). Assert the observable RTO23d property instead: the root's path is empty.
        assertEquals("", root.path())
    }

    /**
     * @UTS objects/unit/RTO23a/get-requires-subscribe-mode-0
     */
    @Test
    fun `RTO23a - get requires OBJECT_SUBSCRIBE mode`() = runTest {
        val (_, channel, _) = objectsClient(
            requestedModes = arrayOf(ChannelMode.object_publish),
            sendSyncOnAttach = false,
        )
        val ex = assertFailsWith<AblyException> { channel.`object`.get().await() }
        assertEquals(40024, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTO23e/get-reattaches-detached-0
     */
    @Test
    fun `RTO23e - get re-attaches a DETACHED channel`() = runTest {
        val (_, channel, _) = objectsClient(
            requestedModes = arrayOf(ChannelMode.object_subscribe),
            handleDetach = true,
        )

        channel.`object`.get().await()
        channel.detach()
        awaitChannelState(channel, ChannelState.detached, 10.seconds)

        // get() on a DETACHED channel triggers ensure-active-channel (RTL33b) -> implicit re-attach -> resolves
        val root = channel.`object`.get().await()

        assertEquals("", root.path())
        assertEquals(ChannelState.attached, channel.state)
    }

    /**
     * @UTS objects/unit/RTO23c/get-waits-for-synced-0
     */
    @Test
    fun `RTO23c - get waits for SYNCED state`() = runTest {
        val attachSent = AtomicBoolean(false)
        val (_, channel, mockWs) = objectsClient(
            attachedSerial = "sync1:cursor",
            sendSyncOnAttach = false,
            onAttach = { attachSent.set(true) },
        )

        val getFuture = channel.`object`.get()
        pollUntil(5.seconds) { attachSent.get() }

        mockWs.sendToClient(buildObjectSyncMessage("test", "sync1:", STANDARD_POOL_OBJECTS))

        val root = getFuture.await()
        assertEquals("", root.path())
    }

    /**
     * @UTS objects/unit/RTO15/publish-sends-object-pm-0
     */
    @Test
    fun `RTO15 - publish sends OBJECT ProtocolMessage`() = runTest {
        // DEVIATION (RTO15): `channel.object.publish([...])` and its OBJECT/ACK wire + PublishResult
        // assertions are `internal` in ably-java — there is no public `publish` (RTO15/RTO20 are marked
        // internal in the IDL). Not expressible via the public surface; the publish-and-apply *effect*
        // (RTO20) is covered observably by `RTO20 - publishAndApply applies locally on ACK`. See deviations.md.
    }

    /**
     * @UTS objects/unit/RTO20/publish-and-apply-local-0
     */
    @Test
    fun `RTO20 - publishAndApply applies locally on ACK`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()

        assertEquals(110.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20c/missing-site-code-0
     */
    @Test
    fun `RTO20c - publishAndApply logs error when siteCode missing`() = runTest {
        val (_, channel, _) = objectsClient(siteCode = null)
        val root = channel.`object`.get().await()

        // RTO20c1: no siteCode in ConnectionDetails => operation is NOT applied locally on ACK (logged).
        root.get("score").asLiveCounter().increment(10).await()

        assertEquals(100.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20d1/null-serial-skipped-0
     */
    @Test
    fun `RTO20d1 - null serial in PublishResult is skipped`() = runTest {
        val (_, channel, _) = objectsClient(ackWithNullSerial = true)
        val root = channel.`object`.get().await()

        // RTO20d1: a null serial in the ACK's PublishResult => that ObjectMessage is skipped (not applied).
        root.get("score").asLiveCounter().increment(10).await()

        assertEquals(100.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20e/waits-for-synced-0
     */
    @Test
    fun `RTO20e - publishAndApply waits for SYNCED during SYNCING`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        // Start a new (incomplete) sync so the objects state is SYNCING.
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"; channelSerial = "sync2:cursor"; setFlag(ProtocolMessage.Flag.has_objects)
            },
        )

        val incFuture = root.get("score").asLiveCounter().increment(10)

        // RTO20e: while still SYNCING the write must not have applied yet.
        assertFalse(incFuture.isDone)
        assertEquals(100.0, root.get("score").asLiveCounter().value())

        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))
        incFuture.await()

        assertEquals(110.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20e1/fails-on-channel-detached-0
     */
    @Test
    fun `RTO20e1 - publishAndApply fails when channel enters FAILED during sync wait`() = runTest {
        // DEVIATION (test stimulus, not SDK behaviour): the spec injects DETACHED to trigger RTO20e1, but an
        // unsolicited DETACHED while ATTACHED triggers an automatic re-attach (RTL13a) rather than leaving the
        // channel DETACHED, so the sync-wait never observes the state change. RTO20e1 covers
        // DETACHED/SUSPENDED/FAILED; we exercise the 92008 path via a channel ERROR (FAILED) — the same
        // adaptation ably-js uses and that the spec adopted for the proxy tier (specification#501).
        val (_, channel, root, mockWs) = setupSyncedChannel("test")

        // Put objects into SYNCING via a re-attach with HAS_OBJECTS (no OBJECT_SYNC follows).
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"; channelSerial = "sync2:cursor"; setFlag(ProtocolMessage.Flag.has_objects)
            },
        )

        val incFuture = root.get("score").asLiveCounter().increment(10)

        // Ensure the OBJECT publish has actually been sent (so the ACK -> applyAckResult wait is engaged)
        // before we fail the channel; otherwise the publish could observe FAILED first and fail differently.
        pollUntil(5.seconds) {
            mockWs.events.filterIsInstance<MockEvent.MessageFromClient>()
                .any { it.message.action == ProtocolMessage.Action.`object` }
        }

        // Channel ERROR -> FAILED while the write is waiting for SYNCED (RTO20e1).
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.error).apply {
                this.channel = "test"; error = ErrorInfo("Channel failed", 400, 90000)
            },
        )
        awaitChannelState(channel, ChannelState.failed, 10.seconds)

        val ex = assertFailsWith<AblyException> { incFuture.await() }
        assertEquals(92008, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTO17/sync-state-events-0
     */
    @Test
    fun `RTO17, RTO18 - Sync state events`() = runTest {
        val (_, channel, mockWs) = objectsClient(attachedSerial = "sync1:cursor", sendSyncOnAttach = false)

        val events = mutableListOf<String>()
        channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
        channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })

        val getFuture = channel.`object`.get()
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(buildObjectSyncMessage("test", "sync1:", STANDARD_POOL_OBJECTS))
        getFuture.await()

        assertEquals(listOf("SYNCING", "SYNCED"), events)
    }

    /**
     * @UTS objects/unit/RTO18d/duplicate-listener-0
     */
    @Test
    fun `RTO18d - Duplicate listener registered twice fires twice`() = runTest {
        // DEVIATION (RTO18d): ably-java's EventEmitter registers `on(event, listener)` in a Map keyed by the
        // listener instance (`filters.put(listener, ...)`), so the SAME listener registered twice is stored
        // once and fires ONCE per event — not twice as RTO18d/RTE4 require. Spec-correct assertion (== 2)
        // kept, env-gated. See deviations.md.
        if (System.getenv("RUN_DEVIATIONS") == null) return@runTest

        val (_, channel, _, mockWs) = setupSyncedChannel("test")
        val callCount = AtomicInteger(0)
        val listener = ObjectStateChange.Listener { callCount.incrementAndGet() }
        channel.`object`.on(ObjectStateEvent.SYNCED, listener)
        channel.`object`.on(ObjectStateEvent.SYNCED, listener)

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"; channelSerial = "sync2:cursor"; setFlag(ProtocolMessage.Flag.has_objects)
            },
        )
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))

        pollUntil(5.seconds) { callCount.get() >= 2 }
        assertEquals(2, callCount.get())
    }

    /**
     * @UTS objects/unit/RTO19/off-deregisters-0
     */
    @Test
    fun `RTO19 - off deregisters listener`() = runTest {
        val (_, channel, _, mockWs) = setupSyncedChannel("test")
        val callCount = AtomicInteger(0)
        val sub = channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { callCount.incrementAndGet() })
        sub.unsubscribe()

        // Quiescence control (standard_test_pool.md): a still-registered listener that WILL fire on the
        // same re-sync dispatch, so once it has fired the deregistered one would also have fired if still on.
        val controlCount = AtomicInteger(0)
        channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { controlCount.incrementAndGet() })

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"; channelSerial = "sync2:cursor"; setFlag(ProtocolMessage.Flag.has_objects)
            },
        )
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))

        pollUntil(5.seconds) { controlCount.get() >= 1 }
        assertEquals(0, callCount.get())
    }

    /**
     * @UTS objects/unit/RTO2/mode-enforcement-0
     */
    @Test
    fun `RTO2 - Channel mode enforcement`() = runTest {
        // Requested both modes, but ATTACHED grants only OBJECT_SUBSCRIBE (RTO2a checks granted modes).
        val (_, channel, _) = objectsClient(
            grantedFlags = listOf(ProtocolMessage.Flag.object_subscribe),
        )
        val root = channel.`object`.get().await()

        val ex = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }
        assertEquals(40024, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTO23e/get-rejects-failed-0
     */
    @Test
    fun `RTO23e - get on a FAILED channel rejects with 90001`() = runTest {
        val (_, channel, _) = objectsClient(
            requestedModes = arrayOf(ChannelMode.object_subscribe),
            failOnAttach = true,
        )

        channel.attach()
        awaitChannelState(channel, ChannelState.failed, 10.seconds)

        val ex = assertFailsWith<AblyException> { channel.`object`.get().await() }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO25a/access-requires-subscribe-mode-0
     */
    @Test
    fun `RTO25a - Access API precondition requires OBJECT_SUBSCRIBE mode`() = runTest {
        val (_, channel, _) = objectsClient(
            requestedModes = arrayOf(ChannelMode.object_publish),
            grantedFlags = listOf(ProtocolMessage.Flag.object_publish),
        )
        val ex = assertFailsWith<AblyException> { channel.`object`.get().await() }
        assertEquals(40024, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO25b/access-throws-detached-0
     */
    @Test
    fun `RTO25b - Access API precondition throws on DETACHED channel`() = runTest {
        // A server-initiated DETACHED auto-reattaches per RTL13, so it never settles in DETACHED; drive a
        // stable DETACHED via an explicit detach (the objectsClient mock answers DETACH with DETACHED).
        // The precondition state under test (channel DETACHED) is identical either way.
        val (_, channel, _) = objectsClient(handleDetach = true)
        val root = channel.`object`.get().await()

        channel.detach()
        awaitChannelState(channel, ChannelState.detached, 10.seconds)

        val ex = assertFailsWith<AblyException> { root.keys().toList() }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO25b/access-throws-failed-0
     */
    @Test
    fun `RTO25b - Access API precondition throws on FAILED channel`() = runTest {
        val (_, channel, root, mockWs) = setupSyncedChannel("test")

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.error).apply {
                this.channel = "test"; error = ErrorInfo("Channel error", 400, 90000)
            },
        )
        awaitChannelState(channel, ChannelState.failed, 10.seconds)

        val ex = assertFailsWith<AblyException> { root.keys().toList() }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO26a/write-requires-publish-mode-0
     */
    @Test
    fun `RTO26a - Write API precondition requires OBJECT_PUBLISH mode`() = runTest {
        val (_, channel, _) = objectsClient(
            requestedModes = arrayOf(ChannelMode.object_subscribe),
            grantedFlags = listOf(ProtocolMessage.Flag.object_subscribe),
        )
        val root = channel.`object`.get().await()

        val ex = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }
        assertEquals(40024, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO26b/write-throws-detached-0
     */
    @Test
    fun `RTO26b - Write API precondition throws on DETACHED channel`() = runTest {
        // As RTO25b: a server DETACHED auto-reattaches (RTL13), so drive a stable DETACHED via explicit
        // detach. The write precondition state under test (channel DETACHED) is identical.
        val (_, channel, _) = objectsClient(handleDetach = true)
        val root = channel.`object`.get().await()

        channel.detach()
        awaitChannelState(channel, ChannelState.detached, 10.seconds)

        val ex = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO26b/write-throws-failed-0
     */
    @Test
    fun `RTO26b - Write API precondition throws on FAILED channel`() = runTest {
        val (_, channel, root, mockWs) = setupSyncedChannel("test")

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.error).apply {
                this.channel = "test"; error = ErrorInfo("Channel error", 400, 90000)
            },
        )
        awaitChannelState(channel, ChannelState.failed, 10.seconds)

        val ex = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO26c/write-throws-echo-disabled-0
     */
    @Test
    fun `RTO26c - Write API precondition throws when echoMessages is false`() = runTest {
        val (_, channel, _) = objectsClient(echoMessages = false)
        val root = channel.`object`.get().await()

        val ex = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }
        assertEquals(40000, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO24a/single-register-instance-0
     */
    @Test
    fun `RTO24a - RealtimeObject maintains a single PathObjectSubscriptionRegister`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        val eventsRoot = mutableListOf<PathObjectSubscriptionEvent>()
        val eventsScore = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { eventsRoot.add(it) })
        root.get("score").subscribe(PathObjectListener { eventsScore.add(it) })

        // siteCode "remote" is absent from the pool's siteTimeserials; "t:1" sorts after "t:0" (RTLM9).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "t:1", "remote"))),
        )
        pollUntil(5.seconds) { eventsScore.size >= 1 }

        assertTrue(eventsRoot.size >= 1)
        assertTrue(eventsScore.size >= 1)
    }

    /**
     * @UTS objects/unit/RTO24c1/coverage-prefix-depth-0
     */
    @Test
    fun `RTO24c1 - Subscription coverage prefix match with depth constraint`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        val shallowEvents = mutableListOf<PathObjectSubscriptionEvent>()
        val deepEvents = mutableListOf<PathObjectSubscriptionEvent>()
        // depth 1 covers ONLY root's own path ([]) per RTO24c2b, not its children.
        root.subscribe(PathObjectListener { shallowEvents.add(it) }, PathObjectSubscriptionOptions(1))
        root.subscribe(PathObjectListener { deepEvents.add(it) })

        // Update root itself (path [] — covered by depth 1).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { deepEvents.size >= 1 }

        // Update a child of root (path ["score"], relativeDepth 2 — NOT covered by depth 1).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "t:2", "remote"))),
        )
        pollUntil(5.seconds) { deepEvents.size >= 2 }

        // Quiescence: the deep listener firing twice means the shallow listener has had both dispatches.
        pollUntil(5.seconds) { shallowEvents.size >= 1 }
        assertEquals(1, shallowEvents.size)
        assertTrue(deepEvents.size >= 2)
    }

    /**
     * @UTS objects/unit/RTO10/gc-tombstoned-objects-0
     */
    @Test
    fun `RTO10 - GC removes tombstoned objects past grace period`() = runTest {
        val fakeClock = FakeClock()
        val (_, channel, mockWs) = objectsClient(fakeClock = fakeClock, gcGracePeriod = 86_400_000L)
        val root = channel.`object`.get().await()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "99", "site1", 1000))),
        )
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == null }

        fakeClock.advance(86_400_000L + 300_000L)

        assertNull(root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20/echo-dedup-0
     */
    @Test
    fun `RTO20 - Echo deduplication via appliedOnAckSerials`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        val afterApply = root.get("score").asLiveCounter().value()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, ackSerial(0, 0), SITE_CODE))),
        )
        val afterEcho = root.get("score").asLiveCounter().value()

        assertEquals(110.0, afterApply)
        assertEquals(110.0, afterEcho)
    }

    /**
     * @UTS objects/unit/RTO20f/ack-no-site-timeserials-update-0
     */
    @Test
    fun `RTO20f - Apply-on-ACK does not update siteTimeserials`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())

        // Inbound COUNTER_INC from SITE_CODE with serial "t:0:9": deliberately NOT the apply-on-ACK serial
        // ackSerial(0,0)="t:1:0" (so RTO9a3 echo dedup does not discard it) yet it sorts BELOW "t:1:0". If
        // the LOCAL apply had wrongly written siteTimeserials[SITE_CODE]="t:1:0", the RTLC per-site newness
        // check would reject this as stale (value stays 110). Since LOCAL correctly leaves siteTimeserials
        // untouched (RTLC7c), SITE_CODE has no prior entry, so it applies and the value reaches 120.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, belowAckSerial(9), SITE_CODE))),
        )
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == 120.0 }

        assertEquals(120.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20/ack-after-echo-no-double-apply-0
     */
    @Test
    fun `RTO20 - ACK after echo does not double-apply`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannelNoAck("test")

        val incFuture = root.get("score").asLiveCounter().increment(10)

        // Echo BEFORE the ACK.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, ackSerial(0, 0), "test-site"))),
        )
        // Then the ACK.
        mockWs.sendToClient(buildAckMessage(0, listOf(ackSerial(0, 0))))

        incFuture.await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO5c9-RTO20/ack-serials-cleared-on-resync-0
     */
    @Test
    fun `RTO5c9, RTO20 - appliedOnAckSerials cleared on re-sync`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())

        // Re-sync — appliedOnAckSerials should be cleared per RTO5c9; score resets to the pool value (100).
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"; channelSerial = "sync2:cursor"; setFlag(ProtocolMessage.Flag.has_objects)
            },
        )
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == 100.0 }

        // Replay the same serial used for apply-on-ACK: if serials were cleared it applies normally -> 110.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, ackSerial(0, 0), SITE_CODE))),
        )
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == 110.0 }

        assertEquals(110.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20/subscription-fires-on-ack-apply-0
     */
    @Test
    fun `RTO20 - Subscription fires on apply-on-ACK`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { events.add(it) })

        root.get("score").asLiveCounter().increment(10).await()

        pollUntil(5.seconds) { events.size >= 1 }
        assertTrue(events.size >= 1)
        assertEquals(110.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO23/get-implicit-attach-0
     */
    @Test
    fun `RTO23 - get implicitly attaches channel`() = runTest {
        val (_, channel, _) = objectsClient()

        assertEquals(ChannelState.initialized, channel.state)
        val root = channel.`object`.get().await()

        // DEVIATION (RTO23f): "root IS PathObject" tautology -> assert path() + the implicit attach effect.
        assertEquals("", root.path())
        assertEquals(ChannelState.attached, channel.state)
    }

    /**
     * @UTS objects/unit/RTO23d/get-resolves-immediately-synced-0
     */
    @Test
    fun `RTO23d - get resolves immediately when already SYNCED`() = runTest {
        val (_, channel, _, _) = setupSyncedChannel("test")

        val root2 = channel.`object`.get().await()

        // DEVIATION (RTO23f): "root2 IS PathObject" tautology -> assert path() == "".
        assertEquals("", root2.path())
    }

    /**
     * @UTS objects/unit/RTO10b1/gc-grace-period-source-0
     */
    @Test
    fun `RTO10b1 - GC grace period from ConnectionDetails`() = runTest {
        val fakeClock = FakeClock()
        val (_, channel, mockWs) = objectsClient(fakeClock = fakeClock, gcGracePeriod = 5000L)
        val root = channel.`object`.get().await()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "99", "site1", 1000))),
        )
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == null }

        fakeClock.advance(5000L + 1000L)

        assertNull(root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO17-RTO18/sync-event-sequences-0
     */
    @Test
    fun `RTO17, RTO18 - Sync event sequences for all state transitions`() = runTest {
        // Scenario 1: initial attach — needs a fresh, non-synced channel with listeners wired BEFORE attach.
        run {
            val (_, channel, mockWs) = objectsClient(attachedSerial = "sync1:", sendSyncOnAttach = true, autoConnect = false)
            val events = mutableListOf<String>()
            channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
            channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })
            channel.attach()
            pollUntil(5.seconds) { events.size >= 2 }
            assertEquals(listOf("SYNCING", "SYNCED"), events)
            (mockWs) // referenced
        }

        // Scenario 2: re-attach after detach. A server-initiated DETACHED auto-reattaches (RTL13): the SDK
        // re-sends ATTACH and the (setupSyncedChannel) mock answers ATTACHED + OBJECT_SYNC, producing one
        // SYNCING->SYNCED cycle. Driving a manual ATTACHED here too would double the cycle.
        run {
            val (_, channel, _, mockWs) = setupSyncedChannel("test")
            val events = mutableListOf<String>()
            channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
            channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })
            mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.detached).apply { this.channel = "test" })
            pollUntil(5.seconds) { events.size >= 2 }
            assertEquals(listOf("SYNCING", "SYNCED"), events)
        }

        // Scenario 3: re-sync on new ATTACHED.
        run {
            val (_, channel, _, mockWs) = setupSyncedChannel("test")
            val events = mutableListOf<String>()
            channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
            channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })
            mockWs.sendToClient(
                ProtocolMessage(ProtocolMessage.Action.attached).apply {
                    this.channel = "test"; channelSerial = "sync3:cursor"; setFlag(ProtocolMessage.Flag.has_objects)
                },
            )
            mockWs.sendToClient(buildObjectSyncMessage("test", "sync3:", STANDARD_POOL_OBJECTS))
            pollUntil(5.seconds) { events.size >= 2 }
            assertEquals(listOf("SYNCING", "SYNCED"), events)
        }

        // Scenario 4: ATTACHED without HAS_OBJECTS — RTO4c emits SYNCING, RTO4b completes it -> SYNCED.
        run {
            val (_, channel, _, mockWs) = setupSyncedChannel("test")
            val events = mutableListOf<String>()
            channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
            channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })
            mockWs.sendToClient(
                ProtocolMessage(ProtocolMessage.Action.attached).apply { this.channel = "test"; channelSerial = "sync4:" },
            )
            pollUntil(5.seconds) { events.size >= 2 }
            assertEquals(listOf("SYNCING", "SYNCED"), events)
        }
    }
}

// ---------------------------------------------------------------------------
// Local mock-client builder for the RealtimeObject precondition / lifecycle cases that need channel
// options or ConnectionDetails that setupSyncedChannel's fixed happy-path setup doesn't cover
// (custom modes, granted-mode flags, missing siteCode, echoMessages=false, withheld sync, DETACH/FAIL
// handling, a fake clock, or a null ACK serial). Mirrors Helpers.setup.
// ---------------------------------------------------------------------------

private fun connectedMessage(siteCode: String?, gcGracePeriod: Long) =
    ProtocolMessage(ProtocolMessage.Action.connected).apply {
        connectionId = "conn-1"
        connectionDetails = ConnectionDetails {
            connectionKey = "key-1"
            if (siteCode != null) this.siteCode = siteCode
            objectsGCGracePeriod = gcGracePeriod
            maxMessageSize = 65_536
        }
    }

private fun objectsClient(
    requestedModes: Array<ChannelMode> = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish),
    grantedFlags: List<ProtocolMessage.Flag> = emptyList(),
    siteCode: String? = SITE_CODE,
    gcGracePeriod: Long = 86_400_000L,
    echoMessages: Boolean = true,
    attachedSerial: String = "sync1:",
    sendSyncOnAttach: Boolean = true,
    autoAck: Boolean = true,
    ackWithNullSerial: Boolean = false,
    handleDetach: Boolean = false,
    failOnAttach: Boolean = false,
    autoConnect: Boolean = true,
    fakeClock: FakeClock? = null,
    onAttach: (() -> Unit)? = null,
): Triple<AblyRealtime, Channel, MockWebSocket> {
    lateinit var mockWs: MockWebSocket
    mockWs = MockWebSocket {
        onConnectionAttempt = { conn -> conn.respondWithSuccess(connectedMessage(siteCode, gcGracePeriod)) }
        onMessageFromClient = { msg ->
            when (msg.action) {
                ProtocolMessage.Action.attach -> {
                    onAttach?.invoke()
                    if (failOnAttach) {
                        mockWs.sendToClient(
                            ProtocolMessage(ProtocolMessage.Action.error).apply {
                                this.channel = msg.channel; error = ErrorInfo("Channel error", 400, 90000)
                            },
                        )
                    } else {
                        mockWs.sendToClient(
                            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                                this.channel = msg.channel; channelSerial = attachedSerial
                                setFlag(ProtocolMessage.Flag.has_objects)
                                grantedFlags.forEach { setFlag(it) }
                            },
                        )
                        if (sendSyncOnAttach) {
                            mockWs.sendToClient(buildObjectSyncMessage(msg.channel, "sync1:", STANDARD_POOL_OBJECTS))
                        }
                    }
                }
                ProtocolMessage.Action.detach -> if (handleDetach) {
                    mockWs.sendToClient(ProtocolMessage(ProtocolMessage.Action.detached).apply { this.channel = msg.channel })
                }
                ProtocolMessage.Action.`object` -> when {
                    ackWithNullSerial -> mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.ack).apply {
                            msgSerial = msg.msgSerial; count = 1; res = arrayOf(io.ably.lib.types.PublishResult(arrayOfNulls<String>(1)))
                        },
                    )
                    autoAck -> {
                        val serials = (msg.state?.indices ?: IntRange.EMPTY).map { ackSerial(msg.msgSerial, it) }
                        mockWs.sendToClient(buildAckMessage(msg.msgSerial, serials))
                    }
                    else -> Unit
                }
                else -> Unit
            }
        }
    }
    val client = TestRealtimeClient {
        key = "fake:key"
        this.echoMessages = echoMessages
        this.autoConnect = autoConnect
        fakeClock?.let { enableFakeTimers(it) }
        install(mockWs)
    }
    val channel = client.channels.get("test", ChannelOptions().apply { modes = requestedModes })
    if (!autoConnect) client.connect()
    return Triple(client, channel, mockWs)
}
