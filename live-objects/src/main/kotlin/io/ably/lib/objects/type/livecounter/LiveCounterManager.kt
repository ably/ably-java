package io.ably.lib.objects.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.objectError
import io.ably.lib.util.Log

internal class LiveCounterManager(private val liveCounter: DefaultLiveCounter) {
  private val objectId = liveCounter.objectId

  private val tag = "LiveCounterManager"

  /**
   * @spec RTLC6 - Overrides counter data with state from sync
   */
  internal fun applyObjectState(objectState: ObjectState): Map<String, Long> {
    if (objectState.objectId != objectId) {
      throw objectError("Invalid object state: object state objectId=${objectState.objectId}; LiveCounter objectId=$objectId")
    }

    // object's site serials are still updated even if it is tombstoned, so always use the site serials received from the operation.
    // should default to empty map if site serials do not exist on the object state, so that any future operation may be applied to this object.
    liveCounter.siteTimeserials.clear()
    liveCounter.siteTimeserials.putAll(objectState.siteTimeserials) // RTLC6a

    if (liveCounter.isTombstoned) {
      // this object is tombstoned. this is a terminal state which can't be overridden. skip the rest of object state message processing
      return mapOf()
    }

    val previousData = liveCounter.data

    if (objectState.tombstone) {
      liveCounter.tombstone()
    } else {
      // override data for this object with data from the object state
      liveCounter.createOperationIsMerged = false // RTLC6b
      liveCounter.data = objectState.counter?.count?.toLong() ?: 0 // RTLC6c

      // RTLC6d
      objectState.createOp?.let { createOp ->
        mergeInitialDataFromCreateOperation(createOp)
      }
    }

    return mapOf("amount" to (liveCounter.data - previousData))
  }

  /**
   * @spec RTLC7 - Applies operations to LiveCounter
   */
  internal fun applyOperation(operation: ObjectOperation, message: ObjectMessage) {
    if (operation.objectId != objectId) {
      throw objectError(
        "Cannot apply object operation with objectId=${operation.objectId}, to this LiveCounter with objectId=$objectId",)
    }

    val opSerial = message.serial
    val opSiteCode = message.siteCode

    if (!liveCounter.canApplyOperation(opSiteCode, opSerial)) {
      // RTLC7b
      Log.v(
        tag,
        "Skipping ${operation.action} op: op serial $opSerial <= site serial ${liveCounter.siteTimeserials[opSiteCode]}; " +
          "objectId=$objectId"
      )
      return
    }
    // should update stored site serial immediately. doesn't matter if we successfully apply the op,
    // as it's important to mark that the op was processed by the object
    liveCounter.siteTimeserials[opSiteCode!!] = opSerial!! // RTLC7c

    if (liveCounter.isTombstoned) {
      // this object is tombstoned so the operation cannot be applied
      return;
    }

    val update = when (operation.action) {
      ObjectOperationAction.CounterCreate -> applyCounterCreate(operation) // RTLC7d1
      ObjectOperationAction.CounterInc -> {
        if (operation.counterOp != null) {
          applyCounterInc(operation.counterOp) // RTLC7d2
        } else {
          throw objectError("No payload found for ${operation.action} op for LiveCounter objectId=${objectId}")
        }
      }
      ObjectOperationAction.ObjectDelete -> liveCounter.tombstone()
      else -> throw objectError("Invalid ${operation.action} op for LiveCounter objectId=${objectId}") // RTLC7d3
    }

    liveCounter.notifyUpdated(update)
  }

  /**
   * @spec RTLC8 - Applies counter create operation
   */
  private fun applyCounterCreate(operation: ObjectOperation): Map<String, Long> {
    if (liveCounter.createOperationIsMerged) {
      // RTLC8b
      // There can't be two different create operation for the same object id, because the object id
      // fully encodes that operation. This means we can safely ignore any new incoming create operations
      // if we already merged it once.
      Log.v(
        tag,
        "Skipping applying COUNTER_CREATE op on a counter instance as it was already applied before; objectId=$objectId"
      )
      return mapOf()
    }

    return mergeInitialDataFromCreateOperation(operation) // RTLC8c
  }

  /**
   * @spec RTLC9 - Applies counter increment operation
   */
  private fun applyCounterInc(counterOp: ObjectCounterOp): Map<String, Long> {
    val amount = counterOp.amount?.toLong() ?: 0
    liveCounter.data += amount // RTLC9b
    return mapOf("amount" to amount)
  }

  /**
   * @spec RTLC10 - Merges initial data from create operation
   */
  private fun mergeInitialDataFromCreateOperation(operation: ObjectOperation): Map<String, Long> {
    // if a counter object is missing for the COUNTER_CREATE op, the initial value is implicitly 0 in this case.
    // note that it is intentional to SUM the incoming count from the create op.
    // if we got here, it means that current counter instance is missing the initial value in its data reference,
    // which we're going to add now.
    val count = operation.counter?.count?.toLong() ?: 0
    liveCounter.data += count // RTLC10a
    liveCounter.createOperationIsMerged = true // RTLC10b
    return mapOf("amount" to count)
  }
}
