package com.ably.pubsub

import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.Message

interface RestChannel : Channel {

  /**
   * Presence set for a channel.
   */
  override val presence: RestPresence

  /**
   * Publish a message on this channel
   *
   * @param name the event name
   * @param data the message payload; see [io.ably.types.Data] for details of supported data types.
   */
  fun publish(name: String? = null, data: Any? = null)

  /**
   * Publish list of messages on this channel. When there are
   * multiple messages to be sent, it is more efficient to use this
   * method to publish them in a single request, as compared with
   * publishing via multiple independent requests.
   *
   * @param messages list of messages to publish.
   */
  fun publish(messages: List<Message>)

  /**
   * Publish a message on this channel asynchronously
   *
   * @see [publish]
   */
  fun publishAsync(name: String? = null, data: Any? = null, listener: CompletionListener)

  /**
   * Publish list of messages on this channel asynchronously
   *
   * @see [publish]
   */
  fun publishAsync(messages: List<Message>, listener: CompletionListener)
}
