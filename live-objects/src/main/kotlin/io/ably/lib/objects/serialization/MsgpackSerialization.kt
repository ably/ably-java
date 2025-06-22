package io.ably.lib.objects.serialization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ably.lib.objects.Binary
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectValue
import io.ably.lib.util.Serialisation
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.msgpack.value.ImmutableMapValue

// Jackson ObjectMapper for MessagePack serialization (respects @JsonProperty annotations)
// Caches type metadata and serializers for ObjectMessage class after first use, so next time it's super fast 🚀
// https://github.com/FasterXML/jackson-modules-java8/tree/3.x/parameter-names
internal val msgpackMapper = ObjectMapper(MessagePackFactory()).apply {
  registerModule(ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
  setSerializationInclusion(JsonInclude.Include.NON_NULL)
  enable(SerializationFeature.WRITE_ENUMS_USING_INDEX) // Serialize enums using their ordinal values
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

internal class JsonObjectMsgpackSerializer : com.fasterxml.jackson.databind.JsonSerializer<JsonObject>() {
  override fun serialize(value: JsonObject, gen: JsonGenerator, serializers: SerializerProvider) {
    gen.writeBinary(Serialisation.gsonToMsgpack(value))
  }
}

internal class JsonObjectMsgpackDeserializer : com.fasterxml.jackson.databind.JsonDeserializer<JsonObject>() {
  override fun deserialize(p: com.fasterxml.jackson.core.JsonParser, ctxt: DeserializationContext): JsonObject {
    return Serialisation.msgpackToGson(p.binaryValue) as JsonObject
  }
}
