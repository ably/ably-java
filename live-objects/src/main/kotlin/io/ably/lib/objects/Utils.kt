package io.ably.lib.objects

import io.ably.lib.types.AblyException
import io.ably.lib.types.Callback
import io.ably.lib.types.ErrorInfo
import io.ably.lib.util.Log
import kotlinx.coroutines.*

internal fun ablyException(
  errorMessage: String,
  errorCode: ErrorCode,
  statusCode: HttpStatusCode = HttpStatusCode.BadRequest,
  cause: Throwable? = null,
): AblyException {
  val errorInfo = createErrorInfo(errorMessage, errorCode, statusCode)
  return createAblyException(errorInfo, cause)
}

internal fun ablyException(
  errorInfo: ErrorInfo,
  cause: Throwable? = null,
): AblyException = createAblyException(errorInfo, cause)

private fun createErrorInfo(
  errorMessage: String,
  errorCode: ErrorCode,
  statusCode: HttpStatusCode,
) = ErrorInfo(errorMessage, statusCode.code, errorCode.code)

private fun createAblyException(
  errorInfo: ErrorInfo,
  cause: Throwable?,
) = cause?.let { AblyException.fromErrorInfo(it, errorInfo) }
  ?: AblyException.fromErrorInfo(errorInfo)

internal fun clientError(errorMessage: String) = ablyException(errorMessage, ErrorCode.BadRequest, HttpStatusCode.BadRequest)

internal fun serverError(errorMessage: String) = ablyException(errorMessage, ErrorCode.InternalError, HttpStatusCode.InternalServerError)

internal fun objectError(errorMessage: String, cause: Throwable? = null): AblyException {
  return ablyException(errorMessage, ErrorCode.InvalidObject, HttpStatusCode.InternalServerError, cause)
}
/**
 * Calculates the byte size of a string.
 * For non-ASCII, the byte size can be 2–4x the character count. For ASCII, there is no difference.
 * e.g. "Hello" has a byte size of 5, while "你" has a byte size of 3 and "😊" has a byte size of 4.
 */
internal val String.byteSize: Int
  get() = this.toByteArray(Charsets.UTF_8).size

/**
 * A global coroutine scope for executing callbacks asynchronously.
 * Provides safe execution of suspend functions with results delivered via callbacks,
 * with proper error handling for both the execution and callback invocation.
 */
internal object GlobalCallbackScope {
  private const val TAG = "GlobalCallbackScope"
  private val scope =
    CoroutineScope(Dispatchers.Default + CoroutineName("LiveObjects-GlobalCallbackScope") + SupervisorJob())

  internal fun <T> launchWithCallback(callback: Callback<T>, block: suspend () -> T) {
    scope.launch {
      try {
        val result = block()
        try { callback.onSuccess(result) } catch (t: Throwable) {
          Log.e(TAG, "Error occurred while executing callback's onSuccess handler", t)
        } // catch and don't rethrow error from callback
      } catch (throwable: Throwable) {
        val exception = throwable as? AblyException
        callback.onError(exception?.errorInfo)
      }
    }
  }
}
