package io.ably.lib.liveobjects.path.types

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.path.DefaultPathObject
import io.ably.lib.liveobjects.path.PathObject
import io.ably.lib.liveobjects.pathNotResolvedError
import io.ably.lib.liveobjects.typeMismatchError
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.liveobjects.value.ResolvedValue
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveMapPathObject], adding map navigation and read/write
 * operations on top of [DefaultPathObject]; all left unimplemented for now.
 *
 * Spec: RTTS6a
 */
internal class DefaultLiveMapPathObject(
  channelObject: DefaultRealtimeObject,
  path: String,
) : DefaultPathObject(channelObject, path), LiveMapPathObject {

  override fun get(key: String): PathObject = TODO("Not yet implemented")

  override fun at(path: String): PathObject = TODO("Not yet implemented")

  override fun entries(): Iterable<Map.Entry<String, PathObject>> {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path) !is ResolvedValue.MapRef) return emptyList() // not a LiveMap (or unresolved) -> empty
    // TODO - iterate the resolved map's entries, yielding (key, child PathObject)
    TODO("Not yet implemented")
  }

  override fun keys(): Iterable<String> {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path) !is ResolvedValue.MapRef) return emptyList() // not a LiveMap (or unresolved) -> empty
    // TODO - return the resolved map's keys
    TODO("Not yet implemented")
  }

  override fun values(): Iterable<PathObject> {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path) !is ResolvedValue.MapRef) return emptyList() // not a LiveMap (or unresolved) -> empty
    // TODO - return a child PathObject for each entry of the resolved map
    TODO("Not yet implemented")
  }

  override fun size(): Long? {
    channelObject.throwIfInvalidAccessApiConfiguration()
    if (resolveValueAtPath(path) !is ResolvedValue.MapRef) return null // not a LiveMap (or unresolved) -> null
    // TODO - return the resolved map's size
    TODO("Not yet implemented")
  }

  override fun set(key: String, value: LiveMapValue): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    val resolvedValue = resolveValueAtPath(path) ?: throw pathNotResolvedError(path)
    if (resolvedValue !is ResolvedValue.MapRef) {
      throw typeMismatchError("Cannot set a key on a non-LiveMap object at path: \"$path\"")
    }
    // TODO - delegate the MAP_SET to the resolved LiveMap
    TODO("Not yet implemented")
  }

  override fun remove(key: String): CompletableFuture<Void> {
    channelObject.throwIfInvalidWriteApiConfiguration()
    val resolvedValue = resolveValueAtPath(path) ?: throw pathNotResolvedError(path)
    if (resolvedValue !is ResolvedValue.MapRef) {
      throw typeMismatchError("Cannot remove a key from a non-LiveMap object at path: \"$path\"")
    }
    // TODO - delegate the MAP_REMOVE to the resolved LiveMap
    TODO("Not yet implemented")
  }
}
