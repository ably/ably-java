package io.ably.lib.objects

internal enum class ErrorCode(public val code: Int) {
  BadRequest(40_000),
  InternalError(50_000),
}

internal enum class HttpStatusCode(public val code: Int) {
  BadRequest(400),
  InternalServerError(500),
}
