package io.ably.lib.objects.path

import com.google.gson.JsonElement
import io.ably.lib.objects.DefaultRealtimeObjects
import io.ably.lib.objects.ObjectsCallback
import io.ably.lib.objects.ObjectsSubscription
import io.ably.lib.objects.path.LiveCounterInstance
import io.ably.lib.objects.path.LiveInstance
import io.ably.lib.objects.path.LiveMapInstance
import io.ably.lib.objects.path.LivePrimitive
import io.ably.lib.objects.path.LiveValue
import io.ably.lib.objects.path.PathChangeListener
import io.ably.lib.objects.path.PathObject
import io.ably.lib.objects.path.PathSubscriptionOptions
import io.ably.lib.objects.type.map.LiveMapValue
import io.ably.lib.objects.path.objectNotFound
import io.ably.lib.objects.path.typeMismatch
import io.ably.lib.types.AblyException
import java.util.AbstractMap

/**
 * Default [PathObject] implementation.
 *
 * P1 scope: implements all read methods plus typed casts. Write methods
 * and subscriptions throw `NotImplementedError` until P2 and P3.
 */
internal open class DefaultPathObject(
    protected val realtimeObjects: DefaultRealtimeObjects,
    protected val segments: List<String>,
) : PathObject {

    private val resolver = PathResolver(realtimeObjects)

    // ---- Identity / navigation -------------------------------------------

    override fun path(): String = segments.joinToString(".")

    override fun get(key: String): PathObject = DefaultPathObject(realtimeObjects, segments + key)

    override fun at(dottedPath: String): PathObject {
        if (dottedPath.isEmpty()) return this
        return DefaultPathObject(realtimeObjects, segments + dottedPath.split('.'))
    }

    // ---- Reads -----------------------------------------------------------

    override fun value(): LiveValue? {
        val v = resolver.resolve(segments) ?: return null
        return LiveValue.fromMapValue(v)
    }

    override fun instance(): LiveInstance? {
        val v = resolver.resolve(segments) ?: return null
        return when {
            v.isLiveMap()     -> v.getAsLiveMap() as? LiveMapInstance
            v.isLiveCounter() -> v.getAsLiveCounter() as? LiveCounterInstance
            else              -> null
        }
    }

    override fun compact(): Map<String, LiveValue> =
        Compactor.compact(resolver.resolve(segments))

    override fun compactJson(): JsonElement =
        Compactor.compactJson(resolver.resolve(segments))

    // ---- Typed assertions ------------------------------------------------

    override fun asLiveMap(): LiveMapInstance {
        val v = resolver.resolve(segments) ?: throw objectNotFound(path())
        if (!v.isLiveMap()) throw typeMismatch(path(), "LiveMap", v)
        return v.getAsLiveMap() as LiveMapInstance
    }

    override fun asLiveCounter(): LiveCounterInstance {
        val v = resolver.resolve(segments) ?: throw objectNotFound(path())
        if (!v.isLiveCounter()) throw typeMismatch(path(), "LiveCounter", v)
        return v.getAsLiveCounter() as LiveCounterInstance
    }

    override fun asPrimitive(): LivePrimitive {
        val v = resolver.resolve(segments) ?: throw objectNotFound(path())
        if (v.isLiveMap() || v.isLiveCounter()) throw typeMismatch(path(), "Primitive", v)
        return LiveValue.fromMapValue(v) as LivePrimitive
    }

    // ---- Map-like enumeration --------------------------------------------

    override fun entries(): Iterable<Map.Entry<String, PathObject>> {
        val v = resolver.resolve(segments) ?: return emptyList()
        if (!v.isLiveMap()) throw typeMismatch(path(), "LiveMap", v)
        val map = v.getAsLiveMap()
        return sequence {
            for (e in map.entries()) {
                val child = DefaultPathObject(realtimeObjects, segments + e.key)
                yield(AbstractMap.SimpleImmutableEntry<String, PathObject>(e.key, child))
            }
        }.asIterable()
    }

    override fun keys(): Iterable<String> {
        val v = resolver.resolve(segments) ?: return emptyList()
        if (!v.isLiveMap()) throw typeMismatch(path(), "LiveMap", v)
        return v.getAsLiveMap().keys()
    }

    override fun values(): Iterable<PathObject> {
        val es = entries()
        return sequence {
            for (e in es) yield(e.value)
        }.asIterable()
    }

    override fun size(): Long? {
        val v = resolver.resolve(segments) ?: return null
        if (!v.isLiveMap()) return null
        return v.getAsLiveMap().size()
    }

    // ---- Writes ----------------------------------------------------------

    private fun resolveAsMap(): io.ably.lib.objects.type.map.LiveMap {
        val v = resolver.resolve(segments) ?: throw objectNotFound(path())
        if (!v.isLiveMap()) throw typeMismatch(path(), "LiveMap", v)
        return v.getAsLiveMap()
    }

    private fun resolveAsCounter(): io.ably.lib.objects.type.counter.LiveCounter {
        val v = resolver.resolve(segments) ?: throw objectNotFound(path())
        if (!v.isLiveCounter()) throw typeMismatch(path(), "LiveCounter", v)
        return v.getAsLiveCounter()
    }

    override fun set(key: String, value: LiveValue) {
        val map = resolveAsMap()
        val mv = LiveValueWriter.toLiveMapValue(value, realtimeObjects)
        map.set(key, mv)
    }

    override fun remove(key: String) {
        val map = resolveAsMap()
        map.remove(key)
    }

    override fun increment(amount: Number) {
        resolveAsCounter().increment(amount)
    }

    override fun decrement(amount: Number) {
        resolveAsCounter().decrement(amount)
    }

    override fun setAsync(key: String, value: LiveValue, callback: ObjectsCallback<Void>) {
        realtimeObjects.asyncScope.launchWithVoidCallback(callback) {
            val map = resolveAsMap()
            val mv = LiveValueWriter.toLiveMapValue(value, realtimeObjects)
            map.set(key, mv)
        }
    }

    override fun removeAsync(key: String, callback: ObjectsCallback<Void>) {
        realtimeObjects.asyncScope.launchWithVoidCallback(callback) {
            resolveAsMap().remove(key)
        }
    }

    override fun incrementAsync(amount: Number, callback: ObjectsCallback<Void>) {
        realtimeObjects.asyncScope.launchWithVoidCallback(callback) {
            resolveAsCounter().increment(amount)
        }
    }

    override fun decrementAsync(amount: Number, callback: ObjectsCallback<Void>) {
        realtimeObjects.asyncScope.launchWithVoidCallback(callback) {
            resolveAsCounter().decrement(amount)
        }
    }

    // ---- Subscriptions — P3 ----------------------------------------------

    override fun subscribe(listener: PathChangeListener): ObjectsSubscription =
        subscribe(listener, PathSubscriptionOptions.unlimited())

    override fun subscribe(listener: PathChangeListener, options: PathSubscriptionOptions): ObjectsSubscription {
        throw NotImplementedError("P3: path subscriptions not yet implemented")
    }
}
