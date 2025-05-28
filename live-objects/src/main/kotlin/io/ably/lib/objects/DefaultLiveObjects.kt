package io.ably.lib.objects

import io.ably.lib.types.Callback
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.util.Log

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

  fun handle(msg: ProtocolMessage) {
    // RTL15b
    msg.channelSerial?.let {
      if (msg.action === ProtocolMessage.Action.`object`) {
        Log.v(tag, "Setting channel serial for channelName: $channelName, value: ${msg.channelSerial}")
        adapter.setChannelSerial(channelName, msg.channelSerial)
      }
    }
  }

  fun dispose() {
    // Dispose of any resources associated with this LiveObjects instance
    // For example, close any open connections or clean up references
  }
}
