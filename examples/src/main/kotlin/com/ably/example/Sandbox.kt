package com.ably.example

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.network.sockets.*
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

private val client = HttpClient(CIO) {
  install(HttpRequestRetry) {
    maxRetries = 5
    retryIf { _, response ->
      !response.status.isSuccess()
    }
    retryOnExceptionIf { _, cause ->
      cause is ConnectTimeoutException ||
        cause is HttpRequestTimeoutException ||
        cause is SocketTimeoutException
    }
    exponentialDelay()
  }
}

class Sandbox private constructor(val appId: String, val apiKey: String) {
  companion object {
    private var cachedInstance: Sandbox? = null

    suspend fun createInstance(): Sandbox {
      val response: HttpResponse = client.post("https://sandbox.realtime.ably-nonprod.net/apps") {
        contentType(ContentType.Application.Json)
        setBody(loadAppCreationRequestBody().toString())
      }
      val body = JsonParser.parseString(response.bodyAsText())

      return Sandbox(
        appId = body.asJsonObject["appId"].asString,
        apiKey = body.asJsonObject["keys"].asJsonArray[0].asJsonObject["keyStr"].asString,
      )
    }

    suspend fun getInstance(): Sandbox {
      cachedInstance?.let { return it }
      val created = createInstance()
      cachedInstance = created
      return created
    }
  }
}

private suspend fun loadAppCreationRequestBody(): JsonElement =
  JsonParser.parseString(
    client.get("https://raw.githubusercontent.com/ably/ably-common/refs/heads/main/test-resources/test-app-setup.json") {
      contentType(ContentType.Application.Json)
    }.bodyAsText(),
  ).asJsonObject.get("post_apps")
