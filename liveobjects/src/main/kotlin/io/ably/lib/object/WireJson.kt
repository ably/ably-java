package io.ably.lib.`object`

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * JSON serialization for the wire model, used to produce the initial-value
 * JSON strings of MAP_CREATE / COUNTER_CREATE operations (RTLMV4f / RTLCV4c).
 *
 * Copied from the legacy serializers (`io.ably.lib.objects.serialization`) so
 * the output is byte-identical to what the legacy create path produces — the
 * initial-value string feeds the objectId hash (RTO14), so the format is a
 * wire contract: enums serialize to their integer codes, ObjectData JSON
 * leaves serialize as a JSON *string* (OD4c5), binary stays base64.
 */
internal val wireGson = GsonBuilder()
  .registerTypeAdapter(WireObjectOperationAction::class.java, JsonSerializer<WireObjectOperationAction> { src, _, _ ->
    JsonPrimitive(src.code)
  })
  .registerTypeAdapter(WireObjectsMapSemantics::class.java, JsonSerializer<WireObjectsMapSemantics> { src, _, _ ->
    JsonPrimitive(src.code)
  })
  .registerTypeAdapter(WireObjectData::class.java, WireObjectDataJsonSerializer())
  .create()

internal class WireObjectDataJsonSerializer : JsonSerializer<WireObjectData> {
  override fun serialize(src: WireObjectData, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
    val obj = JsonObject()
    src.objectId?.let { obj.addProperty("objectId", it) }
    src.string?.let { obj.addProperty("string", it) }
    src.number?.let { obj.addProperty("number", it) }
    src.boolean?.let { obj.addProperty("boolean", it) }
    src.bytes?.let { obj.addProperty("bytes", it) }
    src.json?.let { obj.addProperty("json", it.toString()) } // Spec: OD4c5
    return obj
  }
}
