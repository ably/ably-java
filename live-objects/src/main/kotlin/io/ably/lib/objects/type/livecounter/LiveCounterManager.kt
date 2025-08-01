package io.ably.lib.objects.type.livecounter

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectOperationAction
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.objectError
import io.ably.lib.objects.type.counter.LiveCounterUpdate
import io.ably.lib.util.Log

internal class LiveCounterManager(private val liveCounter: DefaultLiveCounter): LiveCounterChangeCoordinator() {

  private val objectId = liveCounter.objectId

  private val tag = "LiveCounterManager"

  /**
   * @spec RTLC6 - Overrides counter data with state from sync
   */
  internal fun applyState(objectState: ObjectState): LiveCounterUpdate {
    val previousData = liveCounter.data.get()

    if (objectState.tombstone) {
      liveCounter.tombstone()
    } else {
      // override data for this object with data from the object state
      liveCounter.createOperationIsMerged = false // RTLC6b
      liveCounter.data.set(objectState.counter?.count ?: 0.0) // RTLC6c

      // RTLC6d
      objectState.createOp?.let { createOp ->
        mergeInitialDataFromCreateOperation(createOp)
      }
    }

    return LiveCounterUpdate(liveCounter.data.get() - previousData)
  }

  /**
   * @spec RTLC7 - Applies operations to LiveCounter
   */
  internal fun applyOperation(operation: ObjectOperation) {
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

    liveCounter.notifyUpdated(update) // RTLC7d1a, RTLC7d2a
  }

  /**
   * @spec RTLC8 - Applies counter create operation
   */
  private fun applyCounterCreate(operation: ObjectOperation): LiveCounterUpdate {
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
  private fun applyCounterInc(counterOp: ObjectCounterOp): LiveCounterUpdate {
    val amount = counterOp.amount ?: 0.0
    val previousValue = liveCounter.data.get()
    liveCounter.data.set(previousValue + amount) // RTLC9b
    return LiveCounterUpdate(amount)
  }

  /**
   * @spec RTLC10 - Merges initial data from create operation
   */
  private fun mergeInitialDataFromCreateOperation(operation: ObjectOperation): LiveCounterUpdate {
    // if a counter object is missing for the COUNTER_CREATE op, the initial value is implicitly 0 in this case.
    // note that it is intentional to SUM the incoming count from the create op.
    // if we got here, it means that current counter instance is missing the initial value in its data reference,
    // which we're going to add now.
    val count = operation.counter?.count ?: 0.0
    val previousValue = liveCounter.data.get()
    liveCounter.data.set(previousValue + count) // RTLC10a
    liveCounter.createOperationIsMerged = true // RTLC10b
    return LiveCounterUpdate(count)
  }

  internal fun validate(state: ObjectState) {
    liveCounter.validateObjectId(state.objectId)
    state.createOp?.let { createOp ->
      liveCounter.validateObjectId(createOp.objectId)
      validateCounterCreateAction(createOp.action)
    }
  }

  private fun validateCounterCreateAction(action: ObjectOperationAction) {
    if (action != ObjectOperationAction.CounterCreate) {
      throw objectError("Invalid create operation action $action for LiveCounter objectId=${objectId}")
    }
  }
}
