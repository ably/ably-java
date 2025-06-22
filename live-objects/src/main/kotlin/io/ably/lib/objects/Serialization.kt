@file:Suppress("UNCHECKED_CAST")

package io.ably.lib.objects

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.google.gson.*
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.msgpack.value.ImmutableMapValue
import java.lang.reflect.Type
import java.util.*

// Gson instance for JSON serialization/deserialization
internal val gson: Gson = GsonBuilder().create()

// Jackson ObjectMapper for MessagePack serialization (respects @JsonProperty annotations)
// Caches type metadata and serializers for ObjectMessage class after first use, so next time it's super fast 🚀
// https://github.com/FasterXML/jackson-modules-java8/tree/3.x/parameter-names
internal val msgpackMapper = ObjectMapper(MessagePackFactory()).apply {
  registerModule(ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
  setSerializationInclusion(JsonInclude.Include.NON_NULL)
}

internal fun ObjectMessage.toJsonObject(): JsonObject {
  return gson.toJsonTree(this).asJsonObject
}

internal fun JsonObject.toObjectMessage(): ObjectMessage {
  return gson.fromJson(this, ObjectMessage::class.java)
}

internal fun ObjectMessage.writeTo(packer: MessagePacker) {
  val msgpackBytes = msgpackMapper.writeValueAsBytes(this) // returns correct msgpack map structure
  packer.writePayload(msgpackBytes)
}

internal fun ImmutableMapValue.toObjectMessage(): ObjectMessage {
  val msgpackBytes = MessagePack.newDefaultBufferPacker().use { packer ->
    packer.packValue(this)
    packer.toByteArray()
  }
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
    return Array(objectMessagesCount) { unpacker.unpackValue().asMapValue().toObjectMessage() }
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

internal class ObjectDataJsonSerializer : JsonSerializer<ObjectData>, JsonDeserializer<ObjectData> {
  override fun serialize(src: ObjectData, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
    val obj = JsonObject()
    src.objectId?.let { obj.addProperty("objectId", it) }

    src.value?.let { value ->
      when (val v = value.value) {
        is Boolean -> obj.addProperty("boolean", v)
        is String -> obj.addProperty("string", v)
        is Number -> obj.addProperty("number", v.toDouble())
        is Binary -> obj.addProperty("bytes", Base64.getEncoder().encodeToString(v.data))
        // Spec: OD4c5
        is JsonObject, is JsonArray -> {
          obj.addProperty("string", v.toString())
          obj.addProperty("encoding", "json")
        }
      }
    }
    return obj
  }

  override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): ObjectData {
    val obj = if (json.isJsonObject) json.asJsonObject else throw JsonParseException("Expected JsonObject")
    val objectId = if (obj.has("objectId")) obj.get("objectId").asString else null
    val encoding = if (obj.has("encoding")) obj.get("encoding").asString else null
    val value = when {
      obj.has("boolean") -> ObjectValue(obj.get("boolean").asBoolean)
      // Spec: OD5b3
      obj.has("string") && encoding == "json" -> {
        val jsonStr = obj.get("string").asString
        val parsed = JsonParser.parseString(jsonStr)
        ObjectValue(
          when {
            parsed.isJsonObject -> parsed.asJsonObject
            parsed.isJsonArray -> parsed.asJsonArray
            else -> throw JsonParseException("Invalid JSON string for encoding=json")
          }
        )
      }
      obj.has("string") -> ObjectValue(obj.get("string").asString)
      obj.has("number") -> ObjectValue(obj.get("number").asDouble)
      obj.has("bytes") -> ObjectValue(Binary(Base64.getDecoder().decode(obj.get("bytes").asString)))
      else -> throw JsonParseException("ObjectData must have one of the fields: boolean, string, number, or bytes")
    }
    return ObjectData(objectId, value)
  }
}

internal class ObjectDataMsgpackSerializer : com.fasterxml.jackson.databind.JsonSerializer<ObjectData>() {
  override fun serialize(value: ObjectData, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeStartObject()
    value.objectId?.let { gen.writeStringField("objectId", it) }
    value.value?.let { v ->
      when (val data = v.value) {
        is Boolean -> gen.writeBooleanField("boolean", data)
        is String -> gen.writeStringField("string", data)
        is Number -> gen.writeNumberField("number", data.toDouble())
        is Binary -> gen.writeBinaryField("bytes", data.data)
        is JsonObject, is JsonArray -> {
          gen.writeStringField("string", data.toString())
          gen.writeStringField("encoding", "json")
        }
      }
    }
    gen.writeEndObject()
  }
}

internal class ObjectDataMsgpackDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<ObjectData>() {
  override fun deserialize(p: com.fasterxml.jackson.core.JsonParser, ctxt: DeserializationContext): ObjectData {
    val node = p.codec.readTree<com.fasterxml.jackson.databind.JsonNode>(p)
    val objectId = node.get("objectId")?.asText()
    val encoding = node.get("encoding")?.asText()
    val value = when {
      node.has("boolean") -> ObjectValue(node.get("boolean").asBoolean())
      node.has("string") && encoding == "json" -> {
        val jsonStr = node.get("string").asText()
        val parsed = JsonParser.parseString(jsonStr)
        ObjectValue(
          when {
            parsed.isJsonObject -> parsed.asJsonObject
            parsed.isJsonArray -> parsed.asJsonArray
            else -> throw IllegalArgumentException("Invalid JSON string for encoding=json")
          }
        )
      }
      node.has("string") -> ObjectValue(node.get("string").asText())
      node.has("number") -> ObjectValue(node.get("number").doubleValue())
      node.has("bytes") -> ObjectValue(Binary(node.get("bytes").binaryValue()))
      else -> throw IllegalArgumentException("ObjectData must have one of the fields: boolean, string, number, or bytes")
    }
    return ObjectData(objectId, value)
  }
}

internal class InitialValueJsonSerializer : JsonSerializer<Binary>, JsonDeserializer<Binary> {
  override fun serialize(src: Binary, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
    return JsonPrimitive(Base64.getEncoder().encodeToString(src.data))
  }

  override fun deserialize(json: JsonElement, typeOfT: Type?, context: JsonDeserializationContext?): Binary {
    return Binary(Base64.getDecoder().decode(json.asString))
  }
}

internal class InitialValueMsgpackSerializer : com.fasterxml.jackson.databind.JsonSerializer<Binary>() {
  override fun serialize(value: Binary, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeBinary(value.data)
  }
}

internal class InitialValueMsgpackDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<Binary>() {
  override fun deserialize(p: com.fasterxml.jackson.core.JsonParser, ctxt: DeserializationContext): Binary {
    return Binary(p.binaryValue)
  }
}
