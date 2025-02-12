package io.ably.lib.realtime

import com.ably.pubsub.Channels
import com.ably.pubsub.RealtimeChannel
import io.ably.lib.types.ChannelOptions

internal class RealtimeChannelsAdapter(private val javaChannels: AblyRealtime.Channels) : Channels<RealtimeChannel> {
  override fun contains(name: String): Boolean = javaChannels.containsKey(name)

  override fun get(name: String): RealtimeChannel = RealtimeChannelAdapter(javaChannels.get(name))

  override fun get(name: String, options: ChannelOptions): RealtimeChannel =
    RealtimeChannelAdapter(javaChannels.get(name, options))

  override fun release(name: String) = javaChannels.release(name)

  override fun iterator(): Iterator<RealtimeChannel> = iterator {
    javaChannels.entrySet().forEach { yield(RealtimeChannelAdapter(it.value)) }
  }
}
