package io.ably.lib.objects.serialization

import com.google.gson.*
import io.ably.lib.objects.Binary
import io.ably.lib.objects.MapSemantics
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectValue
import java.lang.reflect.Type
import java.util.*
import kotlin.enums.EnumEntries

// Gson instance for JSON serialization/deserialization
internal val gson = GsonBuilder()
  .registerTypeAdapter(ObjectOperationAction::class.java, EnumCodeTypeAdapter({ it.code }, ObjectOperationAction.entries))
  .registerTypeAdapter(MapSemantics::class.java, EnumCodeTypeAdapter({ it.code }, MapSemantics.entries))
  .create()

internal fun ObjectMessage.toJsonObject(): JsonObject {
  return gson.toJsonTree(this).asJsonObject
}

internal fun JsonObject.toObjectMessage(): ObjectMessage {
  return gson.fromJson(this, ObjectMessage::class.java)
}

internal class EnumCodeTypeAdapter<T : Enum<T>>(
  private val getCode: (T) -> Int,
  private val enumValues: EnumEntries<T>
) : JsonSerializer<T>, JsonDeserializer<T> {

  override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
    return JsonPrimitive(getCode(src))
  }

  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T {
    val code = json.asInt
    return enumValues.firstOrNull { getCode(it) == code } ?: enumValues.firstOrNull { getCode(it) == -1 } 
      ?: throw JsonParseException("Unknown enum code: $code and no Unknown fallback found")
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
