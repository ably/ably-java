package io.ably.lib.objects

internal enum class ErrorCode(public val code: Int) {
  BadRequest(40_000),
  InternalError(50_000),
  MaxMessageSizeExceeded(40_009),
  InvalidObject(92_000),
  // LiveMap specific error codes
  MapKeyShouldBeString(40_003),
  MapValueDataTypeUnsupported(40_013),
  // Channel mode and state validation error codes
  ChannelModeRequired(40_024),
  ChannelStateError(90_001),
}

internal enum class HttpStatusCode(public val code: Int) {
  BadRequest(400),
  InternalServerError(500),
}
