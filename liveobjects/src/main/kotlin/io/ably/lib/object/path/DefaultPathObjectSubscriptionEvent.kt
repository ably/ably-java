package io.ably.lib.`object`.path

import io.ably.lib.`object`.message.ObjectMessage

/**
 * Default implementation of [PathObjectSubscriptionEvent], the event delivered to a
 * [PathObjectListener] when a change affects the subscribed path. A plain holder for the
 * changed [PathObject] and the source [ObjectMessage] (if any).
 *
 * Spec: RTPO19e / RTTS3d
 */
internal class DefaultPathObjectSubscriptionEvent(
  private val objectAt: PathObject,
  private val message: ObjectMessage?,
) : PathObjectSubscriptionEvent {

  override fun getObject(): PathObject = objectAt

  override fun getMessage(): ObjectMessage? = message
}
