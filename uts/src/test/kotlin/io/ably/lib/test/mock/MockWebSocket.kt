package io.ably.lib.test.mock

import io.ably.lib.debug.DebugOptions
import io.ably.lib.network.HttpEngine
import io.ably.lib.network.WebSocketEngineFactory
import io.ably.lib.network.WebSocketListener
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.ProtocolSerializer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MockWebSocket {
    private val _events = Collections.synchronizedList(mutableListOf<MockEvent>())
    val events: List<MockEvent> get() = _events.toList()

    private var _pendingConnections = Channel<PendingConnection>(Channel.UNLIMITED)
    private var _pendingRequests = Channel<PendingRequest>(Channel.UNLIMITED)

    @Volatile private var activeListener: WebSocketListener? = null

    val httpEngine: HttpEngine = MockHttpEngine { req ->
        _events.add(MockEvent.HttpRequest(req.url, req.method))
        _pendingRequests.trySend(EventTrackingPendingRequest(req, _events))
    }

    val webSocketEngineFactory: WebSocketEngineFactory = MockWebSocketEngineFactory(
        onConnect = { pending ->
            _events.add(MockEvent.ConnectionAttempt(pending.host, pending.port, pending.tls))
            _pendingConnections.trySend(EventTrackingPendingConnection(pending, _events))
        },
        onConnected = { listener ->
            _events.add(MockEvent.ConnectionEstablished)
            activeListener = listener
        },
    )

    fun installOn(options: DebugOptions) {
        options.httpEngine = httpEngine
        options.webSocketEngineFactory = webSocketEngineFactory
    }

    suspend fun awaitRequest(timeout: Duration = 5.seconds): PendingRequest =
        withTimeout(timeout) { _pendingRequests.receive() }

    suspend fun awaitConnectionAttempt(timeout: Duration = 5.seconds): PendingConnection =
        withTimeout(timeout) { _pendingConnections.receive() }

    fun sendToClient(message: ProtocolMessage) {
        val listener = checkNotNull(activeListener) { "No active WebSocket connection" }
        _events.add(MockEvent.SentToClient(message))
        listener.onMessage(ByteBuffer.wrap(ProtocolSerializer.writeMsgpack(message)))
    }

    fun sendToClientAndClose(message: ProtocolMessage) {
        val listener = checkNotNull(activeListener) { "No active WebSocket connection" }
        _events.add(MockEvent.SentToClient(message))
        listener.onMessage(ByteBuffer.wrap(ProtocolSerializer.writeMsgpack(message)))
        activeListener = null
        listener.onClose(1000, "Normal closure")
    }

    fun simulateDisconnect() {
        val listener = checkNotNull(activeListener) { "No active WebSocket connection" }
        _events.add(MockEvent.Disconnected)
        activeListener = null
        listener.onClose(1006, "Abnormal closure")
    }

    fun reset() {
        _pendingConnections.close()
        _pendingRequests.close()
        _pendingConnections = Channel(Channel.UNLIMITED)
        _pendingRequests = Channel(Channel.UNLIMITED)
        _events.clear()
        activeListener = null
    }
}

private class EventTrackingPendingConnection(
    private val inner: PendingConnection,
    private val events: MutableList<MockEvent>,
) : PendingConnection by inner {
    override fun respondWithRefused() {
        events.add(MockEvent.ConnectionRefused)
        inner.respondWithRefused()
    }

    override fun respondWithTimeout() {
        events.add(MockEvent.ConnectionTimeout)
        inner.respondWithTimeout()
    }

    override fun respondWithDnsError() {
        events.add(MockEvent.DnsError)
        inner.respondWithDnsError()
    }
}

private class EventTrackingPendingRequest(
    private val inner: PendingRequest,
    private val events: MutableList<MockEvent>,
) : PendingRequest by inner
