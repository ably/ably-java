package io.ably.lib.liveobjects.value.livecounter

import io.ably.lib.liveobjects.message.WireCounterInc
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.objectError
import io.ably.lib.liveobjects.value.ObjectUpdate
import io.ably.lib.util.Log

internal class LiveCounterManager(private val liveCounter: InternalLiveCounter): LiveCounterChangeCoordinator() {

  private val objectId = liveCounter.objectId

  private val tag = "LiveCounterManager"

  /**
   * @spec RTLC6 - Overrides counter data with state from sync
   */
  internal fun applyState(wireObjectState: WireObjectState, message: WireObjectMessage): ObjectUpdate {
    if (wireObjectState.tombstone) {
      // RTLC6f, RTLC6f2 - tombstone update returned as-is (carries tombstone flag + message)
      return liveCounter.tombstone(message.serialTimestamp, message)
    }

    val previousData = liveCounter.data.get() // RTLC6g - only the override branch needs it

    // override data for this object with data from the object state
    liveCounter.createOperationIsMerged = false // RTLC6b
    liveCounter.data.set(wireObjectState.counter?.count ?: 0.0) // RTLC6c

    // RTLC6d - merge result is discarded; only the outer diff is returned
    wireObjectState.createOp?.let { createOp ->
      mergeInitialDataFromCreateOperation(createOp, message)
    }

    // RTLC6h - diff between previous and new data, stamped with the source message
    return when (val diff = calculateUpdateFromDataDiff(previousData, liveCounter.data.get())) {
      is ObjectUpdate.CounterUpdate -> diff.copy(objectMessage = message)
      else -> diff // NoOp stays NoOp
    }
  }

  /**
   * @spec RTLC7 - Applies operations to LiveCounter
   * @spec RTLC7f1 - [message] is the source ObjectMessage that contains the operation
   */
  internal fun applyOperation(operation: WireObjectOperation, message: WireObjectMessage): Boolean {
    return when (operation.action) {
      WireObjectOperationAction.CounterCreate -> {
        val update = applyCounterCreate(operation, message) // RTLC7d1
        liveCounter.notifyUpdated(update) // RTLC7d1a
        true // RTLC7d1b
      }
      WireObjectOperationAction.CounterInc -> {
        if (operation.counterInc != null) {
          val update = applyCounterInc(operation.counterInc, message) // RTLC7d5
          liveCounter.notifyUpdated(update) // RTLC7d5a
          true // RTLC7d5b
        } else {
          throw objectError("No payload found for ${operation.action} op for LiveCounter objectId=${objectId}")
        }
      }
      WireObjectOperationAction.ObjectDelete -> {
        val update = liveCounter.tombstone(message.serialTimestamp, message) // RTLC7d4
        liveCounter.notifyUpdated(update) // RTLC7d4c
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
   * @spec RTLC8a2 - [message] is the source ObjectMessage that contains the operation
   */
  private fun applyCounterCreate(operation: WireObjectOperation, message: WireObjectMessage): ObjectUpdate {
    if (liveCounter.createOperationIsMerged) {
      // RTLC8b
      // There can't be two different create operation for the same object id, because the object id
      // fully encodes that operation. This means we can safely ignore any new incoming create operations
      // if we already merged it once.
      Log.v(
        tag,
        "Skipping applying COUNTER_CREATE op on a counter instance as it was already applied before; objectId=$objectId"
      )
      return noOpCounterUpdate // RTLC8b
    }

    return mergeInitialDataFromCreateOperation(operation, message) // RTLC8c, RTLC8e
  }

  /**
   * @spec RTLC9 - Applies counter increment operation
   * @spec RTLC9a3 - [message] is the source ObjectMessage that contains the operation
   */
  private fun applyCounterInc(wireCounterInc: WireCounterInc, message: WireObjectMessage): ObjectUpdate {
    val amount = wireCounterInc.number
    val previousValue = liveCounter.data.get()
    liveCounter.data.set(previousValue + amount) // RTLC9f
    return ObjectUpdate.CounterUpdate(amount, message) // RTLC9g
  }

  internal fun calculateUpdateFromDataDiff(prevData: Double, newData: Double): ObjectUpdate {
    // A zero delta means the value did not change (e.g. clearing an already-zero counter).
    // Return the no-op update so notifyUpdated() short-circuits and no event is emitted. Spec: RTLC14b
    return if (newData == prevData) noOpCounterUpdate else ObjectUpdate.CounterUpdate(newData - prevData)
  }

  /**
   * @spec RTLC16 - Merges initial data from create operation
   */
  private fun mergeInitialDataFromCreateOperation(operation: WireObjectOperation, message: WireObjectMessage): ObjectUpdate {
    // if a counter object is missing for the COUNTER_CREATE op, the initial value is implicitly 0 in this case.
    // note that it is intentional to SUM the incoming count from the create op.
    // if we got here, it means that current counter instance is missing the initial value in its data reference,
    // which we're going to add now.
    val count = operation.counterCreateWithObjectId?.derivedFrom?.count
      ?: operation.counterCreate?.count
      ?: 0.0
    val previousValue = liveCounter.data.get()
    liveCounter.data.set(previousValue + count) // RTLC16a
    liveCounter.createOperationIsMerged = true // RTLC16b
    return ObjectUpdate.CounterUpdate(count, message) // RTLC16c
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
