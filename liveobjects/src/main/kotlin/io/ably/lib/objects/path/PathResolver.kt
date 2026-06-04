package io.ably.lib.objects.path

import io.ably.lib.objects.DefaultRealtimeObjects
import io.ably.lib.objects.type.map.LiveMap
import io.ably.lib.objects.type.map.LiveMapValue

/**
 * Walks a path against the live `ObjectsPool` exposed via [DefaultRealtimeObjects].
 *
 * The resolver is stateless and safe to construct per call; the actual state
 * lives in the underlying pool.
 */
internal class PathResolver(private val realtimeObjects: DefaultRealtimeObjects) {

    /**
     * Resolve the value at [segments]. Returns `null` for an unresolved or
     * tombstoned path (consistent with `LiveMap.get` semantics, RTLM5d1).
     */
    internal fun resolve(segments: List<String>): LiveMapValue? {
        if (segments.isEmpty()) {
            return LiveMapValue.of(realtimeObjects.getRoot())
        }
        var current: LiveMapValue? = LiveMapValue.of(realtimeObjects.getRoot())
        for (segment in segments) {
            val cur = current ?: return null
            if (!cur.isLiveMap()) return null
            current = cur.asLiveMap.get(segment)
            if (current == null) return null
        }
        return current
    }

    /**
     * Resolve the parent LiveMap of [segments] for write operations.
     * Returns `null` if the parent does not resolve to a LiveMap.
     */
    internal fun resolveParentMap(segments: List<String>): LiveMap? {
        if (segments.isEmpty()) return realtimeObjects.getRoot()
        val parent = resolve(segments.dropLast(1)) ?: return null
        if (!parent.isLiveMap()) return null
        return parent.asLiveMap
    }
}

/** Tiny Kotlin sugar — delegates to the Java getter. */
private val LiveMapValue.asLiveMap: LiveMap get() = getAsLiveMap()
