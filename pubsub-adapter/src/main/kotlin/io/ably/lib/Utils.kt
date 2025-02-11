package io.ably.lib

import com.ably.query.OrderBy
import com.ably.query.TimeUnit
import io.ably.lib.types.Param

fun buildStatsParams(
  start: Long?,
  end: Long?,
  limit: Int,
  orderBy: OrderBy,
  unit: TimeUnit,
) = buildList {
  addAll(buildHistoryParams(start, end, limit, orderBy))
  add(Param("unit", unit.toString()))
}

fun buildHistoryParams(
  start: Long?,
  end: Long?,
  limit: Int,
  orderBy: OrderBy,
) = buildList {
  start?.let { add(Param("start", it)) }
  end?.let { add(Param("end", it)) }
  add(Param("limit", limit))
  add(Param("direction", orderBy.direction))
}

fun buildRestPresenceParams(
  limit: Int,
  clientId: String?,
  connectionId: String?,
) = buildList {
  add(Param("limit", limit))
  clientId?.let { add(Param("clientId", it)) }
  connectionId?.let { add(Param("connectionId", it)) }
}
