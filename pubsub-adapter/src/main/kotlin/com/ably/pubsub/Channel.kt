package com.ably.pubsub

import com.ably.query.OrderBy
import io.ably.lib.types.*

/**
 * An interface representing a Channel in the Ably API.
 */
interface Channel {

  /**
   * The channel name.
   */
  val name: String

  /**
   * A [Presence] object.
   *
   *
   * Spec: RTL9
   */
  val presence: Presence

  /**
   * Obtain recent history for this channel using the REST API.
   * The history provided relates to all clients of this application,
   * not just this instance.
   *
   * @param start The start of the query interval as a time in milliseconds since the epoch.
   * A message qualifies as a member of the result set if it was received at or after this time. (default: beginning of time)
   * @param end The end of the query interval as a time in milliseconds since the epoch.
   * A message qualifies as a member of the result set if it was received at or before this time. (default: now)
   * @param limit The maximum number of records to return. A limit greater than 1,000 is invalid.
   * @param orderBy The direction of this query.
   *
   * @return Paginated result of Messages for this Channel.
   */
  fun history(
    start: Long? = null,
    end: Long? = null,
    limit: Int = 100,
    orderBy: OrderBy = OrderBy.NewestFirst,
  ): PaginatedResult<Message>

  /**
   * Asynchronously obtain recent history for this channel using the REST API.
   *
   * @param start The start of the query interval as a time in milliseconds since the epoch.
   * A message qualifies as a member of the result set if it was received at or after this time. (default: beginning of time)
   * @param end The end of the query interval as a time in milliseconds since the epoch.
   * A message qualifies as a member of the result set if it was received at or before this time. (default: now)
   * @param limit The maximum number of records to return. A limit greater than 1,000 is invalid.
   * @param orderBy The direction of this query.
   * @param callback  A Callback returning [AsyncPaginatedResult] object containing an array of [Message] objects.
   * Note: This callback is invoked on a background thread.
   */
  fun historyAsync(
    callback: Callback<AsyncPaginatedResult<Message>>,
    start: Long? = null,
    end: Long? = null,
    limit: Int = 100,
    orderBy: OrderBy = OrderBy.NewestFirst,
  )

}
