package io.ably.lib.rest

import com.ably.pubsub.Channels
import com.ably.pubsub.RestChannel
import com.ably.pubsub.RestClient
import com.ably.pubsub.RestPresence
import com.ably.query.OrderBy
import io.ably.lib.buildHistoryParams
import io.ably.lib.buildRestPresenceParams
import io.ably.lib.rest.ChannelBase.Presence
import io.ably.lib.http.Http
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.*

internal class WrapperRestClient(private val javaClient: AblyRest, private val httpModule: Http) : RestClient by RestClientAdapter(javaClient) {
  override val channels: Channels<out RestChannel>
    get() = WrapperRestChannels(javaClient.channels, httpModule)
}

internal class WrapperRestChannels(private val javaChannels: AblyBase.Channels, private val httpModule: Http) : Channels<RestChannel> by RestChannelsAdapter(javaChannels) {
  override fun get(name: String): RestChannel = WrapperRestChannel(javaChannels.get(name), httpModule)

  override fun get(name: String, options: ChannelOptions): RestChannel =
    WrapperRestChannel(javaChannels.get(name, options), httpModule)
}

internal class WrapperRestChannel(private val javaChannel: Channel, private val httpModule: Http) : RestChannel by RestChannelAdapter(javaChannel) {

  override val presence: RestPresence
    get() = WrapperRestPresence(javaChannel.presence, httpModule)

  override fun publish(name: String?, data: Any?) = javaChannel.publish(httpModule, name, data)

  override fun publish(messages: List<Message>) = javaChannel.publish(httpModule, messages.toTypedArray())

  override fun publishAsync(name: String?, data: Any?, listener: CompletionListener) = javaChannel.publishAsync(httpModule, name, data, listener)

  override fun publishAsync(messages: List<Message>, listener: CompletionListener) = javaChannel.publishAsync(httpModule, messages.toTypedArray(), listener)

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

internal class WrapperRestPresence(private val javaPresence: Presence, private val httpModule: Http) : RestPresence by RestPresenceAdapter(javaPresence) {
  override fun get(limit: Int, clientId: String?, connectionId: String?): PaginatedResult<PresenceMessage> =
    javaPresence.get(buildRestPresenceParams(limit, clientId, connectionId).toTypedArray())

  override fun getAsync(
    callback: Callback<AsyncPaginatedResult<PresenceMessage>>,
    limit: Int,
    clientId: String?,
    connectionId: String?
  ) =
    javaPresence.getAsync(buildRestPresenceParams(limit, clientId, connectionId).toTypedArray(), callback)

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
