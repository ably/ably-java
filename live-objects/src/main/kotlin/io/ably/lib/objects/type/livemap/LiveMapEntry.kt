package io.ably.lib.objects.type.livemap

import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectsPool
import io.ably.lib.objects.ObjectsPoolDefaults

/**
 * @spec RTLM3 - Map data structure storing entries
 */
internal data class LiveMapEntry(
  var isTombstoned: Boolean = false,
  var tombstonedAt: Long? = null,
  var timeserial: String? = null,
  var data: ObjectData? = null
)

/**
 * Checks if entry is directly tombstoned or references a tombstoned object. Spec: RTLM14
 * @param objectsPool The object pool containing referenced LiveObjects
 */
internal fun LiveMapEntry.isEntryOrRefTombstoned(objectsPool: ObjectsPool): Boolean {
  if (isTombstoned) {
    return true // RTLM14a
  }
  data?.objectId?.let { refId -> // RTLM5d2f -has an objectId reference
    objectsPool.get(refId)?.let { refObject ->
      if (refObject.isTombstoned) {
        return true
      }
    }
  }
  return false // RTLM14b
}

/**
 * Returns value as is if object data stores a primitive type or
 * a reference to another LiveObject from the pool if it stores an objectId.
 */
internal fun LiveMapEntry.getResolvedValue(objectsPool: ObjectsPool): Any? {
  if (isTombstoned) {
    return null // RTLM5d2a
  }
  data?.value?.let { primitiveValue ->
    return primitiveValue // RTLM5d2b, RTLM5d2c, RTLM5d2d, RTLM5d2e
  }
  data?.objectId?.let { refId -> // RTLM5d2f -has an objectId reference
    objectsPool.get(refId)?.let { refObject ->
      if (refObject.isTombstoned) {
        return null // tombstoned objects must not be surfaced to the end users
      }
      return refObject // RTLM5d2f2
    }
  }
  return null // RTLM5d2g, RTLM5d2f1
}

/**
 * Extension function to check if a LiveMapEntry is expired and ready for garbage collection
 */
internal fun LiveMapEntry.isEligibleForGc(): Boolean {
  val currentTime = System.currentTimeMillis()
  return isTombstoned && tombstonedAt?.let { currentTime - it >= ObjectsPoolDefaults.GC_GRACE_PERIOD_MS } == true
}
