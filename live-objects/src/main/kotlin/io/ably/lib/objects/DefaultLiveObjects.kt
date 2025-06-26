package io.ably.lib.objects

import io.ably.lib.types.Callback
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Log
import java.util.*

internal class DefaultLiveObjects(private val channelName: String, private val adapter: LiveObjectsAdapter): LiveObjects {
  private val tag = DefaultLiveObjects::class.simpleName

  override fun getRoot(): LiveMap {
    TODO("Not yet implemented")
  }

  override fun createMap(liveMap: LiveMap): LiveMap {
    TODO("Not yet implemented")
  }

  override fun createMap(liveCounter: LiveCounter): LiveMap {
    TODO("Not yet implemented")
  }

  override fun createMap(map: MutableMap<String, Any>): LiveMap {
    TODO("Not yet implemented")
  }

  override fun getRootAsync(callback: Callback<LiveMap>) {
    TODO("Not yet implemented")
  }

  override fun createMapAsync(liveMap: LiveMap, callback: Callback<LiveMap>) {
    TODO("Not yet implemented")
  }

  override fun createMapAsync(liveCounter: LiveCounter, callback: Callback<LiveMap>) {
    TODO("Not yet implemented")
  }

  override fun createMapAsync(map: MutableMap<String, Any>, callback: Callback<LiveMap>) {
    TODO("Not yet implemented")
  }

  override fun createCounterAsync(initialValue: Long, callback: Callback<LiveCounter>) {
    TODO("Not yet implemented")
  }

  override fun createCounter(initialValue: Long): LiveCounter {
    TODO("Not yet implemented")
  }

  fun handle(protocolMessage: ProtocolMessage) {
    // RTL15b
    protocolMessage.channelSerial?.let {
      if (protocolMessage.action === ProtocolMessage.Action.`object`) {
        Log.v(tag, "Setting channel serial for channelName: $channelName, value: ${protocolMessage.channelSerial}")
        adapter.setChannelSerial(channelName, protocolMessage.channelSerial)
      }
    }
    // Populate missing fields from parent
    val objects = protocolMessage.state.filterIsInstance<ObjectMessage>().mapIndexed { index, stateItem ->
      stateItem.copy(
        connectionId = stateItem.connectionId ?: protocolMessage.connectionId,
        timestamp = stateItem.timestamp ?: protocolMessage.timestamp,
        id = stateItem.id ?: (protocolMessage.id + ':' + index)
      )
    }
  }

  fun dispose() {
    // Dispose of any resources associated with this LiveObjects instance
    // For example, close any open connections or clean up references
  }
}
