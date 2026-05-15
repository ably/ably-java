package io.ably.lib.test.mock

import io.ably.lib.debug.DebugOptions
import io.ably.lib.network.HttpEngine
import io.ably.lib.network.WebSocketEngineFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MockHttpClient {
    private var _pendingConnections = Channel<PendingConnection>(Channel.UNLIMITED)
    private var _pendingRequests = Channel<PendingRequest>(Channel.UNLIMITED)

    val httpEngine: HttpEngine =
        MockHttpEngine { _pendingRequests.trySend(it) }

    val webSocketEngineFactory: WebSocketEngineFactory =
        MockWebSocketEngineFactory(onConnect = { _pendingConnections.trySend(it) })

    fun installOn(options: DebugOptions) {
        options.httpEngine = httpEngine
        options.webSocketEngineFactory = webSocketEngineFactory
    }

    suspend fun awaitRequest(timeout: Duration = 5.seconds): PendingRequest =
        withTimeout(timeout) { _pendingRequests.receive() }

    suspend fun awaitConnectionAttempt(timeout: Duration = 5.seconds): PendingConnection =
        withTimeout(timeout) { _pendingConnections.receive() }

    fun reset() {
        _pendingConnections.close()
        _pendingRequests.close()
        _pendingConnections = Channel(Channel.UNLIMITED)
        _pendingRequests = Channel(Channel.UNLIMITED)
    }
}
