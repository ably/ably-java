package io.ably.lib.objects.path

import io.ably.lib.objects.DefaultRealtimeObjects
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.map.LiveMap
import io.ably.lib.objects.type.map.LiveMapValue

/**
 * Converts a public [LiveValue] (which may be a [LivePrimitive], a
 * [LiveInstance], or a creation token from `LiveMap.create(...)` /
 * `LiveCounter.create(...)`) into a [LiveMapValue] suitable for the
 * internal write path.
 *
 * Creation tokens trigger a real `createMap` / `createCounter` operation
 * against [realtimeObjects] — that's how atomic deep create is realised.
 */
internal object LiveValueWriter {

    internal fun toLiveMapValue(value: LiveValue, realtimeObjects: DefaultRealtimeObjects): LiveMapValue {
        return when (value) {
            is LiveCreate.MapCreateToken -> {
                val nested = translateEntries(value.entries, realtimeObjects)
                val map: LiveMap = realtimeObjects.createMap(nested)
                LiveMapValue.of(map)
            }
            is LiveCreate.CounterCreateToken -> {
                val counter: LiveCounter = realtimeObjects.createCounter(value.initialValue)
                LiveMapValue.of(counter)
            }
            is LivePrimitive  -> value.toMapValue()
            is LiveInstance   -> value.toMapValue()
            else -> throw IllegalArgumentException(
                "Unknown LiveValue implementation: ${value.javaClass.name}. " +
                "Construct values via LivePrimitive.of(...), LiveMap.create(...), or LiveCounter.create(...)."
            )
        }
    }

    private fun translateEntries(
        entries: Map<String, LiveValue>,
        realtimeObjects: DefaultRealtimeObjects,
    ): MutableMap<String, LiveMapValue> {
        val out = LinkedHashMap<String, LiveMapValue>(entries.size)
        for ((k, v) in entries) {
            out[k] = toLiveMapValue(v, realtimeObjects)
        }
        return out
    }
}
