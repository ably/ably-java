package com.ably.query

/**
 * Represents direction to query messages in.
 */
public enum class OrderBy(public val direction: String) {

  /**
   * The response will include messages from the end of the time window to the start.
   */
  NewestFirst("backwards"),

  /**
   * The response will include messages from the start of the time window to the end.
   */
  OldestFirst("forwards"),
  ;
}
