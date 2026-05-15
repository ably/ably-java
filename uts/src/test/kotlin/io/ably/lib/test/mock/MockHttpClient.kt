package io.ably.lib.test.mock

import io.ably.lib.debug.DebugOptions
import io.ably.lib.network.HttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class HttpMockConfig {
    var onConnectionAttempt: ((PendingConnection) -> Unit)? = null
    var onRequest: ((PendingRequest) -> Unit)? = null
}

class MockHttpClient(private val config: HttpMockConfig = HttpMockConfig()) {
    private var _pendingConnections = Channel<PendingConnection>(Channel.UNLIMITED)
    private var _pendingRequests = Channel<PendingRequest>(Channel.UNLIMITED)

    val engine: HttpEngine = MockHttpEngine(
        onConnect = { conn ->
            val handler = config.onConnectionAttempt
            if (handler != null) handler(conn) else _pendingConnections.trySend(conn)
        },
        onRequest = { pending ->
            val handler = config.onRequest
            if (handler != null) handler(pending) else _pendingRequests.trySend(pending)
        },
    )

    fun installOn(options: DebugOptions) {
        options.httpEngine = engine
    }

    suspend fun awaitConnectionAttempt(timeout: Duration = 5.seconds): PendingConnection =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _pendingConnections.receive() }
        }

    suspend fun awaitRequest(timeout: Duration = 5.seconds): PendingRequest =
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(timeout) { _pendingRequests.receive() }
        }

    fun reset() {
        _pendingConnections.close()
        _pendingRequests.close()
        _pendingConnections = Channel(Channel.UNLIMITED)
        _pendingRequests = Channel(Channel.UNLIMITED)
    }
}

fun installMockHttpClient(options: DebugOptions, init: HttpMockConfig.() -> Unit): MockHttpClient {
    val mock = MockHttpClient(config = HttpMockConfig().apply(init))
    mock.installOn(options)
    return mock
}
