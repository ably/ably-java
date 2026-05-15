package io.ably.lib.test.mock

import io.ably.lib.types.ProtocolMessage

interface PendingConnection {
    val host: String
    val port: Int
    val tls: Boolean
    fun respondWithSuccess()
    fun respondWithSuccess(message: ProtocolMessage)
    fun respondWithRefused()
    fun respondWithTimeout()
    fun respondWithDnsError()
}
