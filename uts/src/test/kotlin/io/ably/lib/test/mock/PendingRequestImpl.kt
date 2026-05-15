package io.ably.lib.test.mock

import io.ably.lib.network.FailedConnectionException
import io.ably.lib.network.HttpBody
import io.ably.lib.network.HttpRequest
import io.ably.lib.network.HttpResponse
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.CompletableFuture

internal class PendingRequestImpl(
    private val request: HttpRequest,
    private val future: CompletableFuture<HttpResponse>,
) : PendingRequest {
    override val url get() = request.url
    override val method get() = request.method
    override val headers: Map<String, List<String>> get() = request.headers ?: emptyMap()
    override val body get() = request.body?.content ?: ByteArray(0)

    override fun respondWith(status: Int, body: Any, headers: Map<String, String>) {
        val bytes = when (body) {
            is ByteArray -> body
            else -> body.toString().toByteArray(Charsets.UTF_8)
        }
        future.complete(
            HttpResponse.builder()
                .code(status)
                .message("")
                .body(HttpBody("application/json", bytes))
                .headers(emptyMap())
                .build()
        )
    }

    override fun respondWithDelay(delay: Duration, status: Int, body: Any) {
        Thread {
            Thread.sleep(delay.toMillis())
            respondWith(status, body)
        }.start()
    }

    override fun respondWithTimeout() {
        future.completeExceptionally(FailedConnectionException(SocketTimeoutException("Connection timed out")))
    }
}
