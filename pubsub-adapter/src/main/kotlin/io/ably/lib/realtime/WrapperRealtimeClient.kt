package io.ably.lib.realtime

import com.ably.pubsub.*
import com.ably.query.OrderBy
import io.ably.lib.buildHistoryParams
import io.ably.lib.http.Http
import io.ably.lib.types.*

internal class WrapperRealtimeClient(private val javaClient: AblyRealtime, private val httpModule: Http) : RealtimeClient by RealtimeClientAdapter(javaClient) {
  override val channels: Channels<out RealtimeChannel>
    get() = WrapperRealtimeChannels(javaClient.channels, httpModule)
}

internal class WrapperRealtimeChannels(private val javaChannels: AblyRealtime.Channels, private val httpModule: Http) : Channels<RealtimeChannel> by RealtimeChannelsAdapter(javaChannels) {
  override fun get(name: String): RealtimeChannel = WrapperRealtimeChannel(javaChannels.get(name), httpModule)

  override fun get(name: String, options: ChannelOptions): RealtimeChannel =
    WrapperRealtimeChannel(javaChannels.get(name, options), httpModule)
}

internal class WrapperRealtimeChannel(private val javaChannel: Channel, private val httpModule: Http) : RealtimeChannel by RealtimeChannelAdapter(javaChannel) {
  override val presence: RealtimePresence
    get() = WrapperRealtimePresence(javaChannel.presence, httpModule)

  override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<Message> =
    javaChannel.history(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray())

  override fun historyAsync(
    callback: Callback<AsyncPaginatedResult<Message>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
  ) =
    javaChannel.historyAsync(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}

internal class WrapperRealtimePresence(private val javaPresence: Presence, private val httpModule: Http) : RealtimePresence by RealtimePresenceAdapter(javaPresence) {
  override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<PresenceMessage> =
    javaPresence.history(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray())

  override fun historyAsync(
    callback: Callback<AsyncPaginatedResult<PresenceMessage>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
  ) =
    javaPresence.historyAsync(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}
