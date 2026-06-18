package io.ably.lib.`object`

import io.ably.lib.`object`.adapter.AblyClientAdapter
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.ChannelMode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps [onUnsubscribe] in a [Subscription] that runs the cleanup at most once; further
 * calls are no-ops. Use it wherever a [Subscription] is returned: `EventEmitter.off` is
 * `synchronized`, so this avoids re-acquiring that lock (and re-running teardown) on
 * repeated unsubscribe calls. Thread-safe.
 *
 * Spec: SUB2a, SUB2b
 */
internal fun onceSubscription(onUnsubscribe: () -> Unit): Subscription {
  val unsubscribed = AtomicBoolean(false)
  return Subscription {
    if (unsubscribed.compareAndSet(false, true)) {
      onUnsubscribe()
    }
  }
}

/**
 * Validates that the channel is configured for the access (read/subscribe) API: it must be
 * attachable (not detached/failed) and have the `object_subscribe` mode. Copied from the
 * legacy `io.ably.lib.objects` helpers so this package has no dependency on that package.
 *
 * Spec: RTO25
 */
internal fun AblyClientAdapter.throwIfInvalidAccessApiConfiguration(channelName: String) {
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed))
  throwIfMissingChannelMode(channelName, ChannelMode.object_subscribe)
}

/**
 * Validates that the channel is configured for the write (mutation) API: message echo must be
 * enabled, the channel must be usable (not detached/failed/suspended) and have the
 * `object_publish` mode.
 *
 * Spec: RTO26
 */
internal fun AblyClientAdapter.throwIfInvalidWriteApiConfiguration(channelName: String) {
  throwIfEchoMessagesDisabled()
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed, ChannelState.suspended))
  throwIfMissingChannelMode(channelName, ChannelMode.object_publish)
}

/**
 * Resolves the effective channel modes: the attached `modes` if present, otherwise the
 * user-provided channel options as a best effort.
 *
 * Spec: RTO2a, RTO2b
 */
private fun AblyClientAdapter.getChannelModes(channelName: String): Array<ChannelMode>? {
  val channel = getChannel(channelName)
  channel.modes?.let { modes -> if (modes.isNotEmpty()) return modes } // RTO2a
  channel.options?.let { options -> if (options.hasModes()) return options.modes } // RTO2b
  return null
}

// Spec: RTO2a2, RTO2b2
private fun AblyClientAdapter.throwIfMissingChannelMode(channelName: String, channelMode: ChannelMode) {
  val channelModes = getChannelModes(channelName)
  if (channelModes == null || !channelModes.contains(channelMode)) {
    throw objectException(
      "\"${channelMode.name}\" channel mode must be set for this operation",
      ObjectErrorCode.ChannelModeRequired,
    )
  }
}

private fun AblyClientAdapter.throwIfInChannelState(channelName: String, channelStates: Array<ChannelState>) {
  val currentState = getChannel(channelName).state
  if (currentState == null || channelStates.contains(currentState)) {
    throw objectException("Channel is in invalid state: $currentState", ObjectErrorCode.ChannelStateError)
  }
}

private fun AblyClientAdapter.throwIfEchoMessagesDisabled() {
  if (!clientOptions.echoMessages) {
    throw objectException(
      "\"echoMessages\" client option must be enabled for this operation",
      ObjectErrorCode.BadRequest,
    )
  }
}
