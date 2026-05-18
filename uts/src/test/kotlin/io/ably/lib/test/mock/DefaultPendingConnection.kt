package io.ably.lib.test.mock

import io.ably.lib.network.WebSocketListener
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Serialisation
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class DefaultPendingConnection(
    override val host: String,
    override val port: Int,
    override val tls: Boolean,
    override val queryParams: Map<String, String>,
    private val listener: WebSocketListener,
    private val onConnected: (WebSocketListener) -> Unit = {},
) : PendingConnection {
    override fun respondWithSuccess() {
        listener.onOpen()
        onConnected(listener)
    }

    override fun respondWithSuccess(message: ProtocolMessage) {
        listener.onOpen()
        onConnected(listener)
        // Async delivery per spec: the library must store the WS reference before processing CONNECTED.
        val encoded = Serialisation.gson.toJson(message)
        Thread { listener.onMessage(encoded) }.apply { isDaemon = true }.start()
    }

    override fun respondWithRefused() = listener.onError(IOException("Connection refused to $host:$port"))
    override fun respondWithTimeout() = listener.onError(SocketTimeoutException("Connection timed out to $host:$port"))
    override fun respondWithDnsError() = listener.onError(UnknownHostException(host))
}
