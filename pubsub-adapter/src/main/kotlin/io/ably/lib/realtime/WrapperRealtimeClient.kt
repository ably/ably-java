package io.ably.lib.realtime

import com.ably.pubsub.Channels
import com.ably.pubsub.RealtimeChannel
import com.ably.pubsub.RealtimeClient
import com.ably.pubsub.RealtimePresence
import com.ably.query.OrderBy
import io.ably.lib.buildHistoryParams
import io.ably.lib.http.Http
import io.ably.lib.types.*

internal class WrapperRealtimeClient(
    private val javaClient: AblyRealtime,
    private val httpModule: Http,
    private val agents: Map<String, String>,
) : RealtimeClient by RealtimeClientAdapter(javaClient) {
    override val channels: Channels<out RealtimeChannel>
        get() = WrapperRealtimeChannels(javaClient.channels, httpModule, agents)
}

internal class WrapperRealtimeChannels(
    private val javaChannels: AblyRealtime.Channels,
    private val httpModule: Http,
    private val agents: Map<String, String>,
) :
    Channels<RealtimeChannel> by RealtimeChannelsAdapter(javaChannels) {

    override fun get(name: String): RealtimeChannel {
        if (javaChannels.containsKey(name)) return WrapperRealtimeChannel(javaChannels.get(name), httpModule)
        return try {
            WrapperRealtimeChannel(javaChannels.get(name, ChannelOptions().injectAgents(agents)), httpModule)
        } catch (e: AblyException) {
            WrapperRealtimeChannel(javaChannels.get(name), httpModule)
        }

    }

    override fun get(name: String, options: ChannelOptions): RealtimeChannel =
        WrapperRealtimeChannel(javaChannels.get(name, options.injectAgents(agents)), httpModule)
}

internal class WrapperRealtimeChannel(private val javaChannel: Channel, private val httpModule: Http) :
    RealtimeChannel by RealtimeChannelAdapter(javaChannel) {

    override val presence: RealtimePresence
        get() = WrapperRealtimePresence(javaChannel.presence, httpModule)

    override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<Message> =
        javaChannel.history(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray())

    override fun historyAsync(
        callback: Callback<AsyncPaginatedResult<Message>>,
        start: Long?,
        end: Long?,
        limit: Int,
        orderBy: OrderBy,
    ) =
        javaChannel.historyAsync(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}

internal class WrapperRealtimePresence(private val javaPresence: Presence, private val httpModule: Http) :
    RealtimePresence by RealtimePresenceAdapter(javaPresence) {

    override fun history(start: Long?, end: Long?, limit: Int, orderBy: OrderBy): PaginatedResult<PresenceMessage> =
        javaPresence.history(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray())

    override fun historyAsync(
        callback: Callback<AsyncPaginatedResult<PresenceMessage>>,
        start: Long?,
        end: Long?,
        limit: Int,
        orderBy: OrderBy,
    ) =
        javaPresence.historyAsync(httpModule, buildHistoryParams(start, end, limit, orderBy).toTypedArray(), callback)
}

private fun ChannelOptions.injectAgents(agents: Map<String, String>): ChannelOptions {
    val options = ChannelOptions()
    options.params = (this.params ?: mapOf()) + mapOf(
        "agent" to agents.map { "${it.key}/${it.value}" }.joinToString(" "),
    )
    options.modes = modes
    options.cipherParams = cipherParams
    options.attachOnSubscribe = attachOnSubscribe
    options.encrypted = encrypted
    return options
}
