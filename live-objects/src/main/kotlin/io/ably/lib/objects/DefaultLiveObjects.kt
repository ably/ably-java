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

  /**
   * Handles a ProtocolMessage containing proto action as `object` or `object_sync`.
   */
  fun handle(protocolMessage: ProtocolMessage) {
    // RTL15b
    if (protocolMessage.action === ProtocolMessage.Action.`object`) {
      setChannelSerial(protocolMessage.channelSerial)
    }

    if (protocolMessage.state == null || protocolMessage.state.isEmpty()) {
      Log.w(tag, "Received ProtocolMessage with null or empty object state, ignoring")
      return
    }

    // OM2 - Populate missing fields from parent
    val objects = protocolMessage.state.filterIsInstance<ObjectMessage>().mapIndexed { index, objMsg ->
      objMsg.copy(
        connectionId = objMsg.connectionId ?: protocolMessage.connectionId, // OM2c
        timestamp = objMsg.timestamp ?: protocolMessage.timestamp, // OM2e
        id = objMsg.id ?: (protocolMessage.id + ':' + index) // OM2a
      )
    }
  }

  private fun setChannelSerial(channelSerial: String?) {
    if (channelSerial.isNullOrEmpty()) {
      Log.w(tag, "setChannelSerial called with null or empty value, ignoring")
      return
    }
    Log.v(tag, "Setting channel serial for channelName: $channelName, value: $channelSerial")
    adapter.setChannelSerial(channelName, channelSerial)
  }

  fun dispose() {
    // Dispose of any resources associated with this LiveObjects instance
    // For example, close any open connections or clean up references
  }
}
