package io.ably.lib.objects

import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo

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

/**
 * Calculates the byte size of a string.
 * For non-ASCII, the byte size can be 2–4x the character count. For ASCII, there is no difference.
 */
internal val String.byteSize: Int
  get() = this.toByteArray(Charsets.UTF_8).size
