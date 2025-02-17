package com.ably.pubsub

interface RestClient : Client {

  /**
   * Collection of [RestChannel] instances currently managed by the client
   */
  override val channels: Channels<out RestChannel>
}
