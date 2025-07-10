package io.ably.lib.objects.type

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectsPoolDefaults
import io.ably.lib.objects.objectError
import io.ably.lib.util.Log

internal enum class ObjectType(val value: String) {
  Map("map"),
  Counter("counter")
}

/**
 * Base implementation of LiveObject interface.
 * Provides common functionality for all live objects.
 *
 * @spec RTLO1/RTLO2 - Base class for LiveMap/LiveCounter object
 *
 * This should also be included in logging
 */
internal abstract class BaseLiveObject(
  internal val objectId: String, // // RTLO3a
  private val objectType: ObjectType,
  private val adapter: LiveObjectsAdapter
) {

  protected open val tag = "BaseLiveObject"

  private val siteTimeserials = mutableMapOf<String, String>() // RTLO3b

  internal var createOperationIsMerged = false // RTLO3c

  private var isTombstoned = false
  private var tombstonedAt: Long? = null

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object_sync`
   * @return an update describing the changes
   *
   * @spec RTLM6/RTLC6 - Overrides ObjectMessage with object data state from sync to LiveMap/LiveCounter
   */
  internal fun applyObjectSync(objectState: ObjectState): Map<String, Any> {
    if (objectState.objectId != objectId) {
      throw objectError("Invalid object state: object state objectId=${objectState.objectId}; $objectType objectId=$objectId")
    }

    if (objectType == ObjectType.Map && objectState.map?.semantics != MapSemantics.LWW){
        throw objectError(
          "Invalid object state: object state map semantics=${objectState.map?.semantics}; " +
            "$objectType semantics=${MapSemantics.LWW}")
    }

    // object's site serials are still updated even if it is tombstoned, so always use the site serials received from the operation.
    // should default to empty map if site serials do not exist on the object state, so that any future operation may be applied to this object.
    siteTimeserials.clear()
    siteTimeserials.putAll(objectState.siteTimeserials) // RTLC6a, RTLM6a

    if (isTombstoned) {
      // this object is tombstoned. this is a terminal state which can't be overridden. skip the rest of object state message processing
      return mapOf()
    }
    return applyObjectState(objectState) // RTLM6, RTLC6
  }

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object`
   * @return an update describing the changes
   *
   * @spec RTLM15/RTLC7 - Applies ObjectMessage with object data operations to LiveMap/LiveCounter
   */
  internal fun applyObject(objectMessage: ObjectMessage) {
    val objectOperation = objectMessage.operation
    if (objectOperation?.objectId != objectId) {
      throw objectError(
        "Cannot apply object operation with objectId=${objectOperation?.objectId}, to $objectType objectId=$objectId",)
    }

    val msgTimeSerial = objectMessage.serial
    val msgSiteCode = objectMessage.siteCode

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
      return;
    }
    applyObjectOperation(objectOperation, objectMessage) // RTLC7d
  }

  internal fun notifyUpdated(update: Any) {
    // TODO: Implement event emission for updates
    Log.v(tag, "Object $objectId updated: $update")
  }

  /**
   * Checks if an operation can be applied based on serial comparison.
   *
   * @spec RTLO4a - Serial comparison logic for LiveMap/LiveCounter operations
   */
  private fun canApplyOperation(siteCode: String?, timeSerial: String?): Boolean {
    if (timeSerial.isNullOrEmpty()) {
      throw objectError("Invalid serial: $timeSerial") // RTLO4a3
    }
    if (siteCode.isNullOrEmpty()) {
      throw objectError("Invalid site code: $siteCode") // RTLO4a3
    }
    val existingSiteSerial = siteTimeserials[siteCode] // RTLO4a4
    return existingSiteSerial == null || timeSerial > existingSiteSerial // RTLO4a5, RTLO4a6
  }

  /**
   * Marks the object as tombstoned.
   */
  internal fun tombstone(): Any {
    isTombstoned = true
    tombstonedAt = System.currentTimeMillis()
    val update = clearData()
    // TODO: Emit lifecycle events
    return update
  }

  /**
   * Checks if the object is eligible for garbage collection.
   */
  internal fun isEligibleForGc(): Boolean {
    val currentTime = System.currentTimeMillis()
    return isTombstoned && tombstonedAt?.let { currentTime - it >= ObjectsPoolDefaults.GC_GRACE_PERIOD_MS } == true
  }

  /**
   * Applies an object state received during synchronization to this live object.
   * This method should update the internal data structure with the complete state
   * received from the server.
   *
   * @param objectState The complete state to apply to this object
   * @return A map describing the changes made to the object's data
   *
   */
  abstract fun applyObjectState(objectState: ObjectState): Map<String, Any>

  /**
   * Applies an operation to this live object.
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
  abstract fun clearData(): Map<String, Any>

  /**
   * Called during garbage collection intervals to clean up expired entries.
   *
   * This method should identify and remove entries that:
   * - Have been marked as tombstoned
   * - Have a tombstone timestamp older than the configured grace period
   *
   * Implementations typically use single-pass removal techniques to
   * efficiently clean up expired data without creating temporary collections.
   */
  abstract fun onGCInterval()
}
