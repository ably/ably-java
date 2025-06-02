package io.ably.lib.objects.unit

import io.ably.lib.objects.unit.setup.UnitTest
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertNotNull

class LiveObjectTest : UnitTest() {
  @Test
  fun testChannelObjectGetterTest() = runTest {
    val channel = getMockRealtimeChannel("test-channel")
    val objects = channel.objects
    assertNotNull(objects)
  }
}
