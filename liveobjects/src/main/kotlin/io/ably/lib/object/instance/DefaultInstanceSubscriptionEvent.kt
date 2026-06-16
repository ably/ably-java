package io.ably.lib.`object`.instance

import io.ably.lib.`object`.message.ObjectMessage

/**
 * Default implementation of [InstanceSubscriptionEvent], the event delivered to an
 * [InstanceListener] when the wrapped LiveObject is updated. A plain holder for the updated
 * [Instance] and the source [ObjectMessage] (if any).
 *
 * Spec: RTINS16e
 */
internal class DefaultInstanceSubscriptionEvent(
  private val objectAt: Instance,
  private val message: ObjectMessage?,
) : InstanceSubscriptionEvent {

  override fun getObject(): Instance = objectAt

  override fun getMessage(): ObjectMessage? = message
}
