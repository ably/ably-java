package io.ably.lib.objects.type.livemap

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectsMapSemantics
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.type.BaseRealtimeObject
import io.ably.lib.objects.type.ObjectUpdate
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.objects.type.map.LiveMap
import io.ably.lib.objects.type.map.LiveMapChange
import io.ably.lib.objects.type.map.LiveMapUpdate
import io.ably.lib.objects.type.map.LiveMapValue
import io.ably.lib.objects.type.noOp
import io.ably.lib.util.Log
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.AbstractMap

/**
 * @spec RTLM1/RTLM2 - LiveMap implementation extends BaseRealtimeObject
 */
internal class DefaultLiveMap private constructor(
  objectId: String,
  private val realtimeObjects: DefaultRealtimeObjects,
  internal val semantics: ObjectsMapSemantics = ObjectsMapSemantics.LWW
) : LiveMap, BaseRealtimeObject(objectId, ObjectType.Map) {

  override val tag = "LiveMap"

  /**
   * ConcurrentHashMap for thread-safe access from public APIs in LiveMap and LiveMapManager.
   */
  internal val data = ConcurrentHashMap<String, LiveMapEntry>()

  /**
   * LiveMapManager instance for managing LiveMap operations
   */
  private val liveMapManager = LiveMapManager(this)

  private val channelName = realtimeObjects.channelName
  private val adapter: ObjectsAdapter get() = realtimeObjects.adapter
  internal val objectsPool: ObjectsPool get() = realtimeObjects.objectsPool
  private val asyncScope get() = realtimeObjects.asyncScope

  override fun get(keyName: String): LiveMapValue? {
    adapter.throwIfInvalidAccessApiConfiguration(channelName) // RTLM5b, RTLM5c
    if (isTombstoned) {
      return null
    }
    data[keyName]?.let { liveMapEntry ->
      return liveMapEntry.getResolvedValue(objectsPool)
    }
    return null // RTLM5d1
  }

  override fun entries(): Iterable<Map.Entry<String, LiveMapValue>> {
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

  override fun keys(): Iterable<String> {
    val iterableEntries = entries()
    return sequence {
      for (entry in iterableEntries) {
        yield(entry.key) // RTLM12b
      }
    }.asIterable()
  }

  override fun values(): Iterable<LiveMapValue> {
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

  override fun set(keyName: String, value: LiveMapValue) = runBlocking { setAsync(keyName, value) }

  override fun remove(keyName: String) = runBlocking { removeAsync(keyName) }

  override fun setAsync(keyName: String, value: LiveMapValue, callback: ObjectsCallback<Void>) {
    asyncScope.launchWithVoidCallback(callback) { setAsync(keyName, value) }
  }

  override fun removeAsync(keyName: String, callback: ObjectsCallback<Void>) {
    asyncScope.launchWithVoidCallback(callback) { removeAsync(keyName) }
  }

  override fun validate(state: ObjectState) = liveMapManager.validate(state)

  override fun subscribe(listener: LiveMapChange.Listener): ObjectsSubscription {
    adapter.throwIfInvalidAccessApiConfiguration(channelName)
    return liveMapManager.subscribe(listener)
  }

  override fun unsubscribe(listener: LiveMapChange.Listener) = liveMapManager.unsubscribe(listener)

  override fun unsubscribeAll() = liveMapManager.unsubscribeAll()

  private suspend fun setAsync(keyName: String, value: LiveMapValue) {
    // RTLM20b, RTLM20c, RTLM20d - Validate write API configuration
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    // Validate input parameters
    if (keyName.isEmpty()) {
      throw invalidInputError("Map key should not be empty")
    }

    // RTLM20e - Create ObjectMessage with the MAP_SET operation
    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.MapSet,
        objectId = objectId,
        mapOp = ObjectsMapOp(
          key = keyName,
          data = fromLiveMapValue(value)
        )
      )
    )

    // RTLM20f - Publish the message
    realtimeObjects.publish(arrayOf(msg))
  }

  private suspend fun removeAsync(keyName: String) {
    // RTLM21b, RTLM21cm RTLM21d - Validate write API configuration
    adapter.throwIfInvalidWriteApiConfiguration(channelName)

    // Validate input parameter
    if (keyName.isEmpty()) {
      throw invalidInputError("Map key should not be empty")
    }

    // RTLM21e - Create ObjectMessage with the MAP_REMOVE operation
    val msg = ObjectMessage(
      operation = ObjectOperation(
        action = ObjectOperationAction.MapRemove,
        objectId = objectId,
        mapOp = ObjectsMapOp(key = keyName)
      )
    )

    // RTLM21f - Publish the message
    realtimeObjects.publish(arrayOf(msg))
  }

  override fun applyObjectState(objectState: ObjectState, message: ObjectMessage): LiveMapUpdate {
    return liveMapManager.applyState(objectState, message.serialTimestamp)
  }

  override fun applyObjectOperation(operation: ObjectOperation, message: ObjectMessage) {
    liveMapManager.applyOperation(operation, message.serial, message.serialTimestamp)
  }

  override fun clearData(): LiveMapUpdate {
    return liveMapManager.calculateUpdateFromDataDiff(data.toMap(), emptyMap())
      .apply { data.clear() }
  }

  override fun notifyUpdated(update: ObjectUpdate) {
    if (update.noOp) {
      return
    }
    Log.v(tag, "Object $objectId updated: $update")
    liveMapManager.notify(update as LiveMapUpdate)
  }

  override fun onGCInterval() {
    data.entries.removeIf { (_, entry) -> entry.isEligibleForGc() }
  }

  companion object {
    /**
     * Creates a zero-value map object.
     * @spec RTLM4 - Returns LiveMap with empty map data
     */
    internal fun zeroValue(objectId: String, objects: DefaultRealtimeObjects): DefaultLiveMap {
      return DefaultLiveMap(objectId, objects)
    }

    /**
     * Creates an ObjectMap from map entries.
     * Spec: RTO11f4
     */
    internal fun initialValue(entries: MutableMap<String, LiveMapValue>): MapCreatePayload {
      return MapCreatePayload(
        map = ObjectsMap(
          semantics = ObjectsMapSemantics.LWW,
          entries = entries.mapValues { (_, value) ->
            ObjectsMapEntry(
              tombstone = false,
              data = fromLiveMapValue(value)
            )
          }
        )
      )
    }

    /**
     * Spec: RTLM20e5
     */
    private fun fromLiveMapValue(value: LiveMapValue): ObjectData {
      return when {
        value.isLiveMap || value.isLiveCounter -> {
          ObjectData(objectId = (value.value as BaseRealtimeObject).objectId)
        }
        value.isBoolean -> {
          ObjectData(value = ObjectValue.Boolean(value.asBoolean))
        }
        value.isBinary -> {
          ObjectData(value = ObjectValue.Binary(Binary(value.asBinary)))
        }
        value.isNumber -> {
          ObjectData(value = ObjectValue.Number(value.asNumber))
        }
        value.isString -> {
          ObjectData(value = ObjectValue.String(value.asString))
        }
        value.isJsonObject -> {
          ObjectData(value = ObjectValue.JsonObject(value.asJsonObject))
        }
        value.isJsonArray -> {
          ObjectData(value = ObjectValue.JsonArray(value.asJsonArray))
        }
        else -> {
          throw IllegalArgumentException("Unsupported value type")
        }
      }
    }
  }
}
