package io.ably.lib.objects

/**
 * @spec RTO5 - SyncTracker class for tracking objects sync status
 */
internal class ObjectsSyncTracker(syncChannelSerial: String?) {
  private val syncSerial: String? = syncChannelSerial
  internal val syncId: String?
  internal val syncCursor: String?

  init {
    val parsed = parseSyncChannelSerial(syncChannelSerial)
    syncId = parsed.first
    syncCursor = parsed.second
  }

  /**
   * Checks if a new sync sequence has started.
   *
   * @param prevSyncId The previously stored sync ID
   * @return true if a new sync sequence has started, false otherwise
   *
   * Spec: RTO5a5, RTO5a2
   */
  internal fun hasSyncStarted(prevSyncId: String?): Boolean {
    return syncSerial.isNullOrEmpty() || prevSyncId != syncId
  }

  /**
   * Checks if the current sync sequence has ended.
   *
   * @return true if the sync sequence has ended, false otherwise
   *
   * Spec: RTO5a5, RTO5a4
   */
  internal fun hasSyncEnded(): Boolean {
    return syncSerial.isNullOrEmpty() || syncCursor.isNullOrEmpty()
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
