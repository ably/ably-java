package io.ably.lib.liveobjects.value.livemap

import io.ably.lib.liveobjects.message.*
import io.ably.lib.liveobjects.message.WireMapSet
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.objectError
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.liveobjects.value.noOp
import io.ably.lib.util.Log
import kotlin.collections.iterator

internal class LiveMapManager(private val liveMap: InternalLiveMap): LiveMapChangeCoordinator() {

  private val objectId = liveMap.objectId

  private val tag = "LiveMapManager"

  /**
   * @spec RTLM6 - Overrides object data with state from sync
   */
  internal fun applyState(wireObjectState: WireObjectState, serialTimestamp: Long?): ObjectUpdate {
    val previousData = liveMap.data.toMap()

    if (wireObjectState.tombstone) {
      liveMap.tombstone(serialTimestamp)
    } else {
      // override data for this object with data from the object state
      liveMap.createOperationIsMerged = false // RTLM6b
      liveMap.data.clear()

      liveMap.clearTimeserial = wireObjectState.map?.clearTimeserial  // RTLM6i

      wireObjectState.map?.entries?.forEach { (key, entry) ->
        liveMap.data[key] = LiveMapEntry(
          isTombstoned = entry.tombstone ?: false,
          tombstonedAt = if (entry.tombstone == true) entry.serialTimestamp
            ?: liveMap.clock.currentTimeMillis() else null,
          timeserial = entry.timeserial,
          data = entry.data
        )
      } // RTLM6c

      // RTLM6d
      wireObjectState.createOp?.let { createOp ->
        mergeInitialDataFromCreateOperation(createOp)
      }
    }

    return calculateUpdateFromDataDiff(previousData, liveMap.data.toMap())
  }

  /**
   * @spec RTLM15 - Applies operations to LiveMap
   */
  internal fun applyOperation(operation: WireObjectOperation, serial: String?, serialTimestamp: Long?): Boolean {
    return when (operation.action) {
      WireObjectOperationAction.MapCreate -> {
        val update = applyMapCreate(operation) // RTLM15d1
        liveMap.notifyUpdated(update) // RTLM15d1a
        true // RTLM15d1b
      }
      WireObjectOperationAction.MapSet -> {
        if (operation.mapSet != null) {
          val update = applyMapSet(operation.mapSet, serial) // RTLM15d2
          liveMap.notifyUpdated(update) // RTLM15d2a
          true // RTLM15d2b
        } else {
          throw objectError("No payload found for ${operation.action} op for LiveMap objectId=${objectId}")
        }
      }
      WireObjectOperationAction.MapRemove -> {
        if (operation.mapRemove != null) {
          val update = applyMapRemove(operation.mapRemove, serial, serialTimestamp) // RTLM15d3
          liveMap.notifyUpdated(update) // RTLM15d3a
          true // RTLM15d3b
        } else {
          throw objectError("No payload found for ${operation.action} op for LiveMap objectId=${objectId}")
        }
      }
      WireObjectOperationAction.ObjectDelete -> {
        val update = liveMap.tombstone(serialTimestamp)
        liveMap.notifyUpdated(update)
        true // RTLM15d5b
      }
      WireObjectOperationAction.MapClear -> {
        val update = applyMapClear(serial) // RTLM15d8
        liveMap.notifyUpdated(update) // RTLM15d8a
        true // RTLM15d8b
      }
      else -> {
        Log.w(tag, "Invalid ${operation.action} op for LiveMap objectId=${objectId}") // RTLM15d4
        false
      }
    }
  }

  /**
   * @spec RTLM16 - Applies map create operation
   */
  private fun applyMapCreate(operation: WireObjectOperation): ObjectUpdate {
    if (liveMap.createOperationIsMerged) {
      // RTLM16b
      // There can't be two different create operation for the same object id, because the object id
      // fully encodes that operation. This means we can safely ignore any new incoming create operations
      // if we already merged it once.
      Log.v(
        tag,
        "Skipping applying MAP_CREATE op on a map instance as it was already applied before; objectId=${objectId}"
      )
      return noOpMapUpdate
    }

    validateMapSemantics(getEffectiveMapCreate(operation)?.semantics) // RTLM16c

    return mergeInitialDataFromCreateOperation(operation) // RTLM16d
  }

  /**
   * @spec RTLM7 - Applies MAP_SET operation to LiveMap
   */
  private fun applyMapSet(
    wireMapSet: WireMapSet, // RTLM7d1
    timeSerial: String?, // RTLM7d2
  ): ObjectUpdate {
    // RTLM7h - skip if operation is older than the last MAP_CLEAR
    val clearSerial = liveMap.clearTimeserial
    if (clearSerial != null && (timeSerial == null || clearSerial >= timeSerial)) {
      Log.v(tag,
        "Skipping MAP_SET for key=\"${wireMapSet.key}\": op serial $timeSerial <= clear serial $clearSerial; objectId=$objectId")
      return noOpMapUpdate
    }

    val existingEntry = liveMap.data[wireMapSet.key]

    // RTLM7a
    if (existingEntry != null && !canApplyMapOperation(existingEntry.timeserial, timeSerial)) {
      // RTLM7a1 - the operation's serial <= the entry's serial, ignore the operation
      Log.v(tag,
        "Skipping update for key=\"${wireMapSet.key}\": op serial $timeSerial <= entry serial ${existingEntry.timeserial};" +
          " objectId=${objectId}"
      )
      return noOpMapUpdate
    }

    if (wireMapSet.value.isInvalid()) {
      throw objectError("Invalid object data for MAP_SET op on objectId=${objectId} on key=${wireMapSet.key}")
    }

    // RTLM7c
    wireMapSet.value.objectId?.let {
      // this MAP_SET op is setting a key to point to another object via its object id,
      // but it is possible that we don't have the corresponding object in the pool yet (for example, we haven't seen the *_CREATE op for it).
      // we don't want to return undefined from this map's .get() method even if we don't have the object,
      // so instead we create a zero-value object for that object id if it not exists.
      liveMap.objectsPool.createZeroValueObjectIfNotExists(it) // RTLM7c1
    }

    if (existingEntry != null) {
      // RTLM7a2 - Replace existing entry with new one instead of mutating
      liveMap.data[wireMapSet.key] = LiveMapEntry(
        isTombstoned = false, // RTLM7a2c
        timeserial = timeSerial, // RTLM7a2b
        data = wireMapSet.value // RTLM7a2a
      )
    } else {
      // RTLM7b, RTLM7b1
      liveMap.data[wireMapSet.key] = LiveMapEntry(
        isTombstoned = false, // RTLM7b2
        timeserial = timeSerial,
        data = wireMapSet.value
      )
    }

    return ObjectUpdate(mapOf(wireMapSet.key to "updated"))
  }

  /**
   * @spec RTLM8 - Applies MAP_REMOVE operation to LiveMap
   */
  private fun applyMapRemove(
    wireMapRemove: WireMapRemove, // RTLM8c1
    timeSerial: String?, // RTLM8c2
    timeStamp: Long?, // RTLM8c3
  ): ObjectUpdate {
    // RTLM8g - skip if operation is older than the last MAP_CLEAR
    val clearSerial = liveMap.clearTimeserial
    if (clearSerial != null && (timeSerial == null || clearSerial >= timeSerial)) {
      Log.v(tag,
        "Skipping MAP_REMOVE for key=\"${wireMapRemove.key}\": op serial $timeSerial <= clear serial $clearSerial; objectId=$objectId")
      return noOpMapUpdate
    }

    val existingEntry = liveMap.data[wireMapRemove.key]

    // RTLM8a
    if (existingEntry != null && !canApplyMapOperation(existingEntry.timeserial, timeSerial)) {
      // RTLM8a1 - the operation's serial <= the entry's serial, ignore the operation
      Log.v(
        tag,
        "Skipping remove for key=\"${wireMapRemove.key}\": op serial $timeSerial <= entry serial ${existingEntry.timeserial}; " +
          "objectId=${objectId}"
      )
      return noOpMapUpdate
    }

    val tombstonedAt = if (timeStamp != null) timeStamp else {
      Log.w(
        tag,
        "No timestamp provided for MAP_REMOVE op on key=\"${wireMapRemove.key}\"; using current time as tombstone time; " +
          "objectId=${objectId}"
      )
      liveMap.clock.currentTimeMillis()
    }

    if (existingEntry != null) {
      // RTLM8a2 - Replace existing entry with new one instead of mutating
      liveMap.data[wireMapRemove.key] = LiveMapEntry(
        isTombstoned = true, // RTLM8a2c
        tombstonedAt = tombstonedAt,
        timeserial = timeSerial, // RTLM8a2b
        data = null // RTLM8a2a
      )
    } else {
      // RTLM8b, RTLM8b1
      liveMap.data[wireMapRemove.key] = LiveMapEntry(
        isTombstoned = true, // RTLM8b2
        tombstonedAt = tombstonedAt,
        timeserial = timeSerial
      )
    }

    return ObjectUpdate(mapOf(wireMapRemove.key to "removed"))
  }

  /**
   * @spec RTLM24 - Applies MAP_CLEAR operation to LiveMap
   */
  private fun applyMapClear(timeSerial: String?): ObjectUpdate {
    val clearSerial = liveMap.clearTimeserial

    // RTLM24c - skip if existing clear serial is strictly newer than incoming op serial
    if (clearSerial != null && (timeSerial == null || clearSerial > timeSerial)) {
      Log.v(tag,
        "Skipping MAP_CLEAR: op serial $timeSerial <= current clear serial $clearSerial; objectId=$objectId")
      return noOpMapUpdate
    }

    Log.v(tag,
      "Updating clearTimeserial; previous=$clearSerial, new=$timeSerial; objectId=$objectId")
    liveMap.clearTimeserial = timeSerial  // RTLM24d

    val update = mutableMapOf<String, String>()

    // RTLM24e - remove all entries whose serial is older than (or equal to missing) the clear serial
    liveMap.data.entries.removeIf {
      val (key, entry) = it
      val entrySerial = entry.timeserial
      if (entrySerial == null || (timeSerial != null && timeSerial > entrySerial)) {
        update[key] = "removed"
        true
      } else {
        false
      }
    }

    return ObjectUpdate(update)
  }

  /**
   * For Lww CRDT semantics (the only supported LiveMap semantic) an operation
   * Should only be applied if incoming serial is strictly greater than existing entry's serial.
   * @spec RTLM9 - Serial comparison logic for map operations
   */
  private fun canApplyMapOperation(existingMapEntrySerial: String?, timeSerial: String?): Boolean {
    if (existingMapEntrySerial.isNullOrEmpty() && timeSerial.isNullOrEmpty()) { // RTLM9b
      return false
    }
    if (existingMapEntrySerial.isNullOrEmpty()) { // RTLM9d - If true, means timeSerial is not empty based on previous checks
      return true
    }
    if (timeSerial.isNullOrEmpty()) { // RTLM9c - Check reached here means existingMapEntrySerial is not empty
      return false
    }
    return timeSerial > existingMapEntrySerial // RTLM9e - both are not empty
  }

  /**
   * @spec RTLM23 - Merges initial data from create operation
   */
  private fun getEffectiveMapCreate(operation: WireObjectOperation): WireMapCreate? =
    operation.mapCreateWithObjectId?.derivedFrom ?: operation.mapCreate

  private fun mergeInitialDataFromCreateOperation(operation: WireObjectOperation): ObjectUpdate {
    val effectiveMapCreate = getEffectiveMapCreate(operation)
    if (effectiveMapCreate?.entries.isNullOrEmpty()) { // no map entries in MAP_CREATE op
      return noOpMapUpdate
    }

    val aggregatedUpdate = mutableListOf<ObjectUpdate>()

    // RTLM23a
    // in order to apply MAP_CREATE op for an existing map, we should merge their underlying entries keys.
    // we can do this by iterating over entries from MAP_CREATE op and apply changes on per-key basis as if we had MAP_SET, MAP_REMOVE operations.
    effectiveMapCreate?.entries?.forEach { (key, entry) ->
      // for a MAP_CREATE operation we must use the serial value available on an entry, instead of a serial on a message
      val opTimeserial = entry.timeserial
      val update = if (entry.tombstone == true) {
        // RTLM23a2  - entry in MAP_CREATE op is removed, try to apply MAP_REMOVE op
        applyMapRemove(WireMapRemove(key), opTimeserial, entry.serialTimestamp)
      } else {
        // RTLM23a1 - entry in MAP_CREATE op is not removed, try to set it via MAP_SET op
        applyMapSet(WireMapSet(key, entry.data ?: throw objectError("MAP_SET operation without data")), opTimeserial)
      }

      // skip noop updates
      if (update.noOp) {
        return@forEach
      }

      aggregatedUpdate.add(update)
    }

    liveMap.createOperationIsMerged = true // RTLM23b

    // TODO - This will need some rework as per new spec, we have commented out old code.
    // Maybe we no more calculate the difference, so we might get rid of the code.
    return ObjectUpdate(aggregatedUpdate)
//    return ObjectUpdate(
//      aggregatedUpdate.map { it.update }.fold(emptyMap()) { acc, map -> acc + map }
//    )
  }

  internal fun calculateUpdateFromDataDiff(
    prevData: Map<String, LiveMapEntry>,
    newData: Map<String, LiveMapEntry>
  ): ObjectUpdate {
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

    return ObjectUpdate(update)
  }

  internal fun validate(state: WireObjectState) {
    liveMap.validateObjectId(state.objectId)
    validateMapSemantics(state.map?.semantics)
    state.createOp?.let { createOp ->
      liveMap.validateObjectId(createOp.objectId)
      validateMapCreateAction(createOp.action)
      validateMapSemantics(getEffectiveMapCreate(createOp)?.semantics)
    }
  }

  private fun validateMapCreateAction(action: WireObjectOperationAction) {
    if (action != WireObjectOperationAction.MapCreate) {
      throw objectError("Invalid create operation action $action for LiveMap objectId=${objectId}")
    }
  }

  private fun validateMapSemantics(semantics: WireObjectsMapSemantics?) {
    if (semantics != liveMap.semantics) {
      throw objectError(
        "Invalid object: incoming object map semantics=$semantics; current map semantics=${WireObjectsMapSemantics.LWW}"
      )
    }
  }
}
