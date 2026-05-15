package io.ably.lib.test.mock

import io.ably.lib.network.WebSocketListener
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class PendingConnectionImpl(
    override val host: String,
    override val port: Int,
    override val tls: Boolean,
    private val listener: WebSocketListener,
    private val onConnected: (WebSocketListener) -> Unit = {},
) : PendingConnection {
    override fun respondWithSuccess() {
        listener.onOpen()
        onConnected(listener)
    }

    override fun respondWithRefused() = listener.onError(IOException("Connection refused to $host:$port"))
    override fun respondWithTimeout() = listener.onError(SocketTimeoutException("Connection timed out to $host:$port"))
    override fun respondWithDnsError() = listener.onError(UnknownHostException(host))
}
