package io.ably.lib.objects.type

import io.ably.lib.objects.*
import io.ably.lib.objects.ErrorCode
import io.ably.lib.objects.HttpStatusCode
import io.ably.lib.objects.ObjectsPool
import io.ably.lib.objects.ObjectsPoolDefaults
import io.ably.lib.objects.ablyException
import io.ably.lib.objects.objectError
import io.ably.lib.objects.MapSemantics
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectMapOp
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectState
import io.ably.lib.types.AblyException
import io.ably.lib.types.Callback
import io.ably.lib.util.Log

/**
 * Implementation of LiveObject for LiveMap.
 *
 * @spec RTLM1/RTLM2 - LiveMap implementation extends LiveObject
 */
internal class DefaultLiveMap(
  objectId: String,
  adapter: LiveObjectsAdapter,
  private val objectsPool: ObjectsPool,
  private val semantics: MapSemantics = MapSemantics.LWW
) : LiveMap, BaseLiveObject(objectId, adapter) {

  override val tag = "LiveMap"

  /**
   * @spec RTLM3 - Map data structure storing entries
   */
  internal data class LiveMapEntry(
    var tombstone: Boolean = false,
    var tombstonedAt: Long? = null,
    var timeserial: String? = null,
    var data: ObjectData? = null
  )

  /**
   * Map of key to LiveMapEntry
   */
  private val data = mutableMapOf<String, LiveMapEntry>()

  /**
   * @spec RTLM6 - Overrides object data with state from sync
   */
  override fun overrideWithObjectState(objectState: ObjectState): Map<String, String> {
    if (objectState.objectId != objectId) {
      throw objectError("Invalid object state: object state objectId=${objectState.objectId}; LiveMap objectId=$objectId")
    }

    if (objectState.map?.semantics != semantics) {
      throw objectError(
        "Invalid object state: object state map semantics=${objectState.map?.semantics}; LiveMap semantics=$semantics",
      )
    }

    // object's site serials are still updated even if it is tombstoned, so always use the site serials received from the op.
    // should default to empty map if site serials do not exist on the object state, so that any future operation may be applied to this object.
    siteTimeserials.clear()
    siteTimeserials.putAll(objectState.siteTimeserials) // RTLM6a

    if (isTombstoned) {
      // this object is tombstoned. this is a terminal state which can't be overridden. skip the rest of object state message processing
      return mapOf()
    }

    val previousData = data.toMap()

    if (objectState.tombstone) {
      tombstone()
    } else {
      // override data for this object with data from the object state
      createOperationIsMerged = false // RTLM6b
      data.clear()

      objectState.map.entries?.forEach { (key, entry) ->
        data[key] = LiveMapEntry(
          tombstone = entry.tombstone ?: false,
          tombstonedAt = if (entry.tombstone == true) System.currentTimeMillis() else null,
          timeserial = entry.timeserial,
          data = entry.data
        )
      } // RTLM6c

      // RTLM6d
      objectState.createOp?.let { createOp ->
        mergeInitialDataFromCreateOperation(createOp)
      }
    }

    return calculateUpdateFromDataDiff(previousData, data.toMap())
  }

  private fun payloadError(op: ObjectOperation) : AblyException {
    return ablyException("No payload found for ${op.action} op for LiveMap objectId=${this.objectId}",
      ErrorCode.InvalidObject, HttpStatusCode.InternalServerError
    )
  }

  /**
   * @spec RTLM15 - Applies operations to LiveMap
   */
  override fun applyOperation(operation: ObjectOperation, message: ObjectMessage) {
    if (operation.objectId != objectId) {
      throw objectError(
        "Cannot apply object operation with objectId=${operation.objectId}, to this LiveMap with objectId=$objectId",
      )
    }

    val opSerial = message.serial
    val opSiteCode = message.siteCode

    if (!canApplyOperation(opSiteCode, opSerial)) {
      // RTLM15b
      Log.v(
        tag,
        "Skipping ${operation.action} op: op serial $opSerial <= site serial ${siteTimeserials[opSiteCode]}; objectId=$objectId"
      )
      return
    }
    // should update stored site serial immediately. doesn't matter if we successfully apply the op,
    // as it's important to mark that the op was processed by the object
    updateTimeSerial(opSiteCode!!, opSerial!!) // RTLM15c

    if (isTombstoned) {
      // this object is tombstoned so the operation cannot be applied
      return;
    }

    val update = when (operation.action) {
      ObjectOperationAction.MapCreate -> applyMapCreate(operation) // RTLM15d1
      ObjectOperationAction.MapSet -> {
        if (operation.mapOp != null) {
          applyMapSet(operation.mapOp, opSerial) // RTLM15d2
        } else {
          throw payloadError(operation)
        }
      }
      ObjectOperationAction.MapRemove -> {
        if (operation.mapOp != null) {
          applyMapRemove(operation.mapOp, opSerial) // RTLM15d3
        } else {
          throw payloadError(operation)
        }
      }
      ObjectOperationAction.ObjectDelete -> applyObjectDelete()
      else -> throw objectError("Invalid ${operation.action} op for LiveMap objectId=$objectId") // RTLM15d4
    }

    notifyUpdated(update)
  }

  override fun clearData(): Map<String, String> {
    val previousData = data.toMap()
    data.clear()
    return calculateUpdateFromDataDiff(previousData, emptyMap())
  }

  /**
   * @spec RTLM16 - Applies map create operation
   */
  private fun applyMapCreate(operation: ObjectOperation): Map<String, String> {
    if (createOperationIsMerged) {
      // RTLM16b
      // There can't be two different create operation for the same object id, because the object id
      // fully encodes that operation. This means we can safely ignore any new incoming create operations
      // if we already merged it once.
      Log.v(
        tag,
        "Skipping applying MAP_CREATE op on a map instance as it was already applied before; objectId=$objectId"
      )
      return mapOf()
    }

    if (semantics != operation.map?.semantics) {
      // RTLM16c
      throw objectError(
        "Cannot apply MAP_CREATE op on LiveMap objectId=$objectId; map's semantics=$semantics, but op expected ${operation.map?.semantics}",
      )
    }

    return mergeInitialDataFromCreateOperation(operation) // RTLM16d
  }

  /**
   * @spec RTLM7 - Applies MAP_SET operation to LiveMap
   */
  private fun applyMapSet(
    mapOp: ObjectMapOp, // RTLM7d1
    opSerial: String?, // RTLM7d2
  ): Map<String, String> {
    val existingEntry = data[mapOp.key]

    // RTLM7a
    if (existingEntry != null && !canApplyMapOperation(existingEntry.timeserial, opSerial)) {
      // RTLM7a1 - the operation's serial <= the entry's serial, ignore the operation
      Log.v(
        tag,
        "Skipping update for key=\"${mapOp.key}\": op serial $opSerial <= entry serial ${existingEntry.timeserial}; objectId=$objectId"
      )
      return mapOf()
    }

    if (mapOp.data.isInvalid()) {
      throw objectError("Invalid object data for MAP_SET op on objectId=${objectId} on key=${mapOp.key}")
    }

    // RTLM7c
    mapOp.data?.objectId?.let {
      // this MAP_SET op is setting a key to point to another object via its object id,
      // but it is possible that we don't have the corresponding object in the pool yet (for example, we haven't seen the *_CREATE op for it).
      // we don't want to return undefined from this map's .get() method even if we don't have the object,
      // so instead we create a zero-value object for that object id if it not exists.
      objectsPool.createZeroValueObjectIfNotExists(it) // RTLM7c1
    }

    if (existingEntry != null) {
      // RTLM7a2
      existingEntry.tombstone = false // RTLM7a2c
      existingEntry.tombstonedAt = null
      existingEntry.timeserial = opSerial // RTLM7a2b
      existingEntry.data = mapOp.data // RTLM7a2a
    } else {
      // RTLM7b, RTLM7b1
      data[mapOp.key] = LiveMapEntry(
        tombstone = false, // RTLM7b2
        timeserial = opSerial,
        data = mapOp.data
      )
    }

    return mapOf(mapOp.key to "updated")
  }

  /**
   * @spec RTLM8 - Applies MAP_REMOVE operation to LiveMap
   */
  private fun applyMapRemove(
    mapOp: ObjectMapOp, // RTLM8c1
    opSerial: String?, // RTLM8c2
  ): Map<String, String> {
    val existingEntry = data[mapOp.key]

    // RTLM8a
    if (existingEntry != null && !canApplyMapOperation(existingEntry.timeserial, opSerial)) {
      // RTLM8a1 - the operation's serial <= the entry's serial, ignore the operation
      Log.v(
        tag,
        "Skipping remove for key=\"${mapOp.key}\": op serial $opSerial <= entry serial ${existingEntry.timeserial}; objectId=$objectId"
      )
      return mapOf()
    }

    if (existingEntry != null) {
      // RTLM8a2
      existingEntry.tombstone = true // RTLM8a2c
      existingEntry.tombstonedAt = System.currentTimeMillis()
      existingEntry.timeserial = opSerial // RTLM8a2b
      existingEntry.data = null // RTLM8a2a
    } else {
      // RTLM8b, RTLM8b1
      data[mapOp.key] = LiveMapEntry(
        tombstone = true, // RTLM8b2
        tombstonedAt = System.currentTimeMillis(),
        timeserial = opSerial
      )
    }

    return mapOf(mapOp.key to "removed")
  }

  /**
   * For Lww CRDT semantics (the only supported LiveMap semantic) an operation
   * Should only be applied if incoming serial is strictly greater than existing entry's serial.
   * @spec RTLM9 - Serial comparison logic for map operations
   */
  private fun canApplyMapOperation(existingMapEntrySerial: String?, opSerial: String?): Boolean {
    if (existingMapEntrySerial.isNullOrEmpty() && opSerial.isNullOrEmpty()) { // RTLM9b
      return false
    }
    if (existingMapEntrySerial.isNullOrEmpty()) { // RTLM9d - If true, means opSerial is not empty based on previous checks
      return true
    }
    if (opSerial.isNullOrEmpty()) { // RTLM9c - Check reached here means existingMapEntrySerial is not empty
      return false
    }
    return opSerial > existingMapEntrySerial // RTLM9e - both are not empty
  }

  /**
   * @spec RTLM17 - Merges initial data from create operation
   */
  private fun mergeInitialDataFromCreateOperation(operation: ObjectOperation): Map<String, String> {
    if (operation.map?.entries.isNullOrEmpty()) { // no map entries in MAP_CREATE op
      return mapOf()
    }

    val aggregatedUpdate = mutableMapOf<String, String>()

    // RTLM17a
    // in order to apply MAP_CREATE op for an existing map, we should merge their underlying entries keys.
    // we can do this by iterating over entries from MAP_CREATE op and apply changes on per-key basis as if we had MAP_SET, MAP_REMOVE operations.
    operation.map?.entries?.forEach { (key, entry) ->
      // for a MAP_CREATE operation we must use the serial value available on an entry, instead of a serial on a message
      val opTimeserial = entry.timeserial
      val update = if (entry.tombstone == true) {
        // RTLM17a2 - entry in MAP_CREATE op is removed, try to apply MAP_REMOVE op
        applyMapRemove(ObjectMapOp(key), opTimeserial)
      } else {
        // RTLM17a1 - entry in MAP_CREATE op is not removed, try to set it via MAP_SET op
        applyMapSet(ObjectMapOp(key, entry.data), opTimeserial)
      }

      // skip noop updates
      if (update.isEmpty()) {
        return@forEach
      }

      aggregatedUpdate.putAll(update)
    }

    createOperationIsMerged = true // RTLM17b

    return aggregatedUpdate
  }

  private fun calculateUpdateFromDataDiff(prevData: Map<String, LiveMapEntry>, newData: Map<String, LiveMapEntry>): Map<String, String> {
    val update = mutableMapOf<String, String>()

    // Check for removed entries
    for ((key, prevEntry) in prevData) {
      if (!prevEntry.tombstone && !newData.containsKey(key)) {
        update[key] = "removed"
      }
    }

    // Check for added/updated entries
    for ((key, newEntry) in newData) {
      if (!prevData.containsKey(key)) {
        // if property does not exist in current map, but new data has it as non-tombstoned property - got updated
        if (!newEntry.tombstone) {
          update[key] = "updated"
        }
        // otherwise, if new data has this prop tombstoned - do nothing, as property didn't exist anyway
        continue
      }

      // properties that exist both in current and new map data need to have their values compared to decide on update type
      val prevEntry = prevData[key]!!

      // compare tombstones first
      if (prevEntry.tombstone && !newEntry.tombstone) {
        // prev prop is tombstoned, but new is not. it means prop was updated to a meaningful value
        update[key] = "updated"
        continue
      }
      if (!prevEntry.tombstone && newEntry.tombstone) {
        // prev prop is not tombstoned, but new is. it means prop was removed
        update[key] = "removed"
        continue
      }
      if (prevEntry.tombstone && newEntry.tombstone) {
        // props are tombstoned - treat as noop, as there is no data to compare
        continue
      }

      // both props exist and are not tombstoned, need to compare values to see if it was changed
      val valueChanged = prevEntry.data != newEntry.data
      if (valueChanged) {
        update[key] = "updated"
        continue
      }
    }

    return update
  }

  /**
   * Called during garbage collection intervals.
   * Removes tombstoned entries that have exceeded the GC grace period.
   */
  override fun onGCInterval() {
    val keysToDelete = mutableListOf<String>()

    for ((key, entry) in data.entries) {
      if (entry.tombstone &&
          entry.tombstonedAt != null &&
          System.currentTimeMillis() - entry.tombstonedAt!! >= ObjectsPoolDefaults.GC_GRACE_PERIOD_MS
      ) {
        keysToDelete.add(key)
      }
    }

    keysToDelete.forEach { data.remove(it) }
  }

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
