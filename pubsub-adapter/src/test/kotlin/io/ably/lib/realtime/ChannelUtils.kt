package io.ably.lib.realtime

import io.ably.lib.types.ChannelOptions

val ChannelBase.channelOptions: ChannelOptions?
  get() = options
