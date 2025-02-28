package io.ably.lib.rest

import com.ably.annotations.InternalAPI
import io.ably.lib.http.Http
import io.ably.lib.http.HttpCore
import io.ably.lib.types.*

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun AblyBase.time(http: Http): Long = time(http)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun AblyBase.timeAsync(http: Http, callback: Callback<Long>): Unit = timeAsync(http, callback)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun AblyBase.stats(http: Http, params: Array<Param>): PaginatedResult<Stats> = stats(http, params)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun AblyBase.statsAsync(http: Http, params: Array<Param>, callback: Callback<AsyncPaginatedResult<Stats>>): Unit =
  this.statsAsync(http, params, callback)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun AblyBase.request(
  http: Http,
  method: String,
  path: String,
  params: Array<Param>?,
  body: HttpCore.RequestBody?,
  headers: Array<Param>?
): HttpPaginatedResponse = this.request(http, method, path, params, body, headers)

@InternalAPI
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public fun AblyBase.requestAsync(
  http: Http,
  method: String?,
  path: String?,
  params: Array<Param>?,
  body: HttpCore.RequestBody?,
  headers: Array<Param>?,
  callback: AsyncHttpPaginatedResponse.Callback?
): Unit = this.requestAsync(http, method, path, params, body, headers, callback)
