package io.ably.lib.objects

import io.ably.lib.util.Log

/**
 * Implementation of LiveObject for LiveMap.
 * Similar to JavaScript LiveMap class.
 *
 * @spec RTLM1/RTLM2 - LiveMap implementation extends LiveObject
 */
internal class LiveMap(
  objectId: String,
  adapter: LiveObjectsAdapter,
  private val semantics: MapSemantics = MapSemantics.LWW
) : BaseLiveObject(objectId, adapter) {

  override val tag = "LiveMap"

  /**
   * @spec RTLM3 - Map data structure storing entries
   */
  private data class LiveMapEntry(
    var tombstone: Boolean = false,
    var tombstonedAt: Long? = null,
    var timeserial: String? = null,
    var data: ObjectData? = null
  )

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
      return mapOf<String, String>()
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

  /**
   * @spec RTLM7 - Applies operations to LiveMap
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
      Log.v(
        tag,
        "Skipping ${operation.action} op: op serial $opSerial <= site serial ${siteTimeserials[opSiteCode]}; objectId=$objectId"
      )
      return
    }

    // should update stored site serial immediately. doesn't matter if we successfully apply the op,
    // as it's important to mark that the op was processed by the object
    updateTimeSerial(opSiteCode!!, opSerial!!)

    val update = when (operation.action) {
      ObjectOperationAction.MapCreate -> applyMapCreate(operation)
      ObjectOperationAction.MapSet -> applyMapSet(operation.mapOp!!, opSerial)
      ObjectOperationAction.MapRemove -> applyMapRemove(operation.mapOp!!, opSerial)
      ObjectOperationAction.ObjectDelete -> applyObjectDelete()
      else -> {
        Log.w(tag, "Invalid ${operation.action} op for LiveMap objectId=$objectId")
        return
      }
    }

    notifyUpdated(update)
  }

  override fun clearData(): Map<String, String> {
    val previousData = data.toMap()
    data.clear()
    return calculateUpdateFromDataDiff(previousData, emptyMap())
  }

  /**
   * @spec RTLM6d - Merges initial data from create operation
   */
  private fun applyMapCreate(operation: ObjectOperation): Map<String, String> {
    if (createOperationIsMerged) {
      Log.v(
        tag,
        "Skipping applying MAP_CREATE op on a map instance as it was already applied before; objectId=$objectId"
      )
      return mapOf()
    }

    if (semantics != operation.map?.semantics) {
      throw objectError(
        "Cannot apply MAP_CREATE op on LiveMap objectId=$objectId; map's semantics=$semantics, but op expected ${operation.map?.semantics}",
      )
    }

    return mergeInitialDataFromCreateOperation(operation)
  }

  /**
   * @spec RTLM7 - Applies MAP_SET operation to LiveMap
   */
  private fun applyMapSet(mapOp: ObjectMapOp, opSerial: String?): Map<String, String> {
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
  private fun applyMapRemove(mapOp: ObjectMapOp, opSerial: String?): Map<String, String> {
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
   * @spec RTLM6d - Merges initial data from create operation
   */
  private fun mergeInitialDataFromCreateOperation(operation: ObjectOperation): Map<String, String> {
    if (operation.map?.entries.isNullOrEmpty()) { // no map entries in MAP_CREATE op
      return mapOf()
    }

    val aggregatedUpdate = mutableMapOf<String, String>()

    // RTLM6d1
    // in order to apply MAP_CREATE op for an existing map, we should merge their underlying entries keys.
    // we can do this by iterating over entries from MAP_CREATE op and apply changes on per-key basis as if we had MAP_SET, MAP_REMOVE operations.
    operation.map?.entries?.forEach { (key, entry) ->
      // for a MAP_CREATE operation we must use the serial value available on an entry, instead of a serial on a message
      val opSerial = entry.timeserial
      val update = if (entry.tombstone == true) {
        // RTLM6d1b - entry in MAP_CREATE op is removed, try to apply MAP_REMOVE op
        applyMapRemove(ObjectMapOp(key), opSerial)
      } else {
        // RTLM6d1a - entry in MAP_CREATE op is not removed, try to set it via MAP_SET op
        applyMapSet(ObjectMapOp(key, entry.data), opSerial)
      }

      if (update is Map<*, *>) {
        aggregatedUpdate.putAll(update as Map<String, String>)
      }
    }

    createOperationIsMerged = true // RTLM6d2

    return aggregatedUpdate
  }

  private fun calculateUpdateFromDataDiff(prevData: Map<String, LiveMapEntry>, newData: Map<String, LiveMapEntry>): Map<String, String> {
    val update = mutableMapOf<String, String>()

    // Check for removed entries
    for ((key, entry) in prevData) {
      if (!entry.tombstone && !newData.containsKey(key)) {
        update[key] = "removed"
      }
    }

    // Check for added/updated entries
    for ((key, entry) in newData) {
      if (!prevData.containsKey(key)) {
        if (!entry.tombstone) {
          update[key] = "updated"
        }
      } else {
        val prevEntry = prevData[key]!!
        if (prevEntry.tombstone && !entry.tombstone) {
          update[key] = "updated"
        } else if (!prevEntry.tombstone && entry.tombstone) {
          update[key] = "removed"
        } else if (!prevEntry.tombstone && !entry.tombstone) {
          // Compare values
          if (prevEntry.data != entry.data) {
            update[key] = "updated"
          }
        }
      }
    }

    return update
  }

  companion object {
    /**
     * Creates a zero-value map object.
     * @spec RTLM4 - Returns LiveMap with empty map data
     */
    internal fun zeroValue(objectId: String, adapter: LiveObjectsAdapter): LiveMap {
      return LiveMap(objectId, adapter)
    }
  }
}
