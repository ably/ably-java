package io.ably.lib.objects

import io.ably.lib.util.Log

/**
 * Implementation of LiveObject for LiveCounter.
 * Similar to JavaScript LiveCounter class.
 *
 * @spec RTLC1 - LiveCounter implementation
 * @spec RTLC2 - LiveCounter extends LiveObject
 */
internal class LiveCounter(
  objectId: String,
  adapter: LiveObjectsAdapter
) : BaseLiveObject(objectId, adapter) {

  override val tag = "LiveCounter"
  /**
   * @spec RTLC3 - Counter data value
   */
  private var data: Long = 0

  /**
   * @spec RTLC6 - Overrides counter data with state from sync
   */
  override fun overrideWithObjectState(objectState: ObjectState): Any {
    if (objectState.objectId != objectId) {
      throw objectError("Invalid object state: object state objectId=${objectState.objectId}; LiveCounter objectId=$objectId")
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
      throw objectError(
        "Cannot apply object operation with objectId=${operation.objectId}, to this LiveCounter with objectId=$objectId",)
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
      Log.v(
        tag,
        "Skipping applying COUNTER_CREATE op on a counter instance as it was already applied before; objectId=$objectId"
      )
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

  companion object {
    /**
     * Creates a zero-value counter object.
     * @spec RTLC4 - Returns LiveCounter with 0 value
     */
    internal fun zeroValue(objectId: String, adapter: LiveObjectsAdapter): LiveCounter {
      return LiveCounter(objectId, adapter)
    }
  }
}
