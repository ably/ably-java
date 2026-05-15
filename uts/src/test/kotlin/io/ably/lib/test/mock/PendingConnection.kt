package io.ably.lib.test.mock

interface PendingConnection {
    val host: String
    val port: Int
    val tls: Boolean
    fun respondWithSuccess()
    fun respondWithRefused()
    fun respondWithTimeout()
    fun respondWithDnsError()
}
