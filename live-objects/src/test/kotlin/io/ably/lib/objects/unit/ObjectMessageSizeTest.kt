package io.ably.lib.objects.unit

import com.google.gson.JsonObject
import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMapOp
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectValue
import io.ably.lib.objects.ensureMessageSizeWithinLimit
import io.ably.lib.objects.size
import io.ably.lib.transport.Defaults
import io.ably.lib.types.AblyException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.text.toByteArray

class ObjectMessageSizeTest {

  @Test
  fun testObjectMessageSizeWithinLimit() = runTest {
    val mockAdapter = mockk<LiveObjectsAdapter>()
    every { mockAdapter.maxMessageSizeLimit() } returns Defaults.maxMessageSize // 64 kb
    assertEquals(65536, mockAdapter.maxMessageSizeLimit())

    // ObjectMessage with all size-contributing fields
    val objectMessage = ObjectMessage(
      id = "msg_12345", // Not counted in size calculation
      timestamp = 1699123456789L, // Not counted in size calculation
      clientId = "test-client", // Size: 11 bytes (UTF-8 byte length)
      connectionId = "conn_98765", // Not counted in size calculation
      extras = JsonObject().apply { // Size: JSON serialization byte length
        addProperty("meta", "data") // JSON: {"meta":"data","count":42}
        addProperty("count", 42)
      }, // Total extras size: 26 bytes (verified by gson.toJson().length)
      operation = ObjectOperation(
        action = ObjectOperationAction.MapCreate,
        objectId = "obj_54321", // Not counted in operation size

        // MapOp contributes to operation size
        mapOp = ObjectMapOp(
          key = "mapKey", // Size: 6 bytes (UTF-8 byte length)
          data = ObjectData(
            objectId = "ref_obj", // Not counted in data size
            value = ObjectValue("sample") // Size: 6 bytes (UTF-8 byte length)
          ) // Total ObjectData size: 6 bytes
        ), // Total ObjectMapOp size: 6 + 6 = 12 bytes

        // CounterOp contributes to operation size
        counterOp = ObjectCounterOp(
          amount = 10.5 // Size: 8 bytes (number is always 8 bytes)
        ), // Total ObjectCounterOp size: 8 bytes

        // Map contributes to operation size (for MAP_CREATE operations)
        map = ObjectMap(
          semantics = MapSemantics.LWW, // Not counted in size
          entries = mapOf(
            "entry1" to ObjectMapEntry( // Key size: 6 bytes
              tombstone = false, // Not counted in entry size
              timeserial = "ts_123", // Not counted in entry size
              data = ObjectData(
                value = ObjectValue("value1") // Size: 6 bytes
              ) // ObjectMapEntry size: 6 bytes
            ), // Total for this entry: 6 (key) + 6 (entry) = 12 bytes
            "entry2" to ObjectMapEntry( // Key size: 6 bytes
              data = ObjectData(
                value = ObjectValue(42) // Size: 8 bytes (number)
              ) // ObjectMapEntry size: 8 bytes
            ) // Total for this entry: 6 (key) + 8 (entry) = 14 bytes
          ) // Total entries size: 12 + 14 = 26 bytes
        ), // Total ObjectMap size: 26 bytes

        // Counter contributes to operation size (for COUNTER_CREATE operations)
        counter = ObjectCounter(
          count = 100.0 // Size: 8 bytes (number is always 8 bytes)
        ), // Total ObjectCounter size: 8 bytes

        nonce = "nonce123", // Not counted in operation size
        initialValue = Binary("some-value".toByteArray()), // Not counted in operation size
        initialValueEncoding = ProtocolMessageFormat.Json // Not counted in operation size
      ), // Total ObjectOperation size: 12 + 8 + 26 + 8 = 54 bytes

      objectState = ObjectState(
        objectId = "state_obj", // Not counted in state size
        siteTimeserials = mapOf("site1" to "serial1"), // Not counted in state size
        tombstone = false, // Not counted in state size

        // createOp contributes to state size
        createOp = ObjectOperation(
          action = ObjectOperationAction.MapSet,
          objectId = "create_obj",
          mapOp = ObjectMapOp(
            key = "createKey", // Size: 9 bytes
            data = ObjectData(
              value = ObjectValue("createValue") // Size: 11 bytes
            ) // ObjectData size: 11 bytes
          ) // ObjectMapOp size: 9 + 11 = 20 bytes
        ), // Total createOp size: 20 bytes

        // map contributes to state size
        map = ObjectMap(
          entries = mapOf(
            "stateKey" to ObjectMapEntry( // Key size: 8 bytes
              data = ObjectData(
                value = ObjectValue("stateValue") // Size: 10 bytes
              ) // ObjectMapEntry size: 10 bytes
            ) // Total: 8 + 10 = 18 bytes
          )
        ), // Total ObjectMap size: 18 bytes

        // counter contributes to state size
        counter = ObjectCounter(
          count = 50.0 // Size: 8 bytes
        ) // Total ObjectCounter size: 8 bytes
      ), // Total ObjectState size: 20 + 18 + 8 = 46 bytes

      serial = "serial_123", // Not counted in size calculation
      siteCode = "site_abc" // Not counted in size calculation
    )

    // clientId: 11 bytes + operation: 54 bytes + objectState: 46 bytes + extras: 26 bytes = 137 bytes
    val messageSize = objectMessage.size()
    assertEquals(137, messageSize)

    // Verify the message doesn't exceed the maxMessageSize limit
    mockAdapter.ensureMessageSizeWithinLimit(arrayOf(objectMessage))
  }

  @Test
  fun testObjectMessageSizeForUnicodeCharacters() = runTest {
    val objectMessage = ObjectMessage(
      operation = ObjectOperation(
        objectId = "",
        action = ObjectOperationAction.MapCreate,
        mapOp = ObjectMapOp(
          key = "",
          data = ObjectData(
            value = ObjectValue("你😊") // 你 -> 3 bytes, 😊 -> 4 bytes
          ),
        ),
      )
    )
    assertEquals(7, objectMessage.size())
  }

  @Test
  fun testObjectMessageSizeAboveLimit() = runTest {
    val mockAdapter = mockk<LiveObjectsAdapter>()
    every { mockAdapter.maxMessageSizeLimit() } returns Defaults.maxMessageSize // 64 kb

    // Create ObjectMessage with dummy data that results in size 60kb
    val objectMessage1 = ObjectMessage(
      clientId = CharArray(60 * 1024) { ('a'..'z').random() }.concatToString()
    )
    assertEquals(60 * 1024, objectMessage1.size())

    // Create ObjectMessage with dummy data that results in size 5kb
    val objectMessage2 = ObjectMessage(
      clientId = CharArray(5 * 1024) { ('a'..'z').random() }.concatToString()
    )
    assertEquals(5 * 1024, objectMessage2.size())

    val exception = assertFailsWith<AblyException> {
      mockAdapter.ensureMessageSizeWithinLimit(arrayOf(objectMessage1, objectMessage2)) // sum size = 65kb exceeds limit
    }
    // Assert on error code and message
    assertEquals(40009, exception.errorInfo.code)
    val expectedMessage = "ObjectMessage size 66560 exceeds maximum allowed size of 65536 bytes"
    assertEquals(expectedMessage, exception.errorInfo.message)
  }
}
