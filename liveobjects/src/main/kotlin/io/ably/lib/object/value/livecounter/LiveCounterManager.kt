package io.ably.lib.`object`.value.livecounter

import io.ably.lib.`object`.message.WireCounterInc
import io.ably.lib.`object`.message.WireObjectOperation
import io.ably.lib.`object`.message.WireObjectOperationAction
import io.ably.lib.`object`.message.WireObjectState
import io.ably.lib.`object`.objectError
import io.ably.lib.`object`.value.ObjectUpdate
import io.ably.lib.util.Log

internal class LiveCounterManager(private val liveCounter: InternalLiveCounter): LiveCounterChangeCoordinator() {

  private val objectId = liveCounter.objectId

  private val tag = "LiveCounterManager"

  /**
   * @spec RTLC6 - Overrides counter data with state from sync
   */
  internal fun applyState(wireObjectState: WireObjectState, serialTimestamp: Long?): ObjectUpdate {
    val previousData = liveCounter.data.get()

    if (wireObjectState.tombstone) {
      liveCounter.tombstone(serialTimestamp)
    } else {
      // override data for this object with data from the object state
      liveCounter.createOperationIsMerged = false // RTLC6b
      liveCounter.data.set(wireObjectState.counter?.count ?: 0.0) // RTLC6c

      // RTLC6d
      wireObjectState.createOp?.let { createOp ->
        mergeInitialDataFromCreateOperation(createOp)
      }
    }

    return calculateUpdateFromDataDiff(previousData, liveCounter.data.get())
  }

  /**
   * @spec RTLC7 - Applies operations to LiveCounter
   */
  internal fun applyOperation(operation: WireObjectOperation, serialTimestamp: Long?): Boolean {
    return when (operation.action) {
      WireObjectOperationAction.CounterCreate -> {
        val update = applyCounterCreate(operation) // RTLC7d1
        liveCounter.notifyUpdated(update) // RTLC7d1a
        true // RTLC7d1b
      }
      WireObjectOperationAction.CounterInc -> {
        if (operation.counterInc != null) {
          val update = applyCounterInc(operation.counterInc) // RTLC7d2
          liveCounter.notifyUpdated(update) // RTLC7d2a
          true // RTLC7d2b
        } else {
          throw objectError("No payload found for ${operation.action} op for LiveCounter objectId=${objectId}")
        }
      }
      WireObjectOperationAction.ObjectDelete -> {
        val update = liveCounter.tombstone(serialTimestamp)
        liveCounter.notifyUpdated(update)
        true // RTLC7d4b
      }
      else -> {
        Log.w(tag, "Invalid ${operation.action} op for LiveCounter objectId=${objectId}") // RTLC7d3
        false
      }
    }
  }

  /**
   * @spec RTLC8 - Applies counter create operation
   */
  private fun applyCounterCreate(operation: WireObjectOperation): ObjectUpdate {
    if (liveCounter.createOperationIsMerged) {
      // RTLC8b
      // There can't be two different create operation for the same object id, because the object id
      // fully encodes that operation. This means we can safely ignore any new incoming create operations
      // if we already merged it once.
      Log.v(
        tag,
        "Skipping applying COUNTER_CREATE op on a counter instance as it was already applied before; objectId=$objectId"
      )
      return noOpCounterUpdate // RTLC8c
    }

    return mergeInitialDataFromCreateOperation(operation) // RTLC8c
  }

  /**
   * @spec RTLC9 - Applies counter increment operation
   */
  private fun applyCounterInc(wireCounterInc: WireCounterInc): ObjectUpdate {
    val amount = wireCounterInc.number
    val previousValue = liveCounter.data.get()
    liveCounter.data.set(previousValue + amount) // RTLC9f
    return ObjectUpdate(amount)
  }

  internal fun calculateUpdateFromDataDiff(prevData: Double, newData: Double): ObjectUpdate {
    return ObjectUpdate(newData - prevData)
  }

  /**
   * @spec RTLC16 - Merges initial data from create operation
   */
  private fun mergeInitialDataFromCreateOperation(operation: WireObjectOperation): ObjectUpdate {
    // if a counter object is missing for the COUNTER_CREATE op, the initial value is implicitly 0 in this case.
    // note that it is intentional to SUM the incoming count from the create op.
    // if we got here, it means that current counter instance is missing the initial value in its data reference,
    // which we're going to add now.
    val count = operation.counterCreateWithObjectId?.derivedFrom?.count
      ?: operation.counterCreate?.count
      ?: 0.0
    val previousValue = liveCounter.data.get()
    liveCounter.data.set(previousValue + count) // RTLC16
    liveCounter.createOperationIsMerged = true // RTLC16
    return ObjectUpdate(count)
  }

  internal fun validate(state: WireObjectState) {
    liveCounter.validateObjectId(state.objectId)
    state.createOp?.let { createOp ->
      liveCounter.validateObjectId(createOp.objectId)
      validateCounterCreateAction(createOp.action)
    }
  }

  private fun validateCounterCreateAction(action: WireObjectOperationAction) {
    if (action != WireObjectOperationAction.CounterCreate) {
      throw objectError("Invalid create operation action $action for LiveCounter objectId=${objectId}")
    }
  }
}
