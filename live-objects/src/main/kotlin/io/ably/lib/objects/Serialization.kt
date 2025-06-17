@file:Suppress("UNCHECKED_CAST")

package io.ably.lib.objects

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.*
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker
import org.msgpack.jackson.dataformat.MessagePackFactory

// Gson instance for JSON serialization/deserialization
internal val gson: Gson = GsonBuilder().create()

// Jackson ObjectMapper for MessagePack serialization (respects @JsonProperty annotations)
// Caches type metadata and serializers for ObjectMessage class after first use, so next time it's super fast 🚀
private val msgpackMapper = ObjectMapper(MessagePackFactory())

internal fun ObjectMessage.toJsonObject(): JsonObject {
  return gson.toJsonTree(this).asJsonObject
}

internal fun JsonObject.toObjectMessage(): ObjectMessage {
  return gson.fromJson(this, ObjectMessage::class.java)
}

internal fun ObjectMessage.writeTo(packer: MessagePacker) {
  // Jackson automatically creates the correct msgpack map structure
  val msgpackBytes = msgpackMapper.writeValueAsBytes(this)

  // Parse the msgpack bytes to get the structured value
  val tempUnpacker = MessagePack.newDefaultUnpacker(msgpackBytes)
  val msgpackValue = tempUnpacker.unpackValue()
  tempUnpacker.close()

  // Pack the structured value using the provided packer
  packer.packValue(msgpackValue)
}

internal fun MessageUnpacker.readObjectMessage(): ObjectMessage {
  // Read the msgpack value from the unpacker
  val msgpackValue = this.unpackValue()

  // Convert the msgpack value back to bytes
  val tempPacker = MessagePack.newDefaultBufferPacker()
  tempPacker.packValue(msgpackValue)
  val msgpackBytes = tempPacker.toByteArray()
  tempPacker.close()

  // Let Jackson deserialize the msgpack bytes back to ObjectMessage
  return msgpackMapper.readValue(msgpackBytes, ObjectMessage::class.java)
}

/**
 * Default implementation of {@link LiveObjectSerializer} that handles serialization/deserialization
 * of ObjectMessage arrays for both JSON and MessagePack formats using Jackson and Gson.
 * Dynamically loaded by LiveObjectsHelper#getLiveObjectSerializer() to avoid hard dependencies.
 */
@Suppress("unused") // Used via reflection in LiveObjectsHelper
internal class DefaultLiveObjectSerializer : LiveObjectSerializer {

  override fun readMsgpackArray(unpacker: MessageUnpacker): Array<Any> {
    val objectMessagesCount = unpacker.unpackArrayHeader()
    return Array(objectMessagesCount) { unpacker.readObjectMessage() }
  }

  override fun writeMsgpackArray(objects: Array<out Any>?, packer: MessagePacker) {
    val objectMessages: Array<ObjectMessage> = objects as Array<ObjectMessage>
    packer.packArrayHeader(objectMessages.size)
    objectMessages.forEach { it.writeTo(packer) }
  }

  override fun readFromJsonArray(json: JsonArray): Array<Any> {
    return json.map { element ->
      if (element.isJsonObject) element.asJsonObject.toObjectMessage()
      else throw JsonParseException("Expected JsonObject, but found: $element")
    }.toTypedArray()
  }

  override fun asJsonArray(objects: Array<out Any>?): JsonArray {
    val objectMessages: Array<ObjectMessage> = objects as Array<ObjectMessage>
    val jsonArray = JsonArray()
    for (objectMessage in objectMessages) {
      jsonArray.add(objectMessage.toJsonObject())
    }
    return jsonArray
  }
}
