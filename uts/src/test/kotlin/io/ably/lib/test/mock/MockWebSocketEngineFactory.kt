package io.ably.lib.test.mock

import io.ably.lib.network.EngineType
import io.ably.lib.network.WebSocketClient
import io.ably.lib.network.WebSocketEngine
import io.ably.lib.network.WebSocketEngineConfig
import io.ably.lib.network.WebSocketEngineFactory
import io.ably.lib.network.WebSocketListener
import java.net.URI

internal class MockWebSocketEngineFactory(
    private val onConnect: (PendingConnection) -> Unit,
    private val onConnected: (WebSocketListener) -> Unit = {},
) : WebSocketEngineFactory {
    override fun create(config: WebSocketEngineConfig): WebSocketEngine =
        MockWebSocketEngine(onConnect, onConnected)

    override fun getEngineType(): EngineType = EngineType.DEFAULT
}

internal class MockWebSocketEngine(
    private val onConnect: (PendingConnection) -> Unit,
    private val onConnected: (WebSocketListener) -> Unit,
) : WebSocketEngine {
    override fun create(url: String, listener: WebSocketListener): WebSocketClient =
        MockWebSocketClient(url, listener, onConnect, onConnected)

    override fun isPingListenerSupported() = false
}

internal class MockWebSocketClient(
    private val url: String,
    private val listener: WebSocketListener,
    private val onConnect: (PendingConnection) -> Unit,
    private val onConnected: (WebSocketListener) -> Unit,
) : WebSocketClient {
    override fun connect() {
        val uri = URI(url.substringBefore('?'))
        val tls = uri.scheme == "wss"
        val port = if (uri.port == -1) (if (tls) 443 else 80) else uri.port
        onConnect(PendingConnectionImpl(uri.host, port, tls, listener, onConnected))
    }

    override fun close() {}
    override fun close(code: Int, reason: String) {}
    override fun cancel(code: Int, reason: String) {}
    override fun send(message: ByteArray) {}
    override fun send(message: String) {}
}
