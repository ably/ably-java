package io.ably.lib.objects.path.ext

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ably.lib.objects.ObjectsCallback
import io.ably.lib.objects.path.LiveCounter
import io.ably.lib.objects.path.LiveCounterInstance
import io.ably.lib.objects.path.LiveMap
import io.ably.lib.objects.path.LiveMapInstance
import io.ably.lib.objects.path.LivePrimitive
import io.ably.lib.objects.path.LiveValue
import io.ably.lib.objects.path.PathObject
import io.ably.lib.types.AblyException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---------------------------------------------------------------------------
// Idiomatic Kotlin sugar for the path API.
// Reads stay synchronous (already non-blocking off the pool); only the
// Blocking writes get `suspend` wrappers.
// ---------------------------------------------------------------------------

// Note: `obj["key"]` indexing is intentionally not provided — the existing
// Java member `PathObject.get(String)` already shadows any Kotlin extension
// of the same name, so adding it produces a compile warning without enabling
// new syntax. Use `obj.get("key")` directly.

// ---- Typed convenience reads ----------------------------------------------

public fun PathObject.stringValue(): String? =
    (value() as? LivePrimitive)?.takeIf { it.isString }?.asString()

public fun PathObject.numberValue(): Number? =
    (value() as? LivePrimitive)?.takeIf { it.isNumber }?.asNumber()

public fun PathObject.booleanValue(): Boolean? =
    (value() as? LivePrimitive)?.takeIf { it.isBoolean }?.asBoolean()

public fun PathObject.counterValue(): Double? =
    (value() as? LiveCounterInstance)?.value()

public fun PathObject.mapInstanceOrNull(): LiveMapInstance? =
    value() as? LiveMapInstance

// ---- Suspending writes ----------------------------------------------------

public suspend fun PathObject.setSuspending(key: String, value: LiveValue): Unit =
    suspendCancellableCoroutine { cont ->
        setAsync(key, value, voidCb(cont))
    }

public suspend fun PathObject.removeSuspending(key: String): Unit =
    suspendCancellableCoroutine { cont ->
        removeAsync(key, voidCb(cont))
    }

public suspend fun PathObject.incrementSuspending(amount: Number): Unit =
    suspendCancellableCoroutine { cont ->
        incrementAsync(amount, voidCb(cont))
    }

public suspend fun PathObject.decrementSuspending(amount: Number): Unit =
    suspendCancellableCoroutine { cont ->
        decrementAsync(amount, voidCb(cont))
    }

private fun voidCb(cont: kotlinx.coroutines.CancellableContinuation<Unit>) = object : ObjectsCallback<Void> {
    override fun onSuccess(result: Void?) = cont.resume(Unit)
    override fun onError(exception: AblyException) = cont.resumeWithException(exception)
}

// ---- Deep-create DSL ------------------------------------------------------

/**
 * `liveMapOf("k1" to "v1", "k2" to liveMapOf(...))` — Kotlin sugar around
 * [LiveMap.create]. Values can be:
 * - [String], [Number], [Boolean], [ByteArray], [JsonArray], [JsonObject]
 *   → wrapped via [LivePrimitive.of].
 * - Another [LiveValue] (e.g. a nested `liveMapOf` / `liveCounterOf`,
 *   or an existing [LiveMapInstance] / [LiveCounterInstance]) → used as-is.
 */
public fun liveMapOf(vararg pairs: Pair<String, Any?>): LiveValue {
    val map: MutableMap<String, LiveValue> = LinkedHashMap(pairs.size)
    for ((k, v) in pairs) {
        map[k] = toLiveValue(v)
    }
    return LiveMap.create(map)
}

/** `liveCounterOf(5)` → `LiveCounter.create(5)`. */
public fun liveCounterOf(initial: Number = 0): LiveValue = LiveCounter.create(initial)

private fun toLiveValue(v: Any?): LiveValue = when (v) {
    null         -> throw IllegalArgumentException("null values are not supported in liveMapOf; remove the key instead")
    is LiveValue -> v
    is String    -> LivePrimitive.of(v)
    is Number    -> LivePrimitive.of(v)
    is Boolean   -> LivePrimitive.of(v)
    is ByteArray -> LivePrimitive.of(v)
    is JsonArray -> LivePrimitive.of(v)
    is JsonObject -> LivePrimitive.of(v)
    else -> throw IllegalArgumentException(
        "Unsupported value type for liveMapOf: ${v::class.java.name}. " +
        "Supported: String, Number, Boolean, ByteArray, JsonArray, JsonObject, LiveValue."
    )
}
