package io.ably.lib.uts.infra

import io.ably.lib.network.WebSocketListener
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Serialisation
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.Executors

internal class DefaultPendingConnection(
    override val host: String,
    override val port: Int,
    override val tls: Boolean,
    override val queryParams: Map<String, String>,
    private val listener: WebSocketListener,
    private val onConnected: (WebSocketListener) -> Unit = {},
) : PendingConnection {

    private val deliveryExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mock-ws-delivery").apply { isDaemon = true }
    }

    override fun respondWithSuccess() {
        listener.onOpen()
        onConnected(listener)
    }

    override fun respondWithSuccess(message: ProtocolMessage) {
        listener.onOpen()
        onConnected(listener)
        // Async delivery per spec: the library must store the WS reference before processing CONNECTED.
        val encoded = Serialisation.gson.toJson(message)
        deliveryExecutor.submit { listener.onMessage(encoded) }
    }

    override fun respondWithRefused() = listener.onError(IOException("Connection refused to $host:$port"))
    override fun respondWithTimeout() = listener.onError(SocketTimeoutException("Connection timed out to $host:$port"))
    override fun respondWithDnsError() = listener.onError(UnknownHostException(host))
}
