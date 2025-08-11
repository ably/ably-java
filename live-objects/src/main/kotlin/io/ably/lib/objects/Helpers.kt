package io.ably.lib.objects

import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Spec: RTO15g
 */
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

internal suspend fun LiveObjectsAdapter.attachAsync(channelName: String) = suspendCancellableCoroutine { continuation ->
  try {
    this.getChannel(channelName).attach(object : CompletionListener {
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
 * Spec: RTO15d
 */
internal fun LiveObjectsAdapter.ensureMessageSizeWithinLimit(objectMessages: Array<ObjectMessage>) {
  val maximumAllowedSize = maxMessageSizeLimit()
  val objectsTotalMessageSize = objectMessages.sumOf { it.size() }
  if (objectsTotalMessageSize > maximumAllowedSize) {
    throw ablyException("ObjectMessages size $objectsTotalMessageSize exceeds maximum allowed size of $maximumAllowedSize bytes",
      ErrorCode.MaxMessageSizeExceeded)
  }
}

internal fun LiveObjectsAdapter.setChannelSerial(channelName: String, protocolMessage: ProtocolMessage) {
  if (protocolMessage.action != ProtocolMessage.Action.`object`) return
  val channelSerial = protocolMessage.channelSerial
  if (channelSerial.isNullOrEmpty()) return
  setChannelSerial(channelName, channelSerial)
}

internal suspend fun LiveObjectsAdapter.ensureAttached(channelName: String) {
  when (val currentChannelStatus = this.getChannelState(channelName)) {
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
      if (this.getChannelState(channelName) == ChannelState.attached) {
        attachDeferred.complete(Unit)
      }
      attachDeferred.await()
    }
    else ->
      throw ablyException("Channel $channelName is in invalid state: $currentChannelStatus", ErrorCode.ChannelStateError)
  }
}

// Spec: RTLO4b1, RTLO4b2
internal fun LiveObjectsAdapter.throwIfInvalidAccessApiConfiguration(channelName: String) {
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed))
  throwIfMissingChannelMode(channelName, ChannelMode.object_subscribe)
}

internal fun LiveObjectsAdapter.throwIfInvalidWriteApiConfiguration(channelName: String) {
  throwIfEchoMessagesDisabled()
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed, ChannelState.suspended))
  throwIfMissingChannelMode(channelName, ChannelMode.object_publish)
}

internal fun LiveObjectsAdapter.throwIfUnpublishableState(channelName: String) {
  if (!connectionManager.isActive) {
    throw ablyException(connectionManager.stateErrorInfo)
  }
  throwIfInChannelState(channelName, arrayOf(ChannelState.failed, ChannelState.suspended))
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

internal data class CounterCreatePayload(
  val counter: ObjectCounter
)

internal data class MapCreatePayload(
  val map: ObjectMap
)
