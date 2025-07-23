package io.ably.lib.objects.type.livemap

import io.ably.lib.objects.*
import io.ably.lib.objects.MapSemantics
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.ObjectType
import java.util.concurrent.ConcurrentHashMap
import java.util.AbstractMap


/**
 * Implementation of LiveObject for LiveMap.
 *
 * @spec RTLM1/RTLM2 - LiveMap implementation extends LiveObject
 */
internal class DefaultLiveMap private constructor(
  objectId: String,
  private val liveObjects: DefaultLiveObjects,
  internal val semantics: MapSemantics = MapSemantics.LWW
) : LiveMap, BaseLiveObject(objectId, ObjectType.Map) {

  override val tag = "LiveMap"

  /**
   * ConcurrentHashMap for thread-safe access from public APIs in LiveMap and LiveMapManager.
   */
  internal val data = ConcurrentHashMap<String, LiveMapEntry>()

  /**
   * LiveMapManager instance for managing LiveMap operations
   */
  private val liveMapManager = LiveMapManager(this)

  private val channelName = liveObjects.channelName
  private val adapter: LiveObjectsAdapter get() = liveObjects.adapter
  internal val objectsPool: ObjectsPool get() = liveObjects.objectsPool

  override fun get(keyName: String): Any? {
    adapter.throwIfInvalidAccessApiConfiguration(channelName) // RTLM5b, RTLM5c
    if (isTombstoned) {
      return null
    }
    data[keyName]?.let { liveMapEntry ->
      return liveMapEntry.getResolvedValue(objectsPool)
    }
    return null // RTLM5d1
  }

  override fun entries(): Iterable<Map.Entry<String, Any>> {
    adapter.throwIfInvalidAccessApiConfiguration(channelName) // RTLM11b, RTLM11c

    return sequence<Map.Entry<String, Any>> {
      for ((key, entry) in data.entries) {
        val value = entry.getResolvedValue(objectsPool) // RTLM11d, RTLM11d2
        value?.let {
          yield(AbstractMap.SimpleImmutableEntry(key, it))
        }
      }
    }.asIterable()
  }

  override fun keys(): Iterable<String> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.key) // RTLM12b
      }
    }.asIterable()
  }

  override fun values(): Iterable<Any> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.value) // RTLM13b
      }
    }.asIterable()
  }

  override fun size(): Long {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    return data.values.count { !it.isEntryOrRefTombstoned(objectsPool) }.toLong() // RTLM10d
  }

  override fun set(keyName: String, value: Any) {
    TODO("Not yet implemented")
  }

  override fun remove(keyName: String) {
    TODO("Not yet implemented")
  }

  override fun setAsync(keyName: String, value: Any, callback: ObjectsCallback<Void>) {
    TODO("Not yet implemented")
  }

  override fun removeAsync(keyName: String, callback: ObjectsCallback<Void>) {
    TODO("Not yet implemented")
  }

  override fun validate(state: ObjectState) = liveMapManager.validate(state)

  override fun applyObjectState(objectState: ObjectState): Map<String, String> {
    return liveMapManager.applyState(objectState)
  }

  override fun applyObjectOperation(operation: ObjectOperation, message: ObjectMessage) {
    liveMapManager.applyOperation(operation, message.serial)
  }

  override fun clearData(): Map<String, String> {
    return liveMapManager.calculateUpdateFromDataDiff(data.toMap(), emptyMap())
      .apply { data.clear() }
  }

  override fun onGCInterval() {
    data.entries.removeIf { (_, entry) -> entry.isEligibleForGc() }
  }

  companion object {
    /**
     * Creates a zero-value map object.
     * @spec RTLM4 - Returns LiveMap with empty map data
     */
    internal fun zeroValue(objectId: String, objects: DefaultLiveObjects): DefaultLiveMap {
      return DefaultLiveMap(objectId, objects)
    }
  }
}
