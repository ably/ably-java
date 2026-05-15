package io.ably.lib.test.mock

import io.ably.lib.types.ProtocolMessage

sealed class MockEvent {
    data class ConnectionAttempt(val host: String, val port: Int, val tls: Boolean) : MockEvent()
    data object ConnectionEstablished : MockEvent()
    data object ConnectionRefused : MockEvent()
    data object ConnectionTimeout : MockEvent()
    data object DnsError : MockEvent()
    data class HttpRequest(val url: java.net.URL, val method: String) : MockEvent()
    data class SentToClient(val message: ProtocolMessage) : MockEvent()
    data object Disconnected : MockEvent()
}
