package com.ably.pubsub

import io.ably.lib.realtime.Connection

/**
 * A client that extends the functionality of the {@link Client} and provides additional realtime-specific features.
 *
 * This class implements {@link AutoCloseable} so you can use it in
 * try-with-resources constructs and have the JDK close it for you.
 */
interface RealtimeClient : Client {

  /**
   * The {@link Connection} object for this instance.
   * <p>
   * Spec: RTC2
   */
  val connection: Connection

  /**
   * Collection of [RealtimeChannel] instances currently managed by Realtime client
   */
  override val channels: Channels<out RealtimeChannel>
}
