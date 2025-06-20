package io.ably.lib.objects.unit

import io.ably.lib.objects.gson
import io.ably.lib.objects.msgpackMapper
import io.ably.lib.objects.unit.fixtures.*
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.ProtocolSerializer
import io.ably.lib.util.Serialisation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertNotNull

class ObjectMessageSerializationTest {

  private val objectMessages = arrayOf(
    dummyObjectMessageWithStringData(),
    dummyObjectMessageWithBinaryData(),
    dummyObjectMessageWithNumberData(),
    dummyObjectMessageWithBooleanData(),
    dummyObjectMessageWithJsonObjectData(),
    dummyObjectMessageWithJsonArrayData()
  )

  @Test
  fun testObjectMessageMsgPackSerialization() = runTest {
    val protocolMessage = ProtocolMessage()
    protocolMessage.action = ProtocolMessage.Action.`object`
    protocolMessage.state = objectMessages

    // Serialize the ProtocolMessage containing ObjectMessages to MsgPack format
    val serializedProtoMsg = ProtocolSerializer.writeMsgpack(protocolMessage)
    assertNotNull(serializedProtoMsg)

    // Deserialize back to ProtocolMessage
    val deserializedProtoMsg = ProtocolSerializer.readMsgpack(serializedProtoMsg)
    assertNotNull(deserializedProtoMsg)

    deserializedProtoMsg.state.zip(objectMessages).forEach { (actual, expected) ->
      assertEquals(expected, actual as? io.ably.lib.objects.ObjectMessage)
    }
  }

  @Test
  fun testObjectMessageJsonSerialization() = runTest {
    val protocolMessage = ProtocolMessage()
    protocolMessage.action = ProtocolMessage.Action.`object`
    protocolMessage.state = objectMessages

    // Serialize the ProtocolMessage containing ObjectMessages to MsgPack format
    val serializedProtoMsg = ProtocolSerializer.writeJSON(protocolMessage).toString(Charsets.UTF_8)
    assertNotNull(serializedProtoMsg)

    // Deserialize back to ProtocolMessage
    val deserializedProtoMsg = ProtocolSerializer.fromJSON(serializedProtoMsg)
    assertNotNull(deserializedProtoMsg)

    deserializedProtoMsg.state.zip(objectMessages).forEach { (actual, expected) ->
      assertEquals(expected, (actual as? io.ably.lib.objects.ObjectMessage))
    }
  }

  @Test
  fun testOmitNullInSerialization() = runTest {
    val nullableObject = object {
      val name = "Test Object"
      val description: String? = null // This will be omitted if using Gson with excludeNulls
      val value = 42
    }
    val serializedJsonString = gson.toJson(nullableObject)
    // check serializedObject does not contain the null field
    assertEquals("""{"name":"Test Object","value":42}""", serializedJsonString)

    val serializedMsgpackBytes = msgpackMapper.writeValueAsBytes(nullableObject)
    // check serializedObject does not contain the null field
    assertEquals("""{"name":"Test Object","value":42}""", Serialisation.msgpackToGson(serializedMsgpackBytes).toString())
  }
}
