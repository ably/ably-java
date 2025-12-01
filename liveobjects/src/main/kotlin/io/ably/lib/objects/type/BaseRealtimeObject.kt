package io.ably.lib.objects.type

import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.objectError
import io.ably.lib.objects.type.livecounter.noOpCounterUpdate
import io.ably.lib.objects.type.livemap.noOpMapUpdate
import io.ably.lib.util.Log

internal enum class ObjectType(val value: String) {
  Map("map"),
  Counter("counter")
}

// Spec: RTLO4b4b
internal val ObjectUpdate.noOp get() = this.update == null

/**
 * Provides common functionality and base implementation for LiveMap and LiveCounter.
 *
 * @spec RTLO1/RTLO2 - Base class for LiveMap/LiveCounter object
 *
 * This should also be included in logging
 */
internal abstract class BaseRealtimeObject(
  internal val objectId: String, // // RTLO3a
  internal val objectType: ObjectType,
) : ObjectLifecycleCoordinator() {

  protected open val tag = "BaseRealtimeObject"

  internal val siteTimeserials = mutableMapOf<String, String>() // RTLO3b

  internal var createOperationIsMerged = false // RTLO3c

  @Volatile
  internal var isTombstoned = false // Accessed from public API for LiveMap/LiveCounter

  private var tombstonedAt: Long? = null

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object_sync`
   * @return an update describing the changes
   *
   * @spec RTLM6/RTLC6 - Overrides ObjectMessage with object data state from sync to LiveMap/LiveCounter
   */
  internal fun applyObjectSync(objectMessage: ObjectMessage): ObjectUpdate {
    val objectState = objectMessage.objectState as ObjectState // we have non-null objectState here due to RTO5b
    validate(objectState)
    // object's site serials are still updated even if it is tombstoned, so always use the site serials received from the operation.
    // should default to empty map if site serials do not exist on the object state, so that any future operation may be applied to this object.
    siteTimeserials.clear()
    siteTimeserials.putAll(objectState.siteTimeserials) // RTLC6a, RTLM6a

    if (isTombstoned) {
      // this object is tombstoned. this is a terminal state which can't be overridden. skip the rest of object state message processing
      if (objectType == ObjectType.Map) {
        return noOpMapUpdate
      }
      return noOpCounterUpdate
    }
    return applyObjectState(objectState, objectMessage) // RTLM6, RTLC6
  }

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object`
   * @return an update describing the changes
   *
   * @spec RTLM15/RTLC7 - Applies ObjectMessage with object data operations to LiveMap/LiveCounter
   */
  internal fun applyObject(objectMessage: ObjectMessage) {
    validateObjectId(objectMessage.operation?.objectId)

    val msgTimeSerial = objectMessage.serial
    val msgSiteCode = objectMessage.siteCode
    val objectOperation = objectMessage.operation as ObjectOperation

    if (!canApplyOperation(msgSiteCode, msgTimeSerial)) {
      // RTLC7b, RTLM15b
      Log.v(
        tag,
        "Skipping ${objectOperation.action} op: op serial $msgTimeSerial <= site serial ${siteTimeserials[msgSiteCode]}; " +
          "objectId=$objectId"
      )
      return
    }
    // should update stored site serial immediately. doesn't matter if we successfully apply the op,
    // as it's important to mark that the op was processed by the object
    siteTimeserials[msgSiteCode!!] = msgTimeSerial!! // RTLC7c, RTLM15c

    if (isTombstoned) {
      // this object is tombstoned so the operation cannot be applied
      return
    }
    applyObjectOperation(objectOperation, objectMessage) // RTLC7d
  }

  /**
   * Checks if an operation can be applied based on serial comparison.
   *
   * @spec RTLO4a - Serial comparison logic for LiveMap/LiveCounter operations
   */
  internal fun canApplyOperation(siteCode: String?, timeSerial: String?): Boolean {
    if (timeSerial.isNullOrEmpty()) {
      throw objectError("Invalid serial: $timeSerial") // RTLO4a3
    }
    if (siteCode.isNullOrEmpty()) {
      throw objectError("Invalid site code: $siteCode") // RTLO4a3
    }
    val existingSiteSerial = siteTimeserials[siteCode] // RTLO4a4
    return existingSiteSerial == null || timeSerial > existingSiteSerial // RTLO4a5, RTLO4a6
  }

  internal fun validateObjectId(objectId: String?) {
    if (this.objectId != objectId) {
      throw objectError("Invalid object: incoming objectId=$objectId; $objectType objectId=${this.objectId}")
    }
  }

  /**
   * Marks the object as tombstoned.
   */
  internal fun tombstone(serialTimestamp: Long?): ObjectUpdate {
    if (serialTimestamp == null) {
      Log.w(tag, "Tombstoning object $objectId without serial timestamp, using local timestamp instead")
    }
    isTombstoned = true
    tombstonedAt = serialTimestamp?: System.currentTimeMillis()
    val update = clearData()
    // Emit object lifecycle event for deletion
    objectLifecycleChanged(ObjectLifecycle.Deleted)
    return update
  }

  /**
   * Checks if the object is eligible for garbage collection.
   * 
   * An object is eligible for garbage collection if it has been tombstoned and 
   * the time since tombstoning exceeds the specified grace period.
   * 
   * @param gcGracePeriod The grace period in milliseconds that tombstoned objects
   *                      should be kept before being eligible for collection.
   *                      This value is retrieved from the server's connection details
   *                      or defaults to 24 hours if not provided by the server.
   * @return true if the object is tombstoned and the grace period has elapsed,
   *         false otherwise
   */
  internal fun isEligibleForGc(gcGracePeriod: Long): Boolean {
    val currentTime = System.currentTimeMillis()
    return isTombstoned && tombstonedAt?.let { currentTime - it >= gcGracePeriod } == true
  }

  /**
   * Validates that the provided object state is compatible with this object.
   * Checks object ID, type-specific validations, and any included create operations.
   */
  abstract fun validate(state: ObjectState)

  /**
   * Applies an object state received during synchronization to this object.
   * This method should update the internal data structure with the complete state
   * received from the server.
   *
   * @param objectState The complete state to apply to this object
   * @return A map describing the changes made to the object's data
   *
   */
  abstract fun applyObjectState(objectState: ObjectState, message: ObjectMessage): ObjectUpdate

  /**
   * Applies an operation to this object.
   * This method handles the specific operation actions (e.g., update, remove)
   * by modifying the underlying data structure accordingly.
   *
   * @param operation The operation containing the action and data to apply
   * @param message The complete object message containing the operation
   *
   */
  abstract fun applyObjectOperation(operation: ObjectOperation, message: ObjectMessage)

  /**
   * Clears the object's data and returns an update describing the changes.
   * This is called during tombstoning and explicit clear operations.
   *
   * This method:
   * 1. Calculates a diff between the current state and an empty state
   * 2. Clears all entries from the underlying data structure
   * 3. Returns a map containing metadata about what was cleared
   *
   * The returned map is used to notifying other components about what entries were removed.
   *
   * @return A map representing the diff of changes made
   */
  abstract fun clearData(): ObjectUpdate

  /**
   * Notifies subscribers about changes made to this object. Propagates updates through the
   * appropriate manager after converting the generic update map to type-specific update objects.
   * Spec: RTLO4b4c
   */
  abstract fun notifyUpdated(update: ObjectUpdate)

  /**
   * Called during garbage collection intervals to clean up expired entries.
   *
   * This method is invoked periodically (every 5 minutes) by the ObjectsPool
   * to perform cleanup of tombstoned data that has exceeded the grace period.
   *
   * This method should identify and remove entries that:
   * - Have been marked as tombstoned
   * - Have a tombstone timestamp older than the specified grace period
   *
   * @param gcGracePeriod The grace period in milliseconds that tombstoned entries
   *                      should be kept before being eligible for removal.
   *                      This value is retrieved from the server's connection details
   *                      or defaults to 24 hours if not provided by the server.
   *                      Must be greater than 2 minutes to ensure proper operation
   *                      ordering and avoid issues with delayed operations.
   *
   * Implementations typically use single-pass removal techniques to
   * efficiently clean up expired data without creating temporary collections.
   */
  abstract fun onGCInterval(gcGracePeriod: Long)
}
