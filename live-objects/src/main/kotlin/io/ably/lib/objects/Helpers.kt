package io.ably.lib.objects

import io.ably.lib.realtime.CompletionListener
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

internal enum class ProtocolMessageFormat(private val value: String) {
  Msgpack("msgpack"),
  Json("json");

  override fun toString(): String = value
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
