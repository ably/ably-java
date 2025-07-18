package io.ably.lib.objects

import io.ably.lib.objects.type.ObjectType

internal class ObjectId private constructor(
  internal val type: ObjectType,
  private val hash: String,
  private val timestampMs: Long
) {
  /**
   * Converts ObjectId to string representation.
   */
  override fun toString(): String {
    return "${type.value}:$hash@$timestampMs"
  }

  companion object {
    /**
     * Creates ObjectId instance from hashed object id string.
     */
    fun fromString(objectId: String): ObjectId {
      if (objectId.isEmpty()) {
        throw objectError("Invalid object id: $objectId")
      }

      // Parse format: type:hash@msTimestamp
      val parts = objectId.split(':')
      if (parts.size != 2) {
        throw objectError("Invalid object id: $objectId")
      }

      val (typeStr, rest) = parts

      val type = when (typeStr) {
        "map" -> ObjectType.Map
        "counter" -> ObjectType.Counter
        else -> throw objectError("Invalid object type in object id: $objectId")
      }

      val hashAndTimestamp = rest.split('@')
      if (hashAndTimestamp.size != 2) {
        throw objectError("Invalid object id: $objectId")
      }

      val hash = hashAndTimestamp[0]

      if (hash.isEmpty()) {
        throw objectError("Invalid object id: $objectId")
      }

      val msTimestampStr = hashAndTimestamp[1]

      val msTimestamp = try {
        msTimestampStr.toLong()
      } catch (e: NumberFormatException) {
        throw objectError("Invalid object id: $objectId", e)
      }

      return ObjectId(type, hash, msTimestamp)
    }
  }
}
