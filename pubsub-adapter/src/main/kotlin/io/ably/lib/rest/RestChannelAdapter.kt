package io.ably.lib.rest

import com.ably.pubsub.RestChannel
import com.ably.pubsub.RestPresence
import com.ably.query.OrderBy
import io.ably.lib.buildHistoryParams
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.*

internal class RestChannelAdapter(private val javaChannel: Channel) : RestChannel {
  override val name: String
    get() = javaChannel.name

  override val presence: RestPresence
    get() = RestPresenceAdapter(javaChannel.presence)

  override fun publish(name: String?, data: Any?) = javaChannel.publish(name, data)

  override fun publish(messages: List<Message>) = javaChannel.publish(messages.toTypedArray())

  override fun publishAsync(name: String?, data: Any?, listener: CompletionListener) =
    javaChannel.publishAsync(name, data, listener)

  override fun publishAsync(messages: List<Message>, listener: CompletionListener) =
    javaChannel.publishAsync(messages.toTypedArray(), listener)

  override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<Message> =
    javaChannel.history(buildHistoryParams(start, end, limit, orderBy).toTypedArray())

  override fun historyAsync(
    callback: Callback<AsyncPaginatedResult<Message>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
  ) =
    javaChannel.historyAsync(buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}
