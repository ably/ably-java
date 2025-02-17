package com.ably.pubsub

import io.ably.lib.types.*

interface RestPresence : Presence {

  /**
   * Retrieves the current members present on the channel and the metadata for each member,
   * such as their [io.ably.lib.types.PresenceMessage.Action] and ID. Returns a [PaginatedResult] object,
   * containing an array of [PresenceMessage] objects.
   *
   * Spec: RSPa
   *
   *  @param limit (RSP3a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
   *  @param clientId (RSP3a2) - Filters the list of returned presence members by a specific client using its ID.
   *  @param connectionId (RSP3a3) - Filters the list of returned presence members by a specific connection using its ID.
   *  @return A [PaginatedResult] object containing an array of [PresenceMessage] objects.
   */
  fun get(limit: Int = 100, clientId: String? = null, connectionId: String? = null): PaginatedResult<PresenceMessage>

  /**
   * Asynchronously retrieves the current members present on the channel and the metadata for each member,
   * such as their [io.ably.lib.types.PresenceMessage.Action] and ID. Returns a [PaginatedResult] object,
   * containing an array of [PresenceMessage] objects.
   *
   * Spec: RSPa
   *
   * @param limit (RSP3a) - An upper limit on the number of messages returned. The default is 100, and the maximum is 1000.
   * @param clientId (RSP3a2) - Filters the list of returned presence members by a specific client using its ID.
   * @param connectionId (RSP3a3) - Filters the list of returned presence members by a specific connection using its ID.
   * @param callback A Callback returning [AsyncPaginatedResult] object containing an array of [PresenceMessage] objects.
   * This callback is invoked on a background thread.
   */
  fun getAsync(callback: Callback<AsyncPaginatedResult<PresenceMessage>>, limit: Int = 100, clientId: String? = null, connectionId: String? = null)

}
