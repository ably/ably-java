package io.ably.lib.objects.integration.setup

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.UUID

@RunWith(Parameterized::class)
open class IntegrationTest {
  @Parameterized.Parameter
  lateinit var testParams: String

  @JvmField
  @Rule
  val timeout: Timeout = Timeout.seconds(10)

  private val realtimeClients = mutableMapOf<String, AblyRealtime>()

  /**
   * Retrieves a realtime channel for the specified channel name and client ID
   * If a client with the given clientID does not exist, a new client is created using the provided options.
   * The channel is attached and ensured to be in the attached state before returning.
   *
   * @param channelName Name of the channel
   * @param clientId The ID of the client to use or create. Defaults to "client1".
   * @return The attached realtime channel.
   * @throws Exception If the channel fails to attach or the client fails to connect.
   */
  internal suspend fun getRealtimeChannel(channelName: String, clientId: String = "client1"): Channel {
    val client = realtimeClients.getOrPut(clientId) {
      sandbox.createRealtimeClient {
        this.clientId = clientId
        useBinaryProtocol = testParams == "msgpack_protocol"
      }. apply { ensureConnected() }
    }
    return client.channels.get(channelName).apply {
      attach()
      ensureAttached()
    }
  }

  /**
   * Generates a unique channel name for testing purposes.
   * This is mainly to avoid channel name/state/history collisions across tests in same file.
   */
  internal fun generateChannelName(): String {
    return "test-channel-${UUID.randomUUID()}"
  }

  @After
  fun afterEach() {
    for (ablyRealtime in realtimeClients.values) {
      for ((channelName, channel) in ablyRealtime.channels.entrySet()) {
        channel.off()
        ablyRealtime.channels.release(channelName)
      }
      ablyRealtime.close()
    }
    realtimeClients.clear()
  }

  companion object {
    private lateinit var sandbox: Sandbox

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Iterable<String> {
      return listOf("msgpack_protocol", "json_protocol")
    }

    @JvmStatic
    @BeforeClass
    @Throws(Exception::class)
    fun setUpBeforeClass() {
      runBlocking {
        sandbox = Sandbox.createInstance()
      }
    }

    @JvmStatic
    @AfterClass
    @Throws(Exception::class)
    fun tearDownAfterClass() {
    }
  }
}
