package io.ably.lib.objects.unit

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.size
import io.ably.lib.types.AblyException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class LiveObjectTest {
  @Test
  fun testChannelObjectGetterTest() = runTest {
    val channel = getMockRealtimeChannel("test-channel")
    val objects = channel.objects
    assertNotNull(objects)
  }

  @Test
  fun testObjectMessageSizeWithinLimit() = runTest {
    val mockAdapter = mockk<LiveObjectsAdapter>()
    every { mockAdapter.maxMessageSizeLimit() } returns 65536L // 64 kb
    assertEquals(65536L, mockAdapter.maxMessageSizeLimit())

    // Create ObjectMessage with dummy data that results in size 60 kb which is within the limit
    val objectMessage = ObjectMessage(
      clientId = CharArray(60 * 1024) { ('a'..'z').random() }.concatToString()
    )
    assertEquals(60 * 1024L, objectMessage.size()) // 60 kb (61,440 characters)
    // Doesn't throw exception, so test doesn't fail
    mockAdapter.ensureMessageSizeWithinLimit(arrayOf(objectMessage))

    // Create ObjectMessage with dummy data that results in size 5kb
    val objectMessage2 = ObjectMessage(
      clientId = CharArray(5 * 1024) { ('a'..'z').random() }.concatToString()
    )
    assertEquals(5 * 1024L, objectMessage2.size()) // 5 kb

    val exception = assertFailsWith<AblyException> {
      // both messages together with size of 65kb exceed the limit
      mockAdapter.ensureMessageSizeWithinLimit(arrayOf(objectMessage, objectMessage2))
    }
    // Assert on error code and message
    assertEquals(40009, exception.errorInfo.code)
    val expectedMessage = "ObjectMessage size 66560 exceeds maximum allowed size of 65536 bytes"
    assertEquals(expectedMessage, exception.errorInfo.message)
  }
}
