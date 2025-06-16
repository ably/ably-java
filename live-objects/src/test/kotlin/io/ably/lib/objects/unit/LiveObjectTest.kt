package io.ably.lib.objects.unit

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertNotNull

class LiveObjectTest {
  @Test
  fun testChannelObjectGetterTest() = runTest {
    val channel = getMockRealtimeChannel("test-channel")
    val objects = channel.objects
    assertNotNull(objects)
  }
}
