package io.ably.lib.rest

import com.ably.http.HttpMethod
import com.ably.pubsub.*
import com.ably.query.OrderBy
import com.ably.query.TimeUnit
import io.ably.lib.buildStatsParams
import io.ably.lib.http.HttpCore
import io.ably.lib.push.Push
import io.ably.lib.types.*

/**
 * Wrapper for Rest client
 */
public fun RestClient(javaClient: AblyRest): RestClient = RestClientAdapter(javaClient)

internal class RestClientAdapter(private val javaClient: AblyRest) : RestClient, SdkWrapperCompatible<RestClient> {
  override val channels: Channels<out RestChannel>
    get() = RestChannelsAdapter(javaClient.channels)
  override val auth: Auth
    get() = javaClient.auth
  override val options: ClientOptions
    get() = javaClient.options
  override val push: Push
    get() = javaClient.push

  override fun time(): Long = javaClient.time()

  override fun timeAsync(callback: Callback<Long>) = javaClient.timeAsync(callback)

  override fun stats(
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
    unit: TimeUnit
  ): PaginatedResult<Stats> = javaClient.stats(buildStatsParams(start, end, limit, orderBy, unit).toTypedArray())

  override fun statsAsync(
    callback: Callback<AsyncPaginatedResult<Stats>>,
    start: Long?,
    end: Long?,
    limit: Int,
    orderBy: OrderBy,
    unit: TimeUnit
  ) = javaClient.statsAsync(buildStatsParams(start, end, limit, orderBy, unit).toTypedArray(), callback)

  override fun request(
    path: String,
    method: HttpMethod,
    params: List<Param>,
    body: HttpCore.RequestBody?,
    headers: List<Param>,
  ) = javaClient.request(method.toString(), path, params.toTypedArray(), body, headers.toTypedArray())!!

  override fun requestAsync(
    path: String,
    callback: AsyncHttpPaginatedResponse.Callback,
    method: HttpMethod,
    params: List<Param>,
    body: HttpCore.RequestBody?,
    headers: List<Param>,
  ) = javaClient.requestAsync(method.toString(), path, params.toTypedArray(), body, headers.toTypedArray(), callback)

  override fun close() = javaClient.close()

  override fun createWrapperSdkProxy(options: WrapperSdkProxyOptions): RestClient {
    val httpCoreWithAgents = javaClient.httpCore.injectDynamicAgents(options.agents)
    val httpModule = javaClient.http.exchangeHttpCore(httpCoreWithAgents)
    return WrapperRestClient(javaClient, this, httpModule, options.agents)
  }
}
