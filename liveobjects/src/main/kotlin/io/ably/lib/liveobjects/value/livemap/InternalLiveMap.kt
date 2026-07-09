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
  private val realtimeObject: DefaultRealtimeObject,
  internal val semantics: WireObjectsMapSemantics = WireObjectsMapSemantics.LWW
) : BaseRealtimeObject(objectId, ObjectType.Map, realtimeObject.clock) {

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

  private val channelName = realtimeObject.channelName
  private val adapter: AblyClientAdapter get() = realtimeObject.adapter
  internal val objectsPool: ObjectsPool get() = realtimeObject.objectsPool

  internal fun get(keyName: String): LiveMapValue? {
    if (isTombstoned) {
      return null
    }
    data[keyName]?.let { liveMapEntry ->
      return liveMapEntry.getResolvedValue(objectsPool)
    }
    return null // RTLM5d1
  }

  internal fun entries(): Iterable<Map.Entry<String, LiveMapValue>> {
    return sequence<Map.Entry<String, LiveMapValue>> {
      for ((key, entry) in data.entries) {
        val value = entry.getResolvedValue(objectsPool) // RTLM11d, RTLM11d2
        value?.let {
          yield(AbstractMap.SimpleImmutableEntry(key, it))
        }
      }
    }.asIterable()
  }

  internal fun keys(): Iterable<String> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.key) // RTLM12b
      }
    }.asIterable()
  }

  internal fun values(): Iterable<LiveMapValue> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.value) // RTLM13b
      }
    }.asIterable()
  }

  internal fun size(): Long {
    return data.values.count { !it.isEntryOrRefTombstoned(objectsPool) }.toLong() // RTLM10d
  }

  internal suspend fun set(keyName: String, value: LiveMapValue) =  setAsync(keyName, value)

  internal suspend fun remove(keyName: String) = removeAsync(keyName)

  override fun validate(state: WireObjectState) = liveMapManager.validate(state)

  internal fun subscribe(listener: LiveMapChangeListener): Subscription {
    return liveMapManager.subscribe(listener)
  }

  private suspend fun setAsync(keyName: String, value: LiveMapValue) {
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

    // RTLM20h - publish and apply locally on ACK
    realtimeObject.publishAndApply(arrayOf(msg))
  }

  private suspend fun removeAsync(keyName: String) {
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
    realtimeObject.publishAndApply(arrayOf(msg))
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

    // TODO - Emit a proper LiveMapChangeEvent once the Instance/ObjectMessage subscription
    //  pipeline is wired up. ObjectUpdate is not a LiveMapChangeEvent, so casting it (as was
    //  done previously) always throws ClassCastException; emission is deferred until then.
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
     * Spec: RTLMV4e
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
     * Spec: RTLM20e7
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
