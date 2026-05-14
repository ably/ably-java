package io.ably.lib.test.mock

import io.ably.lib.network.FailedConnectionException
import io.ably.lib.network.HttpCall
import io.ably.lib.network.HttpEngine
import io.ably.lib.network.HttpRequest
import io.ably.lib.network.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

internal class MockHttpEngine(private val onRequest: (PendingRequest) -> Unit) : HttpEngine {
    override fun call(request: HttpRequest): HttpCall = MockHttpCall(request, onRequest)
    override fun isUsingProxy() = false
}

internal class MockHttpCall(
    private val request: HttpRequest,
    private val onRequest: (PendingRequest) -> Unit,
) : HttpCall {
    private val future = CompletableFuture<HttpResponse>()

    override fun execute(): HttpResponse {
        val pending = PendingRequestImpl(request, future)
        onRequest(pending)
        return try {
            future.get()
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is FailedConnectionException) throw cause
            throw FailedConnectionException(cause ?: e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw FailedConnectionException(e)
        }
    }

    override fun cancel() {
        future.cancel(true)
    }
}
