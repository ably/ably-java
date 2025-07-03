package io.ably.lib.objects

import io.ably.lib.util.Log

/**
 * Base implementation of LiveObject interface.
 * Provides common functionality for all live objects.
 * 
 * @spec RTLM1 - Base class for LiveMap objects
 * @spec RTLC1 - Base class for LiveCounter objects
 */
internal abstract class BaseLiveObject(
  protected val objectId: String,
  protected val adapter: LiveObjectsAdapter
) : LiveObject {

  protected val tag = "BaseLiveObject"
  protected var isTombstoned = false
  protected var tombstonedAt: Long? = null
  /**
   * @spec RTLM6 - Map of serials keyed by site code for LiveMap
   * @spec RTLC6 - Map of serials keyed by site code for LiveCounter
   */
  protected val siteTimeserials = mutableMapOf<String, String>()
  /**
   * @spec RTLM6 - Flag to track if create operation has been merged for LiveMap
   * @spec RTLC6 - Flag to track if create operation has been merged for LiveCounter
   */
  protected var createOperationIsMerged = false

  override fun notifyUpdated(update: Any) {
    // TODO: Implement event emission for updates
    Log.v(tag, "Object $objectId updated: $update")
  }

  /**
   * Checks if an operation can be applied based on serial comparison.
   * Similar to JavaScript _canApplyOperation method.
   * 
   * @spec RTLM9 - Serial comparison logic for LiveMap operations
   * @spec RTLC9 - Serial comparison logic for LiveCounter operations
   */
  protected fun canApplyOperation(opSerial: String?, opSiteCode: String?): Boolean {
    if (opSerial.isNullOrEmpty()) {
      throw IllegalArgumentException("Invalid serial: $opSerial")
    }
    if (opSiteCode.isNullOrEmpty()) {
      throw IllegalArgumentException("Invalid site code: $opSiteCode")
    }

    val siteSerial = siteTimeserials[opSiteCode]
    return siteSerial == null || opSerial > siteSerial
  }

  /**
   * Applies object delete operation.
   * Similar to JavaScript _applyObjectDelete method.
   * 
   * @spec RTLM10 - Object deletion for LiveMap
   * @spec RTLC10 - Object deletion for LiveCounter
   */
  protected fun applyObjectDelete(): Any {
    return tombstone()
  }

  /**
   * Marks the object as tombstoned.
   * Similar to JavaScript tombstone method.
   * 
   * @spec RTLM11 - Tombstone functionality for LiveMap
   * @spec RTLC11 - Tombstone functionality for LiveCounter
   */
  protected fun tombstone(): Any {
    isTombstoned = true
    tombstonedAt = System.currentTimeMillis()
    val update = clearData()
    // TODO: Emit lifecycle events
    return update
  }

  /**
   * Clears the object's data.
   * Similar to JavaScript clearData method.
   */
  protected abstract fun clearData(): Any

  /**
   * Checks if the object is tombstoned.
   */
  fun isTombstoned(): Boolean = isTombstoned

  /**
   * Gets the timestamp when the object was tombstoned.
   */
  fun tombstonedAt(): Long? = tombstonedAt
}

/**
 * Implementation of LiveObject for LiveMap.
 * Similar to JavaScript LiveMap class.
 * 
 * @spec RTLM1 - LiveMap implementation
 * @spec RTLM2 - LiveMap extends LiveObject
 */
internal class LiveMapImpl(
  objectId: String,
  adapter: LiveObjectsAdapter,
  private val semantics: MapSemantics = MapSemantics.LWW
) : BaseLiveObject(objectId, adapter) {

  /**
   * @spec RTLM3 - Map data structure storing entries
   */
  private data class MapEntry(
    var tombstone: Boolean = false,
    var tombstonedAt: Long? = null,
    var timeserial: String? = null,
    var data: ObjectData? = null
  )

  private val data = mutableMapOf<String, MapEntry>()

  /**
   * @spec RTLM6 - Overrides object data with state from sync
   */
  override fun overrideWithObjectState(objectState: ObjectState): Any {
    if (objectState.objectId != objectId) {
      throw IllegalArgumentException("Invalid object state: object state objectId=${objectState.objectId}; LiveMap objectId=$objectId")
    }

    if (objectState.map?.semantics != semantics) {
      throw IllegalArgumentException("Invalid object state: object state map semantics=${objectState.map?.semantics}; LiveMap semantics=$semantics")
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

      objectState.map?.entries?.forEach { (key, entry) ->
        data[key] = MapEntry(
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
      throw IllegalArgumentException("Cannot apply object operation with objectId=${operation.objectId}, to this LiveMap with objectId=$objectId")
    }

    val opSerial = message.serial
    val opSiteCode = message.siteCode

    if (opSerial.isNullOrEmpty() || opSiteCode.isNullOrEmpty()) {
      Log.w(tag, "Operation missing serial or siteCode, skipping: ${operation.action}")
      return
    }

    if (!canApplyOperation(opSerial, opSiteCode)) {
      Log.v(tag, "Skipping ${operation.action} op: op serial $opSerial <= site serial ${siteTimeserials[opSiteCode]}; objectId=$objectId")
      return
    }

    // should update stored site serial immediately. doesn't matter if we successfully apply the op,
    // as it's important to mark that the op was processed by the object
    siteTimeserials[opSiteCode] = opSerial

    if (isTombstoned) {
      // this object is tombstoned so the operation cannot be applied
      return
    }

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

  override fun clearData(): Any {
    val previousData = data.toMap()
    data.clear()
    return calculateUpdateFromDataDiff(previousData, emptyMap())
  }

  /**
   * @spec RTLM6d - Merges initial data from create operation
   */
  private fun applyMapCreate(operation: ObjectOperation): Any {
    if (createOperationIsMerged) {
      Log.v(tag, "Skipping applying MAP_CREATE op on a map instance as it was already applied before; objectId=$objectId")
      return mapOf<String, String>()
    }

    if (semantics != operation.map?.semantics) {
      throw IllegalArgumentException("Cannot apply MAP_CREATE op on LiveMap objectId=$objectId; map's semantics=$semantics, but op expected ${operation.map?.semantics}")
    }

    return mergeInitialDataFromCreateOperation(operation)
  }

  /**
   * @spec RTLM7 - Applies MAP_SET operation to LiveMap
   */
  private fun applyMapSet(mapOp: ObjectMapOp, opSerial: String?): Any {
    val existingEntry = data[mapOp.key]

    // RTLM7a
    if (existingEntry != null && !canApplyMapOperation(existingEntry.timeserial, opSerial)) {
      // RTLM7a1 - the operation's serial <= the entry's serial, ignore the operation
      Log.v(tag, "Skipping update for key=\"${mapOp.key}\": op serial $opSerial <= entry serial ${existingEntry.timeserial}; objectId=$objectId")
      return mapOf<String, String>()
    }

    if (existingEntry != null) {
      // RTLM7a2
      existingEntry.tombstone = false // RTLM7a2c
      existingEntry.tombstonedAt = null
      existingEntry.timeserial = opSerial // RTLM7a2b
      existingEntry.data = mapOp.data // RTLM7a2a
    } else {
      // RTLM7b, RTLM7b1
      data[mapOp.key] = MapEntry(
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
  private fun applyMapRemove(mapOp: ObjectMapOp, opSerial: String?): Any {
    val existingEntry = data[mapOp.key]

    // RTLM8a
    if (existingEntry != null && !canApplyMapOperation(existingEntry.timeserial, opSerial)) {
      // RTLM8a1 - the operation's serial <= the entry's serial, ignore the operation
      Log.v(tag, "Skipping remove for key=\"${mapOp.key}\": op serial $opSerial <= entry serial ${existingEntry.timeserial}; objectId=$objectId")
      return mapOf<String, String>()
    }

    if (existingEntry != null) {
      // RTLM8a2
      existingEntry.tombstone = true // RTLM8a2c
      existingEntry.tombstonedAt = System.currentTimeMillis()
      existingEntry.timeserial = opSerial // RTLM8a2b
      existingEntry.data = null // RTLM8a2a
    } else {
      // RTLM8b, RTLM8b1
      data[mapOp.key] = MapEntry(
        tombstone = true, // RTLM8b2
        tombstonedAt = System.currentTimeMillis(),
        timeserial = opSerial
      )
    }

    return mapOf(mapOp.key to "removed")
  }

  /**
   * @spec RTLM9 - Serial comparison logic for map operations
   */
  private fun canApplyMapOperation(mapEntrySerial: String?, opSerial: String?): Boolean {
    // for Lww CRDT semantics (the only supported LiveMap semantic) an operation
    // should only be applied if its serial is strictly greater ("after") than an entry's serial.

    if (mapEntrySerial.isNullOrEmpty() && opSerial.isNullOrEmpty()) {
      // RTLM9b - if both serials are nullish or empty strings, we treat them as the "earliest possible" serials,
      // in which case they are "equal", so the operation should not be applied
      return false
    }

    if (mapEntrySerial.isNullOrEmpty()) {
      // RTLM9d - any operation serial is greater than non-existing entry serial
      return true
    }

    if (opSerial.isNullOrEmpty()) {
      // RTLM9c - non-existing operation serial is lower than any entry serial
      return false
    }

    // RTLM9e - if both serials exist, compare them lexicographically
    return opSerial > mapEntrySerial
  }

  /**
   * @spec RTLM6d - Merges initial data from create operation
   */
  private fun mergeInitialDataFromCreateOperation(operation: ObjectOperation): Any {
    if (operation.map?.entries.isNullOrEmpty()) {
      return mapOf<String, String>()
    }

    val aggregatedUpdate = mutableMapOf<String, String>()

    // RTLM6d1
    // in order to apply MAP_CREATE op for an existing map, we should merge their underlying entries keys.
    // we can do this by iterating over entries from MAP_CREATE op and apply changes on per-key basis as if we had MAP_SET, MAP_REMOVE operations.
    operation.map!!.entries!!.forEach { (key, entry) ->
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

  private fun calculateUpdateFromDataDiff(prevData: Map<String, MapEntry>, newData: Map<String, MapEntry>): Map<String, String> {
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
}

/**
 * Implementation of LiveObject for LiveCounter.
 * Similar to JavaScript LiveCounter class.
 * 
 * @spec RTLC1 - LiveCounter implementation
 * @spec RTLC2 - LiveCounter extends LiveObject
 */
internal class LiveCounterImpl(
  objectId: String,
  adapter: LiveObjectsAdapter
) : BaseLiveObject(objectId, adapter) {

  /**
   * @spec RTLC3 - Counter data value
   */
  private var data: Long = 0

  /**
   * @spec RTLC6 - Overrides counter data with state from sync
   */
  override fun overrideWithObjectState(objectState: ObjectState): Any {
    if (objectState.objectId != objectId) {
      throw IllegalArgumentException("Invalid object state: object state objectId=${objectState.objectId}; LiveCounter objectId=$objectId")
    }

    // object's site serials are still updated even if it is tombstoned, so always use the site serials received from the operation.
    // should default to empty map if site serials do not exist on the object state, so that any future operation may be applied to this object.
    siteTimeserials.clear()
    siteTimeserials.putAll(objectState.siteTimeserials) // RTLC6a

    if (isTombstoned) {
      // this object is tombstoned. this is a terminal state which can't be overridden. skip the rest of object state message processing
      return mapOf("amount" to 0L)
    }

    val previousData = data

    if (objectState.tombstone) {
      tombstone()
    } else {
      // override data for this object with data from the object state
      createOperationIsMerged = false // RTLC6b
      data = objectState.counter?.count?.toLong() ?: 0 // RTLC6c

      // RTLC6d
      objectState.createOp?.let { createOp ->
        mergeInitialDataFromCreateOperation(createOp)
      }
    }

    return mapOf("amount" to (data - previousData))
  }

  /**
   * @spec RTLC7 - Applies operations to LiveCounter
   */
  override fun applyOperation(operation: ObjectOperation, message: ObjectMessage) {
    if (operation.objectId != objectId) {
      throw IllegalArgumentException("Cannot apply object operation with objectId=${operation.objectId}, to this LiveCounter with objectId=$objectId")
    }

    val opSerial = message.serial
    val opSiteCode = message.siteCode

    if (opSerial.isNullOrEmpty() || opSiteCode.isNullOrEmpty()) {
      Log.w(tag, "Operation missing serial or siteCode, skipping: ${operation.action}")
      return
    }

    if (!canApplyOperation(opSerial, opSiteCode)) {
      Log.v(tag, "Skipping ${operation.action} op: op serial $opSerial <= site serial ${siteTimeserials[opSiteCode]}; objectId=$objectId")
      return
    }

    // should update stored site serial immediately. doesn't matter if we successfully apply the op,
    // as it's important to mark that the op was processed by the object
    siteTimeserials[opSiteCode] = opSerial

    if (isTombstoned) {
      // this object is tombstoned so the operation cannot be applied
      return
    }

    val update = when (operation.action) {
      ObjectOperationAction.CounterCreate -> applyCounterCreate(operation)
      ObjectOperationAction.CounterInc -> applyCounterInc(operation.counterOp!!)
      ObjectOperationAction.ObjectDelete -> applyObjectDelete()
      else -> {
        Log.w(tag, "Invalid ${operation.action} op for LiveCounter objectId=$objectId")
        return
      }
    }

    notifyUpdated(update)
  }

  override fun clearData(): Any {
    val previousData = data
    data = 0
    return mapOf("amount" to -previousData)
  }

  /**
   * @spec RTLC6d - Merges initial data from create operation
   */
  private fun applyCounterCreate(operation: ObjectOperation): Any {
    if (createOperationIsMerged) {
      Log.v(tag, "Skipping applying COUNTER_CREATE op on a counter instance as it was already applied before; objectId=$objectId")
      return mapOf<String, Long>()
    }

    return mergeInitialDataFromCreateOperation(operation)
  }

  /**
   * @spec RTLC8 - Applies counter increment operation
   */
  private fun applyCounterInc(counterOp: ObjectCounterOp): Any {
    val amount = counterOp.amount?.toLong() ?: 0
    data += amount
    return mapOf("amount" to amount)
  }

  /**
   * @spec RTLC6d - Merges initial data from create operation
   */
  private fun mergeInitialDataFromCreateOperation(operation: ObjectOperation): Any {
    // if a counter object is missing for the COUNTER_CREATE op, the initial value is implicitly 0 in this case.
    // note that it is intentional to SUM the incoming count from the create op.
    // if we got here, it means that current counter instance is missing the initial value in its data reference,
    // which we're going to add now.
    val count = operation.counter?.count?.toLong() ?: 0
    data += count // RTLC6d1
    createOperationIsMerged = true // RTLC6d2
    return mapOf("amount" to count)
  }
}
