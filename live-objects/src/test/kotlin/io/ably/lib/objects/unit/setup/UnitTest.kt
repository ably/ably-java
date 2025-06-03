package io.ably.lib.objects.unit.setup

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.ClientOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.After
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

// This class serves as a base for unit tests related to Live Objects.
// It can be extended to include common setup or utility methods for unit tests.
@RunWith(BlockJUnit4ClassRunner::class)
open class UnitTest {

  private val realtimeClients = mutableMapOf<String, AblyRealtime>()
  internal fun getMockRealtimeChannel(channelName: String, clientId: String = "client1"): Channel {
    val client = realtimeClients.getOrPut(clientId) {
      AblyRealtime(ClientOptions().apply {
        autoConnect = false
        key = "keyName:Value"
        this.clientId = clientId
      })
    }
    val channel = client.channels.get(channelName)
    return spyk(channel) {
      every { attach() } answers {
        state = ChannelState.attached
      }
      every { detach() } answers {
        state = ChannelState.detached
      }
      every { subscribe(any<String>(), any()) } returns mockk(relaxUnitFun = true)
      every { subscribe(any<Array<String>>(), any()) } returns mockk(relaxUnitFun = true)
      every { subscribe(any()) } returns mockk(relaxUnitFun = true)
    }.apply {
      state = ChannelState.attached
    }
  }

  @After
  fun afterEach() {
    realtimeClients.clear()
  }
}
