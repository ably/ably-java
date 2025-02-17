package io.ably.lib.rest

import com.ably.pubsub.Channels
import com.ably.pubsub.RestChannel
import io.ably.lib.types.ChannelOptions

internal class RestChannelsAdapter(private val javaChannels: AblyBase.Channels) : Channels<RestChannel> {
  override fun contains(name: String): Boolean = javaChannels.containsKey(name)

  override fun get(name: String): RestChannel = RestChannelAdapter(javaChannels.get(name))

  override fun get(name: String, options: ChannelOptions): RestChannel =
    RestChannelAdapter(javaChannels.get(name, options))

  override fun release(name: String) = javaChannels.release(name)

  override fun iterator(): Iterator<RestChannel> = iterator {
    javaChannels.entrySet().forEach { yield(RestChannelAdapter(it.value)) }
  }
}
