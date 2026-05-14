package io.ably.lib.test.mock

import java.time.Duration

interface PendingRequest {
    val url: java.net.URL
    val method: String
    val headers: Map<String, List<String>>
    val body: ByteArray
    fun respondWith(status: Int, body: Any, headers: Map<String, String> = emptyMap())
    fun respondWithDelay(delay: Duration, status: Int, body: Any)
    fun respondWithTimeout()
}
