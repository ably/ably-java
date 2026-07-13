package io.ably.lib.uts.infra.unit

import io.ably.lib.network.FailedConnectionException
import io.ably.lib.network.HttpBody
import io.ably.lib.network.HttpRequest
import io.ably.lib.network.HttpResponse
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import java.net.SocketTimeoutException

internal class DefaultPendingRequest(
    private val request: HttpRequest,
    private val deferred: CompletableDeferred<HttpResponse>,
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
        deferred.complete(
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
            Thread.sleep(delay.inWholeMilliseconds)
            respondWith(status, body)
        }.apply { isDaemon = true }.start()
    }

    override fun respondWithTimeout() {
        deferred.completeExceptionally(FailedConnectionException(SocketTimeoutException("Connection timed out")))
    }
}
