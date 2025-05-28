package io.ably.lib.objects

import com.google.gson.JsonArray
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
    val objectMessages = msg.state?.map { it.toObjectMessage() } ?: emptyList()
    Log.v(tag, "Received ${objectMessages.size} object messages for channelName: $channelName")
    objectMessages.forEach { Log.v(tag, "Object message: $it") }
  }

  suspend fun send(message: ObjectMessage) {
    Log.v(tag, "Sending message for channelName: $channelName, message: $message")
    val protocolMsg = ProtocolMessage().apply {
      state = JsonArray().apply {
        add(message.toJsonObject())
      }
    }
    adapter.sendAsync(protocolMsg)
  }

  fun dispose() {
    // Dispose of any resources associated with this LiveObjects instance
    // For example, close any open connections or clean up references
  }
}
