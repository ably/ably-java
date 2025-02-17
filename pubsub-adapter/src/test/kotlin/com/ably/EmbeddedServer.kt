package com.ably

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.ByteArrayInputStream

data class Request(
  val path: String,
  val params: Map<String, String> = emptyMap(),
  val headers: Map<String, String> = emptyMap(),
)

data class Response(
  val mimeType: String,
  val data: ByteArray,
)

fun json(json: String): Response = Response(
  mimeType = "application/json",
  data = json.toByteArray(),
)

fun interface RequestHandler {
  fun handle(request: Request): Response
}

class EmbeddedServer(port: Int, private val requestHandler: RequestHandler? = null) : NanoHTTPD(port) {
  private val _servedRequests = MutableSharedFlow<Request>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  val servedRequests: Flow<Request> = _servedRequests

  override fun serve(session: IHTTPSession): Response {
    val request = Request(
      path = session.uri,
      params = session.parms,
      headers = session.headers,
    )
    _servedRequests.tryEmit(request)
    val response = requestHandler?.handle(request)
    return response?.toNanoHttp() ?: newFixedLengthResponse("<!DOCTYPE html><title>404</title>")
  }
}

private fun Response.toNanoHttp(): NanoHTTPD.Response = NanoHTTPD.newFixedLengthResponse(
  NanoHTTPD.Response.Status.OK,
  mimeType,
  ByteArrayInputStream(data),
  data.size.toLong(),
)
