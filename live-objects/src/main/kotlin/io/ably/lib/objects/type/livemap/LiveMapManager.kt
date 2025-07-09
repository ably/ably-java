package io.ably.lib.objects.type.livemap

import io.ably.lib.objects.ObjectMapOp
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.isInvalid
import io.ably.lib.objects.objectError
import io.ably.lib.util.Log

internal class LiveMapManager(private val liveMap: DefaultLiveMap) {
  private val objectId = liveMap.objectId

  private val tag = "LiveMapManager"

  /**
   * @spec RTLM6 - Overrides object data with state from sync
   */
  internal fun overrideWithObjectState(objectState: ObjectState): Map<String, String> {
    if (objectState.objectId != objectId) {
      throw objectError("Invalid object state: object state objectId=${objectState.objectId}; " +
        "LiveMap objectId=${objectId}")
    }

    if (objectState.map?.semantics != liveMap.semantics) {
      throw objectError(
        "Invalid object state: object state map semantics=${objectState.map?.semantics}; " +
          "LiveMap semantics=${liveMap.semantics}",
      )
    }

    // object's site serials are still updated even if it is tombstoned, so always use the site serials received from the op.
    // should default to empty map if site serials do not exist on the object state, so that any future operation may be applied to this object.
    liveMap.siteTimeserials.clear()
    liveMap.siteTimeserials.putAll(objectState.siteTimeserials) // RTLM6a

    if (liveMap.isTombstoned) {
      // this object is tombstoned. this is a terminal state which can't be overridden. skip the rest of object state message processing
      return mapOf()
    }

    val previousData = liveMap.data.toMap()

    if (objectState.tombstone) {
      liveMap.tombstone()
    } else {
      // override data for this object with data from the object state
      liveMap.createOperationIsMerged = false // RTLM6b
      liveMap.data.clear()

      objectState.map.entries?.forEach { (key, entry) ->
        liveMap.data[key] = LiveMapEntry(
          isTombstoned = entry.tombstone ?: false,
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

    return calculateUpdateFromDataDiff(previousData, liveMap.data.toMap())
  }

  /**
   * @spec RTLM15 - Applies operations to LiveMap
   */
  internal fun applyOperation(operation: ObjectOperation, message: ObjectMessage) {
    if (operation.objectId != objectId) {
      throw objectError(
        "Cannot apply object operation with objectId=${operation.objectId}, to this LiveMap with " +
          "objectId=${objectId}",
      )
    }

    val opSerial = message.serial
    val opSiteCode = message.siteCode

    if (!liveMap.canApplyOperation(opSiteCode, opSerial)) {
      // RTLM15b
      Log.v(
        tag,
        "Skipping ${operation.action} op: op serial $opSerial <= site serial ${liveMap.siteTimeserials[opSiteCode]};" +
          " objectId=${objectId}"
      )
      return
    }
    // should update stored site serial immediately. doesn't matter if we successfully apply the op,
    // as it's important to mark that the op was processed by the object
    liveMap.siteTimeserials[opSiteCode!!] = opSerial!! // RTLM15c

    if (liveMap.isTombstoned) {
      // this object is tombstoned so the operation cannot be applied
      return;
    }

    val update = when (operation.action) {
      ObjectOperationAction.MapCreate -> applyMapCreate(operation) // RTLM15d1
      ObjectOperationAction.MapSet -> {
        if (operation.mapOp != null) {
          applyMapSet(operation.mapOp, opSerial) // RTLM15d2
        } else {
          throw objectError("No payload found for ${operation.action} op for LiveMap objectId=${objectId}")
        }
      }
      ObjectOperationAction.MapRemove -> {
        if (operation.mapOp != null) {
          applyMapRemove(operation.mapOp, opSerial) // RTLM15d3
        } else {
          throw objectError("No payload found for ${operation.action} op for LiveMap objectId=${objectId}")
        }
      }
      ObjectOperationAction.ObjectDelete -> liveMap.tombstone()
      else -> throw objectError("Invalid ${operation.action} op for LiveMap objectId=${objectId}") // RTLM15d4
    }

    liveMap.notifyUpdated(update)
  }

  /**
   * @spec RTLM16 - Applies map create operation
   */
  private fun applyMapCreate(operation: ObjectOperation): Map<String, String> {
    if (liveMap.createOperationIsMerged) {
      // RTLM16b
      // There can't be two different create operation for the same object id, because the object id
      // fully encodes that operation. This means we can safely ignore any new incoming create operations
      // if we already merged it once.
      Log.v(
        tag,
        "Skipping applying MAP_CREATE op on a map instance as it was already applied before; objectId=${objectId}"
      )
      return mapOf()
    }

    if (liveMap.semantics != operation.map?.semantics) {
      // RTLM16c
      throw objectError(
        "Cannot apply MAP_CREATE op on LiveMap objectId=${objectId}; map's semantics=${liveMap.semantics}, but op expected ${operation.map?.semantics}",
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
    val existingEntry = liveMap.data[mapOp.key]

    // RTLM7a
    if (existingEntry != null && !canApplyMapOperation(existingEntry.timeserial, opSerial)) {
      // RTLM7a1 - the operation's serial <= the entry's serial, ignore the operation
      Log.v(tag,
        "Skipping update for key=\"${mapOp.key}\": op serial $opSerial <= entry serial ${existingEntry.timeserial};" +
          " objectId=${objectId}"
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
      liveMap.objectsPool.createZeroValueObjectIfNotExists(it) // RTLM7c1
    }

    if (existingEntry != null) {
      // RTLM7a2
      existingEntry.isTombstoned = false // RTLM7a2c
      existingEntry.tombstonedAt = null
      existingEntry.timeserial = opSerial // RTLM7a2b
      existingEntry.data = mapOp.data // RTLM7a2a
    } else {
      // RTLM7b, RTLM7b1
      liveMap.data[mapOp.key] = LiveMapEntry(
        isTombstoned = false, // RTLM7b2
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
    val existingEntry = liveMap.data[mapOp.key]

    // RTLM8a
    if (existingEntry != null && !canApplyMapOperation(existingEntry.timeserial, opSerial)) {
      // RTLM8a1 - the operation's serial <= the entry's serial, ignore the operation
      Log.v(
        tag,
        "Skipping remove for key=\"${mapOp.key}\": op serial $opSerial <= entry serial ${existingEntry.timeserial}; " +
          "objectId=${objectId}"
      )
      return mapOf()
    }

    if (existingEntry != null) {
      // RTLM8a2
      existingEntry.isTombstoned = true // RTLM8a2c
      existingEntry.tombstonedAt = System.currentTimeMillis()
      existingEntry.timeserial = opSerial // RTLM8a2b
      existingEntry.data = null // RTLM8a2a
    } else {
      // RTLM8b, RTLM8b1
      liveMap.data[mapOp.key] = LiveMapEntry(
        isTombstoned = true, // RTLM8b2
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

    liveMap.createOperationIsMerged = true // RTLM17b

    return aggregatedUpdate
  }

  internal fun calculateUpdateFromDataDiff(prevData: Map<String, LiveMapEntry>, newData: Map<String, LiveMapEntry>): Map<String, String> {
    val update = mutableMapOf<String, String>()

    // Check for removed entries
    for ((key, prevEntry) in prevData) {
      if (!prevEntry.isTombstoned && !newData.containsKey(key)) {
        update[key] = "removed"
      }
    }

    // Check for added/updated entries
    for ((key, newEntry) in newData) {
      if (!prevData.containsKey(key)) {
        // if property does not exist in current map, but new data has it as non-tombstoned property - got updated
        if (!newEntry.isTombstoned) {
          update[key] = "updated"
        }
        // otherwise, if new data has this prop tombstoned - do nothing, as property didn't exist anyway
        continue
      }

      // properties that exist both in current and new map data need to have their values compared to decide on update type
      val prevEntry = prevData[key]!!

      // compare tombstones first
      if (prevEntry.isTombstoned && !newEntry.isTombstoned) {
        // prev prop is tombstoned, but new is not. it means prop was updated to a meaningful value
        update[key] = "updated"
        continue
      }
      if (!prevEntry.isTombstoned && newEntry.isTombstoned) {
        // prev prop is not tombstoned, but new is. it means prop was removed
        update[key] = "removed"
        continue
      }
      if (prevEntry.isTombstoned && newEntry.isTombstoned) {
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
}
