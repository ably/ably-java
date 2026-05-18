package io.ably.lib

import io.ably.lib.debug.DebugOptions
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.rest.AblyRest
import io.ably.lib.test.mock.FakeClock
import io.ably.lib.test.mock.MockHttpClient
import io.ably.lib.test.mock.MockWebSocket

class ClientOptionsBuilder : DebugOptions("appId.keyId:keySecret") {
    init {
        useBinaryProtocol = false
    }

    fun install(mock: MockWebSocket) = mock.installOn(this)
    fun install(mock: MockHttpClient) = mock.installOn(this)

    fun enableFakeTimers(fakeClock: FakeClock) {
        clock = fakeClock
    }
}

fun TestRealtimeClient(block: ClientOptionsBuilder.() -> Unit): AblyRealtime =
    AblyRealtime(ClientOptionsBuilder().apply(block))

fun TestRestClient(block: ClientOptionsBuilder.() -> Unit): AblyRest =
    AblyRest(ClientOptionsBuilder().apply(block))
