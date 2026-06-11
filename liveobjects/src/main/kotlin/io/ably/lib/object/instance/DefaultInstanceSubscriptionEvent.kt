package io.ably.lib.`object`.instance

import io.ably.lib.`object`.message.ObjectMessage

/**
 * Event delivered to [InstanceListener]s.
 *
 * Spec: RTINS16e
 */
internal class DefaultInstanceSubscriptionEvent(
  private val instance: Instance,
  private val message: ObjectMessage?,
) : InstanceSubscriptionEvent {

  override fun getObject(): Instance = instance // RTINS16e1

  override fun getMessage(): ObjectMessage? = message // RTINS16e2
}
