package io.ably.lib.objects.serialization

import com.google.gson.*
import io.ably.lib.objects.*

import io.ably.lib.objects.ObjectMessage
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker

/**
 * Default implementation of {@link ObjectsSerializer} that handles serialization/deserialization
 * of ObjectMessage arrays for both JSON and MessagePack formats using Jackson and Gson.
 * Dynamically loaded by ObjectsHelper#getSerializer() to avoid hard dependencies.
 */
@Suppress("unused") // Used via reflection in ObjectsHelper
internal class DefaultObjectsSerializer : ObjectsSerializer {

  override fun readMsgpackArray(unpacker: MessageUnpacker): Array<Any> {
    val objectMessagesCount = unpacker.unpackArrayHeader()
    return Array(objectMessagesCount) { readObjectMessage(unpacker) }
  }

  override fun writeMsgpackArray(objects: Array<out Any>, packer: MessagePacker) {
    val objectMessages = objects.map { it as ObjectMessage }
    packer.packArrayHeader(objectMessages.size)
    objectMessages.forEach { it.writeMsgpack(packer) }
  }

  override fun readFromJsonArray(json: JsonArray): Array<Any> {
    return json.map { element ->
      if (element.isJsonObject) element.asJsonObject.toObjectMessage()
      else throw JsonParseException("Expected JsonObject, but found: $element")
    }.toTypedArray()
  }

  override fun asJsonArray(objects: Array<out Any>): JsonArray {
    val objectMessages = objects.map { it as ObjectMessage }
    val jsonArray = JsonArray()
    for (objectMessage in objectMessages) {
      jsonArray.add(objectMessage.toJsonObject())
    }
    return jsonArray
  }
}
