package io.ably.lib.`object`.path

import io.ably.lib.`object`.message.ObjectMessage

/**
 * Event delivered to [PathObjectListener]s.
 *
 * Spec: RTPO19e
 */
internal class DefaultPathObjectSubscriptionEvent(
  private val pathObject: PathObject,
  private val message: ObjectMessage?,
) : PathObjectSubscriptionEvent {

  override fun getObject(): PathObject = pathObject // RTPO19e1

  override fun getMessage(): ObjectMessage? = message // RTPO19e2
}
