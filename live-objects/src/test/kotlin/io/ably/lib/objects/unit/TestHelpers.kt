package io.ably.lib.objects.unit

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.ClientOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

internal fun getMockRealtimeChannel(channelName: String, clientId: String = "client1"): Channel {
  val client = AblyRealtime(ClientOptions().apply {
    autoConnect = false
    key = "keyName:Value"
    this.clientId = clientId
  })
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
