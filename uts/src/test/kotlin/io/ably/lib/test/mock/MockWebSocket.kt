package io.ably.lib.test.mock

import io.ably.lib.debug.DebugOptions
import io.ably.lib.network.WebSocketEngineFactory
import io.ably.lib.network.WebSocketListener
import io.ably.lib.types.ConnectionDetails
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Serialisation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class WebSocketMockConfig {
    var onConnectionAttempt: ((PendingConnection) -> Unit)? = null
    var onMessageFromClient: ((ProtocolMessage) -> Unit)? = null
    var onTextDataFrame: ((String) -> Unit)? = null
    var onBinaryDataFrame: ((ByteArray) -> Unit)? = null
}

class MockWebSocket(config: WebSocketMockConfig = WebSocketMockConfig()) {
    private val _events = Collections.synchronizedList(mutableListOf<MockEvent>())
    val events: List<MockEvent> get() = _events.toList()

    private var _pendingConnections = Channel<PendingConnection>(Channel.UNLIMITED)
    private var _messagesFromClient = Channel<ProtocolMessage>(Channel.UNLIMITED)
    private var _clientCloseEvents = Channel<MockEvent.ClientClose>(Channel.UNLIMITED)

    @Volatile private var activeListener: WebSocketListener? = null

    val engineFactory: WebSocketEngineFactory = MockWebSocketEngineFactory(
        onConnect = { pending ->
            _events.add(MockEvent.ConnectionAttempt(pending.host, pending.port, pending.tls))
            val tracked = EventTrackingPendingConnection(pending, _events)
            val handler = config.onConnectionAttempt
            if (handler != null) {
                handler(tracked)
            } else {
                _pendingConnections.trySend(tracked)
            }
        },
        onConnected = { listener ->
            _events.add(MockEvent.ConnectionEstablished)
            activeListener = listener
        },
        onTextFrame = { text ->
            config.onTextDataFrame?.invoke(text)
            val decoded = runCatching { Serialisation.gson.fromJson(text, ProtocolMessage::class.java) }.getOrNull()
            if (decoded != null) {
                _events.add(MockEvent.MessageFromClient(decoded))
                val handler = config.onMessageFromClient
                if (handler != null) {
                    handler(decoded)
                } else {
                    _messagesFromClient.trySend(decoded)
                }
            }
        },
        onBinaryFrame = { bytes ->
            config.onBinaryDataFrame?.invoke(bytes)
        },
        onClientClose = { code, reason ->
            val event = MockEvent.ClientClose(code, reason)
            _events.add(event)
            _clientCloseEvents.trySend(event)
        },
    )

    fun installOn(options: DebugOptions) {
        options.webSocketEngineFactory = engineFactory
    }

    suspend fun awaitConnectionAttempt(timeout: Duration = 5.seconds): PendingConnection =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _pendingConnections.receive() }
        }

    suspend fun awaitNextMessageFromClient(timeout: Duration = 5.seconds): ProtocolMessage =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _messagesFromClient.receive() }
        }

    suspend fun awaitClientClose(timeout: Duration = 5.seconds): MockEvent.ClientClose =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _clientCloseEvents.receive() }
        }

    fun sendToClient(message: ProtocolMessage) {
        val listener = checkNotNull(activeListener) { "No active WebSocket connection" }
        _events.add(MockEvent.SentToClient(message))
        listener.onMessage(Serialisation.gson.toJson(message))
    }

    fun sendToClientAndClose(message: ProtocolMessage) {
        val listener = checkNotNull(activeListener) { "No active WebSocket connection" }
        _events.add(MockEvent.SentToClient(message))
        listener.onMessage(Serialisation.gson.toJson(message))
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
        _messagesFromClient.close()
        _clientCloseEvents.close()
        _pendingConnections = Channel(Channel.UNLIMITED)
        _messagesFromClient = Channel(Channel.UNLIMITED)
        _clientCloseEvents = Channel(Channel.UNLIMITED)
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

fun MockWebSocket(init: WebSocketMockConfig.() -> Unit): MockWebSocket = MockWebSocket(config = WebSocketMockConfig().apply(init))

/** Pre-built CONNECTED message suitable for most unit tests. */
val CONNECTED_MESSAGE: ProtocolMessage
  get() = ProtocolMessage().apply {
    action = ProtocolMessage.Action.connected
    connectionId = "test-connection-id"
    connectionDetails = ConnectionDetails {
      connectionKey = "test-connection-key"
      connectionStateTtl = 120_000L
      maxIdleInterval = 15_000L
    }
  }

