package com.ably.query

/**
 * The period for which the stats query will be aggregated by,
 * values supported are minute, hour, day or month; if omitted the unit defaults
 * to the REST API default (minute)
 */
public enum class TimeUnit(private val unit: String) {
  Minute("minute"),
  Hour("hour"),
  Day("day"),
  Month("month"),
  ;

  override fun toString(): String = unit
}
