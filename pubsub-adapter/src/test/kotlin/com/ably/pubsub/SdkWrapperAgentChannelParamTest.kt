package com.ably.pubsub

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.RealtimeClient
import io.ably.lib.realtime.RealtimeClientAdapter
import io.ably.lib.realtime.channelOptions
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ClientOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SdkWrapperAgentChannelParamTest {

  @Test
  fun `should add agent information to Realtime channels params`() = runTest {
    val javaRealtimeClient = createAblyRealtime()
    val realtimeClient = RealtimeClientAdapter(javaRealtimeClient)
    val wrapperSdkClient =
      realtimeClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    // create channel from sdk proxy wrapper
    wrapperSdkClient.channels.get("chat-channel")

    // create channel without sdk proxy wrapper
    realtimeClient.channels.get("regular-channel")

    assertEquals(
      "chat-android/0.1.0",
      javaRealtimeClient.channels.get("chat-channel").channelOptions?.params?.get("agent")
    )

    assertNull(
      javaRealtimeClient.channels.get("regular-channel").channelOptions?.params?.get("agent")
    )
  }

  @Test
  fun `should add agent information to Realtime channels params when channel created with custom options`() = runTest {
    val javaRealtimeClient = createAblyRealtime()
    val realtimeClient = RealtimeClient(javaRealtimeClient)
    val wrapperSdkClient =
      realtimeClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    // create channel from sdk proxy wrapper
    wrapperSdkClient.channels.get("chat-channel", ChannelOptions().apply {
      params = mapOf("foo" to "bar")
      modes = arrayOf(ChannelMode.presence)
    })

    // create channel without sdk proxy wrapper
    realtimeClient.channels.get("regular-channel", ChannelOptions().apply {
      encrypted = true
    })

    assertEquals(
      "chat-android/0.1.0",
      javaRealtimeClient.channels.get("chat-channel").channelOptions?.params?.get("agent")
    )

    assertEquals(
      "bar",
      javaRealtimeClient.channels.get("chat-channel").channelOptions?.params?.get("foo")
    )

    assertEquals(
      ChannelMode.presence,
      javaRealtimeClient.channels.get("chat-channel").channelOptions?.modes?.get(0)
    )

    assertNull(
      javaRealtimeClient.channels.get("regular-channel").channelOptions?.params?.get("agent")
    )

    assertTrue(
      javaRealtimeClient.channels.get("regular-channel").channelOptions?.encrypted ?: false
    )
  }
}

private fun createAblyRealtime(): AblyRealtime {
  val options = ClientOptions("xxxxx:yyyyyyy").apply {
    autoConnect = false
  }
  return AblyRealtime(options)
}
