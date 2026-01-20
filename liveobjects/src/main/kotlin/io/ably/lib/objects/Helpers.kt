package io.ably.lib.objects

import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.Callback
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.PublishResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Spec: RTO15g
 */
internal suspend fun ObjectsAdapter.sendAsync(message: ProtocolMessage): PublishResult = suspendCancellableCoroutine { continuation ->
  try {
    connectionManager.send(message, clientOptions.queueMessages, object : Callback<PublishResult> {
      override fun onSuccess(result: PublishResult) {
        continuation.resume(result)
      }

      override fun onError(reason: ErrorInfo) {
        continuation.resumeWithException(ablyException(reason))
      }
    })
  } catch (e: Exception) {
    continuation.resumeWithException(e)
  }
}

internal suspend fun ObjectsAdapter.attachAsync(channelName: String) = suspendCancellableCoroutine { continuation ->
  try {
    getChannel(channelName).attach(object : CompletionListener {
      override fun onSuccess() {
        continuation.resume(Unit)
      }

      override fun onError(reason: ErrorInfo) {
        continuation.resumeWithException(ablyException(reason))
      }
    })
  } catch (e: Exception) {
    continuation.resumeWithException(e)
  }
}

/**
 * Retrieves the channel modes for a specific channel.
 * This method returns the modes that are set for the specified channel.
 *
 * @param channelName the name of the channel for which to retrieve the modes
 * @return the array of channel modes for the specified channel, or null if the channel is not found
 * Spec: RTO2a, RTO2b
 */
internal fun ObjectsAdapter.getChannelModes(channelName: String): Array<ChannelMode>? {
  val channel = getChannel(channelName)

  // RTO2a - channel.modes is only populated on channel attachment, so use it only if it is set
  channel.modes?.let { modes ->
    if (modes.isNotEmpty()) {
      return modes
    }
  }

  // RTO2b - otherwise as a best effort use user provided channel options
  channel.options?.let { options ->
    if (options.hasModes()) {
      return options.modes
    }
  }
  return null
}

/**
 * Spec: RTO15d
 */
internal fun ObjectsAdapter.ensureMessageSizeWithinLimit(objectMessages: Array<ObjectMessage>) {
  val maximumAllowedSize = connectionManager.maxMessageSize
  val objectsTotalMessageSize = objectMessages.sumOf { it.size() }
  if (objectsTotalMessageSize > maximumAllowedSize) {
    throw ablyException("ObjectMessages size $objectsTotalMessageSize exceeds maximum allowed size of $maximumAllowedSize bytes",
      ErrorCode.MaxMessageSizeExceeded)
  }
}

internal fun ObjectsAdapter.setChannelSerial(channelName: String, protocolMessage: ProtocolMessage) {
  if (protocolMessage.action != ProtocolMessage.Action.`object`) return
  val channelSerial = protocolMessage.channelSerial
  if (channelSerial.isNullOrEmpty()) return
  getChannel(channelName).properties.channelSerial = channelSerial
}

internal suspend fun ObjectsAdapter.ensureAttached(channelName: String) {
  val channel = getChannel(channelName)
  when (val currentChannelStatus = channel.state) {
    ChannelState.initialized -> attachAsync(channelName)
    ChannelState.attached -> return
    ChannelState.attaching -> {
      val attachDeferred = CompletableDeferred<Unit>()
      getChannel(channelName).once {
        when(it.current) {
          ChannelState.attached -> attachDeferred.complete(Unit)
          else -> {
            val exception = ablyException("Channel $channelName is in invalid state: ${it.current}, " +
              "error: ${it.reason}", ErrorCode.ChannelStateError)
            attachDeferred.completeExceptionally(exception)
          }
        }
      }
      if (channel.state == ChannelState.attached) {
        attachDeferred.complete(Unit)
      }
      attachDeferred.await()
    }
    else ->
      throw ablyException("Channel $channelName is in invalid state: $currentChannelStatus", ErrorCode.ChannelStateError)
  }
}

// Spec: RTLO4b1, RTLO4b2
internal fun ObjectsAdapter.throwIfInvalidAccessApiConfiguration(channelName: String) {
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed))
  throwIfMissingChannelMode(channelName, ChannelMode.object_subscribe)
}

internal fun ObjectsAdapter.throwIfInvalidWriteApiConfiguration(channelName: String) {
  throwIfEchoMessagesDisabled()
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed, ChannelState.suspended))
  throwIfMissingChannelMode(channelName, ChannelMode.object_publish)
}

internal fun ObjectsAdapter.throwIfUnpublishableState(channelName: String) {
  if (!connectionManager.isActive) {
    throw ablyException(connectionManager.stateErrorInfo)
  }
  throwIfInChannelState(channelName, arrayOf(ChannelState.failed, ChannelState.suspended))
}

// Spec: RTO2
private fun ObjectsAdapter.throwIfMissingChannelMode(channelName: String, channelMode: ChannelMode) {
  val channelModes = getChannelModes(channelName)
  if (channelModes == null || !channelModes.contains(channelMode)) {
    // Spec: RTO2a2, RTO2b2
    throw ablyException("\"${channelMode.name}\" channel mode must be set for this operation", ErrorCode.ChannelModeRequired)
  }
}

private fun ObjectsAdapter.throwIfInChannelState(channelName: String, channelStates: Array<ChannelState>) {
  val currentState = getChannel(channelName).state
  if (currentState == null || channelStates.contains(currentState)) {
    throw ablyException("Channel is in invalid state: $currentState", ErrorCode.ChannelStateError)
  }
}

internal fun ObjectsAdapter.throwIfEchoMessagesDisabled() {
   if (!clientOptions.echoMessages) {
     throw clientError("\"echoMessages\" client option must be enabled for this operation")
   }
}

internal class Binary(val data: ByteArray) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Binary) return false
    return data.contentEquals(other.data)
  }

  override fun hashCode(): Int {
    return data.contentHashCode()
  }
}

internal fun Binary.size(): Int {
  return data.size
}

internal data class CounterCreatePayload(
  val counter: ObjectsCounter
)

internal data class MapCreatePayload(
  val map: ObjectsMap
)
