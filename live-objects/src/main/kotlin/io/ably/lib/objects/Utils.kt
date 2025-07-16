package io.ably.lib.objects

import io.ably.lib.types.AblyException
import io.ably.lib.types.Callback
import io.ably.lib.types.ErrorInfo
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
 * Executes a suspend function within a coroutine and handles the result via a callback.
 *
 * This utility bridges between coroutine-based implementation code and callback-based APIs.
 * It launches a coroutine in the current scope to execute the provided suspend block,
 * then routes the result or any error to the appropriate callback method.
 *
 * @param T The type of result expected from the operation
 * @param callback The callback to invoke with the operation result or error
 * @param block The suspend function to execute that returns a value of type T
 */
internal fun <T> CoroutineScope.launchWithCallback(callback: Callback<T>, block: suspend () -> T) {
  launch {
    try {
      val result = block()
      callback.onSuccess(result)
    } catch (throwable: Throwable) {
      val exception = throwable as? AblyException
      callback.onError(exception?.errorInfo)
    }
  }
}
