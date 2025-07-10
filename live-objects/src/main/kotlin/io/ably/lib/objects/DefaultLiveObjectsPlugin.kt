package io.ably.lib.objects

import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.ProtocolMessage
import java.util.concurrent.ConcurrentHashMap

public class DefaultLiveObjectsPlugin(private val adapter: LiveObjectsAdapter) : LiveObjectsPlugin {

  private val liveObjects = ConcurrentHashMap<String, DefaultLiveObjects>()

  override fun getInstance(channelName: String): LiveObjects {
    return liveObjects.getOrPut(channelName) { DefaultLiveObjects(channelName, adapter) }
  }

  override fun handle(msg: ProtocolMessage) {
    val channelName = msg.channel
    liveObjects[channelName]?.handle(msg)
  }

  override fun handleStateChange(channelName: String, state: ChannelState, hasObjects: Boolean) {
    liveObjects[channelName]?.handleStateChange(state, hasObjects)
  }

  override fun dispose(channelName: String) {
    liveObjects[channelName]?.dispose()
    liveObjects.remove(channelName)
  }

  override fun dispose() {
    liveObjects.values.forEach {
      it.dispose()
    }
    liveObjects.clear()
  }
}
