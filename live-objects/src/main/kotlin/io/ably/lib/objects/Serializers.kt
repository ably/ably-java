package io.ably.lib.objects

import com.google.gson.*
import io.ably.lib.util.Base64Coder
import io.ably.lib.util.Serialisation.BinaryJsonPrimitive
import java.lang.reflect.Type

/**
 * Creates a Gson instance with a custom serializer for live objects.
 * Omits null values during serialization.
 */

internal fun ObjectMessage.toJsonObject(): JsonObject {
  return gson.toJsonTree(this).asJsonObject
}

internal fun JsonElement.toObjectMessage(): ObjectMessage {
  return gson.fromJson(this, ObjectMessage::class.java)
}

private val gson: Gson = createGsonSerializer()

private fun createGsonSerializer(): Gson {
  return GsonBuilder()
    .registerTypeAdapter(Binary::class.java, BinarySerializer())
    .create() // Do not call serializeNulls() to omit null values
}

// Custom serializer for Binary type
internal class BinarySerializer : JsonSerializer<Binary>, JsonDeserializer<Binary> {
  override fun serialize(src: Binary?, typeOfSrc: Type, context: JsonSerializationContext): JsonElement? {
    src?.data?.let {
      return BinaryJsonPrimitive(it)
    }
    return null // Omit null values
  }

  override fun deserialize(json: JsonElement?, typeOfT: Type, context: JsonDeserializationContext): Binary? {
    if (json != null && json.isJsonPrimitive) {
      val decodedData = Base64Coder.decode(json.asString)
      return Binary(decodedData)
    }
    return null // Return null if the JSON element is not valid
  }
}
