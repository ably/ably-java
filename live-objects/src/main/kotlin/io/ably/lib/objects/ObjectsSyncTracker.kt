package io.ably.lib.objects

/**
 * @spec RTO5 - SyncTracker class for tracking objects sync status
 */
internal class ObjectsSyncTracker(syncChannelSerial: String?) {
  internal val syncId: String?
  private val syncCursor: String?
  private val hasEnded: Boolean

  init {
    val parsed = parseSyncChannelSerial(syncChannelSerial)
    syncId = parsed.first
    syncCursor = parsed.second
    hasEnded = syncChannelSerial.isNullOrEmpty() || syncCursor.isNullOrEmpty()
  }

  /**
   * Checks if a new sync sequence has started.
   *
   * @param prevSyncId The previously stored sync ID
   * @return true if a new sync sequence has started, false otherwise
   */
  internal fun hasSyncStarted(prevSyncId: String?): Boolean {
    return prevSyncId != syncId
  }

  /**
   * Checks if the current sync sequence has ended.
   *
   * @return true if the sync sequence has ended, false otherwise
   */
  internal fun hasSyncEnded(): Boolean {
    return hasEnded
  }

  companion object {
    /**
     * Parses sync channel serial to extract syncId and syncCursor.
     *
     * @param syncChannelSerial The sync channel serial to parse
     * @return Pair of syncId and syncCursor, both null if parsing fails
     */
    private fun parseSyncChannelSerial(syncChannelSerial: String?): Pair<String?, String?> {
      if (syncChannelSerial.isNullOrEmpty()) {
        return Pair(null, null)
      }

      // RTO5a1 - syncChannelSerial is a two-part identifier: <sequence id>:<cursor value>
      val match = Regex("^([\\w-]+):(.*)$").find(syncChannelSerial)
      return if (match != null) {
        val syncId = match.groupValues[1]
        val syncCursor = match.groupValues[2]
        Pair(syncId, syncCursor)
      } else {
        Pair(null, null)
      }
    }
  }
}
