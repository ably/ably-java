package com.ably.pubsub

import app.cash.turbine.test
import com.ably.EmbeddedServer
import com.ably.json
import com.ably.pubsub.SdkWrapperAgentHeaderTest.Companion.PORT
import com.ably.waitFor
import fi.iki.elonen.NanoHTTPD
import io.ably.lib.BuildConfig
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.RealtimeClient
import io.ably.lib.rest.AblyRest
import io.ably.lib.rest.RestClient
import io.ably.lib.types.ClientOptions
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals

class SdkWrapperAgentHeaderTest {

  @Test
  fun `should use additional agents in Realtime wrapper SDK client calls`() = runTest {
    val realtimeClient = createRealtimeClient()

    val wrapperSdkClient =
      realtimeClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    server.servedRequests.test {
      wrapperSdkClient.time()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      realtimeClient.time()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      wrapperSdkClient.request("/time")
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }
  }

  @Test
  fun `should use additional agents in Rest wrapper SDK client calls`() = runTest {
    val restClient = createRealtimeClient()

    val wrapperSdkClient =
      restClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    server.servedRequests.test {
      wrapperSdkClient.time()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      restClient.time()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      wrapperSdkClient.request("/time")
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }
  }

  @Test
  fun `should use additional agents in Rest wrapper SDK channel calls`() = runTest {
    val restClient = createRestClient()

    val wrapperSdkClient =
      restClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    server.servedRequests.test {
      wrapperSdkClient.channels.get("test").history()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      restClient.channels.get("test").history()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      wrapperSdkClient.channels.get("test").presence.history()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }
  }

  @Test
  fun `should use additional agents in Realtime wrapper SDK channel calls`() = runTest {
    val realtimeClient = createRealtimeClient()

    val wrapperSdkClient =
      realtimeClient.createWrapperSdkProxy(WrapperSdkProxyOptions(agents = mapOf("chat-android" to "0.1.0")))

    server.servedRequests.test {
      wrapperSdkClient.channels.get("test").history()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      realtimeClient.channels.get("test").history()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }

    server.servedRequests.test {
      wrapperSdkClient.channels.get("test").presence.history()
      assertEquals(
        setOf("ably-java/${BuildConfig.VERSION}", "jre/${System.getProperty("java.version")}", "chat-android/0.1.0"),
        awaitItem().headers["ably-agent"]?.split(" ")?.toSet(),
      )
    }
  }

  companion object {

    const val PORT = 27332
    lateinit var server: EmbeddedServer

    @JvmStatic
    @BeforeAll
    fun setUp() = runTest {
      server = EmbeddedServer(PORT) {
        when (it.path) {
          "/time" -> json("[1739551931167]")
          else -> json("[]")
        }
      }
      server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
      waitFor { server.wasStarted() }
    }

    @JvmStatic
    @AfterAll
    fun tearDown() {
      server.stop()
    }
  }
}

private fun createRealtimeClient(): RealtimeClient {
  val options = ClientOptions("xxxxx:yyyyyyy").apply {
    port = PORT
    useBinaryProtocol = false
    realtimeHost = "localhost"
    restHost = "localhost"
    tls = false
    autoConnect = false
  }

  return RealtimeClient(AblyRealtime(options))
}

private fun createRestClient(): RestClient {
  val options = ClientOptions("xxxxx:yyyyyyy").apply {
    port = PORT
    useBinaryProtocol = false
    realtimeHost = "localhost"
    restHost = "localhost"
    tls = false
    autoConnect = false
  }

  return RestClient(AblyRest(options))
}
