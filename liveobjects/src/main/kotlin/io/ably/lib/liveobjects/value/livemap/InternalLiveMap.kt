package io.ably.lib.liveobjects.value.livemap

import io.ably.lib.liveobjects.*
import io.ably.lib.liveobjects.ObjectsPool
import io.ably.lib.liveobjects.adapter.AblyClientAdapter
import io.ably.lib.liveobjects.message.*
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.message.WireObjectsMapSemantics
import io.ably.lib.liveobjects.throwIfInvalidAccessApiConfiguration
import io.ably.lib.liveobjects.throwIfInvalidWriteApiConfiguration
import io.ably.lib.liveobjects.value.*
import io.ably.lib.liveobjects.value.BaseRealtimeObject
import io.ably.lib.liveobjects.value.ObjectType
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.liveobjects.value.noOp
import io.ably.lib.util.Log
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.AbstractMap

/**
 * @spec RTLM1/RTLM2 - LiveMap implementation extends BaseRealtimeObject
 */
internal class InternalLiveMap private constructor(
  objectId: String,
  private val realtimeObjects: DefaultRealtimeObject,
  internal val semantics: WireObjectsMapSemantics = WireObjectsMapSemantics.LWW
) : BaseRealtimeObject(objectId, ObjectType.Map, realtimeObjects.clock) {

  override val tag = "LiveMap"

  /**
   * ConcurrentHashMap for thread-safe access from public APIs in LiveMap and LiveMapManager.
   */
  internal val data = ConcurrentHashMap<String, LiveMapEntry>()

  /** @spec RTLM25 */
  internal var clearTimeserial: String? = null

  /**
   * LiveMapManager instance for managing LiveMap operations
   */
  private val liveMapManager = LiveMapManager(this)

  private val channelName = realtimeObjects.channelName
  private val adapter: AblyClientAdapter get() = realtimeObjects.adapter
  internal val objectsPool: ObjectsPool get() = realtimeObjects.objectsPool

  fun get(keyName: String): LiveMapValue? {
    adapter.throwIfInvalidAccessApiConfiguration(channelName) // RTLM5b, RTLM5c
    if (isTombstoned) {
      return null
    }
    data[keyName]?.let { liveMapEntry ->
      return liveMapEntry.getResolvedValue(objectsPool)
    }
    return null // RTLM5d1
  }

  fun entries(): Iterable<Map.Entry<String, LiveMapValue>> {
    adapter.throwIfInvalidAccessApiConfiguration(channelName) // RTLM11b, RTLM11c

    return sequence<Map.Entry<String, LiveMapValue>> {
      for ((key, entry) in data.entries) {
        val value = entry.getResolvedValue(objectsPool) // RTLM11d, RTLM11d2
        value?.let {
          yield(AbstractMap.SimpleImmutableEntry(key, it))
        }
      }
    }.asIterable()
  }

  fun keys(): Iterable<String> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.key) // RTLM12b
      }
    }.asIterable()
  }

  fun values(): Iterable<LiveMapValue> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.value) // RTLM13b
      }
    }.asIterable()
  }

  fun size(): Long {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    return data.values.count { !it.isEntryOrRefTombstoned(objectsPool) }.toLong() // RTLM10d
  }

  suspend fun set(keyName: String, value: LiveMapValue) =  setAsync(keyName, value)

  suspend fun remove(keyName: String) = removeAsync(keyName)

  override fun validate(state: WireObjectState) = liveMapManager.validate(state)

  fun subscribe(listener: LiveMapChangeListener): Subscription {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    return liveMapManager.subscribe(listener)
  }

  private suspend fun setAsync(keyName: String, value: LiveMapValue) {
    // RTLM20b, RTLM20c, RTLM20d - Validate write API configuration
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    // Validate input parameters
    if (keyName.isEmpty()) {
      throw invalidInputError("Map key should not be empty")
    }

    // RTLM20e - Create ObjectMessage with the MAP_SET operation
    val msg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapSet,
        objectId = objectId,
        mapSet = WireMapSet(
          key = keyName,
          value = fromLiveMapValue(value)
        )
      )
    )

    // RTLM20g - publish and apply locally on ACK
    realtimeObjects.publishAndApply(arrayOf(msg))
  }

  private suspend fun removeAsync(keyName: String) {
    // RTLM21b, RTLM21cm RTLM21d - Validate write API configuration
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    // Validate input parameter
    if (keyName.isEmpty()) {
      throw invalidInputError("Map key should not be empty")
    }

    // RTLM21e - Create ObjectMessage with the MAP_REMOVE operation
    val msg = WireObjectMessage(
      operation = WireObjectOperation(
        action = WireObjectOperationAction.MapRemove,
        objectId = objectId,
        mapRemove = WireMapRemove(key = keyName)
      )
    )

    // RTLM21g - publish and apply locally on ACK
    realtimeObjects.publishAndApply(arrayOf(msg))
  }

  override fun applyObjectState(wireObjectState: WireObjectState, message: WireObjectMessage): ObjectUpdate {
    return liveMapManager.applyState(wireObjectState, message.serialTimestamp)
  }

  override fun applyObjectOperation(operation: WireObjectOperation, message: WireObjectMessage): Boolean {
    return liveMapManager.applyOperation(operation, message.serial, message.serialTimestamp)
  }

  override fun clearData(): ObjectUpdate {
    clearTimeserial = null  // RTLM4
    return liveMapManager.calculateUpdateFromDataDiff(data.toMap(), emptyMap())
      .apply { this@InternalLiveMap.data.clear() }
  }

  override fun notifyUpdated(update: ObjectUpdate) {
    if (update.noOp) {
      return
    }
    Log.v(tag, "Object $objectId updated: $update")

    // TODO - Current cast for emitting event is wrong, need to fix the same.
    liveMapManager.notify(update as LiveMapChangeEvent)
  }

  override fun onGCInterval(gcGracePeriod: Long) {
    data.entries.removeIf { (_, entry) -> entry.isEligibleForGc(gcGracePeriod, clock) }
  }

  companion object {
    /**
     * Creates a zero-value map object.
     * @spec RTLM4 - Returns LiveMap with empty map data
     */
    internal fun zeroValue(objectId: String, objects: DefaultRealtimeObject): InternalLiveMap {
      return InternalLiveMap(objectId, objects)
    }

    /**
     * Creates a MapCreate payload from map entries.
     * Spec: RTO11f14
     */
    internal fun initialValue(entries: MutableMap<String, LiveMapValue>): WireMapCreate {
      return WireMapCreate(
        semantics = WireObjectsMapSemantics.LWW,
        entries = entries.mapValues { (_, value) ->
          WireObjectsMapEntry(
            tombstone = false,
            data = fromLiveMapValue(value)
          )
        }
      )
    }

    /**
     * Spec: RTLM20e5
     */
    private fun fromLiveMapValue(value: LiveMapValue): WireObjectData {
      return when {
        value.isLiveMap || value.isLiveCounter ->
          WireObjectData(objectId = (value.value as BaseRealtimeObject).objectId)
        value.isBoolean ->
          WireObjectData(boolean = value.asBoolean)
        value.isBinary ->
          WireObjectData(bytes = Base64.getEncoder().encodeToString(value.asBinary))
        value.isNumber ->
          WireObjectData(number = value.asNumber.toDouble())
        value.isString ->
          WireObjectData(string = value.asString)
        value.isJsonObject ->
          WireObjectData(json = value.asJsonObject)
        value.isJsonArray ->
          WireObjectData(json = value.asJsonArray)
        else ->
          throw IllegalArgumentException("Unsupported value type")
      }
    }
  }
}
