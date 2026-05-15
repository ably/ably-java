package io.ably.lib.test.mock

import io.ably.lib.debug.DebugOptions
import io.ably.lib.types.ConnectionDetails
import io.ably.lib.types.ProtocolMessage

data class NetworkMocks(val http: MockHttpClient, val webSocket: MockWebSocket)

class NetworkMocksConfig {
    internal val httpConfig = HttpMockConfig()
    internal val wsConfig = WebSocketMockConfig()

    fun httpClient(block: HttpMockConfig.() -> Unit) {
        httpConfig.apply(block)
    }

    fun webSocketClient(block: WebSocketMockConfig.() -> Unit) {
        wsConfig.apply(block)
    }
}

fun installNetworkMocks(options: DebugOptions, block: NetworkMocksConfig.() -> Unit = {}): NetworkMocks {
    val cfg = NetworkMocksConfig().apply(block)
    val http = MockHttpClient(cfg.httpConfig)
    val ws = MockWebSocket(cfg.wsConfig)
    options.httpEngine = http.engine
    options.webSocketEngineFactory = ws.engineFactory
    return NetworkMocks(http, ws)
}

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
