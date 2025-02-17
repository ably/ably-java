package io.ably.lib.rest

import com.ably.pubsub.RestPresence
import com.ably.query.OrderBy
import io.ably.lib.buildHistoryParams
import io.ably.lib.buildRestPresenceParams
import io.ably.lib.rest.ChannelBase.Presence
import io.ably.lib.types.AsyncPaginatedResult
import io.ably.lib.types.Callback
import io.ably.lib.types.PaginatedResult
import io.ably.lib.types.PresenceMessage

internal class RestPresenceAdapter(private val javaPresence: Presence) : RestPresence {
  override fun get(
    limit: Int,
    clientId: String?,
    connectionId: String?,
  ): PaginatedResult<PresenceMessage> =
    javaPresence.get(buildRestPresenceParams(limit, clientId, connectionId).toTypedArray())

  override fun getAsync(
    callback: Callback<AsyncPaginatedResult<PresenceMessage>>, limit: Int,
    clientId: String?,
    connectionId: String?,
  ) =
    javaPresence.getAsync(buildRestPresenceParams(limit, clientId, connectionId).toTypedArray(), callback)

  override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<PresenceMessage> =
    javaPresence.history(buildHistoryParams(start, end, limit, orderBy).toTypedArray())

  override fun historyAsync(
    callback: Callback<AsyncPaginatedResult<PresenceMessage>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
  ) =
    javaPresence.historyAsync(buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}
