package io.ably.lib.objects.path

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.map.LiveMap
import io.ably.lib.objects.type.map.LiveMapValue
import java.util.Base64

/**
 * `compact()` / `compactJson()` for a subtree rooted at a given
 * [LiveMapValue]. Mirrors the JS implementation:
 *
 * - `compact()` keeps direct references to live-object instances → cycles
 *   are preserved naturally; consumer should not pass the result to a
 *   serialiser that follows references blindly.
 * - `compactJson()` breaks cycles via `{"objectId":"…"}` placeholders and
 *   base64-encodes binary primitives so the result is safe for
 *   `Gson.toJson(...)` / `JsonElement.toString()`.
 */
internal object Compactor {

    /** Live, in-memory snapshot of [value]. */
    internal fun compact(value: LiveMapValue?): Map<String, LiveValue> {
        if (value == null) return emptyMap()
        if (!value.isLiveMap()) {
            // For a non-map root, return a synthetic single-entry map keyed by ""
            // to keep the Java return type stable. Consumers typically call
            // compact() on a map path; calling on a primitive/counter is uncommon.
            return mapOf("" to LiveValue.fromMapValue(value))
        }
        val out = LinkedHashMap<String, LiveValue>()
        for (entry in value.getAsLiveMap().entries()) {
            out[entry.key] = LiveValue.fromMapValue(entry.value)
        }
        return out
    }

    /** JSON-safe snapshot of [value]. */
    internal fun compactJson(value: LiveMapValue?): JsonElement {
        if (value == null) return JsonNull.INSTANCE
        return toJsonElement(value, HashSet())
    }

    private fun toJsonElement(value: LiveMapValue, visited: MutableSet<String>): JsonElement {
        return when {
            value.isLiveMap()     -> mapToJson(value.getAsLiveMap(), visited)
            value.isLiveCounter() -> counterToJson(value.getAsLiveCounter())
            value.isString()      -> JsonPrimitive(value.getAsString())
            value.isNumber()      -> JsonPrimitive(value.getAsNumber())
            value.isBoolean()     -> JsonPrimitive(value.getAsBoolean())
            value.isBinary()      -> JsonPrimitive(Base64.getEncoder().encodeToString(value.getAsBinary()))
            value.isJsonArray()   -> value.getAsJsonArray()
            value.isJsonObject()  -> value.getAsJsonObject()
            else                  -> JsonNull.INSTANCE
        }
    }

    private fun mapToJson(map: LiveMap, visited: MutableSet<String>): JsonElement {
        // For the root map we don't have a direct objectId accessor on the
        // public LiveMap interface; fall back to identityHashCode to detect
        // cycles. (When DefaultLiveMap is the concrete type this is stable
        // for the object's lifetime.)
        val key = System.identityHashCode(map).toString()
        if (!visited.add(key)) {
            val ref = JsonObject()
            ref.addProperty("objectId", key)
            return ref
        }
        try {
            val obj = JsonObject()
            for (entry in map.entries()) {
                obj.add(entry.key, toJsonElement(entry.value, visited))
            }
            return obj
        } finally {
            visited.remove(key)
        }
    }

    private fun counterToJson(counter: LiveCounter): JsonElement {
        return JsonPrimitive(counter.value())
    }
}
