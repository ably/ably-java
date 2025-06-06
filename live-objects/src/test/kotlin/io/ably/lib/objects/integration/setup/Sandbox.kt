package io.ably.lib.objects.integration.setup

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ably.lib.objects.ablyException
import io.ably.lib.realtime.*
import io.ably.lib.types.ClientOptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CompletableDeferred

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
    private suspend fun loadAppCreationJson(): JsonElement =
      JsonParser.parseString(
        client.get("https://raw.githubusercontent.com/ably/ably-common/refs/heads/main/test-resources/test-app-setup.json") {
          contentType(ContentType.Application.Json)
        }.bodyAsText(),
      ).asJsonObject.get("post_apps")

    internal suspend fun createInstance(): Sandbox {
      val response: HttpResponse = client.post("https://sandbox.realtime.ably-nonprod.net/apps") {
        contentType(ContentType.Application.Json)
        setBody(loadAppCreationJson().toString())
      }
      val body = JsonParser.parseString(response.bodyAsText())

      return Sandbox(
        appId = body.asJsonObject["appId"].asString,
        // From JS chat repo at 7985ab7 — "The key we need to use is the one at index 5, which gives enough permissions to interact with Chat and Channels"
        apiKey = body.asJsonObject["keys"].asJsonArray[0].asJsonObject["keyStr"].asString,
      )
    }
  }
}


internal fun Sandbox.createRealtimeClient(options: ClientOptions.() -> Unit): AblyRealtime {
  val clientOptions = ClientOptions().apply {
    apply(options)
    key = apiKey
    environment = "sandbox"
  }
  return AblyRealtime(clientOptions)
}

internal suspend fun AblyRealtime.ensureConnected() {
  if (this.connection.state == ConnectionState.connected) {
    return
  }
  val connectedDeferred = CompletableDeferred<Unit>()
  this.connection.on {
    if (it.event == ConnectionEvent.connected) {
      connectedDeferred.complete(Unit)
      this.connection.off()
    } else if (it.event != ConnectionEvent.connecting) {
      connectedDeferred.completeExceptionally(ablyException(it.reason))
      this.connection.off()
      this.close()
    }
  }
  connectedDeferred.await()
}

internal suspend fun Channel.ensureAttached() {
  if (this.state == ChannelState.attached) {
    return
  }
  val attachedDeferred = CompletableDeferred<Unit>()
  this.on {
    if (it.event == ChannelEvent.attached) {
      attachedDeferred.complete(Unit)
      this.off()
    } else if (it.event != ChannelEvent.attaching) {
      attachedDeferred.completeExceptionally(ablyException(it.reason))
      this.off()
    }
  }
  attachedDeferred.await()
}
