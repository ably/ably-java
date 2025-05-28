package io.ably.lib.objects

import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import kotlinx.coroutines.CompletableDeferred

internal suspend fun LiveObjectsAdapter.sendAsync(message: ProtocolMessage) {
  val deferred = CompletableDeferred<Unit>()
  try {
    this.send(message, object : CompletionListener {
      override fun onSuccess() {
        deferred.complete(Unit)
      }

      override fun onError(reason: ErrorInfo) {
        deferred.completeExceptionally(Exception(reason.message))
      }
    })
  } catch (e: Exception) {
    deferred.completeExceptionally(e)
  }
  deferred.await()
}

internal enum class ProtocolMessageFormat(private val value: String) {
  Msgpack("msgpack"),
  Json("json");

  override fun toString(): String = value
}

internal class Binary(val data: ByteArray?) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Binary) return false
    return data?.contentEquals(other.data) == true
  }

  override fun hashCode(): Int {
    return data?.contentHashCode() ?: 0
  }
}
