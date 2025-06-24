package io.ably.lib.objects.unit

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import io.ably.lib.objects.unit.fixtures.*
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.ProtocolMessage.ActionSerializer
import io.ably.lib.types.ProtocolSerializer
import io.ably.lib.util.Serialisation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
  fun testOmitNullsInObjectMessageSerialization() = runTest {
    val objectMessage = dummyObjectMessageWithStringData()
    val objectMessageWithNullFields = objectMessage.copy(
      id = null,
      timestamp = null,
      clientId = "test-client",
      connectionId = "test-connection",
      extras = null,
      operation = null,
      objectState = null,
      serial = null,
      siteCode = null
    )
    val protocolMessage = ProtocolMessage()
    protocolMessage.action = ProtocolMessage.Action.`object`
    protocolMessage.state = arrayOf(objectMessageWithNullFields)

    // check if Gson/Msgpack serialization omits null fields
    fun assertSerializedObjectMessage(serializedProtoMsg: String) {
      val deserializedProtoMsg = Gson().fromJson(serializedProtoMsg, JsonElement::class.java).asJsonObject
      val serializedObjectMessage = deserializedProtoMsg.get("state").asJsonArray[0].asJsonObject.toString()
      assertEquals("""{"clientId":"test-client","connectionId":"test-connection"}""", serializedObjectMessage)
    }

    // Serialize using Gson
    val serializedProtoMsg = ProtocolSerializer.writeJSON(protocolMessage).toString(Charsets.UTF_8)
    assertSerializedObjectMessage(serializedProtoMsg)

    // Serialize using MsgPack
    val serializedMsgpackBytes = ProtocolSerializer.writeMsgpack(protocolMessage)
    val serializedJsonStringFromMsgpackBytes = Serialisation.msgpackToGson(serializedMsgpackBytes).toString()
    assertSerializedObjectMessage(serializedJsonStringFromMsgpackBytes)
  }

  @Test
  fun testSerializeEnumsIntoOrdinalValues() = runTest {
    val objectMessage = dummyObjectMessageWithStringData()
    val protocolMessage = ProtocolMessage()
    protocolMessage.action = ProtocolMessage.Action.`object`
    protocolMessage.state = arrayOf(objectMessage)

    fun assertSerializedObjectMessage(serializedProtoMsg: String) {
      val deserializedProtoMsg = Gson().fromJson(serializedProtoMsg, JsonElement::class.java).asJsonObject
      val serializedObjectMessage = deserializedProtoMsg.get("state").asJsonArray[0].asJsonObject
      val operation = serializedObjectMessage.get("operation").asJsonObject
      assertTrue(operation.has("action"))
      assertEquals(0, operation.get("action").asInt) // Check if action is serialized as code
    }

    // Serialize using Gson
    val serializedProtoMsg = ProtocolSerializer.writeJSON(protocolMessage).toString(Charsets.UTF_8)
    assertSerializedObjectMessage(serializedProtoMsg)
    // Serialize using MsgPack
    val serializedMsgpackBytes = ProtocolSerializer.writeMsgpack(protocolMessage)
    val serializedJsonStringFromMsgpackBytes = Serialisation.msgpackToGson(serializedMsgpackBytes).toString()
    assertSerializedObjectMessage(serializedJsonStringFromMsgpackBytes)
  }

  @Test
  fun testHandleNullsInObjectMessageDeserialization() = runTest {
    val protocolMessage = ProtocolMessage()
    protocolMessage.id = "id"
    protocolMessage.action = ProtocolMessage.Action.`object`
    protocolMessage.state = null

    // Serialize using Gson with serializeNulls enabled
    val gsonBuilderCreatingNulls = GsonBuilder()
      .registerTypeAdapter(ProtocolMessage.Action::class.java, ActionSerializer())
      .serializeNulls().create()

    var protoMsgJsonObject = gsonBuilderCreatingNulls.toJsonTree(protocolMessage).asJsonObject
    assertTrue(protoMsgJsonObject.has("state"))
    assertEquals(JsonNull.INSTANCE, protoMsgJsonObject.get("state"))

    var deserializedProtoMsg = ProtocolSerializer.fromJSON(protoMsgJsonObject.toString())
    assertNull(deserializedProtoMsg.state)

    var serializedMsgpackBytes = Serialisation.gsonToMsgpack(protoMsgJsonObject)
    deserializedProtoMsg = ProtocolSerializer.readMsgpack(serializedMsgpackBytes)
    assertNull(deserializedProtoMsg.state)

    // Create ObjectMessage and serialize in a way that resulting string/bytes include null fields
    val objectMessage = dummyObjectMessageWithStringData()
    val objectMessageWithNullFields = objectMessage.copy(
      id = null,
      timestamp = null,
      clientId = "test-client",
      connectionId = "test-connection",
      extras = null,
      operation = objectMessage.operation?.copy(
        initialValue = null, // initialValue set to null
        mapOp = objectMessage.operation.mapOp?.copy(
          data = null // objectData set to null
        )
      ),
      objectState = null,
      serial = null,
      siteCode = null
    )
    protocolMessage.state = arrayOf(objectMessageWithNullFields)
    protoMsgJsonObject = gsonBuilderCreatingNulls.toJsonTree(protocolMessage).asJsonObject

    // Check if gson deserialization works correctly
    deserializedProtoMsg = ProtocolSerializer.fromJSON(protoMsgJsonObject.toString())
    assertEquals(objectMessageWithNullFields, deserializedProtoMsg.state[0] as? io.ably.lib.objects.ObjectMessage)

    // Check if msgpack deserialization works correctly
    serializedMsgpackBytes = Serialisation.gsonToMsgpack(protoMsgJsonObject)
    deserializedProtoMsg = ProtocolSerializer.readMsgpack(serializedMsgpackBytes)
    assertEquals(objectMessageWithNullFields, deserializedProtoMsg.state[0] as? io.ably.lib.objects.ObjectMessage)
  }
}
