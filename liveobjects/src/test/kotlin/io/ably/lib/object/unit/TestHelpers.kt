package io.ably.lib.`object`.unit

import io.ably.lib.`object`.adapter.AblyClientAdapter
import io.ably.lib.`object`.connectionManager
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.transport.ConnectionManager
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ClientOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk

internal fun getMockRealtimeChannel(
  channelName: String,
  clientId: String = "client1",
  channelModes: Array<ChannelMode> = arrayOf(ChannelMode.object_publish, ChannelMode.object_subscribe)): Channel {
    val client = AblyRealtime(ClientOptions().apply {
      autoConnect = false
      key = "keyName:Value"
      this.clientId = clientId
    })
    val channelOpts = ChannelOptions().apply { modes = channelModes }
    val channel = client.channels.get(channelName, channelOpts)
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

internal fun getMockAblyClientAdapter(): AblyClientAdapter {
  mockkStatic("io.ably.lib.object.HelpersKt")
  return mockk<AblyClientAdapter>(relaxed = true) {
    every { getChannel(any()) } returns getMockRealtimeChannel("testChannelName")
    every { connectionManager } returns mockk<ConnectionManager>(relaxed = true)
  }
}
