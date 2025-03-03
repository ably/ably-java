package com.ably.pubsub

public interface RestClient : Client {

  /**
   * Collection of [RestChannel] instances currently managed by the client
   */
  override val channels: Channels<out RestChannel>
}
