package io.ably.lib.uts.unit.liveobjects

import io.ably.lib.liveobjects.path.PathObjectListener
import io.ably.lib.liveobjects.path.PathObjectSubscriptionEvent
import io.ably.lib.liveobjects.path.PathObjectSubscriptionOptions
import io.ably.lib.liveobjects.state.ObjectStateChange
import io.ably.lib.liveobjects.state.ObjectStateEvent
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.PublishResult
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.ConnectionDetails
import io.ably.lib.uts.infra.unit.FakeClock
import io.ably.lib.uts.infra.unit.MockWebSocket
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS `objects/unit/realtime_object.md` (RTO2, RTO10, RTO15, RTO17–RTO20, RTO22–RTO26) — the
 * `RealtimeObject` entry point (`channel.object`): the `get()` root accessor, its access/write/mode
 * preconditions, publish-and-apply local-apply / echo-dedup behaviour, the sync-state event API
 * (`on(SYNCING/SYNCED)` / `off` / `unsubscribe`), the single subscription register, depth coverage, and GC.
 *
 * This is a **mixed** spec (mapping §13). The public-API parts translate directly:
 *  - `channel.object.get()` (§2) → `CompletableFuture<LiveMapPathObject>`, awaited with `.await()`.
 *  - Precondition failures (§12): `40024` (missing OBJECT_SUBSCRIBE/OBJECT_PUBLISH mode), `90001`
 *    (DETACHED/FAILED channel), `92008` (channel leaves ATTACHED while awaiting SYNCED), `40000`
 *    (`echoMessages` false) — all `AblyException` with those int codes.
 *  - Sync-state events (§9): `channel.object.on(ObjectStateEvent.SYNCING/SYNCED, listener)` returning a
 *    `Subscription`, `off(listener)`, and `Subscription.unsubscribe()`.
 *  - publishAndApply (RTO20) and GC (RTO10) effects are asserted **observably** through the public read API
 *    (counter `value()`), since the apply/echo/dedup/GC machinery is internal (§13).
 *
 * The one internal-only case is `publish` (RTO15): `channel.object.publish(...)` is marked `internal` in the
 * IDL and its OBJECT/ACK wire-message assertions reach `ProtocolMessage.state` wire objects (§13). There is
 * no public `publish` on `RealtimeObject`, so that test is a documented deviation (see deviations.md).
 *
 * Most tests use `setupSyncedChannel` (helpers.kt), which needs the SDK's OBJECT_SYNC processing +
 * `RealtimeObject.get()` — still TODO — so these compile now and run once that lands (translate-only).
 */
class RealtimeObjectTest {

    private fun connected(withSiteCode: Boolean = true, gcGracePeriodMs: Long? = 86_400_000L): ProtocolMessage =
        ProtocolMessage(ProtocolMessage.Action.connected).apply {
            connectionId = "conn-1"
            connectionDetails = ConnectionDetails {
                connectionKey = "key-1"
                if (withSiteCode) siteCode = "test-site"
                gcGracePeriodMs?.let { objectsGCGracePeriod = it }
            }
        }

    /**
     * @UTS objects/unit/RTO23/get-returns-path-object-0
     */
    @Test
    fun `RTO23d - get returns PathObject wrapping root`() = runTest {
        val (_, _, root, _) = setupSyncedChannel("test")

        // root IS PathObject (always a LiveMapPathObject, RTO23f); path is the empty list -> "".
        assertEquals("", root.path())
    }

    /**
     * @UTS objects/unit/RTO23a/get-requires-subscribe-mode-0
     */
    @Test
    fun `RTO23a - get requires OBJECT_SUBSCRIBE mode`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected(gcGracePeriodMs = null)) }
            onMessageFromClient = { msg ->
                if (msg.action == ProtocolMessage.Action.attach) {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:"
                            setFlag(ProtocolMessage.Flag.has_objects)
                        },
                    )
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_publish) },
        )

        val ex = assertFailsWith<AblyException> { channel.`object`.get().await() }
        assertEquals(40024, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTO23b/get-throws-detached-0
     */
    @Test
    fun `RTO23b - get throws on DETACHED channel`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected(gcGracePeriodMs = null)) }
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
                        mockWs.sendToClient(
                            ProtocolMessage(ProtocolMessage.Action.detached).apply { channel = msg.channel },
                        )
                    else -> Unit
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe) },
        )

        channel.`object`.get().await()
        channel.detach()
        awaitChannelState(channel, ChannelState.detached)

        val ex = assertFailsWith<AblyException> { channel.`object`.get().await() }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO23c/get-waits-for-synced-0
     */
    @Test
    fun `RTO23c - get waits for SYNCED state`() = runTest {
        var attachSent = false
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
            onMessageFromClient = { msg ->
                if (msg.action == ProtocolMessage.Action.attach) {
                    attachSent = true
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:cursor"
                            setFlag(ProtocolMessage.Flag.has_objects)
                        },
                    )
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )

        val getFuture = channel.`object`.get()
        pollUntil(5.seconds) { attachSent }

        mockWs.sendToClient(buildObjectSyncMessage("test", "sync1:", STANDARD_POOL_OBJECTS))

        val root = getFuture.await()
        assertEquals("", root.path())
    }

    /**
     * @UTS objects/unit/RTO15/publish-sends-object-pm-0
     */
    @Test
    fun `RTO15 - publish sends OBJECT ProtocolMessage`() = runTest {
        // DEVIATION (RTO15): the spec calls the internal `channel.object.publish([...])` and asserts on the
        // captured OBJECT ProtocolMessage's wire form (action/channel/state) and the PublishResult.serials
        // from the ACK. ably-java's `RealtimeObject` exposes no public `publish` method (RTO15 is `internal`
        // in the IDL), and the wire `state` objects + ACK PublishResult are internal `:liveobjects` types
        // not reachable through the public API (mapping §13). Not expressible against the public surface.
        // See deviations.md.
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
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected(withSiteCode = false)) }
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
                        mockWs.sendToClient(buildObjectSyncMessage("test", "sync1:", STANDARD_POOL_OBJECTS))
                    }
                    ProtocolMessage.Action.`object` ->
                        mockWs.sendToClient(buildAckMessage(msg.msgSerial, listOf("serial-0")))
                    else -> Unit
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )
        val root = channel.`object`.get().await()

        root.get("score").asLiveCounter().increment(10).await()

        // With no siteCode in ConnectionDetails, the synthetic message cannot be applied locally (RTO20c1),
        // so the score stays at its synced value of 100.
        assertEquals(100.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20d1/null-serial-skipped-0
     */
    @Test
    fun `RTO20d1 - null serial in PublishResult is skipped`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
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
                        mockWs.sendToClient(buildObjectSyncMessage("test", "sync1:", STANDARD_POOL_OBJECTS))
                    }
                    ProtocolMessage.Action.`object` ->
                        // A single null serial in the PublishResult — built directly since the
                        // buildAckMessage helper only accepts non-null serials.
                        mockWs.sendToClient(
                            ProtocolMessage(ProtocolMessage.Action.ack).apply {
                                msgSerial = msg.msgSerial
                                res = arrayOf(PublishResult(arrayOf<String?>(null)))
                            },
                        )
                    else -> Unit
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )
        val root = channel.`object`.get().await()

        root.get("score").asLiveCounter().increment(10).await()

        // Null serial in the PublishResult means the synthetic message is skipped (RTO20d1), so the local
        // apply does not happen and the score stays at the synced value of 100.
        assertEquals(100.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20e/waits-for-synced-0
     */
    @Test
    fun `RTO20e - publishAndApply waits for SYNCED during SYNCING`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        // Begin a new sync (channel re-ATTACHED with a new cursor and HAS_OBJECTS).
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"
                channelSerial = "sync2:cursor"
                setFlag(ProtocolMessage.Flag.has_objects)
            },
        )

        val incFuture = root.get("score").asLiveCounter().increment(10)

        // Complete the sync — the pending increment can now apply.
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))

        incFuture.await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20e1/fails-on-channel-failed-0
     */
    @Test
    fun `RTO20e1 - publishAndApply fails when channel enters FAILED during sync wait`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"
                channelSerial = "sync2:cursor"
                setFlag(ProtocolMessage.Flag.has_objects)
            },
        )

        val incFuture = root.get("score").asLiveCounter().increment(10)

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.detached).apply {
                this.channel = "test"
                error = ErrorInfo("Channel detached", 400, 90000)
            },
        )

        val ex = assertFailsWith<AblyException> { incFuture.await() }
        assertEquals(92008, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTO17/sync-state-events-0
     */
    @Test
    fun `RTO17 RTO18 - Sync state events`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
            onMessageFromClient = { msg ->
                if (msg.action == ProtocolMessage.Action.attach) {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:cursor"
                            setFlag(ProtocolMessage.Flag.has_objects)
                        },
                    )
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )

        val events = mutableListOf<String>()
        channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
        channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })

        val getFuture = channel.`object`.get()
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(buildObjectSyncMessage("test", "sync1:", STANDARD_POOL_OBJECTS))
        getFuture.await()

        pollUntil(5.seconds) { events.size >= 2 }
        assertEquals(listOf("SYNCING", "SYNCED"), events)
    }

    /**
     * @UTS objects/unit/RTO18d/duplicate-listener-0
     */
    @Test
    fun `RTO18d - Duplicate listener registered twice fires twice`() = runTest {
        val (_, channel, _, mockWs) = setupSyncedChannel("test")
        var callCount = 0
        val listener = ObjectStateChange.Listener { callCount++ }
        channel.`object`.on(ObjectStateEvent.SYNCED, listener)
        channel.`object`.on(ObjectStateEvent.SYNCED, listener)

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"
                channelSerial = "sync2:cursor"
                setFlag(ProtocolMessage.Flag.has_objects)
            },
        )
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))

        pollUntil(5.seconds) { callCount >= 2 }
        assertEquals(2, callCount)
    }

    /**
     * @UTS objects/unit/RTO19/off-deregisters-0
     */
    @Test
    fun `RTO19 - off deregisters listener`() = runTest {
        val (_, channel, _, mockWs) = setupSyncedChannel("test")
        var callCount = 0
        val listener = ObjectStateChange.Listener { callCount++ }
        // The spec's `sub.off()` maps to the returned Subscription's unsubscribe() (§9).
        val sub = channel.`object`.on(ObjectStateEvent.SYNCED, listener)
        sub.unsubscribe()

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"
                channelSerial = "sync2:cursor"
                setFlag(ProtocolMessage.Flag.has_objects)
            },
        )
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))

        assertEquals(0, callCount)
    }

    /**
     * @UTS objects/unit/RTO2/mode-enforcement-0
     */
    @Test
    fun `RTO2 - Channel mode enforcement`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
            onMessageFromClient = { msg ->
                if (msg.action == ProtocolMessage.Action.attach) {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:"
                            setFlag(ProtocolMessage.Flag.has_objects)
                            // Server grants only OBJECT_SUBSCRIBE (RTO2a checks granted modes when ATTACHED);
                            // granted modes are carried as flag bits, not a `modes` field, on ProtocolMessage.
                            setFlag(ProtocolMessage.Flag.object_subscribe)
                        },
                    )
                    mockWs.sendToClient(buildObjectSyncMessage(msg.channel, "sync1:", STANDARD_POOL_OBJECTS))
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )
        val root = channel.`object`.get().await()

        val ex = assertFailsWith<AblyException> { root.set("name", LiveMapValue.of("Bob")).await() }
        assertEquals(40024, ex.errorInfo.code)
    }

    /**
     * @UTS objects/unit/RTO25a/access-requires-subscribe-mode-0
     */
    @Test
    fun `RTO25a - Access API requires OBJECT_SUBSCRIBE mode`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
            onMessageFromClient = { msg ->
                if (msg.action == ProtocolMessage.Action.attach) {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:"
                            setFlag(ProtocolMessage.Flag.has_objects)
                            setFlag(ProtocolMessage.Flag.object_publish)
                        },
                    )
                    mockWs.sendToClient(buildObjectSyncMessage(msg.channel, "sync1:", STANDARD_POOL_OBJECTS))
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_publish) },
        )

        val ex = assertFailsWith<AblyException> { channel.`object`.get().await() }
        assertEquals(40024, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO25b/access-throws-detached-0
     */
    @Test
    fun `RTO25b - Access API throws on DETACHED channel`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected(gcGracePeriodMs = null)) }
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
                        mockWs.sendToClient(
                            ProtocolMessage(ProtocolMessage.Action.detached).apply { channel = msg.channel },
                        )
                    else -> Unit
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe) },
        )

        channel.`object`.get().await()
        channel.detach()
        awaitChannelState(channel, ChannelState.detached)

        val ex = assertFailsWith<AblyException> { channel.`object`.get().await() }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO25b/access-throws-failed-0
     */
    @Test
    fun `RTO25b - Access API throws on FAILED channel`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected(gcGracePeriodMs = null)) }
            onMessageFromClient = { msg ->
                if (msg.action == ProtocolMessage.Action.attach) {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.error).apply {
                            channel = msg.channel
                            error = ErrorInfo("Channel error", 400, 90000)
                        },
                    )
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe) },
        )

        channel.attach()
        awaitChannelState(channel, ChannelState.failed)

        val ex = assertFailsWith<AblyException> { channel.`object`.get().await() }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO26a/write-requires-publish-mode-0
     */
    @Test
    fun `RTO26a - Write API requires OBJECT_PUBLISH mode`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
            onMessageFromClient = { msg ->
                if (msg.action == ProtocolMessage.Action.attach) {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:"
                            setFlag(ProtocolMessage.Flag.has_objects)
                            setFlag(ProtocolMessage.Flag.object_subscribe)
                        },
                    )
                    mockWs.sendToClient(buildObjectSyncMessage(msg.channel, "sync1:", STANDARD_POOL_OBJECTS))
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe) },
        )
        val root = channel.`object`.get().await()

        val ex = assertFailsWith<AblyException> {
            root.set("name", LiveMapValue.of("Bob")).await()
        }
        assertEquals(40024, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO26b/write-throws-detached-0
     */
    @Test
    fun `RTO26b - Write API throws on DETACHED channel`() = runTest {
        val (_, channel, root, mockWs) = setupSyncedChannel("test")

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.detached).apply {
                this.channel = "test"
                error = ErrorInfo("Channel detached", 400, 90000)
            },
        )
        awaitChannelState(channel, ChannelState.detached)

        val ex = assertFailsWith<AblyException> {
            root.set("name", LiveMapValue.of("Bob")).await()
        }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO26b/write-throws-failed-0
     */
    @Test
    fun `RTO26b - Write API throws on FAILED channel`() = runTest {
        val (_, channel, root, mockWs) = setupSyncedChannel("test")

        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.error).apply {
                this.channel = "test"
                error = ErrorInfo("Channel error", 400, 90000)
            },
        )
        awaitChannelState(channel, ChannelState.failed)

        val ex = assertFailsWith<AblyException> {
            root.set("name", LiveMapValue.of("Bob")).await()
        }
        assertEquals(90001, ex.errorInfo.code)
        assertEquals(400, ex.errorInfo.statusCode)
    }

    /**
     * @UTS objects/unit/RTO26c/write-throws-echo-disabled-0
     */
    @Test
    fun `RTO26c - Write API throws when echoMessages is false`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
            onMessageFromClient = { msg ->
                if (msg.action == ProtocolMessage.Action.attach) {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:"
                            setFlag(ProtocolMessage.Flag.has_objects)
                        },
                    )
                    mockWs.sendToClient(buildObjectSyncMessage(msg.channel, "sync1:", STANDARD_POOL_OBJECTS))
                }
            }
        }
        val client = TestRealtimeClient {
            key = "fake:key"
            echoMessages = false
            install(mockWs)
        }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )
        val root = channel.`object`.get().await()

        val ex = assertFailsWith<AblyException> {
            root.set("name", LiveMapValue.of("Bob")).await()
        }
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
        val scorePath = root.get("score")
        scorePath.subscribe(PathObjectListener { eventsScore.add(it) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "s:1", "aaa"))),
        )
        pollUntil(5.seconds) { eventsScore.size >= 1 }

        // Both subscriptions are managed by the same register and both fire.
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

        // depth 1 — covers root and immediate children only.
        root.subscribe(PathObjectListener { shallowEvents.add(it) }, PathObjectSubscriptionOptions(1))
        // no depth limit — covers everything.
        root.subscribe(PathObjectListener { deepEvents.add(it) })

        // Update a direct child of root (path ["score"]) — depth 1 from root.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "s:1", "aaa"))),
        )
        pollUntil(5.seconds) { deepEvents.size >= 1 }

        // Update a nested object (path ["profile", "nested_counter"]) — depth 2 from root.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:nested@1000", 1, "s:2", "aaa"))),
        )
        pollUntil(5.seconds) { deepEvents.size >= 2 }

        assertEquals(1, shallowEvents.size)
        assertTrue(deepEvents.size >= 2)
    }

    /**
     * @UTS objects/unit/RTO10/gc-tombstoned-objects-0
     */
    @Test
    fun `RTO10 - GC removes tombstoned objects past grace period`() = runTest {
        val fakeClock = FakeClock()
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
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
                    ProtocolMessage.Action.`object` -> {
                        val serials = (msg.state?.indices ?: IntRange.EMPTY).map { "ack-${msg.msgSerial}:$it" }
                        mockWs.sendToClient(buildAckMessage(msg.msgSerial, serials))
                    }
                    else -> Unit
                }
            }
        }
        val client = TestRealtimeClient {
            key = "fake:key"
            install(mockWs)
            enableFakeTimers(fakeClock)
        }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )
        val root = channel.`object`.get().await()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "99", "site1", 1000))),
        )

        // Advance past the GC grace period (86400000ms) plus the check interval.
        fakeClock.advance(86_400_000L + 300_000L)

        assertNull(root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO10b1/gc-grace-period-source-0
     */
    @Test
    fun `RTO10b1 - GC grace period from ConnectionDetails`() = runTest {
        val fakeClock = FakeClock()
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected(gcGracePeriodMs = 5000L)) }
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
                    ProtocolMessage.Action.`object` -> {
                        val serials = (msg.state?.indices ?: IntRange.EMPTY).map { "ack-${msg.msgSerial}:$it" }
                        mockWs.sendToClient(buildAckMessage(msg.msgSerial, serials))
                    }
                    else -> Unit
                }
            }
        }
        val client = TestRealtimeClient {
            key = "fake:key"
            install(mockWs)
            enableFakeTimers(fakeClock)
        }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )
        val root = channel.`object`.get().await()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "99", "site1", 1000))),
        )

        // Short grace period (5000ms) — advance past it.
        fakeClock.advance(5000L + 1000L)

        assertNull(root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO20/echo-dedup-0
     */
    @Test
    fun `RTO20 - Echo deduplication via appliedOnAckSerials`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        val scoreAfterApply = root.get("score").asLiveCounter().value()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "ack-0:0", "test-site"))),
        )
        val scoreAfterEcho = root.get("score").asLiveCounter().value()

        assertEquals(110.0, scoreAfterApply)
        assertEquals(110.0, scoreAfterEcho)
    }

    /**
     * @UTS objects/unit/RTO20f/ack-no-site-timeserials-update-0
     */
    @Test
    fun `RTO20f - Apply-on-ACK does not update siteTimeserials`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())

        // Inbound COUNTER_INC from siteCode "test" with serial "t:1:0" (same as the ACK). If LOCAL had
        // incorrectly written siteTimeserials, the newness check would reject this as stale.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "t:1:0", "test"))),
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

        // Send the echo BEFORE the ACK.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "ack-0:0", "test-site"))),
        )

        // Now send the ACK.
        mockWs.sendToClient(buildAckMessage(0, listOf("ack-0:0")))

        incFuture.await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO5c9-RTO20/ack-serials-cleared-on-resync-0
     */
    @Test
    fun `RTO5c9 RTO20 - appliedOnAckSerials cleared on re-sync`() = runTest {
        val (_, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(10).await()
        assertEquals(110.0, root.get("score").asLiveCounter().value())

        // Trigger re-sync — appliedOnAckSerials should be cleared per RTO5c9; score resets to synced 100.
        mockWs.sendToClient(
            ProtocolMessage(ProtocolMessage.Action.attached).apply {
                this.channel = "test"
                channelSerial = "sync2:cursor"
                setFlag(ProtocolMessage.Flag.has_objects)
            },
        )
        mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == 100.0 }
        assertEquals(100.0, root.get("score").asLiveCounter().value())

        // Replay the same serial used for apply-on-ACK. If cleared, this applies normally.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "t:1:0", "test"))),
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

        assertTrue(events.size >= 1)
        assertEquals(110.0, root.get("score").asLiveCounter().value())
    }

    /**
     * @UTS objects/unit/RTO23/get-implicit-attach-0
     */
    @Test
    fun `RTO23 - get implicitly attaches channel`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { it.respondWithSuccess(connected()) }
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
                    ProtocolMessage.Action.`object` -> {
                        val serials = (msg.state?.indices ?: IntRange.EMPTY).map { "ack-${msg.msgSerial}:$it" }
                        mockWs.sendToClient(buildAckMessage(msg.msgSerial, serials))
                    }
                    else -> Unit
                }
            }
        }
        val client = TestRealtimeClient { key = "fake:key"; install(mockWs) }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )

        assertEquals(ChannelState.initialized, channel.state)
        val root = channel.`object`.get().await()

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
        assertEquals("", root2.path())
    }

    /**
     * @UTS objects/unit/RTO17-RTO18/sync-event-sequences-0
     */
    @Test
    fun `RTO17 RTO18 - Sync event sequences for all state transitions`() = runTest {
        data class Scenario(
            val name: String,
            val trigger: (channel: Channel, mockWs: MockWebSocket) -> Unit,
            val expectedEvents: List<String>,
        )

        val scenarios = listOf(
            Scenario(
                name = "initial attach",
                trigger = { channel, _ -> channel.attach() },
                expectedEvents = listOf("SYNCING", "SYNCED"),
            ),
            Scenario(
                name = "re-attach after detach",
                trigger = { _, mockWs ->
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.detached).apply { channel = "test" },
                    )
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            this.channel = "test"
                            channelSerial = "sync2:cursor"
                            setFlag(ProtocolMessage.Flag.has_objects)
                        },
                    )
                    mockWs.sendToClient(buildObjectSyncMessage("test", "sync2:", STANDARD_POOL_OBJECTS))
                },
                expectedEvents = listOf("SYNCING", "SYNCED"),
            ),
            Scenario(
                name = "re-sync on new ATTACHED",
                trigger = { _, mockWs ->
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            this.channel = "test"
                            channelSerial = "sync3:cursor"
                            setFlag(ProtocolMessage.Flag.has_objects)
                        },
                    )
                    mockWs.sendToClient(buildObjectSyncMessage("test", "sync3:", STANDARD_POOL_OBJECTS))
                },
                expectedEvents = listOf("SYNCING", "SYNCED"),
            ),
            Scenario(
                name = "ATTACHED without HAS_OBJECTS",
                trigger = { _, mockWs ->
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            this.channel = "test"
                            channelSerial = "sync4:"
                        },
                    )
                },
                expectedEvents = listOf("SYNCED"),
            ),
        )

        for (scenario in scenarios) {
            val (_, channel, _, mockWs) = setupSyncedChannel("test")
            val events = mutableListOf<String>()
            channel.`object`.on(ObjectStateEvent.SYNCING, ObjectStateChange.Listener { events.add("SYNCING") })
            channel.`object`.on(ObjectStateEvent.SYNCED, ObjectStateChange.Listener { events.add("SYNCED") })

            scenario.trigger(channel, mockWs)
            pollUntil(5.seconds) { events.size >= scenario.expectedEvents.size }

            assertEquals(scenario.expectedEvents, events, scenario.name)
        }
    }
}
