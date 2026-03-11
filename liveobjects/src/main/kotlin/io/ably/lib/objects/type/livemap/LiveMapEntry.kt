package io.ably.lib.objects.type.livemap

import io.ably.lib.objects.*
import io.ably.lib.objects.ObjectData
import io.ably.lib.objects.ObjectsPool
import io.ably.lib.objects.ObjectsPoolDefaults
import io.ably.lib.objects.type.BaseRealtimeObject
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.map.LiveMap
import io.ably.lib.objects.type.map.LiveMapValue
import java.util.Base64

/**
 * @spec RTLM3 - Map data structure storing entries
 */
internal data class LiveMapEntry(
  val isTombstoned: Boolean = false,
  val tombstonedAt: Long? = null,
  val timeserial: String? = null,
  val data: ObjectData? = null
)

/**
 * Checks if entry is directly tombstoned or references a tombstoned object. Spec: RTLM14
 * @param objectsPool The object pool containing referenced DefaultRealtimeObjects
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
 * a reference to another RealtimeObject from the pool if it stores an objectId.
 */
internal fun LiveMapEntry.getResolvedValue(objectsPool: ObjectsPool): LiveMapValue? {
  if (isTombstoned) { return null } // RTLM5d2a

  data?.let { d -> // RTLM5d2b, RTLM5d2c, RTLM5d2d, RTLM5d2e
    d.string?.let { return LiveMapValue.of(it) }
    d.number?.let { return LiveMapValue.of(it) }
    d.boolean?.let { return LiveMapValue.of(it) }
    d.bytes?.let { return LiveMapValue.of(Base64.getDecoder().decode(it)) }
    d.json?.let { parsed ->
      return when {
        parsed.isJsonObject -> LiveMapValue.of(parsed.asJsonObject)
        parsed.isJsonArray -> LiveMapValue.of(parsed.asJsonArray)
        else -> null
      }
    }
    d.objectId?.let { refId -> // RTLM5d2f - has an objectId reference
      objectsPool.get(refId)?.let { refObject ->
        if (refObject.isTombstoned) {
          return null // tombstoned objects must not be surfaced to the end users
        }
        return fromRealtimeObject(refObject) // RTLM5d2f2
      }
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

private fun fromRealtimeObject(realtimeObject: BaseRealtimeObject): LiveMapValue {
  return when (realtimeObject.objectType) {
    ObjectType.Map -> LiveMapValue.of(realtimeObject as LiveMap)
    ObjectType.Counter -> LiveMapValue.of(realtimeObject as LiveCounter)
  }
}
