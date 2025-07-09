package io.ably.lib.objects.type

import io.ably.lib.objects.LiveObjectsAdapter
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.ObjectState
import io.ably.lib.objects.ObjectsPool
import io.ably.lib.objects.objectError
import io.ably.lib.util.Log

/**
 * Base implementation of LiveObject interface.
 * Provides common functionality for all live objects.
 *
 * @spec RTLO1/RTLO2 - Base class for LiveMap/LiveCounter objects
 */
internal abstract class BaseLiveObject(
  protected val objectId: String, // // RTLO3a
  protected val adapter: LiveObjectsAdapter
) {

  protected open val tag = "BaseLiveObject"
  internal var isTombstoned = false
  internal var tombstonedAt: Long? = null

  protected val siteTimeserials = mutableMapOf<String, String>() // RTLO3b

  /**
   * @spec RTLO3 - Flag to track if create operation has been merged for LiveMap/LiveCounter
   */
  protected var createOperationIsMerged = false // RTLO3c

  fun notifyUpdated(update: Any) {
    // TODO: Implement event emission for updates
    Log.v(tag, "Object $objectId updated: $update")
  }

  /**
   * Checks if an operation can be applied based on serial comparison.
   *
   * @spec RTLO4a - Serial comparison logic for LiveMap/LiveCounter operations
   */
  protected fun canApplyOperation(siteCode: String?, serial: String?): Boolean {
    if (serial.isNullOrEmpty()) {
      throw objectError("Invalid serial: $serial") // RTLO4a3
    }
    if (siteCode.isNullOrEmpty()) {
      throw objectError("Invalid site code: $siteCode") // RTLO4a3
    }
    val existingSiteSerial = siteTimeserials[siteCode] // RTLO4a4
    return existingSiteSerial == null || serial > existingSiteSerial // RTLO4a5, RTLO4a6
  }

  /**
   * Updates the time serial for a given site code.
   */
  protected fun updateTimeSerial(opSiteCode: String, opSerial: String) {
    siteTimeserials[opSiteCode] = opSerial
  }

  /**
   * Applies object delete operation.
   *
   * @spec RTLM10/RTLC10 - Object deletion for LiveMap/LiveCounter
   */
  protected fun applyObjectDelete(): Any {
    return tombstone()
  }

  /**
   * Marks the object as tombstoned.
   *
   * @spec RTLM11/RTLC11 - Tombstone functionality for LiveMap/LiveCounter
   */
  protected fun tombstone(): Any {
    isTombstoned = true
    tombstonedAt = System.currentTimeMillis()
    val update = clearData()
    // TODO: Emit lifecycle events
    return update
  }

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object_sync`
   * @return an update describing the changes
   * @spec RTLM6/RTLC6 - Overrides ObjectMessage with object data state from sync to LiveMap/LiveCounter
   */
  abstract fun overrideWithObjectState(objectState: ObjectState): Map<String, Any>

  /**
   * This is invoked by ObjectMessage having updated data with parent `ProtocolMessageAction` as `object`
   * @return an update describing the changes
   * @spec RTLM7/RTLC7 - Applies ObjectMessage with object data operations to LiveMap/LiveCounter
   */
  abstract fun applyOperation(operation: ObjectOperation, message: ObjectMessage)

  /**
   * Clears the object's data and returns an update describing the changes.
   */
  abstract fun clearData(): Map<String, Any>

  /**
   * Called during garbage collection intervals.
   */
  abstract fun onGCInterval()
}
