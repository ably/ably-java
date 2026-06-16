package io.ably.lib.`object`.path.types

import io.ably.lib.`object`.DefaultRealtimeObject
import io.ably.lib.`object`.path.DefaultPathObject
import io.ably.lib.`object`.path.PathObject
import io.ably.lib.`object`.value.LiveMapValue
import java.util.concurrent.CompletableFuture

/**
 * Default implementation of [LiveMapPathObject], adding map navigation and read/write
 * operations on top of [DefaultPathObject]; all left unimplemented for now.
 *
 * Spec: RTTS6a
 */
internal class DefaultLiveMapPathObject(
  channelObject: DefaultRealtimeObject,
) : DefaultPathObject(channelObject), LiveMapPathObject {

  override fun get(key: String): PathObject = TODO("Not yet implemented")

  override fun at(path: String): PathObject = TODO("Not yet implemented")

  override fun entries(): Iterable<Map.Entry<String, PathObject>> = TODO("Not yet implemented")

  override fun keys(): Iterable<String> = TODO("Not yet implemented")

  override fun values(): Iterable<PathObject> = TODO("Not yet implemented")

  @Suppress("RedundantNullableReturnType")
  override fun size(): Long? = TODO("Not yet implemented")

  override fun set(key: String, value: LiveMapValue): CompletableFuture<Void> = TODO("Not yet implemented")

  override fun remove(key: String): CompletableFuture<Void> = TODO("Not yet implemented")
}
