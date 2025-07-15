package io.ably.lib.objects.type.livemap

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectsPool
import io.ably.lib.objects.ObjectsPoolDefaults
import io.ably.lib.objects.MapSemantics
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.types.Callback

/**
 * @spec RTLM3 - Map data structure storing entries
 */
internal data class LiveMapEntry(
  var isTombstoned: Boolean = false,
  var tombstonedAt: Long? = null,
  var timeserial: String? = null,
  var data: ObjectData? = null
)

/**
 * Extension function to check if a LiveMapEntry is expired and ready for garbage collection
 */
private fun LiveMapEntry.isEligibleForGc(): Boolean {
  val currentTime = System.currentTimeMillis()
  return isTombstoned && tombstonedAt?.let { currentTime - it >= ObjectsPoolDefaults.GC_GRACE_PERIOD_MS } == true
}

/**
 * Implementation of LiveObject for LiveMap.
 *
 * @spec RTLM1/RTLM2 - LiveMap implementation extends LiveObject
 */
internal class DefaultLiveMap private constructor(
  objectId: String,
  adapter: LiveObjectsAdapter,
  internal val objectsPool: ObjectsPool,
  internal val semantics: MapSemantics = MapSemantics.LWW
) : LiveMap, BaseLiveObject(objectId, ObjectType.Map, adapter) {

  override val tag = "LiveMap"
  /**
   * Map of key to LiveMapEntry
   */
  internal val data = mutableMapOf<String, LiveMapEntry>()

  /**
   * LiveMapManager instance for managing LiveMap operations
   */
  private val liveMapManager = LiveMapManager(this)


  override fun get(keyName: String): Any? {
    TODO("Not yet implemented")
  }

  override fun entries(): MutableIterable<MutableMap.MutableEntry<String, Any>> {
    TODO("Not yet implemented")
  }

  override fun keys(): MutableIterable<String> {
    TODO("Not yet implemented")
  }

  override fun values(): MutableIterable<Any> {
    TODO("Not yet implemented")
  }

  override fun set(keyName: String, value: Any) {
    TODO("Not yet implemented")
  }

  override fun remove(keyName: String) {
    TODO("Not yet implemented")
  }

  override fun size(): Long {
    TODO("Not yet implemented")
  }

  override fun setAsync(keyName: String, value: Any, callback: Callback<Void>) {
    TODO("Not yet implemented")
  }

  override fun removeAsync(keyName: String, callback: Callback<Void>) {
    TODO("Not yet implemented")
  }

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
    internal fun zeroValue(objectId: String, adapter: LiveObjectsAdapter, objectsPool: ObjectsPool): DefaultLiveMap {
      return DefaultLiveMap(objectId, adapter, objectsPool)
    }
  }
}
