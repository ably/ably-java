package io.ably.lib.objects

import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun LiveObjectsAdapter.sendAsync(message: ProtocolMessage) = suspendCancellableCoroutine { continuation ->
  try {
    this.send(message, object : CompletionListener {
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

internal fun LiveObjectsAdapter.ensureMessageSizeWithinLimit(objectMessages: Array<ObjectMessage>) {
  val maximumAllowedSize = maxMessageSizeLimit()
  val objectsTotalMessageSize = objectMessages.sumOf { it.size() }
  if (objectsTotalMessageSize > maximumAllowedSize) {
    throw ablyException("ObjectMessage size $objectsTotalMessageSize exceeds maximum allowed size of $maximumAllowedSize bytes",
      ErrorCode.MaxMessageSizeExceeded)
  }
}

internal fun LiveObjectsAdapter.setChannelSerial(channelName: String, protocolMessage: ProtocolMessage) {
  if (protocolMessage.action != ProtocolMessage.Action.`object`) return
  val channelSerial = protocolMessage.channelSerial
  if (channelSerial.isNullOrEmpty()) return
  setChannelSerial(channelName, channelSerial)
}

// Spec: RTLO4b1, RTLO4b2
internal fun LiveObjectsAdapter.throwIfInvalidAccessApiConfiguration(channelName: String) {
  throwIfMissingChannelMode(channelName, ChannelMode.object_subscribe)
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed))
}

internal fun LiveObjectsAdapter.throwIfInvalidWriteApiConfiguration(channelName: String) {
  throwIfEchoMessagesDisabled()
  throwIfMissingChannelMode(channelName, ChannelMode.object_publish)
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed, ChannelState.suspended))
}

// Spec: RTO2
internal fun LiveObjectsAdapter.throwIfMissingChannelMode(channelName: String, channelMode: ChannelMode) {
  val channelModes = getChannelModes(channelName)
  if (channelModes == null || !channelModes.contains(channelMode)) {
    // Spec: RTO2a2, RTO2b2
    throw ablyException("\"${channelMode.name}\" channel mode must be set for this operation", ErrorCode.ChannelModeRequired)
  }
}

internal fun LiveObjectsAdapter.throwIfInChannelState(channelName: String, channelStates: Array<ChannelState>) {
  val currentState = getChannelState(channelName)
  if (currentState == null || channelStates.contains(currentState)) {
    throw ablyException("Channel is in invalid state: $currentState", ErrorCode.ChannelStateError)
  }
}

internal fun LiveObjectsAdapter.throwIfEchoMessagesDisabled() {
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
