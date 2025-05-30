package io.ably.lib.objects.integration

import io.ably.lib.objects.integration.setup.IntegrationTest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertNotNull

open class LiveObjectTest : IntegrationTest() {

  @Test
  fun testChannelObjectGetterTest() = runTest {
    val channelName = generateChannelName()
    val channel = getRealtimeChannel(channelName)
    val objects = channel.objects
    assertNotNull(objects)
  }
}
